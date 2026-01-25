package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AIJobQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIJobQueueService.class);
    
    // Queue names
    private static final String COURSE_GENERATION_QUEUE = "ai:queue:course-generation";
    private static final String LECTURE_GENERATION_QUEUE = "ai:queue:lecture-generation";
    private static final String COURSE_COPY_QUEUE = "ai:queue:course-copy";
    
    // Job storage prefix
    private static final String JOB_PREFIX = "ai:job:";
    private static final long JOB_TTL_HOURS = 24; // Keep jobs for 24 hours
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Submit a course generation job to the queue
     */
    public AIGenerationJobDTO submitCourseGenerationJob(Object request, AIJobWorker jobWorker) {
        String jobId = UlidGenerator.nextUlid();
        AIGenerationJobDTO job = createJob(jobId, AIGenerationJobDTO.JobType.COURSE_GENERATION);
        
        try {
            // Store request data in worker
            jobWorker.storeJobRequest(jobId, request);
            
            // Save job and add to queue
            saveJob(job);
            redisTemplate.opsForList().rightPush(COURSE_GENERATION_QUEUE, jobId);
            
            logger.info("Course generation job {} submitted to queue", jobId);
            return job;
        } catch (Exception e) {
            logger.error("Failed to submit course generation job", e);
            throw new RuntimeException("Failed to submit job", e);
        }
    }
    
    /**
     * Submit a lecture generation job to the queue
     */
    public AIGenerationJobDTO submitLectureGenerationJob(Object request, AIJobWorker jobWorker) {
        String jobId = UlidGenerator.nextUlid();
        AIGenerationJobDTO job = createJob(jobId, AIGenerationJobDTO.JobType.LECTURE_GENERATION);
        
        try {
            // Store request data in worker
            jobWorker.storeJobRequest(jobId, request);
            
            saveJob(job);
            redisTemplate.opsForList().rightPush(LECTURE_GENERATION_QUEUE, jobId);
            
            logger.info("Lecture generation job {} submitted to queue", jobId);
            return job;
        } catch (Exception e) {
            logger.error("Failed to submit lecture generation job", e);
            throw new RuntimeException("Failed to submit job", e);
        }
    }
    
    /**
     * Submit a sub-lecture generation job to the queue
     */
    public AIGenerationJobDTO submitSubLectureGenerationJob(Object request, AIJobWorker jobWorker) {
        String jobId = UlidGenerator.nextUlid();
        AIGenerationJobDTO job = createJob(jobId, AIGenerationJobDTO.JobType.SUB_LECTURE_GENERATION);
        
        try {
            // Store request data in worker
            jobWorker.storeJobRequest(jobId, request);
            
            saveJob(job);
            redisTemplate.opsForList().rightPush(LECTURE_GENERATION_QUEUE, jobId);
            
            logger.info("Sub-lecture generation job {} submitted to queue", jobId);
            return job;
        } catch (Exception e) {
            logger.error("Failed to submit sub-lecture generation job", e);
            throw new RuntimeException("Failed to submit job", e);
        }
    }
    
    /**
     * Submit a course copy job to the queue
     */
    public AIGenerationJobDTO submitCourseCopyJob(String jobId) {
        AIGenerationJobDTO job = createJob(jobId, AIGenerationJobDTO.JobType.COURSE_COPY);
        
        try {
            saveJob(job);
            redisTemplate.opsForList().rightPush(COURSE_COPY_QUEUE, jobId);
            
            logger.info("Course copy job {} submitted to queue", jobId);
            return job;
        } catch (Exception e) {
            logger.error("Failed to submit course copy job", e);
            throw new RuntimeException("Failed to submit job", e);
        }
    }
    
    /**
     * Get a job from the queue (blocking)
     */
    public String getJobFromQueue(String queueName, long timeoutSeconds) {
        return redisTemplate.opsForList().leftPop(queueName, timeoutSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Get job status
     */
    public AIGenerationJobDTO getJob(String jobId) {
        try {
            String jobJson = redisTemplate.opsForValue().get(JOB_PREFIX + jobId);
            if (jobJson == null) {
                return null;
            }
            return objectMapper.readValue(jobJson, AIGenerationJobDTO.class);
        } catch (Exception e) {
            logger.error("Failed to get job {}", jobId, e);
            return null;
        }
    }
    
    /**
     * Update job status
     */
    public void updateJob(AIGenerationJobDTO job) {
        try {
            job.setUpdatedAt(OffsetDateTime.now());
            saveJob(job);
        } catch (Exception e) {
            logger.error("Failed to update job {}", job.getJobId(), e);
        }
    }
    
    /**
     * Save job to Redis
     */
    private void saveJob(AIGenerationJobDTO job) {
        try {
            String jobJson = objectMapper.writeValueAsString(job);
            redisTemplate.opsForValue().set(
                JOB_PREFIX + job.getJobId(),
                jobJson,
                JOB_TTL_HOURS,
                TimeUnit.HOURS
            );
        } catch (Exception e) {
            logger.error("Failed to save job {}", job.getJobId(), e);
            throw new RuntimeException("Failed to save job", e);
        }
    }
    
    /**
     * Create a new job
     */
    private AIGenerationJobDTO createJob(String jobId, AIGenerationJobDTO.JobType jobType) {
        AIGenerationJobDTO job = new AIGenerationJobDTO();
        job.setJobId(jobId);
        job.setJobType(jobType);
        job.setStatus(AIGenerationJobDTO.JobStatus.PENDING);
        job.setCreatedAt(OffsetDateTime.now());
        job.setUpdatedAt(OffsetDateTime.now());
        job.setProgress(0);
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr != null && !clientIdStr.isEmpty()) {
            try {
                job.setClientId(UUID.fromString(clientIdStr));
            } catch (Exception e) {
                logger.warn("Invalid client ID: {}", clientIdStr);
            }
        }
        
        return job;
    }
    
    /**
     * Get queue name for job type
     */
    public static String getQueueName(AIGenerationJobDTO.JobType jobType) {
        switch (jobType) {
            case COURSE_GENERATION:
                return COURSE_GENERATION_QUEUE;
            case LECTURE_GENERATION:
            case SUB_LECTURE_GENERATION:
                return LECTURE_GENERATION_QUEUE;
            case COURSE_COPY:
                return COURSE_COPY_QUEUE;
            default:
                throw new IllegalArgumentException("Unknown job type: " + jobType);
        }
    }
}

