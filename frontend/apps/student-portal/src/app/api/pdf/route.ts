import { NextRequest, NextResponse } from "next/server";
import { BlobServiceClient } from "@azure/storage-blob";
import { PDFDocument, StandardFonts, degrees, rgb } from "pdf-lib";

export const runtime = "nodejs"; // important: Azure SDK needs Node runtime (not Edge)
export const dynamic = "force-dynamic";
export const revalidate = 0;

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

  // Prevent PDF viewers from auto-linking as `mailto:` using ASCII-only text
  // (Helvetica in pdf-lib is WinAnsi and cannot encode zero-width chars).
  const text = email
    .trim()
    .slice(0, 200)
    .replace(/@/g, " [at] ")
    .replace(/\./g, " [dot] ");

  for (const page of pdfDoc.getPages()) {
    const { width, height } = page.getSize();
    // Smaller font, more repetition
    const fontSize = Math.min(12, Math.max(8, Math.min(width, height) / 70));
    const textWidth = font.widthOfTextAtSize(text, fontSize);

    // Dense tiling across the entire page, with two angles to survive cropping
    const stepX = Math.max(60, textWidth + 24);
    const stepY = Math.max(38, fontSize * 2.6);
    const angles = [degrees(-35), degrees(35)];

    for (const angle of angles) {
      for (let y = -height; y < height * 2; y += stepY) {
        const rowOffset = (Math.floor(y / stepY) % 2) * (stepX / 2);
        for (let x = -width; x < width * 2; x += stepX) {
          page.drawText(text, {
            x: x + rowOffset,
            y,
            size: fontSize,
            font,
            color: rgb(0.5, 0.5, 0.5),
            rotate: angle,
            opacity: 0.12,
          });
        }
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
