package com.datagami.edudron.content.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.datagami.edudron.common.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Async task for processing videos in the background.
 * Uploads unprocessed video immediately, then processes and updates the blob.
 */
@Component
public class VideoProcessingTask {
    
    private static final Logger logger = LoggerFactory.getLogger(VideoProcessingTask.class);
    
    @Autowired(required = false)
    private VideoProcessingService videoProcessingService;
    
    /**
     * Process video asynchronously and update the blob with optimized version.
     * 
     * @param tempFile Original video file (will be deleted after processing)
     * @param blobClient Blob client to update
     * @param contentType Content type for headers
     * @param originalFilename Original filename for logging
     * @param tenantId Tenant ID for context
     */
    @Async
    public void processAndUpdateVideo(
            File tempFile,
            BlobClient blobClient,
            String contentType,
            String originalFilename,
            String tenantId) {
        
        // Set tenant context for async processing
        String originalTenantId = TenantContext.getClientId();
        try {
            if (tenantId != null) {
                TenantContext.setClientId(tenantId);
            }
            
            logger.info("Starting async video processing for: {}", originalFilename);
            
            // Skip if processing is disabled or service not available
            if (videoProcessingService == null) {
                logger.info("Video processing service not available, skipping optimization");
                return;
            }
            
            File processedFile = null;
            try {
                // Process video with ffmpeg
                logger.info("Processing video for streaming optimization: {}", originalFilename);
                processedFile = videoProcessingService.processVideoForStreaming(tempFile);
                
                // Upload processed version (overwrites original)
                logger.info("Uploading optimized video version: {}", originalFilename);
                
                BlobHttpHeaders headers = new BlobHttpHeaders()
                        .setContentType(contentType);
                
                ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions()
                        .setBlockSizeLong(4L * 1024 * 1024) // 4MB blocks
                        .setMaxConcurrency(3);
                
                blobClient.uploadFromFile(
                        processedFile.getAbsolutePath(),
                        parallelTransferOptions,
                        headers,
                        null, null, null, null
                );
                
                logger.info("Successfully updated video with optimized version: {}", originalFilename);
                
            } catch (Exception e) {
                logger.error("Failed to process/update video: {}", originalFilename, e);
                // Don't throw - video is already uploaded, just not optimized
            } finally {
                // Clean up temp files
                if (processedFile != null && processedFile.exists()) {
                    boolean deleted = processedFile.delete();
                    if (!deleted) {
                        logger.warn("Failed to delete processed temp file: {}", processedFile.getAbsolutePath());
                    }
                }
            }
            
        } finally {
            // Restore original tenant context
            if (originalTenantId != null) {
                TenantContext.setClientId(originalTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }
}
