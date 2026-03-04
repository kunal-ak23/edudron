package com.datagami.edudron.student.service;

import com.datagami.edudron.common.UlidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Queues proctoring photos in Redis for async upload to Azure Blob Storage.
 * Falls back to synchronous upload if Redis is unavailable.
 */
@Service
public class ProctoringPhotoQueueService {

    private static final Logger logger = LoggerFactory.getLogger(ProctoringPhotoQueueService.class);

    private static final String QUEUE_KEY = "proctoring:queue:photo-upload";
    private static final String PHOTO_PREFIX = "proctoring:photo:";
    private static final String JOB_PREFIX = "proctoring:job:";
    private static final long TTL_MINUTES = 30;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProctoringPhotoService proctoringPhotoService;

    /**
     * Queue a photo for async upload to Azure Blob Storage.
     * If Redis is unavailable, falls back to synchronous upload.
     *
     * @param base64Photo  Base64 encoded photo data (may include data URI prefix)
     * @param submissionId The submission ID
     * @param photoType    "identity_verification" or "exam_capture"
     * @param clientId     Tenant client ID for multi-tenant isolation
     * @param capturedAt   When the photo was captured (null for identity verification)
     * @return Result containing jobId (async) or photoUrl (sync fallback)
     */
    public PhotoQueueResult queuePhoto(String base64Photo, String submissionId,
                                       String photoType, String clientId,
                                       OffsetDateTime capturedAt) {
        if (redisTemplate == null) {
            logger.debug("Redis not configured, falling back to sync upload");
            return syncFallback(base64Photo, submissionId, photoType);
        }

        try {
            String jobId = UlidGenerator.nextUlid();

            // Strip data URI prefix before storing (save Redis memory)
            String photoData = base64Photo;
            if (base64Photo.contains(",")) {
                photoData = base64Photo.split(",")[1];
            }

            // Store photo data with TTL
            redisTemplate.opsForValue().set(
                    PHOTO_PREFIX + jobId, photoData, TTL_MINUTES, TimeUnit.MINUTES);

            // Store job metadata with TTL
            Map<String, String> jobMeta = Map.of(
                    "clientId", clientId,
                    "submissionId", submissionId,
                    "photoType", photoType,
                    "capturedAt", capturedAt != null ? capturedAt.toString() : "",
                    "retryCount", "0",
                    "createdAt", OffsetDateTime.now().toString(),
                    "status", "PENDING"
            );
            redisTemplate.opsForHash().putAll(JOB_PREFIX + jobId, jobMeta);
            redisTemplate.expire(JOB_PREFIX + jobId, TTL_MINUTES, TimeUnit.MINUTES);

            // Push job ID to queue
            redisTemplate.opsForList().rightPush(QUEUE_KEY, jobId);

            logger.info("Queued photo upload job {} for submission {} (type: {})",
                    jobId, submissionId, photoType);

            return new PhotoQueueResult(jobId, null, false);

        } catch (Exception e) {
            logger.warn("Redis unavailable, falling back to sync upload for submission {}: {}",
                    submissionId, e.getMessage());
            return syncFallback(base64Photo, submissionId, photoType);
        }
    }

    /**
     * Synchronous fallback when Redis is unavailable.
     */
    private PhotoQueueResult syncFallback(String base64Photo, String submissionId, String photoType) {
        String url = proctoringPhotoService.uploadPhoto(base64Photo, submissionId, photoType);
        return new PhotoQueueResult(null, url, true);
    }

    /**
     * Result of queuing a photo upload.
     *
     * @param jobId       Job ID if queued async (null if sync)
     * @param photoUrl    Photo URL if uploaded synchronously (null if async)
     * @param synchronous True if photo was uploaded synchronously (fallback)
     */
    public record PhotoQueueResult(String jobId, String photoUrl, boolean synchronous) {
    }
}
