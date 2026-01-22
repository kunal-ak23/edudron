package com.datagami.edudron.common.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.common.domain.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Unified Event Service for logging events to common.events table.
 * This service can be used by all microservices to log events centrally.
 * 
 * Note: This is an abstract base class. Each service should create a concrete
 * implementation with a repository that extends JpaRepository<Event, String>.
 * 
 * Concrete implementations should add @Async and @Transactional annotations
 * to the methods that need them.
 */
public abstract class EventService {
    
    protected static final Logger log = LoggerFactory.getLogger(EventService.class);
    
    protected final ObjectMapper objectMapper;
    
    public EventService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get the repository - must be implemented by concrete classes.
     * Returns a JpaRepository<Event, String> implementation.
     */
    @SuppressWarnings("rawtypes")
    protected abstract org.springframework.data.repository.CrudRepository getEventRepository();
    
    /**
     * Get the service name - must be implemented by concrete classes
     */
    protected abstract String getServiceName();
    
    /**
     * Get the async executor name - can be overridden
     */
    protected String getAsyncExecutorName() {
        return "eventTaskExecutor";
    }
    
    /**
     * Log an HTTP request event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logHttpRequest(String method, String path, Integer status, Long durationMs,
                              String traceId, String requestId, String userAgent, String ipAddress,
                              String userId, String userEmail, Map<String, Object> additionalData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("HTTP_REQUEST");
            event.setHttpMethod(method);
            event.setHttpPath(path);
            event.setHttpStatus(status);
            event.setDurationMs(durationMs);
            event.setTraceId(traceId);
            event.setRequestId(requestId);
            event.setUserAgent(userAgent);
            event.setIpAddress(ipAddress);
            event.setUserId(userId);
            event.setUserEmail(userEmail);
            event.setEndpoint(path);
            
            if (additionalData != null && !additionalData.isEmpty()) {
                event.setEventData(objectMapper.writeValueAsString(additionalData));
            }
            
            parseUserAgent(event, userAgent);
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log HTTP request event", e);
        }
    }
    
    /**
     * Log a user action event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logUserAction(String actionType, String userId, String userEmail,
                             String endpoint, Map<String, Object> actionData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("USER_ACTION");
            event.setUserId(userId);
            event.setUserEmail(userEmail);
            event.setEndpoint(endpoint);
            
            Map<String, Object> eventDataMap = Map.of(
                "actionType", actionType,
                "data", actionData != null ? actionData : Map.of()
            );
            event.setEventData(objectMapper.writeValueAsString(eventDataMap));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log user action event", e);
        }
    }
    
    /**
     * Log a login event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logLogin(String userId, String userEmail, String ipAddress, String userAgent,
                        String sessionId, Map<String, Object> loginData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("LOGIN");
            event.setUserId(userId);
            event.setUserEmail(userEmail);
            event.setIpAddress(ipAddress);
            event.setUserAgent(userAgent);
            event.setSessionId(sessionId);
            event.setEndpoint("/auth/login");
            
            if (loginData != null && !loginData.isEmpty()) {
                event.setEventData(objectMapper.writeValueAsString(loginData));
            }
            
            parseUserAgent(event, userAgent);
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log login event", e);
        }
    }
    
    /**
     * Log a logout event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logLogout(String userId, String userEmail, String sessionId, Map<String, Object> logoutData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("LOGOUT");
            event.setUserId(userId);
            event.setUserEmail(userEmail);
            event.setSessionId(sessionId);
            event.setEndpoint("/auth/logout");
            
            if (logoutData != null && !logoutData.isEmpty()) {
                event.setEventData(objectMapper.writeValueAsString(logoutData));
            }
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log logout event", e);
        }
    }
    
    /**
     * Log video watch progress event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logVideoWatchProgress(String userId, String courseId, String lectureId,
                                     Integer progressPercent, Long watchDurationSeconds,
                                     Map<String, Object> progressData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("VIDEO_WATCH_PROGRESS");
            event.setUserId(userId);
            event.setEndpoint("/api/lectures/" + lectureId + "/progress");
            
            Map<String, Object> eventDataMap = new java.util.HashMap<>();
            eventDataMap.put("courseId", courseId);
            eventDataMap.put("lectureId", lectureId);
            eventDataMap.put("progressPercent", progressPercent);
            eventDataMap.put("watchDurationSeconds", watchDurationSeconds);
            if (progressData != null) {
                eventDataMap.putAll(progressData);
            }
            event.setEventData(objectMapper.writeValueAsString(eventDataMap));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log video watch progress event", e);
        }
    }
    
    /**
     * Log assessment submission event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logAssessmentSubmission(String userId, String assessmentId, String courseId,
                                      Integer score, Integer maxScore, Boolean passed,
                                      Map<String, Object> submissionData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("ASSESSMENT_SUBMITTED");
            event.setUserId(userId);
            event.setEndpoint("/api/assessments/" + assessmentId + "/submit");
            
            Map<String, Object> eventDataMap = new java.util.HashMap<>();
            eventDataMap.put("assessmentId", assessmentId);
            eventDataMap.put("courseId", courseId);
            eventDataMap.put("score", score);
            eventDataMap.put("maxScore", maxScore);
            eventDataMap.put("passed", passed);
            if (submissionData != null) {
                eventDataMap.putAll(submissionData);
            }
            event.setEventData(objectMapper.writeValueAsString(eventDataMap));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log assessment submission event", e);
        }
    }
    
    /**
     * Log search query event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logSearchQuery(String userId, String query, String searchType,
                              Integer resultCount, Long durationMs, Map<String, Object> searchData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("SEARCH_QUERY");
            event.setUserId(userId);
            event.setDurationMs(durationMs);
            event.setEndpoint("/api/search");
            
            Map<String, Object> eventDataMap = new java.util.HashMap<>();
            eventDataMap.put("query", query);
            eventDataMap.put("searchType", searchType);
            eventDataMap.put("resultCount", resultCount);
            if (searchData != null) {
                eventDataMap.putAll(searchData);
            }
            event.setEventData(objectMapper.writeValueAsString(eventDataMap));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log search query event", e);
        }
    }
    
    /**
     * Log file upload event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logFileUpload(String userId, String fileName, String fileType, Long fileSize,
                             String uploadType, Map<String, Object> uploadData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("FILE_UPLOADED");
            event.setUserId(userId);
            event.setEndpoint("/api/upload");
            
            Map<String, Object> eventDataMap = new java.util.HashMap<>();
            eventDataMap.put("fileName", fileName);
            eventDataMap.put("fileType", fileType);
            eventDataMap.put("fileSize", fileSize);
            eventDataMap.put("uploadType", uploadType);
            if (uploadData != null) {
                eventDataMap.putAll(uploadData);
            }
            event.setEventData(objectMapper.writeValueAsString(eventDataMap));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log file upload event", e);
        }
    }
    
    /**
     * Log lecture completion event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logLectureCompletion(String userId, String courseId, String lectureId,
                                    Integer totalDurationSeconds, Map<String, Object> completionData) {
        try {
            Event event = createBaseEvent();
            event.setEventType("LECTURE_COMPLETED");
            event.setUserId(userId);
            event.setEndpoint("/api/lectures/" + lectureId + "/complete");
            
            Map<String, Object> eventDataMap = new java.util.HashMap<>();
            eventDataMap.put("courseId", courseId);
            eventDataMap.put("lectureId", lectureId);
            eventDataMap.put("totalDurationSeconds", totalDurationSeconds);
            if (completionData != null) {
                eventDataMap.putAll(completionData);
            }
            event.setEventData(objectMapper.writeValueAsString(eventDataMap));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log lecture completion event", e);
        }
    }
    
    /**
     * Log an error event asynchronously.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logError(String errorType, String errorMessage, String stackTrace,
                        String endpoint, String userId, String traceId) {
        try {
            Event event = createBaseEvent();
            event.setEventType("ERROR");
            event.setErrorMessage(errorMessage);
            event.setErrorStackTrace(stackTrace);
            event.setEndpoint(endpoint);
            event.setUserId(userId);
            event.setTraceId(traceId);
            
            Map<String, Object> errorData = Map.of(
                "errorType", errorType != null ? errorType : "UNKNOWN"
            );
            event.setEventData(objectMapper.writeValueAsString(errorData));
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log error event", e);
        }
    }
    
    /**
     * Generic method to log any event with full control.
     * Note: Concrete implementations should add @Async and @Transactional annotations.
     */
    public void logEvent(Event event) {
        try {
            if (event.getId() == null) {
                event.setId(UlidGenerator.nextUlid());
            }
            if (event.getClientId() == null) {
                event.setClientId(getClientId());
            }
            if (event.getCreatedAt() == null) {
                event.setCreatedAt(OffsetDateTime.now());
            }
            if (event.getServiceName() == null) {
                event.setServiceName(getServiceName());
            }
            
            ((org.springframework.data.repository.CrudRepository<Event, String>) getEventRepository()).save(event);
        } catch (Exception e) {
            log.error("Failed to log event", e);
        }
    }
    
