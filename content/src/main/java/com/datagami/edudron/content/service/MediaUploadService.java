package com.datagami.edudron.content.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.constants.MediaFolderConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MediaUploadService {

    @Autowired(required = false)
    private BlobServiceClient blobServiceClient;

    @Autowired(required = false)
    private VideoProcessingService videoProcessingService;
    
    @Autowired(required = false)
    private VideoProcessingTask videoProcessingTask;

    @Value("${azure.storage.container-name:edudron-media}")
    private String containerName;

    @Value("${azure.storage.base-url:}")
    private String baseUrl;

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB for generic files

    public String uploadImage(MultipartFile file, String folder) throws IOException {
        return uploadImage(file, folder, null);
    }

    public String uploadImage(MultipartFile file, String folder, String tenantId) throws IOException {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "D", "location", "MediaUploadService.java:39", "message", "Upload image service called", "data", java.util.Map.of("blobServiceClient", blobServiceClient != null ? "configured" : "null", "tenantId", tenantId != null ? tenantId : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        if (blobServiceClient == null) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "D", "location", "MediaUploadService.java:41", "message", "Azure Storage not configured", "data", java.util.Map.of(), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            throw new IllegalStateException("Azure Storage is not configured");
        }

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }

        // Validate file size
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image size must be less than 10MB");
        }

        // Validate folder name
        if (!MediaFolderConstants.isValidFolder(folder)) {
            throw new IllegalArgumentException("Invalid folder name. Allowed folders: " + 
                String.join(", ",
                    MediaFolderConstants.COURSES,
                    MediaFolderConstants.THUMBNAILS,
                    MediaFolderConstants.VIDEOS,
                    MediaFolderConstants.PREVIEW_VIDEOS,
                    MediaFolderConstants.LECTURES,
                    MediaFolderConstants.ASSESSMENTS,
                    MediaFolderConstants.RESOURCES,
                    MediaFolderConstants.INSTRUCTORS,
                    MediaFolderConstants.TEMP,
                    MediaFolderConstants.LOGOS,
                    MediaFolderConstants.FAVICONS));
        }

        // Get tenant ID from context if not provided
        if (tenantId == null) {
            tenantId = TenantContext.getClientId();
            if (tenantId == null) {
                throw new IllegalStateException("Tenant context is not set");
            }
        }

        // Generate unique filename with tenant-based structure
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueId = UUID.randomUUID().toString();
        // Structure: tenantId/folder/yyyy/MM/dd/uniqueId.extension
        String fileName = String.format("%s/%s/%s/%s%s", tenantId, folder, timestamp, uniqueId, extension);

        // Get container client
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }

        // Get blob client
        BlobClient blobClient = containerClient.getBlobClient(fileName);

        // Set content type
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(contentType);

        // Upload file
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        blobClient.setHttpHeaders(headers);

        // Return public URL
        if (!baseUrl.isEmpty()) {
            return String.format("%s/%s/%s", baseUrl, containerName, fileName);
        } else {
            return blobClient.getBlobUrl();
        }
    }

    public String uploadVideo(MultipartFile file, String folder) throws IOException {
        return uploadVideo(file, folder, null);
    }

    public String uploadVideo(MultipartFile file, String folder, String tenantId) throws IOException {
        if (blobServiceClient == null) {
            throw new IllegalStateException("Azure Storage is not configured");
        }

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("File must be a video");
        }

        // Validate file size
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new IllegalArgumentException("Video size must be less than 2GB");
        }

        // Validate folder name
        if (!MediaFolderConstants.isValidFolder(folder)) {
            throw new IllegalArgumentException("Invalid folder name");
        }

        // Get tenant ID from context if not provided
        if (tenantId == null) {
            tenantId = TenantContext.getClientId();
            if (tenantId == null) {
                throw new IllegalStateException("Tenant context is not set");
            }
        }

        // Generate unique filename with tenant-based structure
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueId = UUID.randomUUID().toString();
        // Structure: tenantId/folder/yyyy/MM/dd/uniqueId.extension
        String fileName = String.format("%s/%s/%s/%s%s", tenantId, folder, timestamp, uniqueId, extension);

        // Get container client
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }

        // Get blob client
        BlobClient blobClient = containerClient.getBlobClient(fileName);

        // Set content type
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(contentType);

        // MEMORY SAFETY: Use temp file approach to prevent OOM errors
        // Even with file-size-threshold: 0, Spring Boot's multipart parser may still buffer
        // the request body in memory before writing to disk. Using temp file ensures:
        // 1. Spring Boot writes to temp file (on disk, not memory)
        // 2. We upload unprocessed video immediately (fast response)
        // 3. We process video asynchronously in background
        // 4. We update blob with optimized version when ready
        // 5. Temp files are cleaned up after processing
        //
        // ASYNC PROCESSING STRATEGY:
        // - Upload unprocessed video immediately → return URL fast
        // - Process video in background → no user waiting
        // - Update blob with optimized version → seamless upgrade
        File tempFile = null;
        try {
            // Create temp file - Spring Boot will write to this (on disk)
            tempFile = File.createTempFile("video-upload-", ".tmp");
            file.transferTo(tempFile);
            
            // Upload unprocessed video immediately (fast response)
            // Video will be available immediately, even if not optimized
            ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions()
                    .setBlockSizeLong(4L * 1024 * 1024) // 4MB blocks
                    .setMaxConcurrency(3); // 3 parallel uploads
            
            blobClient.uploadFromFile(
                    tempFile.getAbsolutePath(), 
                    parallelTransferOptions, 
                    headers, 
                    null, null, null, null
            );
            
            // Process video asynchronously in background
            // This will update the blob with optimized version when ready
            if (videoProcessingTask != null && videoProcessingService != null) {
                // Keep temp file for async processing (will be deleted by async task)
                File finalTempFile = tempFile;
                tempFile = null; // Don't delete in finally block
                
                videoProcessingTask.processAndUpdateVideo(
                        finalTempFile,
                        blobClient,
                        contentType,
                        originalFilename,
                        tenantId
                );
            }
            
        } finally {
            // Only clean up temp file if not used for async processing
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        // Return public URL
        if (!baseUrl.isEmpty()) {
            return String.format("%s/%s/%s", baseUrl, containerName, fileName);
        } else {
            return blobClient.getBlobUrl();
        }
    }

    public void deleteMedia(String mediaUrl) {
        if (blobServiceClient == null) {
            return; // Silently fail if Azure Storage is not configured
        }
        
        try {
            // Extract blob name from URL
            String blobName = extractBlobNameFromUrl(mediaUrl);
            if (blobName != null) {
                BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
                BlobClient blobClient = containerClient.getBlobClient(blobName);
                blobClient.deleteIfExists();
            }
        } catch (Exception e) {
            // Log error but don't throw exception to avoid breaking the main operation
            System.err.println("Failed to delete media: " + mediaUrl + ", Error: " + e.getMessage());
        }
    }

    /**
     * Generic file upload method that accepts any file type
     * @param file The file to upload
     * @param folder The folder name (must be a valid folder from MediaFolderConstants)
     * @return The URL of the uploaded file
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        return uploadFile(file, folder, null);
    }

    /**
     * Generic file upload method that accepts any file type
     * @param file The file to upload
     * @param folder The folder name (must be a valid folder from MediaFolderConstants)
     * @param tenantId Optional tenant ID (uses TenantContext if not provided)
     * @return The URL of the uploaded file
     */
    public String uploadFile(MultipartFile file, String folder, String tenantId) throws IOException {
        if (blobServiceClient == null) {
            throw new IllegalStateException("Azure Storage is not configured");
        }

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must be less than 100MB");
        }

        // Validate folder name - allow nested folders like "lectures/videos"
        String baseFolder = folder.contains("/") ? folder.split("/")[0] : folder;
        if (!MediaFolderConstants.isValidFolder(baseFolder)) {
            throw new IllegalArgumentException("Invalid folder name");
        }

        // Get tenant ID from context if not provided
        if (tenantId == null) {
            tenantId = TenantContext.getClientId();
            if (tenantId == null) {
                throw new IllegalStateException("Tenant context is not set");
            }
        }

        // Generate unique filename with tenant-based structure
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueId = UUID.randomUUID().toString();
        // Structure: tenantId/folder/yyyy/MM/dd/uniqueId.extension
        String fileName = String.format("%s/%s/%s/%s%s", tenantId, folder, timestamp, uniqueId, extension);

        // Get container client
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }

        // Get blob client
        BlobClient blobClient = containerClient.getBlobClient(fileName);

        // Upload file first
        blobClient.upload(file.getInputStream(), file.getSize(), true);

        // Set content type after upload
        String contentType = file.getContentType();
        if (contentType != null) {
            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType(contentType);
            blobClient.setHttpHeaders(headers);
        }

        // Return public URL
        if (!baseUrl.isEmpty()) {
            return String.format("%s/%s/%s", baseUrl, containerName, fileName);
        } else {
            return blobClient.getBlobUrl();
        }
    }

    private String extractBlobNameFromUrl(String mediaUrl) {
        try {
            // Extract blob name from URL like: https://account.blob.core.windows.net/container/folder/file.jpg
            String[] parts = mediaUrl.split("/" + containerName + "/");
            if (parts.length > 1) {
                return parts[1];
            }
        } catch (Exception e) {
            System.err.println("Failed to extract blob name from URL: " + mediaUrl);
        }
        return null;
    }
}

