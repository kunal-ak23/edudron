package com.datagami.edudron.student.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.datagami.edudron.common.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class ProctoringPhotoService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProctoringPhotoService.class);
    
    @Autowired(required = false)
    private BlobServiceClient blobServiceClient;
    
    @Value("${azure.storage.proctoring-container-name:proctoring-photos}")
    private String containerName;
    
    @Value("${azure.storage.photo-retention-days:90}")
    private int retentionDays;
    
    /**
     * Upload a proctoring photo to Azure Blob Storage
     * 
     * @param base64Photo Base64 encoded photo data
     * @param submissionId The submission ID
     * @param photoType Type of photo (identity_verification or exam_capture)
     * @return URL of the uploaded photo
     */
    public String uploadPhoto(String base64Photo, String submissionId, String photoType) {
        if (blobServiceClient == null) {
            logger.warn("Azure Blob Storage is not configured. Skipping photo upload.");
            return "local://" + photoType + "/" + submissionId + "/" + UUID.randomUUID() + ".jpg";
        }
        
        try {
            String clientIdStr = TenantContext.getClientId();
            if (clientIdStr == null) {
                throw new IllegalStateException("Tenant context is not set");
            }
            
            // Get or create container
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
                logger.info("Created proctoring photos container: {}", containerName);
            }
            
            // Remove data URI prefix if present
            String photoData = base64Photo;
            if (base64Photo.contains(",")) {
                photoData = base64Photo.split(",")[1];
            }
            
            // Decode base64
            byte[] photoBytes = Base64.getDecoder().decode(photoData);
            
            // Generate blob name: {clientId}/{submissionId}/{photoType}/{timestamp}.jpg
            String timestamp = String.valueOf(System.currentTimeMillis());
            String blobName = String.format("%s/%s/%s/%s.jpg", clientIdStr, submissionId, photoType, timestamp);
            
            // Upload blob
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType("image/jpeg");
            
            blobClient.upload(BinaryData.fromBytes(photoBytes), true);
            blobClient.setHttpHeaders(headers);
            
            // Generate SAS URL (valid for retention period)
            String sasUrl = generateSasUrl(blobClient);
            
            logger.info("Uploaded proctoring photo: {} for submission: {}", blobName, submissionId);
            return sasUrl;
            
        } catch (Exception e) {
            logger.error("Failed to upload proctoring photo for submission: {}", submissionId, e);
            throw new RuntimeException("Failed to upload proctoring photo", e);
        }
    }
    
    /**
     * Generate a SAS URL for accessing the blob
     */
    private String generateSasUrl(BlobClient blobClient) {
        try {
            BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
            OffsetDateTime expiryTime = OffsetDateTime.now().plusDays(retentionDays);
            
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);
            
            String sasToken = blobClient.generateSas(sasValues);
            return blobClient.getBlobUrl() + "?" + sasToken;
        } catch (Exception e) {
            logger.warn("Failed to generate SAS URL, returning blob URL without SAS", e);
            return blobClient.getBlobUrl();
        }
    }
    
    /**
     * Delete a proctoring photo from Azure Blob Storage
     */
    public void deletePhoto(String photoUrl) {
        if (blobServiceClient == null) {
            logger.warn("Azure Blob Storage is not configured. Skipping photo deletion.");
            return;
        }
        
        try {
            // Extract blob name from URL
            String blobName = extractBlobNameFromUrl(photoUrl);
            if (blobName == null) {
                logger.warn("Invalid photo URL format: {}", photoUrl);
                return;
            }
            
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            if (blobClient.exists()) {
                blobClient.delete();
                logger.info("Deleted proctoring photo: {}", blobName);
            } else {
                logger.warn("Proctoring photo not found: {}", blobName);
            }
        } catch (Exception e) {
            logger.error("Failed to delete proctoring photo: {}", photoUrl, e);
        }
    }
    
    /**
     * Extract blob name from full URL
     */
    private String extractBlobNameFromUrl(String url) {
        try {
            // URL format: https://{account}.blob.core.windows.net/{container}/{blobName}?sas
            if (url.contains(containerName + "/")) {
                String[] parts = url.split(containerName + "/");
                if (parts.length > 1) {
                    String blobNameWithSas = parts[1];
                    // Remove SAS token if present
                    return blobNameWithSas.split("\\?")[0];
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to extract blob name from URL: {}", url, e);
            return null;
        }
    }
    
    /**
     * Delete all photos for a submission (used for cleanup/GDPR compliance)
     */
    public void deleteAllPhotosForSubmission(String submissionId) {
        if (blobServiceClient == null) {
            logger.warn("Azure Blob Storage is not configured. Skipping photo deletion.");
            return;
        }
        
        try {
            String clientIdStr = TenantContext.getClientId();
            if (clientIdStr == null) {
                throw new IllegalStateException("Tenant context is not set");
            }
            
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            String prefix = String.format("%s/%s/", clientIdStr, submissionId);
            
            containerClient.listBlobsByHierarchy(prefix).forEach(blobItem -> {
                try {
                    BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
                    blobClient.delete();
                    logger.info("Deleted proctoring photo: {}", blobItem.getName());
                } catch (Exception e) {
                    logger.error("Failed to delete blob: {}", blobItem.getName(), e);
                }
            });
            
            logger.info("Deleted all proctoring photos for submission: {}", submissionId);
        } catch (Exception e) {
            logger.error("Failed to delete photos for submission: {}", submissionId, e);
        }
    }
}
