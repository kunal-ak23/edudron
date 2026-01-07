package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.GenerateCourseRequest;
import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.dto.SectionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AIJobWorker {
    
    private static final Logger logger = LoggerFactory.getLogger(AIJobWorker.class);
    
    @Autowired
    private AIJobQueueService queueService;
    
    @Autowired
    private CourseGenerationService courseGenerationService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // Store request data for jobs
    private final Map<String, Object> jobRequests = new ConcurrentHashMap<>();
    
    /**
     * Process a course generation job
     */
    @Async
    public void processCourseGenerationJob(String jobId) {
        logger.info("Processing course generation job: {}", jobId);
        
        AIGenerationJobDTO job = queueService.getJob(jobId);
        if (job == null) {
            logger.error("Job {} not found", jobId);
            return;
        }
        
        // Set tenant context
        if (job.getClientId() != null) {
            TenantContext.setClientId(job.getClientId().toString());
        }
        
        try {
            // Update job status to processing
            job.setStatus(AIGenerationJobDTO.JobStatus.PROCESSING);
            job.setMessage("Starting course generation...");
            job.setProgress(10);
            queueService.updateJob(job);
            
            // Get request data
            Object requestObj = jobRequests.get(jobId);
            if (requestObj == null) {
                throw new RuntimeException("Request data not found for job: " + jobId);
            }
            
            GenerateCourseRequest request = objectMapper.convertValue(requestObj, GenerateCourseRequest.class);
            
            // Update progress
            job.setProgress(20);
            job.setMessage("Parsing course requirements...");
            queueService.updateJob(job);
            
            // Generate course
            CourseDTO course = courseGenerationService.generateCourseFromPrompt(request);
            
            // Update job with result
            job.setStatus(AIGenerationJobDTO.JobStatus.COMPLETED);
            job.setResult(course);
            job.setMessage("Course generated successfully");
            job.setProgress(100);
            queueService.updateJob(job);
            
            logger.info("Course generation job {} completed successfully", jobId);
            
        } catch (Exception e) {
            logger.error("Error processing course generation job: {}", jobId, e);
            job.setStatus(AIGenerationJobDTO.JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setMessage("Course generation failed: " + e.getMessage());
            queueService.updateJob(job);
        } finally {
            // Clean up
            jobRequests.remove(jobId);
            TenantContext.clear();
        }
    }
    
    /**
     * Process a lecture generation job
     */
    @Async
    public void processLectureGenerationJob(String jobId) {
        logger.info("Processing lecture generation job: {}", jobId);
        
        AIGenerationJobDTO job = queueService.getJob(jobId);
        if (job == null) {
            logger.error("Job {} not found", jobId);
            return;
        }
        
        // Set tenant context
        if (job.getClientId() != null) {
            TenantContext.setClientId(job.getClientId().toString());
        }
        
        try {
            job.setStatus(AIGenerationJobDTO.JobStatus.PROCESSING);
            job.setMessage("Starting lecture generation...");
            job.setProgress(10);
            queueService.updateJob(job);
            
            // Get request data
            Object requestObj = jobRequests.get(jobId);
            if (requestObj == null) {
                throw new RuntimeException("Request data not found for job: " + jobId);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> request = (Map<String, String>) requestObj;
            String courseId = request.get("courseId");
            String prompt = request.get("prompt");
            
            job.setProgress(30);
            job.setMessage("Generating lecture structure...");
            queueService.updateJob(job);
            
            SectionDTO section = courseGenerationService.generateLectureWithSubLectures(courseId, prompt);
            
            job.setStatus(AIGenerationJobDTO.JobStatus.COMPLETED);
            job.setResult(section);
            job.setMessage("Lecture generated successfully");
            job.setProgress(100);
            queueService.updateJob(job);
            
            logger.info("Lecture generation job {} completed successfully", jobId);
            
        } catch (Exception e) {
            logger.error("Error processing lecture generation job: {}", jobId, e);
            job.setStatus(AIGenerationJobDTO.JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setMessage("Lecture generation failed: " + e.getMessage());
            queueService.updateJob(job);
        } finally {
            jobRequests.remove(jobId);
            TenantContext.clear();
        }
    }
    
    /**
     * Process a sub-lecture generation job
     */
    @Async
    public void processSubLectureGenerationJob(String jobId) {
        logger.info("Processing sub-lecture generation job: {}", jobId);
        
        AIGenerationJobDTO job = queueService.getJob(jobId);
        if (job == null) {
            logger.error("Job {} not found", jobId);
            return;
        }
        
        // Set tenant context
        if (job.getClientId() != null) {
            TenantContext.setClientId(job.getClientId().toString());
        }
        
        try {
            job.setStatus(AIGenerationJobDTO.JobStatus.PROCESSING);
            job.setMessage("Starting sub-lecture generation...");
            job.setProgress(10);
            queueService.updateJob(job);
            
            // Get request data
            Object requestObj = jobRequests.get(jobId);
            if (requestObj == null) {
                throw new RuntimeException("Request data not found for job: " + jobId);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> request = (Map<String, String>) requestObj;
            String courseId = request.get("courseId");
            String sectionId = request.get("sectionId");
            String prompt = request.get("prompt");
            
            job.setProgress(30);
            job.setMessage("Generating sub-lecture content...");
            queueService.updateJob(job);
            
            LectureDTO lecture = courseGenerationService.generateSubLectureWithAI(courseId, sectionId, prompt);
            
            job.setStatus(AIGenerationJobDTO.JobStatus.COMPLETED);
            job.setResult(lecture);
            job.setMessage("Sub-lecture generated successfully");
            job.setProgress(100);
            queueService.updateJob(job);
            
            logger.info("Sub-lecture generation job {} completed successfully", jobId);
            
        } catch (Exception e) {
            logger.error("Error processing sub-lecture generation job: {}", jobId, e);
            job.setStatus(AIGenerationJobDTO.JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setMessage("Sub-lecture generation failed: " + e.getMessage());
            queueService.updateJob(job);
        } finally {
            jobRequests.remove(jobId);
            TenantContext.clear();
        }
    }
    
    /**
     * Store request data for a job
     */
    public void storeJobRequest(String jobId, Object request) {
        jobRequests.put(jobId, request);
    }
}

