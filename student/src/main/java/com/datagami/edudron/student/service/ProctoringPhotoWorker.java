package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Background worker that drains the Redis photo upload queue.
 * Reads queued photos, uploads them to Azure Blob Storage,
 * and updates the database with the resulting SAS URLs.
 */
@Component
public class ProctoringPhotoWorker {

    private static final Logger logger = LoggerFactory.getLogger(ProctoringPhotoWorker.class);

    private static final String QUEUE_KEY = "proctoring:queue:photo-upload";
    private static final String PHOTO_PREFIX = "proctoring:photo:";
    private static final String JOB_PREFIX = "proctoring:job:";
    private static final int MAX_RETRIES = 3;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProctoringPhotoService proctoringPhotoService;

    @Autowired
    private ProctoringService proctoringService;

    /**
     * Poll the photo upload queue every 500ms.
     * Uses a 1-second blocking pop to avoid busy-waiting.
     */
    @Scheduled(fixedDelay = 500)
    public void processPhotoUploadQueue() {
        if (redisTemplate == null) return;

        try {
            String jobId = redisTemplate.opsForList().leftPop(QUEUE_KEY, 1, TimeUnit.SECONDS);
            if (jobId == null) return;

            processJob(jobId);
        } catch (Exception e) {
            logger.error("Error polling photo upload queue", e);
        }
    }

    private void processJob(String jobId) {
        try {
            // Read job metadata
            Map<Object, Object> jobMeta = redisTemplate.opsForHash().entries(JOB_PREFIX + jobId);
            if (jobMeta.isEmpty()) {
                logger.warn("Job metadata not found for {}, may have expired", jobId);
                return;
            }

            String clientId = (String) jobMeta.get("clientId");
            String submissionId = (String) jobMeta.get("submissionId");
            String photoType = (String) jobMeta.get("photoType");
            String capturedAtStr = (String) jobMeta.get("capturedAt");

            // Mark as processing
            redisTemplate.opsForHash().put(JOB_PREFIX + jobId, "status", "PROCESSING");

            // Read photo data
            String base64Photo = redisTemplate.opsForValue().get(PHOTO_PREFIX + jobId);
            if (base64Photo == null) {
                logger.warn("Photo data not found for job {}, may have expired", jobId);
                redisTemplate.opsForHash().put(JOB_PREFIX + jobId, "status", "FAILED");
                return;
            }

            // Set TenantContext for the upload (worker runs outside HTTP request thread)
            TenantContext.setClientId(clientId);
            try {
                // Upload to Azure Blob Storage (reuses existing sync service)
                String photoUrl = proctoringPhotoService.uploadPhoto(base64Photo, submissionId, photoType);

                // Update database based on photo type
                if ("identity_verification".equals(photoType)) {
                    proctoringService.storeIdentityVerificationPhoto(submissionId, photoUrl);
                } else {
                    OffsetDateTime capturedAt = (capturedAtStr != null && !capturedAtStr.isEmpty())
                            ? OffsetDateTime.parse(capturedAtStr)
                            : OffsetDateTime.now();
                    proctoringService.addPhotoToProctoringData(submissionId, photoUrl, capturedAt);
                }

                // Success — clean up Redis keys
                redisTemplate.delete(PHOTO_PREFIX + jobId);
                redisTemplate.delete(JOB_PREFIX + jobId);

                logger.info("Successfully processed photo upload job {} for submission {} (type: {})",
                        jobId, submissionId, photoType);

            } finally {
                TenantContext.clear();
            }

        } catch (Exception e) {
            logger.error("Failed to process photo upload job {}", jobId, e);
            handleRetry(jobId);
        }
    }

    private void handleRetry(String jobId) {
        try {
            Map<Object, Object> jobMeta = redisTemplate.opsForHash().entries(JOB_PREFIX + jobId);
            if (jobMeta.isEmpty()) return;

            int retryCount = Integer.parseInt((String) jobMeta.getOrDefault("retryCount", "0"));

            if (retryCount < MAX_RETRIES) {
                // Increment retry count, re-queue at back of line
                redisTemplate.opsForHash().put(JOB_PREFIX + jobId, "retryCount",
                        String.valueOf(retryCount + 1));
                redisTemplate.opsForHash().put(JOB_PREFIX + jobId, "status", "PENDING");
                redisTemplate.opsForList().rightPush(QUEUE_KEY, jobId);
                logger.warn("Re-queued photo upload job {} (retry {}/{})", jobId, retryCount + 1, MAX_RETRIES);
            } else {
                // Max retries exceeded — mark as failed, TTL will clean up
                redisTemplate.opsForHash().put(JOB_PREFIX + jobId, "status", "FAILED");
                logger.error("Photo upload job {} failed after {} retries", jobId, MAX_RETRIES);
            }
        } catch (Exception retryError) {
            logger.error("Failed to handle retry for job {}", jobId, retryError);
        }
    }
}
