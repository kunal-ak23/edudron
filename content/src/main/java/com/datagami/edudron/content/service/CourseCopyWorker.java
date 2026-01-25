package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.datagami.edudron.content.dto.CourseCopyJobData;
import com.datagami.edudron.content.dto.CourseCopyRequest;
import com.datagami.edudron.content.dto.CourseCopyResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            // Add interceptor to forward JWT token
            interceptors.add((request, body, execution) -> {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank() && !request.getHeaders().containsKey("Authorization")) {
                        request.getHeaders().add("Authorization", authHeader);
                    }
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
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
        // Get user role from identity service (same pattern as CourseService)
        String userRole = getCurrentUserRole();
        
        if (!"SYSTEM_ADMIN".equals(userRole)) {
            logger.warn("User with role {} attempted to copy course - access denied", userRole);
            throw new AccessDeniedException("Only SYSTEM_ADMIN can copy courses across tenants. Current role: " + userRole);
        }
        
        logger.info("SYSTEM_ADMIN validation passed for user with role: {}", userRole);
    }
    
    private String getCurrentUserRole() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return null;
            }
            
            // Get user info from identity service
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object role = response.getBody().get("role");
                return role != null ? role.toString() : null;
            }
        } catch (Exception e) {
            logger.error("Could not determine user role: {}", e.getMessage(), e);
        }
        return null;
    }
}
