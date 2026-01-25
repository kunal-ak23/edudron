package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.datagami.edudron.content.dto.CourseCopyJobData;
import com.datagami.edudron.content.dto.CourseCopyRequest;
import com.datagami.edudron.content.dto.CourseCopyResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CourseCopyWorker {
    
    private static final Logger logger = LoggerFactory.getLogger(CourseCopyWorker.class);
    private static final String JOB_DATA_PREFIX = "course-copy:job-data:";
    
    @Autowired
    private AIJobQueueService queueService;
    
    @Autowired
    private CourseCopyService copyService;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Submit course copy job to queue (called by API endpoint)
     */
    public AIGenerationJobDTO submitCourseCopyJob(String courseId, CourseCopyRequest request) {
        // Validate SYSTEM_ADMIN
        validateSystemAdmin();
        
        String jobId = UlidGenerator.nextUlid();
        
        // Create job data
        CourseCopyJobData jobData = new CourseCopyJobData();
        jobData.setSourceCourseId(courseId);
        jobData.setTargetClientId(request.getTargetClientId());
        jobData.setNewCourseTitle(request.getNewCourseTitle());
        jobData.setCopyPublishedState(request.getCopyPublishedState());
        
        // Store job data in Redis
        storeJobData(jobId, jobData);
        
        // Create and submit job to queue
        AIGenerationJobDTO job = queueService.submitCourseCopyJob(jobId);
        
        logger.info("Course copy job {} submitted for course {}", jobId, courseId);
        return job;
    }
    
    /**
     * Process course copy job (called by queue processor)
     */
    @Async("eventTaskExecutor")
    public void processCourseCopyJob(String jobId) {
        AIGenerationJobDTO job = queueService.getJob(jobId);
        if (job == null) {
            logger.error("Job {} not found", jobId);
            return;
        }
        
        try {
            // Update status to PROCESSING
            job.setStatus(AIGenerationJobDTO.JobStatus.PROCESSING);
            job.setProgress(0);
            job.setMessage("Starting course copy");
            queueService.updateJob(job);
            
            // Load job data
            CourseCopyJobData jobData = loadJobData(jobId);
            if (jobData == null) {
                throw new IllegalStateException("Job data not found for job " + jobId);
            }
            
            // Execute copy with progress updates
            CourseCopyResultDTO result = copyService.copyCourseToTenant(
                jobId,
                jobData,
                (step, progress) -> updateProgress(jobId, step, progress)
            );
            
            // Mark completed
            job.setStatus(AIGenerationJobDTO.JobStatus.COMPLETED);
            job.setProgress(100);
            job.setMessage("Course copy completed successfully");
            job.setResult(result);
            queueService.updateJob(job);
            
            logger.info("Course copy job {} completed successfully", jobId);
            
        } catch (Exception e) {
            logger.error("Course copy job {} failed", jobId, e);
            job.setStatus(AIGenerationJobDTO.JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setMessage("Course copy failed: " + e.getMessage());
            queueService.updateJob(job);
        } finally {
            // Clean up job data
            cleanupJobData(jobId);
        }
    }
    
    private void updateProgress(String jobId, String step, int progress) {
        AIGenerationJobDTO job = queueService.getJob(jobId);
        if (job != null) {
            job.setMessage(step);
            job.setProgress(progress);
            queueService.updateJob(job);
        }
    }
    
    private void storeJobData(String jobId, CourseCopyJobData data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(
                JOB_DATA_PREFIX + jobId,
                json,
                24, TimeUnit.HOURS
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to store job data", e);
        }
    }
    
    private CourseCopyJobData loadJobData(String jobId) {
        try {
            String json = redisTemplate.opsForValue().get(JOB_DATA_PREFIX + jobId);
            return json != null ? objectMapper.readValue(json, CourseCopyJobData.class) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load job data", e);
        }
    }
    
    private void cleanupJobData(String jobId) {
        try {
            redisTemplate.delete(JOB_DATA_PREFIX + jobId);
        } catch (Exception e) {
            logger.warn("Failed to cleanup job data for job {}", jobId, e);
        }
    }
    
    private void validateSystemAdmin() {
        String currentTenantContext = TenantContext.getClientId();
        
        // For now, check if tenant context is SYSTEM
        // In a full implementation, we'd also validate the user's role
        if (currentTenantContext == null || 
            (!currentTenantContext.equals("SYSTEM") && 
             !currentTenantContext.equals("PENDING_TENANT_SELECTION"))) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can copy courses across tenants. Please ensure you're logged in as SYSTEM_ADMIN.");
        }
        
        logger.info("SYSTEM_ADMIN validation passed for tenant context: {}", currentTenantContext);
    }
}
