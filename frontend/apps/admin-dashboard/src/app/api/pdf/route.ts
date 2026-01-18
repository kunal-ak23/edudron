import { NextRequest, NextResponse } from "next/server";
import { BlobServiceClient } from "@azure/storage-blob";
import { PDFDocument, StandardFonts, degrees, rgb } from "pdf-lib";

export const runtime = "nodejs"; // Azure SDK needs Node runtime (not Edge)

function getBlobServiceClient() {
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
    const fontSize = Math.max(18, Math.min(width, height) / 12);
    const textWidth = font.widthOfTextAtSize(text, fontSize);

    const x = Math.max(12, (width - textWidth) / 2);
    const y = height / 2;

    page.drawText(text, {
      x,
      y,
      size: fontSize,
      font,
      color: rgb(0.65, 0.65, 0.65),
      rotate: degrees(-35),
      opacity: 0.18,
    });
  }

  return await pdfDoc.save();
}

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const blobName = searchParams.get("blob");
    if (!blobName) return new NextResponse("Missing blob parameter", { status: 400 });

    // TEMP: email comes from query param; in production, derive from session/JWT instead
    const email = searchParams.get("email");
    if (!email) return new NextResponse("Missing email parameter", { status: 400 });

    const containerName = process.env.AZURE_CONTAINER_NAME!;
    if (!containerName) {
      return new NextResponse("AZURE_CONTAINER_NAME environment variable is not set", { status: 500 });
    }

    const blobService = getBlobServiceClient();
    const containerClient = blobService.getContainerClient(containerName);
    const blobClient = containerClient.getBlobClient(blobName);

    const exists = await blobClient.exists();
    if (!exists) return new NextResponse("Blob not found", { status: 404 });

    const originalPdf = await blobClient.downloadToBuffer();
    const watermarkedPdf = await addEmailWatermarkToPdf(originalPdf, email);

    const filename = blobName.split("/").pop() || "document.pdf";

    return new NextResponse(Buffer.from(watermarkedPdf), {
      headers: {
        "Content-Type": "application/pdf",
        "Content-Disposition": `inline; filename="${filename}"`,
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    console.error("Error streaming watermarked PDF from Azure:", error);
    return new NextResponse(
      error instanceof Error ? error.message : "Internal server error",
      { status: 500 }
    );
  }
}

import { NextRequest, NextResponse } from "next/server";
import { BlobServiceClient } from "@azure/storage-blob";

export const runtime = "nodejs"; // important: Azure SDK needs Node runtime (not Edge)

function getBlobServiceClient() {
  // Prefer Managed Identity in production if possible; simplest shown is connection string:
  const conn = process.env.AZURE_STORAGE_CONNECTION_STRING!;
  if (!conn) {
    throw new Error("AZURE_STORAGE_CONNECTION_STRING environment variable is not set");
  }
  return BlobServiceClient.fromConnectionString(conn);
}

export async function GET(req: NextRequest) {
  try {
    console.info("[api/pdf] request:", req.url);
    // 1) AuthZ gate (do your real auth here)
    // e.g. verify session / JWT and check user can access this doc
    // if (!user) return new NextResponse("Unauthorized", { status: 401 });

    const { searchParams } = new URL(req.url);
    const blobName = searchParams.get("blob"); // e.g. "docs/abc.pdf"
    if (!blobName) {
      return new NextResponse(`Missing blob parameter. url=${req.url}`, { status: 400 });
    }
    console.info("[api/pdf] blob:", blobName);

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
      return new NextResponse(`Blob not found. blob=${blobName} url=${req.url}`, { status: 404 });
    }

    // Download stream
    const downloadResp = await blobClient.download();
    const stream = downloadResp.readableStreamBody;
    if (!stream) {
      return new NextResponse("Failed to read blob stream", { status: 500 });
    }

    // Convert Node stream -> Web stream for Next Response
    const webStream = new ReadableStream({
      start(controller) {
        stream.on("data", (chunk) => controller.enqueue(chunk));
        stream.on("end", () => controller.close());
        stream.on("error", (err) => controller.error(err));
      },
      cancel() {
      // `readableStreamBody` is a Node stream in Node runtime, but typings can vary.
      // Be defensive here.
      (stream as any)?.destroy?.();
      },
    });

    // Get the filename from blob name for Content-Disposition
    const filename = blobName.split("/").pop() || "document.pdf";

    return new NextResponse(webStream, {
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

