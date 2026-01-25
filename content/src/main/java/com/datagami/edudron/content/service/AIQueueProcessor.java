package com.datagami.edudron.content.service;

import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background processor that polls Redis queues and processes AI generation jobs
 */
@Component
public class AIQueueProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(AIQueueProcessor.class);
    
    private static final String COURSE_GENERATION_QUEUE = "ai:queue:course-generation";
    private static final String LECTURE_GENERATION_QUEUE = "ai:queue:lecture-generation";
    private static final String COURSE_COPY_QUEUE = "ai:queue:course-copy";
    
    @Autowired
    private AIJobQueueService queueService;
    
    @Autowired
    private AIJobWorker jobWorker;
    
    @Autowired
    private CourseCopyWorker courseCopyWorker;
    
    private volatile boolean processing = false;
    
    /**
     * Process course generation queue
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000)
    public void processCourseGenerationQueue() {
        if (processing) {
            return; // Skip if already processing
        }
        
        try {
            processing = true;
            String jobId = queueService.getJobFromQueue(COURSE_GENERATION_QUEUE, 1);
            if (jobId != null) {
                logger.info("Found course generation job: {}", jobId);
                AIGenerationJobDTO job = queueService.getJob(jobId);
                if (job != null) {
                    job.setStatus(AIGenerationJobDTO.JobStatus.QUEUED);
                    queueService.updateJob(job);
                    jobWorker.processCourseGenerationJob(jobId);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing course generation queue", e);
        } finally {
            processing = false;
        }
    }
    
    /**
     * Process lecture generation queue (includes sub-lectures)
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 1000)
    public void processLectureGenerationQueue() {
        if (processing) {
            return; // Skip if already processing
        }
        
        try {
            processing = true;
            String jobId = queueService.getJobFromQueue(LECTURE_GENERATION_QUEUE, 1);
            if (jobId != null) {
                logger.info("Found lecture generation job: {}", jobId);
                AIGenerationJobDTO job = queueService.getJob(jobId);
                if (job != null) {
                    job.setStatus(AIGenerationJobDTO.JobStatus.QUEUED);
                    queueService.updateJob(job);
                    
                    // Route to appropriate worker based on job type
                    if (job.getJobType() == AIGenerationJobDTO.JobType.LECTURE_GENERATION) {
                        jobWorker.processLectureGenerationJob(jobId);
                    } else if (job.getJobType() == AIGenerationJobDTO.JobType.SUB_LECTURE_GENERATION) {
                        jobWorker.processSubLectureGenerationJob(jobId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing lecture generation queue", e);
        } finally {
            processing = false;
        }
    }
    
    /**
     * Process course copy queue
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void processCourseCopyQueue() {
        if (processing) {
            return; // Skip if already processing
        }
        
        try {
            processing = true;
            String jobId = queueService.getJobFromQueue(COURSE_COPY_QUEUE, 1);
            if (jobId != null) {
                logger.info("Found course copy job: {}", jobId);
                AIGenerationJobDTO job = queueService.getJob(jobId);
                if (job != null) {
                    job.setStatus(AIGenerationJobDTO.JobStatus.QUEUED);
                    queueService.updateJob(job);
                    courseCopyWorker.processCourseCopyJob(jobId);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing course copy queue", e);
        } finally {
            processing = false;
        }
    }
}

