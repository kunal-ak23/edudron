package com.datagami.edudron.student.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.AssessmentSubmissionDTO;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.client.ContentExamClient;
import com.datagami.edudron.student.repo.SectionRepository;
import com.datagami.edudron.student.service.ExamSubmissionService;
import com.datagami.edudron.student.util.UserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student/exams")
@Tag(name = "Student Exams", description = "Student exam taking endpoints")
public class StudentExamController {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentExamController.class);
    
    @Autowired
    private ExamSubmissionService examSubmissionService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private AssessmentSubmissionRepository submissionRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private ContentExamClient contentExamClient;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();
    
    private RestTemplate getRestTemplate() {
        // Double-checked locking for thread-safe lazy initialization
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate newTemplate = new RestTemplate();
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
                    newTemplate.setInterceptors(interceptors);
                    restTemplate = newTemplate;
                }
            }
        }
        return restTemplate;
    }
    
    @GetMapping
    @Operation(summary = "Get available exams", description = "Get all available exams (live, scheduled, and completed) for the student based on their enrollments")
    public ResponseEntity<List<?>> getAvailableExams() {
        try {
            String studentId = UserUtil.getCurrentUserId();
            String clientIdStr = TenantContext.getClientId();
            if (clientIdStr == null) {
                return ResponseEntity.ok(new ArrayList<>());
            }
            UUID clientId = UUID.fromString(clientIdStr);
            
            // Get student's enrollments to determine which courses they have access to
            List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId);
            Set<String> enrolledCourseIds = enrollments.stream()
                .map(Enrollment::getCourseId)
                .filter(courseId -> courseId != null && !courseId.isEmpty())
                .collect(Collectors.toSet());
            
            // Get all exam submissions for enrolled courses to find completed exams
            Set<String> submissionCourseIds = new HashSet<>();
            Set<String> completedExamIds = new HashSet<>();
            for (String courseId : enrolledCourseIds) {
                List<AssessmentSubmission> courseSubmissions = submissionRepository
                    .findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
                for (AssessmentSubmission submission : courseSubmissions) {
                    if (submission.getCourseId() != null) {
                        submissionCourseIds.add(submission.getCourseId());
                    }
                    if (submission.getAssessmentId() != null && submission.getCompletedAt() != null) {
                        completedExamIds.add(submission.getAssessmentId());
                    }
                }
            }
            
            // Combine both sets for accessible course IDs
            Set<String> accessibleCourseIds = new HashSet<>(enrolledCourseIds);
            accessibleCourseIds.addAll(submissionCourseIds);
            
            if (accessibleCourseIds.isEmpty()) {
                return ResponseEntity.ok(new ArrayList<>());
            }
            
            // Get live exams
            List<Object> exams = new ArrayList<>();
            try {
                String url = gatewayUrl + "/api/exams/live";
                HttpHeaders liveHeaders = new HttpHeaders();
                liveHeaders.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
                
                ResponseEntity<Object[]> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(liveHeaders),
                    Object[].class
                );
                
                if (response.getBody() != null) {
                    exams.addAll(List.of(response.getBody()));
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch live exams", e);
            }
            
            // Get scheduled exams
            try {
                String scheduledUrl = gatewayUrl + "/api/exams/scheduled";
                HttpHeaders scheduledHeaders = new HttpHeaders();
                scheduledHeaders.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
                
                ResponseEntity<Object[]> scheduledResponse = getRestTemplate().exchange(
                    scheduledUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(scheduledHeaders),
                    Object[].class
                );
                
                if (scheduledResponse.getBody() != null) {
                    exams.addAll(List.of(scheduledResponse.getBody()));
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch scheduled exams", e);
            }
            
            // Fetch completed exam details (only for exams not already in the list)
            Set<String> existingExamIds = new HashSet<>();
            for (Object exam : exams) {
                try {
                    JsonNode examNode = objectMapper.valueToTree(exam);
                    if (examNode.has("id")) {
                        existingExamIds.add(examNode.get("id").asText());
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            for (String examId : completedExamIds) {
                if (!existingExamIds.contains(examId)) {
                    try {
                        String examUrl = gatewayUrl + "/api/exams/" + examId;
                        HttpHeaders examHeaders = new HttpHeaders();
                        examHeaders.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
                        
                        ResponseEntity<Object> examResponse = getRestTemplate().exchange(
                            examUrl,
                            HttpMethod.GET,
                            new HttpEntity<>(examHeaders),
                            Object.class
                        );
                        if (examResponse.getBody() != null) {
                            exams.add(examResponse.getBody());
                        }
                    } catch (Exception e) {
                    }
                }
            }
            
            // Filter exams to only include those for courses the student is enrolled in
            // Also check section-based or class-based access if exam is restricted
            // Also deduplicate by exam ID to prevent same exam appearing multiple times
            Set<String> seenExamIds = new HashSet<>();
            List<Object> filteredExams = exams.stream()
                .filter(exam -> {
                    try {
                        JsonNode examNode = objectMapper.valueToTree(exam);
                        String examId = examNode.has("id") ? examNode.get("id").asText() : "unknown";
                        
                        // Check if exam ID exists and hasn't been seen before
                        if (examNode.has("id")) {
                            if (seenExamIds.contains(examId)) {
                                return false; // Skip duplicate
                            }
                            seenExamIds.add(examId);
                        }
                        
                        // Filter by course enrollment
                        if (examNode.has("courseId")) {
                            String courseId = examNode.get("courseId").asText();
                            if (!accessibleCourseIds.contains(courseId)) {
                                return false; // Student not enrolled in this course
                            }
                            
                            // Find student's enrollment for this course
                            Enrollment studentEnrollment = null;
                            for (Enrollment enrollment : enrollments) {
                                if (courseId.equals(enrollment.getCourseId())) {
                                    studentEnrollment = enrollment;
                                    break;
                                }
                            }
                            
                            if (studentEnrollment == null) {
                                return false; // Should not happen, but safety check
                            }
                            
                            String studentSectionId = studentEnrollment.getBatchId(); // batchId represents sectionId
                            
                            // Priority 1: Check if exam is section-specific
                            String examSectionId = examNode.has("sectionId") ? examNode.get("sectionId").asText() : null;
                            if (examSectionId != null && !examSectionId.isEmpty()) {
                                // Student can access if their section matches
                                if (studentSectionId != null && studentSectionId.equals(examSectionId)) {
                                    return true;
                                }
                                // Student not in the required section
                                return false;
                            }
                            
                            // Priority 2: Check if exam is class-specific
                            String examClassId = examNode.has("classId") ? examNode.get("classId").asText() : null;
                            if (examClassId != null && !examClassId.isEmpty()) {
                                // Need to check if student's section belongs to this class
                                if (studentSectionId == null) {
                                    return false;
                                }
                                
                                // Get student's section to check its classId
                                try {
                                    java.util.Optional<Section> sectionOpt = sectionRepository.findByIdAndClientId(studentSectionId, clientId);
                                    
                                    if (sectionOpt.isPresent()) {
                                        Section studentSection = sectionOpt.get();
                                        String studentClassId = studentSection.getClassId();
                                        
                                        if (studentClassId != null && studentClassId.equals(examClassId)) {
                                            return true;
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.warn("Error checking class membership for student section", e);
                                }
                                
                                // Student's section not in the required class
                                return false;
                            }
                            
                            // Exam has no section or class requirement - accessible to all students in course
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        logger.warn("Error filtering exam", e);
                        return false;
                    }
                })
                .collect(Collectors.toList());
            
            // Add attemptsTaken for each exam so frontend knows if student has completed it
            List<Map<String, Object>> examsWithAttempts = new ArrayList<>();
            for (Object exam : filteredExams) {
                try {
                    Map<String, Object> examMap = objectMapper.convertValue(exam, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    String examId = examMap.containsKey("id") ? String.valueOf(examMap.get("id")) : null;
                    
                    if (examId != null) {
                        // Count submissions for this exam
                        long attemptCount = submissionRepository.countByClientIdAndStudentIdAndAssessmentId(
                            clientId, studentId, examId);
                        examMap.put("attemptsTaken", (int) attemptCount);
                    }
                    examsWithAttempts.add(examMap);
                } catch (Exception ex) {
                    // Still include the exam even if we can't add attemptsTaken
                    Map<String, Object> examMap = objectMapper.convertValue(exam, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    examsWithAttempts.add(examMap);
                }
            }
            
            return ResponseEntity.ok(examsWithAttempts);
        } catch (Exception e) {
            logger.error("Failed to fetch available exams", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get exam details", description = "Get exam details by ID")
    public ResponseEntity<?> getExamDetails(@PathVariable String id) {
        try {
            String studentId = UserUtil.getCurrentUserId();
            String clientIdStr = TenantContext.getClientId();
            if (clientIdStr == null) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }
            UUID clientId = UUID.fromString(clientIdStr);
            
            JsonNode exam = contentExamClient.getExam(id);
            if (exam == null) {
                return ResponseEntity.notFound().build();
            }
            String courseId = exam.has("courseId") ? exam.get("courseId").asText() : null;
            
            if (courseId == null) {
                logger.warn("Exam {} has no courseId, allowing access", id);
                return ResponseEntity.ok(exam);
            }
            
            // Verify enrollment in the exam's course
            List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(
                clientId, studentId, courseId);
            
            if (enrollments.isEmpty()) {
                logger.warn("Student {} attempted to access exam {} but not enrolled in course {}", 
                    studentId, id, courseId);
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            
            // Compute whether exam is available for take (live and in window)
            AvailabilityResult availability = computeAvailabilityForTake(exam);
            com.fasterxml.jackson.databind.node.ObjectNode examNode = (com.fasterxml.jackson.databind.node.ObjectNode) exam.deepCopy();
            examNode.put("availableForTake", availability.available);
            if (availability.message != null) {
                examNode.put("availabilityMessage", availability.message);
            }
            
            if (!availability.available) {
                // Do not send questions to frontend when not allowed to take
                examNode.set("questions", objectMapper.createArrayNode());
                return ResponseEntity.ok(examNode);
            }
            
            // Apply randomization if student has a submission with randomized order
            JsonNode finalExam = applyRandomization(examNode, id, studentId, clientId);
            ((com.fasterxml.jackson.databind.node.ObjectNode) finalExam).put("availableForTake", true);
            
            return ResponseEntity.ok(finalExam);
        } catch (Exception e) {
            logger.error("Failed to fetch exam details", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/start")
    @Operation(summary = "Start exam", description = "Start an exam attempt")
    public ResponseEntity<?> startExam(
            @PathVariable String id,
            @RequestBody(required = false) JsonNode request) {
        
        String studentId = UserUtil.getCurrentUserId();
        
        try {
            JsonNode exam = contentExamClient.getExam(id);
            if (exam == null) {
                return ResponseEntity.notFound().build();
            }
            String courseId = exam.has("courseId") ? exam.get("courseId").asText() : null;
            Integer timeLimitSeconds = exam.has("timeLimitSeconds") ? 
                exam.get("timeLimitSeconds").asInt() : null;
            Integer maxAttempts = exam.has("maxAttempts") && !exam.get("maxAttempts").isNull() ? 
                exam.get("maxAttempts").asInt() : null;
            
            // Get timing mode (defaults to FIXED_WINDOW for backward compatibility)
            String timingModeStr = exam.has("timingMode") && !exam.get("timingMode").isNull() ?
                exam.get("timingMode").asText() : "FIXED_WINDOW";
            ExamSubmissionService.TimingMode timingMode;
            try {
                timingMode = ExamSubmissionService.TimingMode.valueOf(timingModeStr);
            } catch (IllegalArgumentException e) {
                timingMode = ExamSubmissionService.TimingMode.FIXED_WINDOW;
            }
            
            if (courseId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            // Parse start/end times once for availability and submission
            java.time.OffsetDateTime endTime = null;
            java.time.OffsetDateTime startTime = null;
            if (exam.has("startTime") && !exam.get("startTime").isNull()) {
                String startTimeStr = exam.get("startTime").asText();
                if (startTimeStr != null && !startTimeStr.isBlank()) {
                    try {
                        startTime = java.time.OffsetDateTime.parse(startTimeStr);
                    } catch (Exception e) {
                        logger.debug("Failed to parse exam start time: {}", startTimeStr);
                    }
                }
            }
            if (exam.has("endTime") && !exam.get("endTime").isNull()) {
                String endTimeStr = exam.get("endTime").asText();
                if (endTimeStr != null && !endTimeStr.isBlank()) {
                    try {
                        endTime = java.time.OffsetDateTime.parse(endTimeStr);
                    } catch (Exception e) {
                        logger.debug("Failed to parse exam end time: {}", endTimeStr);
                    }
                }
            }
            
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            String status = exam.has("status") && !exam.get("status").isNull() ? exam.get("status").asText() : null;

            // Allow start only when exam is in window (same logic as computeAvailabilityForTake)
            if (timingMode == ExamSubmissionService.TimingMode.FLEXIBLE_START) {
                if (!"LIVE".equals(status)) {
                    logger.warn("Student {} attempted to start exam {} which is not LIVE. Status: {}", studentId, id, status);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "EXAM_NOT_AVAILABLE", "message", "Exam has not begun yet"));
                }
            } else {
                // FIXED_WINDOW: use time window so cached SCHEDULED does not block when now is in window
                if (startTime != null && endTime != null) {
                    if (now.isBefore(startTime)) {
                        logger.warn("Student {} attempted to start exam {} before start time. Start: {}, Now: {}", studentId, id, startTime, now);
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "EXAM_NOT_AVAILABLE", "message", "Exam has not begun yet"));
                    }
                    if (now.isAfter(endTime)) {
                        logger.warn("Student {} attempted to start exam {} after end time. End: {}, Now: {}", studentId, id, endTime, now);
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Exam has ended"));
                    }
                } else {
                    if (!"LIVE".equals(status)) {
                        logger.warn("Student {} attempted to start exam {} which is not LIVE. Status: {}", studentId, id, status);
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "EXAM_NOT_AVAILABLE", "message", "Exam has not begun yet"));
                    }
                }
            }
            
            AssessmentSubmission submission = examSubmissionService.startExam(
                studentId, courseId, id, timeLimitSeconds, maxAttempts, timingMode, endTime);
            
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(toDTO(submission));
        } catch (IllegalStateException e) {
            // Handle max attempts exceeded or other state errors
            logger.warn("State error starting exam: {}", e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            logger.error("Failed to start exam", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/save-progress")
    @Operation(summary = "Save exam progress", description = "Auto-save exam answers and timer")
    public ResponseEntity<AssessmentSubmissionDTO> saveProgress(
            @PathVariable String id,
            @RequestBody JsonNode request) {
        
        String submissionId = request.has("submissionId") ? 
            request.get("submissionId").asText() : null;
        JsonNode answers = request.has("answers") ? request.get("answers") : null;
        Integer timeRemainingSeconds = request.has("timeRemainingSeconds") ? 
            request.get("timeRemainingSeconds").asInt() : null;
        
        if (submissionId == null || answers == null) {
            return ResponseEntity.badRequest().build();
        }
        
        AssessmentSubmission submission = examSubmissionService.saveProgress(
            submissionId, answers, timeRemainingSeconds);
        
        return ResponseEntity.ok(toDTO(submission));
    }
    
    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit exam", description = "Submit final exam answers")
    public ResponseEntity<AssessmentSubmissionDTO> submitExam(
            @PathVariable String id,
            @RequestBody JsonNode request) {
        
        String submissionId = request.has("submissionId") ? 
            request.get("submissionId").asText() : null;
        JsonNode answers = request.has("answers") ? request.get("answers") : null;
        String reviewMethod = request.has("reviewMethod") ? 
            request.get("reviewMethod").asText() : null;
        
        if (submissionId == null || answers == null) {
            return ResponseEntity.badRequest().build();
        }
        
        AssessmentSubmission submission = examSubmissionService.submitExam(submissionId, answers);
        
        // Only trigger AI review if review method is AI (not INSTRUCTOR)
        if (reviewMethod == null || !"INSTRUCTOR".equalsIgnoreCase(reviewMethod)) {
            try {
                String reviewUrl = gatewayUrl + "/api/exams/" + id + "/submissions/" + submissionId + "/review";
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                
                getRestTemplate().exchange(
                    reviewUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Void.class
                );
            } catch (Exception e) {
                logger.warn("Failed to trigger AI review, but submission was successful", e);
            }
        }
        
        return ResponseEntity.ok(toDTO(submission));
    }
    
    @GetMapping("/{id}/submission")
    @Operation(summary = "Get submission", description = "Get exam submission details")
    public ResponseEntity<AssessmentSubmissionDTO> getSubmission(@PathVariable String id) {
        String studentId = UserUtil.getCurrentUserId();
        
        AssessmentSubmission submission = examSubmissionService.getSubmissionByExamId(studentId, id);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(toDTO(submission));
    }
    
    @GetMapping("/submissions/{submissionId}")
    @Operation(summary = "Get submission by ID", description = "Get submission details by submission ID")
    public ResponseEntity<AssessmentSubmissionDTO> getSubmissionById(@PathVariable String submissionId) {
        AssessmentSubmission submission = examSubmissionService.getSubmissionStatus(submissionId);
        return ResponseEntity.ok(toDTO(submission));
    }
    
    @PutMapping(value = "/submissions/{submissionId}/manual-grade", produces = "application/json")
    @Operation(summary = "Manual grade submission", description = "Manually grade an exam submission")
    public ResponseEntity<AssessmentSubmissionDTO> manualGrade(
            @PathVariable String submissionId,
            @RequestBody JsonNode request) {
        
        try {
            Double score = request.has("score") && !request.get("score").isNull() ? 
                request.get("score").asDouble() : null;
            Double maxScore = request.has("maxScore") && !request.get("maxScore").isNull() ? 
                request.get("maxScore").asDouble() : null;
            Boolean isPassed = request.has("isPassed") && !request.get("isPassed").isNull() ? 
                request.get("isPassed").asBoolean() : null;
            String instructorFeedback = request.has("instructorFeedback") && !request.get("instructorFeedback").isNull() ? 
                request.get("instructorFeedback").asText() : null;
            JsonNode aiReviewFeedback = request.has("aiReviewFeedback") && !request.get("aiReviewFeedback").isNull() ? 
                request.get("aiReviewFeedback") : null;
            
            AssessmentSubmission submission = examSubmissionService.manualGrade(
                submissionId, score, maxScore, isPassed, instructorFeedback, aiReviewFeedback);
            
            return ResponseEntity.ok(toDTO(submission));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for manual grading: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to manually grade submission", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Result of availability check for taking an exam.
     */
    private static class AvailabilityResult {
        final boolean available;
        final String message;

        AvailabilityResult(boolean available, String message) {
            this.available = available;
            this.message = message;
        }
    }

    /**
     * Compute whether the exam is currently available for take (live and within window).
     * FIXED_WINDOW: when startTime/endTime are present, available when now is within [startTime, endTime]
     * (do not gate on cached status so scheduled exams go live at the correct time).
     * When start/end are missing, require status == LIVE.
     * FLEXIBLE_START: available when status == LIVE.
     */
    private AvailabilityResult computeAvailabilityForTake(JsonNode exam) {
        String status = exam.has("status") && !exam.get("status").isNull() ? exam.get("status").asText() : null;
        String timingModeStr = exam.has("timingMode") && !exam.get("timingMode").isNull() ?
            exam.get("timingMode").asText() : "FIXED_WINDOW";
        boolean isFlexible = "FLEXIBLE_START".equalsIgnoreCase(timingModeStr);
        OffsetDateTime now = OffsetDateTime.now();

        if (isFlexible) {
            boolean available = "LIVE".equals(status);
            return new AvailabilityResult(available, available ? null : "Exam has not begun yet");
        }

        // FIXED_WINDOW: parse window first; if we have both start and end, use time window only (ignore cached status)
        OffsetDateTime startTime = null;
        OffsetDateTime endTime = null;
        if (exam.has("startTime") && !exam.get("startTime").isNull()) {
            String startStr = exam.get("startTime").asText();
            if (startStr != null && !startStr.isBlank()) {
                try {
                    startTime = OffsetDateTime.parse(startStr);
                } catch (Exception e) {
                    logger.debug("Failed to parse exam startTime: {}", startStr);
                }
            }
        }
        if (exam.has("endTime") && !exam.get("endTime").isNull()) {
            String endStr = exam.get("endTime").asText();
            if (endStr != null && !endStr.isBlank()) {
                try {
                    endTime = OffsetDateTime.parse(endStr);
                } catch (Exception e) {
                    logger.debug("Failed to parse exam endTime: {}", endStr);
                }
            }
        }
        if (startTime != null && endTime != null) {
            // Use time window only so scheduled exams go live at the correct time (cached status may be stale)
            if (now.isBefore(startTime)) {
                return new AvailabilityResult(false, "Exam has not begun yet");
            }
            if (now.isAfter(endTime)) {
                return new AvailabilityResult(false, "Exam has ended");
            }
            return new AvailabilityResult(true, null);
        }
        // No window: fall back to requiring LIVE status
        if (!"LIVE".equals(status)) {
            return new AvailabilityResult(false, "Exam has not begun yet");
        }
        return new AvailabilityResult(true, null);
    }

    /**
     * Apply randomization to exam based on student's submission
     */
    private JsonNode applyRandomization(JsonNode exam, String examId, String studentId, UUID clientId) {
        try {
            // Try to find student's submission
            AssessmentSubmission submission = submissionRepository
                .findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(clientId, studentId, examId)
                .orElse(null);
            
            if (submission == null) {
                // No submission yet, return exam as-is
                return exam;
            }
            
            JsonNode questionOrder = submission.getQuestionOrder();
            JsonNode mcqOptionOrders = submission.getMcqOptionOrders();
            
            if (questionOrder == null && mcqOptionOrders == null) {
                // No randomization in submission
                return exam;
            }
            
            // Clone the exam to avoid modifying the original
            com.fasterxml.jackson.databind.node.ObjectNode examCopy = exam.deepCopy();
            JsonNode questionsNode = examCopy.get("questions");
            
            if (questionsNode == null || !questionsNode.isArray()) {
                return exam;
            }
            
            List<JsonNode> questionsList = new ArrayList<>();
            questionsNode.forEach(questionsList::add);
            
            // Apply question randomization
            if (questionOrder != null && questionOrder.isArray()) {
                Map<String, JsonNode> questionMap = new HashMap<>();
                for (JsonNode question : questionsList) {
                    questionMap.put(question.get("id").asText(), question);
                }
                
                com.fasterxml.jackson.databind.node.ArrayNode reorderedQuestions = objectMapper.createArrayNode();
                for (JsonNode qId : questionOrder) {
                    String questionId = qId.asText();
                    JsonNode question = questionMap.get(questionId);
                    if (question != null) {
                        reorderedQuestions.add(question);
                    }
                }
                
                examCopy.set("questions", reorderedQuestions);
                questionsList.clear();
                reorderedQuestions.forEach(questionsList::add);
            }
            
            // Apply MCQ option randomization
            if (mcqOptionOrders != null && mcqOptionOrders.isObject()) {
                for (JsonNode question : questionsList) {
                    String questionId = question.get("id").asText();
                    JsonNode optionOrder = mcqOptionOrders.get(questionId);
                    
                    if (optionOrder != null && optionOrder.isArray()) {
                        JsonNode optionsNode = question.get("options");
                        if (optionsNode != null && optionsNode.isArray()) {
                            // Build map of option ID to option
                            Map<String, JsonNode> optionMap = new HashMap<>();
                            optionsNode.forEach(option -> {
                                optionMap.put(option.get("id").asText(), option);
                            });
                            
                            // Reorder options
                            com.fasterxml.jackson.databind.node.ArrayNode reorderedOptions = objectMapper.createArrayNode();
                            for (JsonNode optId : optionOrder) {
                                String optionId = optId.asText();
                                JsonNode option = optionMap.get(optionId);
                                if (option != null) {
                                    reorderedOptions.add(option);
                                }
                            }
                            
                            ((com.fasterxml.jackson.databind.node.ObjectNode) question).set("options", reorderedOptions);
                        }
                    }
                }
            }
            
            return examCopy;
            
        } catch (Exception e) {
            logger.error("Failed to apply randomization for exam: {}, student: {}", examId, studentId, e);
            return exam; // Return original exam on error
        }
    }
    
    private AssessmentSubmissionDTO toDTO(AssessmentSubmission submission) {
        AssessmentSubmissionDTO dto = new AssessmentSubmissionDTO();
        dto.setId(submission.getId());
        dto.setClientId(submission.getClientId());
        dto.setStudentId(submission.getStudentId());
        dto.setEnrollmentId(submission.getEnrollmentId());
        dto.setAssessmentId(submission.getAssessmentId());
        dto.setCourseId(submission.getCourseId());
        dto.setScore(submission.getScore());
        dto.setMaxScore(submission.getMaxScore());
        dto.setPercentage(submission.getPercentage());
        dto.setIsPassed(submission.getIsPassed());
        dto.setAnswersJson(submission.getAnswersJson());
        dto.setSubmittedAt(submission.getSubmittedAt());
        dto.setGradedAt(submission.getGradedAt());
        dto.setCreatedAt(submission.getCreatedAt());
        
        // Add exam-specific fields
        dto.setStartedAt(submission.getStartedAt());
        dto.setCompletedAt(submission.getCompletedAt());
        dto.setTimeRemainingSeconds(submission.getTimeRemainingSeconds());
        if (submission.getReviewStatus() != null) {
            dto.setReviewStatus(submission.getReviewStatus().name());
        }
        dto.setAiReviewFeedback(submission.getAiReviewFeedback());
        
        return dto;
    }
}
