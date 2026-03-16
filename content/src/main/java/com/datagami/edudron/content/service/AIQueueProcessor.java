package com.datagami.edudron.content.service;

import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background processor that polls Redis queues and processes AI generation jobs.
 * Each queue type has its own processing flag so they can be polled independently.
 */
@Component
public class AIQueueProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AIQueueProcessor.class);

    private static final String COURSE_GENERATION_QUEUE = "ai:queue:course-generation";
    private static final String LECTURE_GENERATION_QUEUE = "ai:queue:lecture-generation";
    private static final String COURSE_COPY_QUEUE = "ai:queue:course-copy";
    private static final String IMAGE_GENERATION_QUEUE = "ai:queue:image-generation";

    @Autowired
    private AIJobQueueService queueService;

    @Autowired
    private AIJobWorker jobWorker;

    @Autowired
    private CourseCopyWorker courseCopyWorker;

    // Per-queue processing flags (independent so queues don't block each other)
    private volatile boolean processingCourseQueue = false;
    private volatile boolean processingLectureQueue = false;
    private volatile boolean processingImageQueue = false;
    private volatile boolean processingCopyQueue = false;

    /**
     * Process course generation queue
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000)
    public void processCourseGenerationQueue() {
        if (processingCourseQueue) {
            return;
        }

        try {
            processingCourseQueue = true;
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
            processingCourseQueue = false;
        }
    }

    /**
     * Process lecture generation queue (includes sub-lectures)
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 1000)
    public void processLectureGenerationQueue() {
        if (processingLectureQueue) {
            return;
        }

        try {
            processingLectureQueue = true;
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
            processingLectureQueue = false;
        }
    }

    /**
     * Process image generation queue
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 1500)
    public void processImageGenerationQueue() {
        if (processingImageQueue) {
            return;
        }

        try {
            processingImageQueue = true;
            String jobId = queueService.getJobFromQueue(IMAGE_GENERATION_QUEUE, 1);
            if (jobId != null) {
                logger.info("Found image generation job: {}", jobId);
                AIGenerationJobDTO job = queueService.getJob(jobId);
                if (job != null) {
                    job.setStatus(AIGenerationJobDTO.JobStatus.QUEUED);
                    queueService.updateJob(job);
                    jobWorker.processImageGenerationJob(jobId);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing image generation queue", e);
        } finally {
            processingImageQueue = false;
        }
    }

    /**
     * Process course copy queue
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void processCourseCopyQueue() {
        if (processingCopyQueue) {
            return;
        }

        try {
            processingCopyQueue = true;
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
            processingCopyQueue = false;
        }
    }
}
