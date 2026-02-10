package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.AssessmentJourneyEvent;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.repo.AssessmentJourneyEventRepository;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AssessmentJourneyService {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentJourneyService.class);

    @Autowired
    private AssessmentJourneyEventRepository journeyEventRepository;

    @Autowired
    private AssessmentSubmissionRepository submissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Record a journey event with an existing submission (validates submission belongs to current user).
     */
    public AssessmentJourneyEvent recordEvent(
            String submissionId,
            String eventType,
            String severity,
            Map<String, Object> metadata) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        String studentId = com.datagami.edudron.student.util.UserUtil.getCurrentUserId();
        if (studentId == null) {
            throw new IllegalStateException("User context is not set");
        }

        AssessmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        if (!submission.getClientId().equals(clientId) || !submission.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }

        return doRecord(clientId, submissionId, submission.getAssessmentId(), studentId, eventType, severity, metadata);
    }

    /**
     * Record a journey event without submission (e.g. EXAM_TAKE_CLICKED before submission exists).
     * Requires assessmentId in request or path.
     */
    public AssessmentJourneyEvent recordEventWithoutSubmission(
            String assessmentId,
            String eventType,
            String severity,
            Map<String, Object> metadata) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        String studentId = com.datagami.edudron.student.util.UserUtil.getCurrentUserId();
        if (studentId == null) {
            throw new IllegalStateException("User context is not set");
        }
        if (assessmentId == null || assessmentId.isBlank()) {
            throw new IllegalArgumentException("Assessment ID is required for events without submission");
        }
        return doRecord(clientId, null, assessmentId, studentId, eventType, severity, metadata);
    }

    private AssessmentJourneyEvent doRecord(
            UUID clientId,
            String submissionId,
            String assessmentId,
            String studentId,
            String eventType,
            String severity,
            Map<String, Object> metadata) {
        AssessmentJourneyEvent event = new AssessmentJourneyEvent();
        event.setId(UlidGenerator.nextUlid());
        event.setClientId(clientId);
        event.setSubmissionId(submissionId);
        event.setAssessmentId(assessmentId);
        event.setStudentId(studentId);
        event.setEventType(eventType);
        event.setSeverity(severity != null && !severity.isBlank() ? severity : "INFO");
        if (metadata != null && !metadata.isEmpty()) {
            JsonNode metadataNode = objectMapper.valueToTree(metadata);
            event.setMetadata(metadataNode);
        }
        event.setCreatedAt(OffsetDateTime.now());
        AssessmentJourneyEvent saved = journeyEventRepository.save(event);
        logger.debug("Recorded journey event: {} for submission: {}", eventType, submissionId);
        return saved;
    }

    /**
     * Get all journey events for a submission (for teachers/admins).
     * Resolves client_id from the submission so events are returned even when the request
     * tenant context (X-Client-Id) is missing or differs. Caller must have permission to view the submission.
     */
    public List<AssessmentJourneyEvent> getJourneyEvents(String submissionId) {
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        UUID clientId = submission.getClientId();
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr != null) {
            UUID requestClientId = UUID.fromString(clientIdStr);
            if (!requestClientId.equals(clientId)) {
                throw new IllegalArgumentException("Submission not found: " + submissionId);
            }
        }
        return journeyEventRepository.findByClientIdAndSubmissionIdOrderByCreatedAtAsc(clientId, submissionId);
    }
}
