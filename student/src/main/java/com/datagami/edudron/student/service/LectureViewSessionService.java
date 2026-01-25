package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.LectureViewSession;
import com.datagami.edudron.student.dto.EndSessionRequest;
import com.datagami.edudron.student.dto.LectureViewSessionDTO;
import com.datagami.edudron.student.dto.StartSessionRequest;
import com.datagami.edudron.student.dto.UpdateSessionRequest;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.LectureViewSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LectureViewSessionService {
    
    private static final Logger log = LoggerFactory.getLogger(LectureViewSessionService.class);
    
    @Autowired
    private LectureViewSessionRepository sessionRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private CommonEventService eventService;
    
    public LectureViewSessionDTO startSession(String studentId, StartSessionRequest request) {
        log.info("[Session Service] Starting session: studentId={}, courseId={}, lectureId={}, progressAtStart={}", 
            studentId, request.getCourseId(), request.getLectureId(), request.getProgressAtStart());
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            log.error("[Session Service] Tenant context is not set for studentId={}", studentId);
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        log.debug("[Session Service] ClientId={}", clientId);
        
        // Verify enrollment
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, request.getCourseId());
        log.debug("[Session Service] Found {} enrollments for studentId={}, courseId={}", 
            enrollments.size(), studentId, request.getCourseId());
        
        if (enrollments.isEmpty()) {
            log.warn("[Session Service] Student {} is not enrolled in course {}", studentId, request.getCourseId());
            throw new IllegalArgumentException("Student is not enrolled in this course");
        }
        Enrollment enrollment = enrollments.get(0);
        log.debug("[Session Service] Using enrollment: enrollmentId={}", enrollment.getId());
        
        // Create new session
        LectureViewSession session = new LectureViewSession();
        String sessionId = UlidGenerator.nextUlid();
        session.setId(sessionId);
        session.setClientId(clientId);
        session.setEnrollmentId(enrollment.getId());
        session.setStudentId(studentId);
        session.setCourseId(request.getCourseId());
        session.setLectureId(request.getLectureId());
        session.setSessionStartedAt(java.time.OffsetDateTime.now());
        session.setProgressAtStart(request.getProgressAtStart() != null ? request.getProgressAtStart() : BigDecimal.ZERO);
        
        log.info("[Session Service] Saving session: sessionId={}, studentId={}, lectureId={}, startedAt={}", 
            sessionId, studentId, request.getLectureId(), session.getSessionStartedAt());
        
        LectureViewSession saved = sessionRepository.save(session);
        log.info("[Session Service] Successfully started lecture view session: sessionId={}, studentId={}, lectureId={}, enrollmentId={}", 
            saved.getId(), studentId, request.getLectureId(), enrollment.getId());
        
        // Invalidate course analytics cache when new session is created
        evictCourseAnalyticsCache(saved.getCourseId());
        
        return toDTO(saved);
    }
    
    @Transactional
    public LectureViewSessionDTO updateSession(String sessionId, UpdateSessionRequest request) {
        log.info("[Session Service] Updating session: sessionId={}, progressAtEnd={}, isCompleted={}", 
            sessionId, request.getProgressAtEnd(), request.getIsCompleted());
        
        LectureViewSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> {
                log.error("[Session Service] Session not found: sessionId={}", sessionId);
                return new IllegalArgumentException("Session not found: " + sessionId);
            });
        
        log.debug("[Session Service] Found session: sessionId={}, lectureId={}, studentId={}, isEnded={}", 
            sessionId, session.getLectureId(), session.getStudentId(), session.getSessionEndedAt() != null);
        
        // Update progress and completion without ending the session
        boolean needsUpdate = false;
        if (request.getProgressAtEnd() != null && 
            (session.getProgressAtEnd() == null || !session.getProgressAtEnd().equals(request.getProgressAtEnd()))) {
            session.setProgressAtEnd(request.getProgressAtEnd());
            needsUpdate = true;
        }
        if (request.getIsCompleted() != null && 
            (session.getIsCompletedInSession() == null || !session.getIsCompletedInSession().equals(request.getIsCompleted()))) {
            session.setIsCompletedInSession(request.getIsCompleted());
            needsUpdate = true;
        }
        
        if (needsUpdate) {
            log.info("[Session Service] Updating session progress/completion: sessionId={}, progressAtEnd={}, isCompleted={}", 
                sessionId, session.getProgressAtEnd(), session.getIsCompletedInSession());
            session = sessionRepository.save(session);
            // Invalidate course analytics cache when session is updated
            evictCourseAnalyticsCache(session.getCourseId());
        } else {
            log.debug("[Session Service] No updates needed for session: sessionId={}", sessionId);
        }
        
        return toDTO(session);
    }
    
    @Transactional
    public LectureViewSessionDTO endSession(String sessionId, EndSessionRequest request) {
        log.info("[Session Service] Ending session: sessionId={}, progressAtEnd={}, isCompleted={}", 
            sessionId, request.getProgressAtEnd(), request.getIsCompleted());
        
        LectureViewSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> {
                log.error("[Session Service] Session not found: sessionId={}", sessionId);
                return new IllegalArgumentException("Session not found: " + sessionId);
            });
        
        log.debug("[Session Service] Found session: sessionId={}, lectureId={}, studentId={}, startedAt={}, alreadyEnded={}", 
            sessionId, session.getLectureId(), session.getStudentId(), session.getSessionStartedAt(), 
            session.getSessionEndedAt() != null);
        
        // If session is already ended, return existing session (idempotent operation)
        // But update progress/completion if provided and different
        if (session.getSessionEndedAt() != null) {
            log.warn("[Session Service] Session already ended, returning existing session: sessionId={}, endedAt={}", 
                sessionId, session.getSessionEndedAt());
            
            boolean needsUpdate = false;
            if (request.getProgressAtEnd() != null && 
                (session.getProgressAtEnd() == null || !session.getProgressAtEnd().equals(request.getProgressAtEnd()))) {
                session.setProgressAtEnd(request.getProgressAtEnd());
                needsUpdate = true;
            }
            if (request.getIsCompleted() != null && 
                (session.getIsCompletedInSession() == null || !session.getIsCompletedInSession().equals(request.getIsCompleted()))) {
                session.setIsCompletedInSession(request.getIsCompleted());
                needsUpdate = true;
            }
            
            if (needsUpdate) {
                log.info("[Session Service] Updating already-ended session with new progress/completion: sessionId={}", sessionId);
                session = sessionRepository.save(session);
                // Invalidate course analytics cache when session is updated
                evictCourseAnalyticsCache(session.getCourseId());
            }
            
            return toDTO(session);
        }
        
        java.time.OffsetDateTime endTime = java.time.OffsetDateTime.now();
        session.setSessionEndedAt(endTime);
        if (request.getProgressAtEnd() != null) {
            session.setProgressAtEnd(request.getProgressAtEnd());
        }
        if (request.getIsCompleted() != null) {
            session.setIsCompletedInSession(request.getIsCompleted());
        }
        
        log.info("[Session Service] Saving ended session: sessionId={}, endedAt={}, duration will be calculated", 
            sessionId, endTime);
        
        LectureViewSession saved = sessionRepository.save(session);
        log.info("[Session Service] Successfully ended lecture view session: sessionId={}, lectureId={}, studentId={}, duration={}s, isCompleted={}", 
            saved.getId(), saved.getLectureId(), saved.getStudentId(), saved.getDurationSeconds(), saved.getIsCompletedInSession());
        
        // Invalidate course analytics cache when session is updated (ended)
        evictCourseAnalyticsCache(saved.getCourseId());
        
        // Log video watch progress event
        int progressPercent = saved.getProgressAtEnd() != null ? saved.getProgressAtEnd().intValue() : 0;
        int durationSeconds = saved.getDurationSeconds() != null ? saved.getDurationSeconds() : 0;
        Map<String, Object> progressData = Map.of(
            "sessionId", saved.getId(),
            "enrollmentId", saved.getEnrollmentId(),
            "progressAtStart", saved.getProgressAtStart() != null ? saved.getProgressAtStart().intValue() : 0,
            "progressAtEnd", progressPercent,
            "isCompleted", saved.getIsCompletedInSession() != null ? saved.getIsCompletedInSession() : false
        );
        eventService.logVideoWatchProgress(
            saved.getStudentId(),
            saved.getCourseId(),
            saved.getLectureId(),
            progressPercent,
            (long) durationSeconds,
            progressData
        );
        
        // Log lecture completion event if completed
        if (Boolean.TRUE.equals(saved.getIsCompletedInSession())) {
            Map<String, Object> completionData = Map.of(
                "sessionId", saved.getId(),
                "enrollmentId", saved.getEnrollmentId(),
                "totalSessions", 1 // Could be enhanced to count total sessions
            );
            eventService.logLectureCompletion(
                saved.getStudentId(),
                saved.getCourseId(),
                saved.getLectureId(),
                durationSeconds,
                completionData
            );
        }
        
        return toDTO(saved);
    }
    
    public List<LectureViewSessionDTO> getLectureAnalytics(String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<LectureViewSession> sessions = sessionRepository.findByClientIdAndLectureId(clientId, lectureId);
        return sessions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<LectureViewSessionDTO> getCourseAnalytics(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<LectureViewSession> sessions = sessionRepository.findByClientIdAndCourseId(clientId, courseId);
        return sessions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<LectureViewSessionDTO> detectSkippedLectures(String courseId, String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // This will be enhanced in AnalyticsService with lecture duration data
        List<LectureViewSession> sessions = sessionRepository.findByClientIdAndCourseId(clientId, courseId);
        if (studentId != null) {
            sessions = sessions.stream()
                .filter(s -> s.getStudentId().equals(studentId))
                .collect(Collectors.toList());
        }
        
        return sessions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    /**
     * Evict course analytics cache for a specific course.
     * This is called when sessions are created or updated to ensure analytics are fresh.
     */
    @CacheEvict(value = "courseAnalytics", key = "#courseId")
    public void evictCourseAnalyticsCache(String courseId) {
        log.debug("[Session Service] Evicting course analytics cache for courseId={}", courseId);
    }

    /**
     * Evict section analytics cache for a specific section.
     * This is called manually via API endpoint to refresh section analytics.
     */
    @CacheEvict(value = "sectionAnalytics", key = "#sectionId")
    public void evictSectionAnalyticsCache(String sectionId) {
        log.info("[Session Service] Evicting section analytics cache for sectionId={}", sectionId);
    }

    /**
     * Evict class analytics cache for a specific class.
     * This is called manually via API endpoint to refresh class analytics.
     */
    @CacheEvict(value = "classAnalytics", key = "#classId")
    public void evictClassAnalyticsCache(String classId) {
        log.info("[Session Service] Evicting class analytics cache for classId={}", classId);
    }
    
    private LectureViewSessionDTO toDTO(LectureViewSession session) {
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
