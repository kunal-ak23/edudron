package com.datagami.edudron.content.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.MediaAsset;
import com.datagami.edudron.content.repo.MediaAssetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MediaAssetService {
    private static final Logger logger = LoggerFactory.getLogger(MediaAssetService.class);
    
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024; // 500MB
    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
    
    @Autowired(required = false)
    private BlobServiceClient blobServiceClient;
    
    @Value("${azure.storage.container-name:edudron-media}")
    private String containerName;
    
    @Value("${azure.storage.base-url:}")
    private String baseUrl;
    
    @Autowired
    private MediaAssetRepository mediaAssetRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    public MediaAsset uploadAsset(MultipartFile file, MediaAsset.AssetType assetType, String uploadedBy) throws IOException {
        if (blobServiceClient == null) {
            throw new IllegalStateException("Azure Storage is not configured");
        }
        
        // Validate file
        validateFile(file, assetType);
        
        // Get tenant ID
        String tenantIdStr = TenantContext.getClientId();
        if (tenantIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID tenantId = UUID.fromString(tenantIdStr);
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueId = UUID.randomUUID().toString();
        
        // Folder structure: {tenant-id}/courses/{asset-type}/yyyy/MM/dd/{uuid}.{ext}
        String folderPath = String.format("%s/courses/%s/%s", tenantId, assetType.name().toLowerCase(), timestamp);
        String fileName = String.format("%s/%s%s", folderPath, uniqueId, extension);
        
        // Get container client
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        
        // Upload file
        BlobClient blobClient = containerClient.getBlobClient(fileName);
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(file.getContentType());
        
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        blobClient.setHttpHeaders(headers);
        
        // Generate URL
        String fileUrl = baseUrl.isEmpty() 
            ? blobClient.getBlobUrl() 
            : String.format("%s/%s/%s", baseUrl, containerName, fileName);
        
        // Create metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("originalFileName", originalFilename);
        metadata.put("uploadedAt", LocalDateTime.now().toString());
        // Add more metadata based on asset type (duration, dimensions, etc.)
        
        // Create MediaAsset entity
        MediaAsset asset = new MediaAsset();
        asset.setId(UlidGenerator.nextUlid());
        asset.setClientId(tenantId);
        asset.setAssetType(assetType);
        asset.setFileName(originalFilename != null ? originalFilename : uniqueId + extension);
        asset.setFileUrl(fileUrl);
        asset.setFileSizeBytes(file.getSize());
        asset.setMimeType(file.getContentType());
        asset.setUploadedBy(uploadedBy);
        asset.setMetadata(metadata);
        
        // Save to database
        MediaAsset saved = mediaAssetRepository.save(asset);
        
        logger.info("Uploaded media asset: {} ({} bytes) to {}", originalFilename, file.getSize(), fileUrl);
        
        return saved;
    }
    
    public void deleteAsset(String assetId) {
        String tenantIdStr = TenantContext.getClientId();
        if (tenantIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID tenantId = UUID.fromString(tenantIdStr);
        
        MediaAsset asset = mediaAssetRepository.findByIdAndClientId(assetId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
        
        // Delete from Azure Storage
        if (blobServiceClient != null && asset.getFileUrl() != null) {
            try {
                String blobName = extractBlobNameFromUrl(asset.getFileUrl());
                if (blobName != null) {
                    BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
                    BlobClient blobClient = containerClient.getBlobClient(blobName);
                    blobClient.deleteIfExists();
                }
            } catch (Exception e) {
                logger.warn("Failed to delete asset from Azure Storage: {}", e.getMessage());
            }
        }
        
        // Delete from database
        mediaAssetRepository.delete(asset);
    }
    
    private void validateFile(MultipartFile file, MediaAsset.AssetType assetType) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        long maxSize = (assetType == MediaAsset.AssetType.VIDEO) ? MAX_VIDEO_SIZE : MAX_FILE_SIZE;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum allowed size: %d bytes (max: %d)", 
                    file.getSize(), maxSize)
            );
        }
        
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("File content type is not specified");
        }
        
        // Validate content type based on asset type
        switch (assetType) {
            case VIDEO:
                if (!contentType.startsWith("video/")) {
                    throw new IllegalArgumentException("File must be a video");
                }
                break;
            case PDF:
                if (!contentType.equals("application/pdf")) {
                    throw new IllegalArgumentException("File must be a PDF");
                }
                break;
            case IMAGE:
                if (!contentType.startsWith("image/")) {
                    throw new IllegalArgumentException("File must be an image");
                }
                break;
            case AUDIO:
                if (!contentType.startsWith("audio/")) {
                    throw new IllegalArgumentException("File must be an audio file");
                }
                break;
            case DOCUMENT:
                // Allow various document types
                if (!contentType.contains("document") && 
                    !contentType.contains("text") && 
                    !contentType.contains("pdf") &&
                    !contentType.contains("word") &&
                    !contentType.contains("excel")) {
                    throw new IllegalArgumentException("File must be a document");
                }
                break;
        }
    }
    
    private String extractBlobNameFromUrl(String fileUrl) {
        try {
            // Extract blob name from URL
            // Format: https://account.blob.core.windows.net/container/tenant-id/courses/type/yyyy/MM/dd/uuid.ext
            if (fileUrl.contains("/" + containerName + "/")) {
                int index = fileUrl.indexOf("/" + containerName + "/");
                return fileUrl.substring(index + containerName.length() + 2);
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to extract blob name from URL: {}", fileUrl);
            return null;
        }
    }
}


