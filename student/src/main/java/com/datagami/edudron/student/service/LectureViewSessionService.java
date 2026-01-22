package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.LectureViewSession;
import com.datagami.edudron.student.dto.EndSessionRequest;
import com.datagami.edudron.student.dto.LectureViewSessionDTO;
import com.datagami.edudron.student.dto.StartSessionRequest;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.LectureViewSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
    
    public LectureViewSessionDTO startSession(String studentId, StartSessionRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify enrollment
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, request.getCourseId());
        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("Student is not enrolled in this course");
        }
        Enrollment enrollment = enrollments.get(0);
        
        // Create new session
        LectureViewSession session = new LectureViewSession();
        session.setId(UlidGenerator.nextUlid());
        session.setClientId(clientId);
        session.setEnrollmentId(enrollment.getId());
        session.setStudentId(studentId);
        session.setCourseId(request.getCourseId());
        session.setLectureId(request.getLectureId());
        session.setSessionStartedAt(java.time.OffsetDateTime.now());
        session.setProgressAtStart(request.getProgressAtStart() != null ? request.getProgressAtStart() : BigDecimal.ZERO);
        
        LectureViewSession saved = sessionRepository.save(session);
        log.debug("Started lecture view session {} for student {} and lecture {}", 
            saved.getId(), studentId, request.getLectureId());
        
        return toDTO(saved);
    }
    
    public LectureViewSessionDTO endSession(String sessionId, EndSessionRequest request) {
        LectureViewSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        
        session.setSessionEndedAt(java.time.OffsetDateTime.now());
        if (request.getProgressAtEnd() != null) {
            session.setProgressAtEnd(request.getProgressAtEnd());
        }
        if (request.getIsCompleted() != null) {
            session.setIsCompletedInSession(request.getIsCompleted());
        }
        
        LectureViewSession saved = sessionRepository.save(session);
        log.debug("Ended lecture view session {} with duration {} seconds", 
            saved.getId(), saved.getDurationSeconds());
        
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
