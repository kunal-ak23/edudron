package com.datagami.edudron.student.web;

import com.datagami.edudron.student.domain.AssessmentJourneyEvent;
import com.datagami.edudron.student.domain.ProctoringEvent;
import com.datagami.edudron.student.service.AssessmentJourneyService;
import com.datagami.edudron.student.service.ProctoringPhotoService;
import com.datagami.edudron.student.service.ProctoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/exams")
@Tag(name = "Proctoring", description = "Proctoring endpoints for exam monitoring")
public class ProctoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProctoringController.class);
    
    @Autowired
    private ProctoringService proctoringService;
    
    @Autowired
    private ProctoringPhotoService proctoringPhotoService;

    @Autowired
    private AssessmentJourneyService assessmentJourneyService;
    
    @PostMapping("/{examId}/submissions/{submissionId}/proctoring/log-event")
    @Operation(summary = "Log a proctoring event", description = "Records a proctoring event during exam taking")
    public ResponseEntity<ProctoringEvent> logProctoringEvent(
            @PathVariable String examId,
            @PathVariable String submissionId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String eventTypeStr = (String) request.get("eventType");
            String severityStr = (String) request.get("severity");
            
            if (eventTypeStr == null || severityStr == null) {
                return ResponseEntity.badRequest().build();
            }
            
            ProctoringEvent.EventType eventType = ProctoringEvent.EventType.valueOf(eventTypeStr);
            ProctoringEvent.Severity severity = ProctoringEvent.Severity.valueOf(severityStr);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = request.get("metadata") != null ? 
                (Map<String, Object>) request.get("metadata") : new HashMap<>();
            
            ProctoringEvent event = proctoringService.recordProctoringEvent(
                submissionId, eventType, severity, metadata
            );
            
            return ResponseEntity.ok(event);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid event type or severity", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error logging proctoring event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{examId}/submissions/{submissionId}/proctoring/verify-identity")
    @Operation(summary = "Verify student identity", description = "Uploads and stores identity verification photo")
    public ResponseEntity<Map<String, String>> verifyIdentity(
            @PathVariable String examId,
            @PathVariable String submissionId,
            @RequestBody Map<String, String> request) {
        
        try {
            String base64Photo = request.get("photo");
            if (base64Photo == null || base64Photo.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Upload photo to Azure Blob Storage
            String photoUrl = proctoringPhotoService.uploadPhoto(base64Photo, submissionId, "identity_verification");
            
            proctoringService.storeIdentityVerificationPhoto(submissionId, photoUrl);
            
            // Record event
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("photoUrl", photoUrl);
            proctoringService.recordProctoringEvent(
                submissionId,
                ProctoringEvent.EventType.IDENTITY_VERIFIED,
                ProctoringEvent.Severity.INFO,
                metadata
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Identity verified successfully");
            response.put("submissionId", submissionId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error verifying identity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{examId}/submissions/{submissionId}/proctoring/capture-photo")
    @Operation(summary = "Capture proctoring photo", description = "Uploads and stores a webcam photo captured during the exam")
    public ResponseEntity<Map<String, String>> capturePhoto(
            @PathVariable String examId,
            @PathVariable String submissionId,
            @RequestBody Map<String, String> request) {
        
        try {
            String base64Photo = request.get("photo");
            if (base64Photo == null || base64Photo.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Upload photo to Azure Blob Storage
            String photoUrl = proctoringPhotoService.uploadPhoto(base64Photo, submissionId, "exam_capture");
            
            OffsetDateTime capturedAt = OffsetDateTime.now();
            proctoringService.addPhotoToProctoringData(submissionId, photoUrl, capturedAt);
            
            // Record event
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("photoUrl", photoUrl);
            metadata.put("capturedAt", capturedAt.toString());
            proctoringService.recordProctoringEvent(
                submissionId,
                ProctoringEvent.EventType.PHOTO_CAPTURED,
                ProctoringEvent.Severity.INFO,
                metadata
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Photo captured successfully");
            response.put("submissionId", submissionId);
            response.put("capturedAt", capturedAt.toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error capturing photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{examId}/submissions/{submissionId}/proctoring/report")
    @Operation(summary = "Get proctoring report", description = "Retrieves the proctoring report for a submission")
    public ResponseEntity<Map<String, Object>> getProctoringReport(
            @PathVariable String examId,
            @PathVariable String submissionId) {
        
        try {
            Map<String, Object> report = proctoringService.getProctoringReport(submissionId);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            logger.error("Submission not found: {}", submissionId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting proctoring report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{examId}/submissions/{submissionId}/proctoring/events")
    @Operation(summary = "Get proctoring events by severity", description = "Retrieves proctoring events filtered by severity")
    public ResponseEntity<List<ProctoringEvent>> getProctoringEvents(
            @PathVariable String examId,
            @PathVariable String submissionId,
            @RequestParam(required = false) String severity) {
        
        try {
            List<ProctoringEvent> events;
            if (severity != null) {
                ProctoringEvent.Severity severityEnum = ProctoringEvent.Severity.valueOf(severity);
                events = proctoringService.getEventsBySeverity(submissionId, severityEnum);
            } else {
                Map<String, Object> report = proctoringService.getProctoringReport(submissionId);
                @SuppressWarnings("unchecked")
                List<ProctoringEvent> allEvents = (List<ProctoringEvent>) report.get("events");
                events = allEvents;
            }
            return ResponseEntity.ok(events);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid severity or submission not found", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error getting proctoring events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----- Assessment journey events (full timeline from Take test to Submit) -----

    @PostMapping("/{examId}/submissions/{submissionId}/journey/events")
    @Operation(summary = "Log assessment journey event", description = "Records a journey event for the submission (student)")
    public ResponseEntity<AssessmentJourneyEvent> logJourneyEvent(
            @PathVariable String examId,
            @PathVariable String submissionId,
            @RequestBody Map<String, Object> request) {
        try {
            String eventType = (String) request.get("eventType");
            if (eventType == null || eventType.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            String severity = request.get("severity") != null ? request.get("severity").toString() : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = request.get("metadata") != null ?
                    (Map<String, Object>) request.get("metadata") : new HashMap<>();
            AssessmentJourneyEvent event = assessmentJourneyService.recordEvent(
                    submissionId, eventType, severity, metadata);
            return ResponseEntity.ok(event);
        } catch (IllegalArgumentException e) {
            logger.warn("Journey event validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error logging journey event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{examId}/journey/events")
    @Operation(summary = "Log early journey event", description = "Records a journey event before submission exists (e.g. EXAM_TAKE_CLICKED)")
    public ResponseEntity<AssessmentJourneyEvent> logJourneyEventWithoutSubmission(
            @PathVariable String examId,
            @RequestBody Map<String, Object> request) {
        try {
            String eventType = (String) request.get("eventType");
            if (eventType == null || eventType.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            String severity = request.get("severity") != null ? request.get("severity").toString() : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = request.get("metadata") != null ?
                    (Map<String, Object>) request.get("metadata") : new HashMap<>();
            AssessmentJourneyEvent event = assessmentJourneyService.recordEventWithoutSubmission(
                    examId, eventType, severity, metadata);
            return ResponseEntity.ok(event);
        } catch (IllegalArgumentException e) {
            logger.warn("Journey event validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error logging journey event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{examId}/submissions/{submissionId}/journey/events")
    @Operation(summary = "Get assessment journey events", description = "Returns timeline of journey events for a submission (teachers/admins)")
    public ResponseEntity<List<AssessmentJourneyEvent>> getJourneyEvents(
            @PathVariable String examId,
            @PathVariable String submissionId) {
        try {
            List<AssessmentJourneyEvent> events = assessmentJourneyService.getJourneyEvents(submissionId);
            return ResponseEntity.ok(events);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting journey events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