    /**
     * Create a base event with common fields populated.
     */
    protected Event createBaseEvent() {
        Event event = new Event();
        event.setId(UlidGenerator.nextUlid());
        event.setClientId(getClientId());
        event.setServiceName(getServiceName());
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }
    
    /**
     * Get client ID from tenant context, handling SYSTEM_ADMIN case.
     */
    protected UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return UUID.fromString(clientIdStr);
        }
        // For SYSTEM_ADMIN, use a placeholder UUID
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
    
    /**
     * Parse user agent string to extract device, browser, and OS info.
     */
    protected void parseUserAgent(Event event, String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return;
        }
        
        String ua = userAgent.toLowerCase();
        
        // Detect device type
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipad")) {
            event.setDeviceType("mobile");
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            event.setDeviceType("tablet");
        } else {
            event.setDeviceType("desktop");
        }
        
        // Detect browser
        if (ua.contains("chrome") && !ua.contains("edg")) {
            event.setBrowser("Chrome");
        } else if (ua.contains("firefox")) {
            event.setBrowser("Firefox");
        } else if (ua.contains("safari") && !ua.contains("chrome")) {
            event.setBrowser("Safari");
        } else if (ua.contains("edg")) {
            event.setBrowser("Edge");
        } else if (ua.contains("opera")) {
            event.setBrowser("Opera");
        }
        
        // Detect OS
        if (ua.contains("windows")) {
            event.setOs("Windows");
        } else if (ua.contains("mac os x") || ua.contains("macintosh")) {
            event.setOs("macOS");
        } else if (ua.contains("linux")) {
            event.setOs("Linux");
        } else if (ua.contains("android")) {
            event.setOs("Android");
        } else if (ua.contains("iphone") || ua.contains("ipad")) {
            event.setOs("iOS");
        }
    }
}
