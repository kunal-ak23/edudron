package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.student.domain.LectureViewSession;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.LectureViewSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    
    @Autowired
    private LectureViewSessionRepository sessionRepository;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            interceptors.add((request, body, execution) -> {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().add("Authorization", authHeader);
                        }
                    }
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
    /**
     * Batch fetch all lecture metadata for a course in one HTTP call.
     * Returns a map of lectureId -> {title, durationSeconds}
     */
    private Map<String, LectureMetadata> batchFetchLectureMetadata(String courseId) {
        Map<String, LectureMetadata> metadataMap = new HashMap<>();
        try {
            String url = gatewayUrl + "/content/courses/" + courseId + "/lectures";
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                url, HttpMethod.GET, null, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode sections = response.getBody();
                if (sections.isArray()) {
                    for (JsonNode section : sections) {
                        String sectionId = section.has("id") ? section.get("id").asText() : null;
                        String title = section.has("title") ? section.get("title").asText() : null;
                        Integer duration = section.has("durationSeconds") ? section.get("durationSeconds").asInt() : null;
                        
                        if (sectionId != null) {
                            metadataMap.put(sectionId, new LectureMetadata(title, duration));
                        }
                        
                        // Also check sub-lectures
                        if (section.has("lectures") && section.get("lectures").isArray()) {
                            for (JsonNode lecture : section.get("lectures")) {
                                String lectureId = lecture.has("id") ? lecture.get("id").asText() : null;
                                String lectureTitle = lecture.has("title") ? lecture.get("title").asText() : null;
                                Integer lectureDuration = lecture.has("durationSeconds") ? lecture.get("durationSeconds").asInt() : null;
                                
                                if (lectureId != null) {
                                    metadataMap.put(lectureId, new LectureMetadata(lectureTitle, lectureDuration));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch fetch lecture metadata for course {}: {}", courseId, e.getMessage());
        }
        return metadataMap;
    }
    
    /**
     * Inner class to hold lecture metadata.
     */
    private static class LectureMetadata {
        final String title;
        final Integer durationSeconds;
        
        LectureMetadata(String title, Integer durationSeconds) {
            this.title = title;
            this.durationSeconds = durationSeconds;
        }
    }
    
    @Cacheable(value = "courseAnalytics", key = "#courseId", unless = "#result == null")
    public CourseAnalyticsDTO getCourseEngagementMetrics(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        CourseAnalyticsDTO dto = new CourseAnalyticsDTO();
        dto.setCourseId(courseId);
        
        // Get course title from content service
        try {
            String url = gatewayUrl + "/content/courses/" + courseId;
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                url, HttpMethod.GET, null, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode course = response.getBody();
                dto.setCourseTitle(course.has("title") ? course.get("title").asText() : courseId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch course title for {}: {}", courseId, e.getMessage());
            dto.setCourseTitle(courseId);
        }
        
        // Get course-level aggregates from database (OPTIMIZED: database-level aggregation)
        Object[] courseAggregates = sessionRepository.getCourseAggregates(clientId, courseId);
        if (courseAggregates != null && courseAggregates.length >= 4) {
            dto.setTotalViewingSessions(((Number) courseAggregates[0]).longValue());
            dto.setUniqueStudentsEngaged(((Number) courseAggregates[1]).longValue());
            Double avgDuration = courseAggregates[2] != null ? ((Number) courseAggregates[2]).doubleValue() : 0.0;
            dto.setAverageTimePerLectureSeconds(avgDuration.intValue());
            
            long completedSessions = ((Number) courseAggregates[3]).longValue();
            long totalSessions = ((Number) courseAggregates[0]).longValue();
            BigDecimal completionRate = totalSessions > 0 ?
                BigDecimal.valueOf(completedSessions)
                    .divide(BigDecimal.valueOf(totalSessions), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            dto.setOverallCompletionRate(completionRate);
        } else {
            dto.setTotalViewingSessions(0L);
            dto.setUniqueStudentsEngaged(0L);
            dto.setAverageTimePerLectureSeconds(0);
            dto.setOverallCompletionRate(BigDecimal.ZERO);
        }
        
        // OPTIMIZED: Batch fetch all lecture metadata in one HTTP call
        Map<String, LectureMetadata> lectureMetadata = batchFetchLectureMetadata(courseId);
        
        // OPTIMIZED: Get aggregated lecture engagement data (database-level aggregation)
        // Use a default threshold of 60 seconds for skip detection (will be refined per lecture)
        List<LectureEngagementAggregateDTO> aggregates = sessionRepository.getLectureEngagementAggregatesByCourse(
            clientId, courseId, 60);
        
        // Convert aggregates to DTOs with metadata
        List<LectureEngagementSummaryDTO> lectureEngagements = new ArrayList<>();
        List<SkippedLectureDTO> skippedLectures = new ArrayList<>();
        
        for (LectureEngagementAggregateDTO aggregate : aggregates) {
            LectureMetadata metadata = lectureMetadata.get(aggregate.getLectureId());
            String lectureTitle = metadata != null && metadata.title != null ? metadata.title :
                "Lecture " + aggregate.getLectureId().substring(0, Math.min(8, aggregate.getLectureId().length()));
            Integer lectureDuration = metadata != null ? metadata.durationSeconds : null;
            
            LectureEngagementSummaryDTO summary = new LectureEngagementSummaryDTO();
            summary.setLectureId(aggregate.getLectureId());
            summary.setLectureTitle(lectureTitle);
            summary.setTotalViews(aggregate.getTotalViews());
            summary.setUniqueViewers(aggregate.getUniqueViewers());
            summary.setAverageDurationSeconds(aggregate.getAverageDurationSecondsInt());
            summary.setCompletionRate(aggregate.getCompletionRate());
            
            // Calculate skip rate if we have lecture duration
            if (lectureDuration != null && lectureDuration > 0 && aggregate.getTotalSessions() > 0) {
                BigDecimal skipRate = aggregate.getSkipRate();
                summary.setSkipRate(skipRate);
                
                // Check if this lecture should be marked as skipped (>50% skip rate)
                if (skipRate.compareTo(BigDecimal.valueOf(50)) > 0) {
                    SkippedLectureDTO skipped = new SkippedLectureDTO();
                    skipped.setLectureId(aggregate.getLectureId());
                    skipped.setLectureTitle(lectureTitle);
                    skipped.setLectureDurationSeconds(lectureDuration);
                    skipped.setTotalSessions(aggregate.getTotalSessions());
                    skipped.setSkippedSessions(aggregate.getShortDurationSessions());
                    skipped.setSkipRate(skipRate);
                    skipped.setAverageDurationSeconds(aggregate.getAverageDurationSecondsInt());
                    skipped.setSkipReason("DURATION_THRESHOLD");
                    skippedLectures.add(skipped);
                }
            } else {
                summary.setSkipRate(BigDecimal.ZERO);
            }
            
            lectureEngagements.add(summary);
        }
        
        dto.setLectureEngagements(lectureEngagements);
        dto.setSkippedLectures(skippedLectures);
        
        // OPTIMIZED: Get activity timeline (aggregated by day at database level)
        List<Object[]> timelineData = sessionRepository.getActivityTimelineByCourse(clientId, courseId);
        List<ActivityTimelinePointDTO> timeline = new ArrayList<>();
        for (Object[] row : timelineData) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long sessionCount = ((Number) row[1]).longValue();
            long uniqueStudents = ((Number) row[2]).longValue();
            
            OffsetDateTime timestamp = date.atStartOfDay().atOffset(ZoneOffset.UTC);
            timeline.add(new ActivityTimelinePointDTO(timestamp, sessionCount, uniqueStudents));
        }
        dto.setActivityTimeline(timeline);
        
        return dto;
    }
    
    /**
     * Fetch single lecture metadata (fallback for lecture-specific analytics).
     */
    private LectureMetadata fetchLectureMetadata(String courseId, String lectureId) {
        try {
            String url = gatewayUrl + "/content/courses/" + courseId + "/lectures/" + lectureId;
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                url, HttpMethod.GET, null, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode lecture = response.getBody();
                String title = lecture.has("title") ? lecture.get("title").asText() : null;
                Integer duration = lecture.has("durationSeconds") ? lecture.get("durationSeconds").asInt() : null;
                return new LectureMetadata(title, duration);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch lecture metadata for {}: {}", lectureId, e.getMessage());
        }
        return new LectureMetadata(null, null);
    }
    
    @Cacheable(value = "lectureAnalytics", key = "#lectureId", unless = "#result == null")
    public LectureAnalyticsDTO getLectureEngagementMetrics(String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // OPTIMIZED: Get courseId from first session with pagination (limit 1)
        Page<LectureViewSession> sampleSessions = sessionRepository.findRecentSessionsByLectureId(
            clientId, lectureId, PageRequest.of(0, 1));
        String courseId = sampleSessions.isEmpty() ? null : sampleSessions.getContent().get(0).getCourseId();
        
        LectureAnalyticsDTO dto = new LectureAnalyticsDTO();
        dto.setLectureId(lectureId);
        
        // Fetch lecture metadata
        LectureMetadata metadata = courseId != null ? 
            fetchLectureMetadata(courseId, lectureId) : 
            new LectureMetadata(null, null);
        
        dto.setLectureTitle(metadata.title != null && !metadata.title.trim().isEmpty() ?
            metadata.title : "Lecture " + lectureId.substring(0, Math.min(8, lectureId.length())));
        
        // OPTIMIZED: Get aggregated metrics from database
        Integer thresholdSeconds = metadata.durationSeconds != null ? 
            (int) (metadata.durationSeconds * 0.1) : 60;
        LectureEngagementAggregateDTO aggregate = sessionRepository.getLectureEngagementAggregate(
            clientId, lectureId, thresholdSeconds);
        
        if (aggregate != null) {
            dto.setTotalViews(aggregate.getTotalViews());
            dto.setUniqueViewers(aggregate.getUniqueViewers());
            dto.setAverageSessionDurationSeconds(aggregate.getAverageDurationSecondsInt());
            dto.setCompletionRate(aggregate.getCompletionRate());
            dto.setSkipRate(aggregate.getSkipRate());
        } else {
            dto.setTotalViews(0L);
            dto.setUniqueViewers(0L);
            dto.setAverageSessionDurationSeconds(0);
            dto.setCompletionRate(BigDecimal.ZERO);
            dto.setSkipRate(BigDecimal.ZERO);
        }
        
        // OPTIMIZED: Get first and last view timestamps from database
        Object[] firstLast = sessionRepository.getFirstAndLastView(clientId, lectureId);
        if (firstLast != null && firstLast.length >= 2) {
            if (firstLast[0] != null) {
                dto.setFirstViewAt(((java.sql.Timestamp) firstLast[0]).toInstant()
                    .atOffset(ZoneOffset.UTC));
            }
            if (firstLast[1] != null) {
                dto.setLastViewAt(((java.sql.Timestamp) firstLast[1]).toInstant()
                    .atOffset(ZoneOffset.UTC));
            }
        }
        
        // OPTIMIZED: Get recent sessions with pagination (limit 20)
        Pageable pageable = PageRequest.of(0, 20);
        Page<LectureViewSession> recentSessionsPage = sessionRepository.findRecentSessionsByLectureId(
            clientId, lectureId, pageable);
        List<LectureViewSessionDTO> recentSessions = recentSessionsPage.getContent().stream()
            .map(this::toSessionDTO)
            .collect(Collectors.toList());
        dto.setRecentSessions(recentSessions);
        
        // OPTIMIZED: Get student engagements with pagination (limit 100 to avoid memory issues)
        Pageable studentPageable = PageRequest.of(0, 100);
        Page<LectureViewSession> studentSessionsPage = sessionRepository.findRecentSessionsByLectureId(
            clientId, lectureId, studentPageable);
        List<LectureViewSession> studentSessions = studentSessionsPage.getContent();
        dto.setStudentEngagements(getStudentEngagements(studentSessions));
        
        return dto;
    }
    
    // NOTE: This method is kept for backward compatibility but is no longer used
    // Skipped lectures are now detected in getCourseEngagementMetrics using aggregated queries
    @Deprecated
    public List<SkippedLectureDTO> detectSkippedLectures(String courseId, Map<String, List<LectureViewSession>> sessionsByLecture) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<SkippedLectureDTO> skipped = new ArrayList<>();
        
        for (Map.Entry<String, List<LectureViewSession>> entry : sessionsByLecture.entrySet()) {
            String lectureId = entry.getKey();
            List<LectureViewSession> sessions = entry.getValue();
            
            // Get lecture duration from content service
            Integer lectureDurationSeconds = null;
            String lectureTitle = null;
            try {
                // Use the correct endpoint with courseId
                String url = gatewayUrl + "/content/courses/" + courseId + "/lectures/" + lectureId;
                log.debug("Fetching lecture info for skipped detection from: {}", url);
                ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                    url, HttpMethod.GET, null, JsonNode.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode lecture = response.getBody();
                    if (lecture.has("title")) {
                        String title = lecture.get("title").asText();
                        if (title != null && !title.trim().isEmpty() && !title.equals(lectureId)) {
                            lectureTitle = title;
                        }
                    }
                    if (lecture.has("durationSeconds")) {
                        lectureDurationSeconds = lecture.get("durationSeconds").asInt();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch lecture duration for {}: {}", lectureId, e.getMessage());
            }
            // Use fallback title if not found
            if (lectureTitle == null || lectureTitle.trim().isEmpty()) {
                lectureTitle = "Lecture " + lectureId.substring(0, Math.min(8, lectureId.length()));
            }
            
            if (lectureDurationSeconds == null || lectureDurationSeconds == 0) {
                continue; // Skip if we don't have duration
            }
            
            // Create final copy for use in lambda expressions
            final int finalLectureDurationSeconds = lectureDurationSeconds;
            
            List<LectureViewSession> completedSessions = sessions.stream()
                .filter(s -> s.getSessionEndedAt() != null && s.getDurationSeconds() != null)
                .collect(Collectors.toList());
            
            if (completedSessions.isEmpty()) {
                continue;
            }
            
            // Method 1: Duration threshold (< 10% of lecture duration)
            int thresholdSeconds = (int) (finalLectureDurationSeconds * 0.1);
            long shortDurationSessions = completedSessions.stream()
                .filter(s -> s.getDurationSeconds() < thresholdSeconds)
                .count();
            
            // Method 2: Quick completion (marked complete but < 5% duration)
            int quickCompletionThreshold = (int) (finalLectureDurationSeconds * 0.05);
            long quickCompletionSessions = completedSessions.stream()
                .filter(s -> s.getIsCompletedInSession() != null && s.getIsCompletedInSession())
                .filter(s -> s.getDurationSeconds() < quickCompletionThreshold)
                .count();
            
            long totalSkipped = Math.max(shortDurationSessions, quickCompletionSessions);
            
            if (totalSkipped > 0) {
                BigDecimal skipRate = BigDecimal.valueOf(totalSkipped)
                    .divide(BigDecimal.valueOf(completedSessions.size()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                // Only include if skip rate > 50%
                if (skipRate.compareTo(BigDecimal.valueOf(50)) > 0) {
                    SkippedLectureDTO skippedDto = new SkippedLectureDTO();
                    skippedDto.setLectureId(lectureId);
                    skippedDto.setLectureTitle(lectureTitle);
                    skippedDto.setLectureDurationSeconds(finalLectureDurationSeconds);
                    skippedDto.setTotalSessions((long) completedSessions.size());
                    skippedDto.setSkippedSessions(totalSkipped);
                    skippedDto.setSkipRate(skipRate);
                    
                    double avgDuration = completedSessions.stream()
                        .mapToInt(LectureViewSession::getDurationSeconds)
                        .average()
                        .orElse(0.0);
                    skippedDto.setAverageDurationSeconds((int) avgDuration);
                    
                    skippedDto.setSkipReason(quickCompletionSessions > shortDurationSessions ? 
                        "QUICK_COMPLETION" : "DURATION_THRESHOLD");
                    
                    skipped.add(skippedDto);
                }
            }
        }
        
        return skipped;
    }
    
    // NOTE: This method is deprecated - lecture engagement summaries are now generated
    // using database-level aggregations in getCourseEngagementMetrics
    @Deprecated
    private List<LectureEngagementSummaryDTO> getLectureEngagementSummaries(
            String courseId, Map<String, List<LectureViewSession>> sessionsByLecture) {
        // This method is no longer used but kept for backward compatibility
        return new ArrayList<>();
    }
    
    private List<StudentLectureEngagementDTO> getStudentEngagements(List<LectureViewSession> sessions) {
        Map<String, List<LectureViewSession>> byStudent = sessions.stream()
            .collect(Collectors.groupingBy(LectureViewSession::getStudentId));
        
        List<StudentLectureEngagementDTO> engagements = new ArrayList<>();
        
        for (Map.Entry<String, List<LectureViewSession>> entry : byStudent.entrySet()) {
            String studentId = entry.getKey();
            List<LectureViewSession> studentSessions = entry.getValue();
            
            StudentLectureEngagementDTO engagement = new StudentLectureEngagementDTO();
            engagement.setStudentId(studentId);
            
            // Get student email from identity service
            try {
                String url = gatewayUrl + "/idp/users/" + studentId;
                ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                    url, HttpMethod.GET, null, JsonNode.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode user = response.getBody();
                    engagement.setStudentEmail(user.has("email") ? user.get("email").asText() : studentId);
                }
            } catch (Exception e) {
                engagement.setStudentEmail(studentId);
            }
            
            engagement.setTotalSessions((long) studentSessions.size());
            
            List<LectureViewSession> completedSessions = studentSessions.stream()
                .filter(s -> s.getSessionEndedAt() != null && s.getDurationSeconds() != null)
                .collect(Collectors.toList());
            
            if (!completedSessions.isEmpty()) {
                int totalDuration = completedSessions.stream()
                    .mapToInt(LectureViewSession::getDurationSeconds)
                    .sum();
                engagement.setTotalDurationSeconds(totalDuration);
                
                double avgDuration = completedSessions.stream()
                    .mapToInt(LectureViewSession::getDurationSeconds)
                    .average()
                    .orElse(0.0);
                engagement.setAverageSessionDurationSeconds((int) avgDuration);
            }
            
            Optional<OffsetDateTime> firstView = studentSessions.stream()
                .map(LectureViewSession::getSessionStartedAt)
                .min(OffsetDateTime::compareTo);
            engagement.setFirstViewAt(firstView.orElse(null));
            
            Optional<OffsetDateTime> lastView = studentSessions.stream()
                .map(LectureViewSession::getSessionStartedAt)
                .max(OffsetDateTime::compareTo);
            engagement.setLastViewAt(lastView.orElse(null));
            
            boolean isCompleted = studentSessions.stream()
                .anyMatch(s -> s.getIsCompletedInSession() != null && s.getIsCompletedInSession());
            engagement.setIsCompleted(isCompleted);
            
            engagements.add(engagement);
        }
        
        return engagements;
    }
    
    // NOTE: This method is deprecated - activity timeline is now generated
    // using database-level aggregations in getCourseEngagementMetrics
    @Deprecated
    private List<ActivityTimelinePointDTO> getActivityTimeline(List<LectureViewSession> sessions) {
        // This method is no longer used but kept for backward compatibility
        return new ArrayList<>();
    }
    
    private LectureViewSessionDTO toSessionDTO(LectureViewSession session) {
        LectureViewSessionDTO dto = new LectureViewSessionDTO();
        dto.setId(session.getId());
        dto.setClientId(session.getClientId());
        dto.setEnrollmentId(session.getEnrollmentId());
        dto.setStudentId(session.getStudentId());
        dto.setCourseId(session.getCourseId());
        dto.setLectureId(session.getLectureId());
        dto.setSessionStartedAt(session.getSessionStartedAt());
        dto.setSessionEndedAt(session.getSessionEndedAt());
        dto.setDurationSeconds(session.getDurationSeconds());
        dto.setProgressAtStart(session.getProgressAtStart());
        dto.setProgressAtEnd(session.getProgressAtEnd());
        dto.setIsCompletedInSession(session.getIsCompletedInSession());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }
}
