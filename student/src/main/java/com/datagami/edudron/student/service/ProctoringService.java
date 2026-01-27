package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.ProctoringEvent;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.ProctoringEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class ProctoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProctoringService.class);
    
    @Autowired
    private ProctoringEventRepository proctoringEventRepository;
    
    @Autowired
    private AssessmentSubmissionRepository submissionRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Record a proctoring event
     */
    public ProctoringEvent recordProctoringEvent(
            String submissionId,
            ProctoringEvent.EventType eventType,
            ProctoringEvent.Severity severity,
            Map<String, Object> metadata) {
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        ProctoringEvent event = new ProctoringEvent();
        event.setId(UlidGenerator.nextUlid());
        event.setClientId(clientId);
        event.setSubmissionId(submissionId);
        event.setEventType(eventType);
        event.setSeverity(severity);
        
        if (metadata != null && !metadata.isEmpty()) {
            JsonNode metadataNode = objectMapper.valueToTree(metadata);
            event.setMetadata(metadataNode);
        }
        
        event.setCreatedAt(OffsetDateTime.now());
        
        ProctoringEvent saved = proctoringEventRepository.save(event);
        logger.info("Recorded proctoring event: {} for submission: {} with severity: {}", 
                    eventType, submissionId, severity);
        
        // Update submission counters based on event type
        updateSubmissionCounters(submissionId, eventType);
        
        return saved;
    }
    
    /**
     * Update submission counters based on event type
     */
    private void updateSubmissionCounters(String submissionId, ProctoringEvent.EventType eventType) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<AssessmentSubmission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isEmpty()) {
            logger.warn("Submission not found: {}", submissionId);
            return;
        }
        
        AssessmentSubmission submission = submissionOpt.get();
        
        switch (eventType) {
            case TAB_SWITCH:
                submission.setTabSwitchCount(
                    (submission.getTabSwitchCount() != null ? submission.getTabSwitchCount() : 0) + 1
                );
                break;
            case COPY_ATTEMPT:
            case PASTE_ATTEMPT:
                submission.setCopyAttemptCount(
                    (submission.getCopyAttemptCount() != null ? submission.getCopyAttemptCount() : 0) + 1
                );
                break;
            case IDENTITY_VERIFIED:
                submission.setIdentityVerified(true);
                break;
            default:
                // No counter update needed for other event types
                break;
        }
        
        submissionRepository.save(submission);
    }
    
    /**
     * Store identity verification photo URL
     */
    public void storeIdentityVerificationPhoto(String submissionId, String photoUrl) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<AssessmentSubmission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        AssessmentSubmission submission = submissionOpt.get();
        submission.setIdentityVerificationPhotoUrl(photoUrl);
        submission.setIdentityVerified(true);
        submissionRepository.save(submission);
        
        logger.info("Stored identity verification photo for submission: {}", submissionId);
    }
    
    /**
     * Add photo URL to proctoring data
     */
    public void addPhotoToProctoringData(String submissionId, String photoUrl, OffsetDateTime capturedAt) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<AssessmentSubmission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        AssessmentSubmission submission = submissionOpt.get();
        
        ObjectNode proctoringData;
        if (submission.getProctoringData() == null) {
            proctoringData = objectMapper.createObjectNode();
            proctoringData.set("photos", objectMapper.createArrayNode());
        } else {
            proctoringData = (ObjectNode) submission.getProctoringData();
            if (!proctoringData.has("photos")) {
                proctoringData.set("photos", objectMapper.createArrayNode());
            }
        }
        
        ArrayNode photos = (ArrayNode) proctoringData.get("photos");
        ObjectNode photo = objectMapper.createObjectNode();
        photo.put("url", photoUrl);
        photo.put("capturedAt", capturedAt.toString());
        photos.add(photo);
        
        submission.setProctoringData(proctoringData);
        submissionRepository.save(submission);
        
        logger.info("Added photo to proctoring data for submission: {}", submissionId);
    }
    
    /**
     * Analyze proctoring data and update status
     */
    public void analyzeProctoringData(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<AssessmentSubmission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        AssessmentSubmission submission = submissionOpt.get();
        
        // Count violations
        long violationCount = proctoringEventRepository.countByClientIdAndSubmissionIdAndSeverity(
            clientId, submissionId, ProctoringEvent.Severity.VIOLATION
        );
        
        // Count warnings
        long warningCount = proctoringEventRepository.countByClientIdAndSubmissionIdAndSeverity(
            clientId, submissionId, ProctoringEvent.Severity.WARNING
        );
        
        // Determine proctoring status based on event counts
        AssessmentSubmission.ProctoringStatus status;
        if (violationCount > 0) {
            status = AssessmentSubmission.ProctoringStatus.VIOLATION;
        } else if (warningCount >= 3) {
            status = AssessmentSubmission.ProctoringStatus.SUSPICIOUS;
        } else if (warningCount > 0) {
            status = AssessmentSubmission.ProctoringStatus.FLAGGED;
        } else {
            status = AssessmentSubmission.ProctoringStatus.CLEAR;
        }
        
        submission.setProctoringStatus(status);
        submissionRepository.save(submission);
        
        logger.info("Analyzed proctoring data for submission: {}. Status: {}, Violations: {}, Warnings: {}", 
                    submissionId, status, violationCount, warningCount);
    }
    
    /**
     * Get proctoring report for a submission
     */
    public Map<String, Object> getProctoringReport(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<AssessmentSubmission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        AssessmentSubmission submission = submissionOpt.get();
        
        // Get all proctoring events
        List<ProctoringEvent> events = proctoringEventRepository
            .findByClientIdAndSubmissionIdOrderByCreatedAtAsc(clientId, submissionId);
        
        // Count by severity
        long infoCount = proctoringEventRepository.countByClientIdAndSubmissionIdAndSeverity(
            clientId, submissionId, ProctoringEvent.Severity.INFO
        );
        long warningCount = proctoringEventRepository.countByClientIdAndSubmissionIdAndSeverity(
            clientId, submissionId, ProctoringEvent.Severity.WARNING
        );
        long violationCount = proctoringEventRepository.countByClientIdAndSubmissionIdAndSeverity(
            clientId, submissionId, ProctoringEvent.Severity.VIOLATION
        );
        
        // Build report
        Map<String, Object> report = new HashMap<>();
        report.put("submissionId", submissionId);
        report.put("proctoringStatus", submission.getProctoringStatus());
        report.put("tabSwitchCount", submission.getTabSwitchCount());
        report.put("copyAttemptCount", submission.getCopyAttemptCount());
        report.put("identityVerified", submission.getIdentityVerified());
        report.put("identityVerificationPhotoUrl", submission.getIdentityVerificationPhotoUrl());
        report.put("proctoringData", submission.getProctoringData());
        
        Map<String, Long> eventCounts = new HashMap<>();
        eventCounts.put("info", infoCount);
        eventCounts.put("warning", warningCount);
        eventCounts.put("violation", violationCount);
        report.put("eventCounts", eventCounts);
        
        report.put("events", events);
        
        return report;
    }
    
    /**
     * Get events by severity
     */
    public List<ProctoringEvent> getEventsBySeverity(String submissionId, ProctoringEvent.Severity severity) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        return proctoringEventRepository.findByClientIdAndSubmissionIdAndSeverityOrderByCreatedAtAsc(
            clientId, submissionId, severity
        );
    }
    
    /**
     * Check if submission has violations
     */
    public boolean hasViolations(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = UUID.fromString(clientIdStr);
        
        long violationCount = proctoringEventRepository.countByClientIdAndSubmissionIdAndSeverity(
            clientId, submissionId, ProctoringEvent.Severity.VIOLATION
        );
        
        return violationCount > 0;
    }
}
