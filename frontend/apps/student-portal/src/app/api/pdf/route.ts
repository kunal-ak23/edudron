import { NextRequest, NextResponse } from "next/server";
import { BlobServiceClient } from "@azure/storage-blob";
import { PDFDocument, StandardFonts, degrees, rgb } from "pdf-lib";

export const runtime = "nodejs"; // important: Azure SDK needs Node runtime (not Edge)

function getBlobServiceClient() {
  // Prefer Managed Identity in production if possible; simplest shown is connection string:
  const conn = process.env.AZURE_STORAGE_CONNECTION_STRING!;
  if (!conn) {
    throw new Error("AZURE_STORAGE_CONNECTION_STRING environment variable is not set");
  }
  return BlobServiceClient.fromConnectionString(conn);
}

async function addEmailWatermarkToPdf(pdfBytes: Uint8Array, email: string) {
  const pdfDoc = await PDFDocument.load(pdfBytes);
  const font = await pdfDoc.embedFont(StandardFonts.Helvetica);

  const text = email.trim().slice(0, 200);

  for (const page of pdfDoc.getPages()) {
    const { width, height } = page.getSize();
    const fontSize = Math.max(14, Math.min(width, height) / 18);
    const textWidth = font.widthOfTextAtSize(text, fontSize);

    // Tile the watermark across the entire page (diagonal)
    const stepX = Math.max(120, textWidth + 140);
    const stepY = Math.max(120, fontSize * 4);
    const angle = degrees(-35);

    for (let y = -height; y < height * 2; y += stepY) {
      const rowOffset = (Math.floor(y / stepY) % 2) * (stepX / 2);
      for (let x = -width; x < width * 2; x += stepX) {
        page.drawText(text, {
          x: x + rowOffset,
          y,
          size: fontSize,
          font,
          color: rgb(0.55, 0.55, 0.55),
          rotate: angle,
          opacity: 0.12,
        });
      }
    }
  }

  return await pdfDoc.save();
}

export async function GET(req: NextRequest) {
  try {
    // 1) AuthZ gate (do your real auth here)
    // e.g. verify session / JWT and check user can access this doc
    // if (!user) return new NextResponse("Unauthorized", { status: 401 });

    const { searchParams } = new URL(req.url);
    const blobName = searchParams.get("blob"); // e.g. "docs/abc.pdf"
    if (!blobName) {
      return new NextResponse("Missing blob parameter", { status: 400 });
    }

    // TEMP: email comes from query param; in production, derive from session/JWT instead
    const email = searchParams.get("email");
    if (!email) {
      return new NextResponse("Missing email parameter", { status: 400 });
    }

    const containerName = process.env.AZURE_CONTAINER_NAME!;
    if (!containerName) {
      return new NextResponse("AZURE_CONTAINER_NAME environment variable is not set", { status: 500 });
    }

    const blobService = getBlobServiceClient();
    const containerClient = blobService.getContainerClient(containerName);
    const blobClient = containerClient.getBlobClient(blobName);

    // Optional: check exists
    const exists = await blobClient.exists();
    if (!exists) {
      return new NextResponse("Blob not found", { status: 404 });
    }

    const originalPdf = await blobClient.downloadToBuffer();
    const watermarkedPdf = await addEmailWatermarkToPdf(originalPdf, email);

    // Get the filename from blob name for Content-Disposition
    const filename = blobName.split("/").pop() || "document.pdf";

    return new NextResponse(Buffer.from(watermarkedPdf), {
      headers: {
        "Content-Type": "application/pdf",
        // inline (not attachment) to discourage "download as default"
        "Content-Disposition": `inline; filename="${filename}"`,
        "Cache-Control": "private, no-store",
        // optional hardening
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    console.error("Error streaming PDF from Azure:", error);
    return new NextResponse(
      error instanceof Error ? error.message : "Internal server error",
      { status: 500 }
    );
  }
}
