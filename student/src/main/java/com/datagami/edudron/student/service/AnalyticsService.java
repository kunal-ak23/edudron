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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    
    public CourseAnalyticsDTO getCourseEngagementMetrics(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<LectureViewSession> sessions = sessionRepository.findByClientIdAndCourseId(clientId, courseId);
        
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
        
        dto.setTotalViewingSessions((long) sessions.size());
        dto.setUniqueStudentsEngaged(sessionRepository.countUniqueViewersByCourseId(clientId, courseId));
        
        // Calculate average time per lecture
        Map<String, List<LectureViewSession>> sessionsByLecture = sessions.stream()
            .filter(s -> s.getSessionEndedAt() != null && s.getDurationSeconds() != null)
            .collect(Collectors.groupingBy(LectureViewSession::getLectureId));
        
        if (!sessionsByLecture.isEmpty()) {
            double avgDuration = sessionsByLecture.values().stream()
                .flatMap(List::stream)
                .mapToInt(LectureViewSession::getDurationSeconds)
                .average()
                .orElse(0.0);
            dto.setAverageTimePerLectureSeconds((int) avgDuration);
        }
        
        // Calculate overall completion rate
        long completedSessions = sessions.stream()
            .filter(s -> s.getIsCompletedInSession() != null && s.getIsCompletedInSession())
            .count();
        BigDecimal completionRate = sessions.isEmpty() ? BigDecimal.ZERO :
            BigDecimal.valueOf(completedSessions)
                .divide(BigDecimal.valueOf(sessions.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        dto.setOverallCompletionRate(completionRate);
        
        // Get lecture engagements
        dto.setLectureEngagements(getLectureEngagementSummaries(courseId, sessionsByLecture));
        
        // Get skipped lectures
        dto.setSkippedLectures(detectSkippedLectures(courseId, sessionsByLecture));
        
        // Get activity timeline
        dto.setActivityTimeline(getActivityTimeline(sessions));
        
        return dto;
    }
    
    public LectureAnalyticsDTO getLectureEngagementMetrics(String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<LectureViewSession> sessions = sessionRepository.findByClientIdAndLectureId(clientId, lectureId);
        
        LectureAnalyticsDTO dto = new LectureAnalyticsDTO();
        dto.setLectureId(lectureId);
        
        // Get courseId from sessions if available
        String courseId = sessions.isEmpty() ? null : sessions.get(0).getCourseId();
        
        // Get lecture title and duration from content service
        Integer lectureDurationSeconds = null;
        String lectureTitle = null;
        try {
            // Use the correct endpoint with courseId if available, otherwise fallback to old endpoint
            String url = courseId != null 
                ? gatewayUrl + "/content/courses/" + courseId + "/lectures/" + lectureId
                : gatewayUrl + "/content/lectures/" + lectureId;
            log.debug("Fetching lecture info from: {}", url);
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
            log.warn("Failed to fetch lecture info for {}: {}", lectureId, e.getMessage());
        }
        dto.setLectureTitle(lectureTitle != null && !lectureTitle.trim().isEmpty() 
            ? lectureTitle 
            : "Lecture " + lectureId.substring(0, Math.min(8, lectureId.length())));
        
        dto.setTotalViews((long) sessions.size());
        dto.setUniqueViewers(sessionRepository.countUniqueViewersByLectureId(clientId, lectureId));
        
        // Calculate average session duration
        List<LectureViewSession> completedSessions = sessions.stream()
            .filter(s -> s.getSessionEndedAt() != null && s.getDurationSeconds() != null)
            .collect(Collectors.toList());
        
        if (!completedSessions.isEmpty()) {
            double avgDuration = completedSessions.stream()
                .mapToInt(LectureViewSession::getDurationSeconds)
                .average()
                .orElse(0.0);
            dto.setAverageSessionDurationSeconds((int) avgDuration);
        }
        
        // Calculate completion rate
        long completed = sessions.stream()
            .filter(s -> s.getIsCompletedInSession() != null && s.getIsCompletedInSession())
            .count();
        BigDecimal completionRate = sessions.isEmpty() ? BigDecimal.ZERO :
            BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(sessions.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        dto.setCompletionRate(completionRate);
        
        // Calculate skip rate
        if (lectureDurationSeconds != null && lectureDurationSeconds > 0) {
            int thresholdSeconds = (int) (lectureDurationSeconds * 0.1); // 10% threshold
            long skipped = completedSessions.stream()
                .filter(s -> s.getDurationSeconds() < thresholdSeconds)
                .count();
            BigDecimal skipRate = completedSessions.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(skipped)
                    .divide(BigDecimal.valueOf(completedSessions.size()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            dto.setSkipRate(skipRate);
        } else {
            dto.setSkipRate(BigDecimal.ZERO);
        }
        
        // First and last view
        Optional<OffsetDateTime> firstView = sessions.stream()
            .map(LectureViewSession::getSessionStartedAt)
            .min(OffsetDateTime::compareTo);
        dto.setFirstViewAt(firstView.orElse(null));
        
        Optional<OffsetDateTime> lastView = sessions.stream()
            .map(LectureViewSession::getSessionStartedAt)
            .max(OffsetDateTime::compareTo);
        dto.setLastViewAt(lastView.orElse(null));
        
        // Student engagements
        dto.setStudentEngagements(getStudentEngagements(sessions));
        
        // Recent sessions (last 20)
        List<LectureViewSessionDTO> recentSessions = sessions.stream()
            .sorted((a, b) -> b.getSessionStartedAt().compareTo(a.getSessionStartedAt()))
            .limit(20)
            .map(this::toSessionDTO)
            .collect(Collectors.toList());
        dto.setRecentSessions(recentSessions);
        
        return dto;
    }
    
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
    
    private List<LectureEngagementSummaryDTO> getLectureEngagementSummaries(
            String courseId, Map<String, List<LectureViewSession>> sessionsByLecture) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<LectureEngagementSummaryDTO> summaries = new ArrayList<>();
        
        for (Map.Entry<String, List<LectureViewSession>> entry : sessionsByLecture.entrySet()) {
            String lectureId = entry.getKey();
            List<LectureViewSession> sessions = entry.getValue();
            
            LectureEngagementSummaryDTO summary = new LectureEngagementSummaryDTO();
            summary.setLectureId(lectureId);
            
            // Get lecture title and duration - use the correct endpoint with courseId
            String lectureTitle = null;
            Integer lectureDurationSeconds = null;
            try {
                String url = gatewayUrl + "/content/courses/" + courseId + "/lectures/" + lectureId;
                log.debug("Fetching lecture info from: {}", url);
                ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                    url, HttpMethod.GET, null, JsonNode.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode lecture = response.getBody();
                    log.debug("Lecture response for {}: {}", lectureId, lecture.toString());
                    if (lecture.has("title")) {
                        String title = lecture.get("title").asText();
                        if (title != null && !title.trim().isEmpty() && !title.equals(lectureId)) {
                            lectureTitle = title;
                            log.debug("Found lecture title for {}: {}", lectureId, lectureTitle);
                        } else {
                            log.debug("Title for {} is empty or equals ID: '{}'", lectureId, title);
                        }
                    } else {
                        log.debug("Lecture {} response does not have 'title' field", lectureId);
                    }
                    if (lecture.has("durationSeconds")) {
                        lectureDurationSeconds = lecture.get("durationSeconds").asInt();
                        log.debug("Found lecture duration for {}: {} seconds", lectureId, lectureDurationSeconds);
                    }
                } else {
                    log.warn("Failed to fetch lecture info for {}: HTTP {}", lectureId, response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch lecture info for {}: {}", lectureId, e.getMessage(), e);
            }
            // Set title, or use a formatted version of the ID if title is not available
            if (lectureTitle == null || lectureTitle.trim().isEmpty()) {
                lectureTitle = "Lecture " + lectureId.substring(0, Math.min(8, lectureId.length()));
                log.debug("Using fallback title for {}: {}", lectureId, lectureTitle);
            }
            summary.setLectureTitle(lectureTitle);
            
            summary.setTotalViews((long) sessions.size());
            summary.setUniqueViewers(sessionRepository.countUniqueViewersByLectureId(clientId, lectureId));
            
            List<LectureViewSession> completedSessions = sessions.stream()
                .filter(s -> s.getSessionEndedAt() != null && s.getDurationSeconds() != null)
                .collect(Collectors.toList());
            
            if (!completedSessions.isEmpty()) {
                double avgDuration = completedSessions.stream()
                    .mapToInt(LectureViewSession::getDurationSeconds)
                    .average()
                    .orElse(0.0);
                summary.setAverageDurationSeconds((int) avgDuration);
            }
            
            long completed = sessions.stream()
                .filter(s -> s.getIsCompletedInSession() != null && s.getIsCompletedInSession())
                .count();
            BigDecimal completionRate = sessions.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(completed)
                    .divide(BigDecimal.valueOf(sessions.size()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            summary.setCompletionRate(completionRate);
            
            // Calculate skip rate if we have lecture duration
            if (lectureDurationSeconds != null && lectureDurationSeconds > 0 && !completedSessions.isEmpty()) {
                int thresholdSeconds = (int) (lectureDurationSeconds * 0.1);
                long skipped = completedSessions.stream()
                    .filter(s -> s.getDurationSeconds() < thresholdSeconds)
                    .count();
                BigDecimal skipRate = BigDecimal.valueOf(skipped)
                    .divide(BigDecimal.valueOf(completedSessions.size()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                summary.setSkipRate(skipRate);
            } else {
                summary.setSkipRate(BigDecimal.ZERO);
            }
            
            summaries.add(summary);
        }
        
        return summaries;
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
    
    private List<ActivityTimelinePointDTO> getActivityTimeline(List<LectureViewSession> sessions) {
        // Group by day
        Map<String, List<LectureViewSession>> byDay = sessions.stream()
            .collect(Collectors.groupingBy(s -> 
                s.getSessionStartedAt().toLocalDate().toString()));
        
        List<ActivityTimelinePointDTO> timeline = new ArrayList<>();
        
        for (Map.Entry<String, List<LectureViewSession>> entry : byDay.entrySet()) {
            String dateStr = entry.getKey();
            List<LectureViewSession> daySessions = entry.getValue();
            
            long uniqueStudents = daySessions.stream()
                .map(LectureViewSession::getStudentId)
                .distinct()
                .count();
            
            OffsetDateTime timestamp = daySessions.get(0).getSessionStartedAt()
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            timeline.add(new ActivityTimelinePointDTO(
                timestamp,
                (long) daySessions.size(),
                uniqueStudents
            ));
        }
        
        timeline.sort(Comparator.comparing(ActivityTimelinePointDTO::getTimestamp));
        return timeline;
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
