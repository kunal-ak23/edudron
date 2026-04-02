package com.datagami.edudron.student.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Helper for uploading/downloading certificate PDFs to/from Azure Blob Storage.
 */
@Component
public class MediaUploadHelper {

    private static final Logger log = LoggerFactory.getLogger(MediaUploadHelper.class);

    private final BlobServiceClient blobServiceClient;

    @Value("${azure.storage.container-name:edudron-media}")
    private String containerName;

    public MediaUploadHelper(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    /**
     * Upload a certificate PDF to Azure Blob Storage.
     *
     * @param tenantId     tenant UUID string
     * @param credentialId unique credential ID
     * @param pdfBytes     PDF content
     * @return blob URL of the uploaded file
     */
    public String uploadCertificatePdf(String tenantId, String credentialId, byte[] pdfBytes) {
        if (blobServiceClient == null) {
            log.warn("BlobServiceClient is not configured. Skipping PDF upload for credential {}", credentialId);
            return null;
        }

        String blobName = tenantId + "/certificates/" + credentialId + ".pdf";

        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            blobClient.upload(new ByteArrayInputStream(pdfBytes), pdfBytes.length, true);

            String url = blobClient.getBlobUrl();
            log.info("Uploaded certificate PDF: {} ({} bytes)", blobName, pdfBytes.length);
            return url;
        } catch (Exception e) {
            log.error("Failed to upload certificate PDF for credential {}: {}", credentialId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload certificate PDF", e);
        }
    }

    /**
     * Download a file from Azure Blob Storage by URL.
     *
     * @param blobUrl the full blob URL
     * @return file bytes
     */
    public byte[] downloadFile(String blobUrl) {
        if (blobServiceClient == null) {
            throw new IllegalStateException("BlobServiceClient is not configured");
        }

        try {
            // Extract blob name from URL: everything after the container name
            String blobName = extractBlobName(blobUrl);
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            blobClient.downloadStream(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to download blob from URL {}: {}", blobUrl, e.getMessage(), e);
            throw new RuntimeException("Failed to download file from storage", e);
        }
    }

    /**
     * Upload a template asset (image) to Azure Blob Storage.
     *
     * @param tenantId  tenant UUID string
     * @param fileName  original file name
     * @param data      file bytes
     * @param contentType MIME type
     * @return blob URL of the uploaded file
     */
    public String uploadTemplateAsset(String tenantId, String fileName, byte[] data, String contentType) {
        if (blobServiceClient == null) {
            log.warn("BlobServiceClient is not configured. Skipping asset upload for {}", fileName);
            return null;
        }

        String blobName = tenantId + "/certificate-templates/assets/" + System.currentTimeMillis() + "-" + fileName;

        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            blobClient.upload(new ByteArrayInputStream(data), data.length, true);

            String url = blobClient.getBlobUrl();
            log.info("Uploaded template asset: {} ({} bytes)", blobName, data.length);
            return url;
        } catch (Exception e) {
            log.error("Failed to upload template asset {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Failed to upload template asset", e);
        }
    }

    /**
     * Extract blob name from a full blob URL.
     * URL format: https://account.blob.core.windows.net/container/path/to/blob
     */
    private String extractBlobName(String blobUrl) {
        // Find the container name in the URL and extract everything after it
        int containerIdx = blobUrl.indexOf("/" + containerName + "/");
        if (containerIdx >= 0) {
            return blobUrl.substring(containerIdx + containerName.length() + 2);
        }
        // Fallback: try to extract from the last path segments
        String[] parts = blobUrl.split("/");
        if (parts.length >= 3) {
            // Assume last 3 segments are container/tenant/certificates/file.pdf
            StringBuilder sb = new StringBuilder();
            // Skip scheme + host + container
            boolean foundContainer = false;
            for (String part : parts) {
                if (foundContainer) {
                    if (sb.length() > 0) sb.append("/");
                    sb.append(part);
                }
                if (part.equals(containerName)) {
                    foundContainer = true;
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        throw new IllegalArgumentException("Cannot extract blob name from URL: " + blobUrl);
    }
}
