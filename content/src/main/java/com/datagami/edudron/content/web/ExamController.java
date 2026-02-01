package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.dto.BatchExamGenerationRequest;
import com.datagami.edudron.content.dto.BatchExamGenerationResponse;
import com.datagami.edudron.content.dto.ExamDetailDTO;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.datagami.edudron.content.service.ExamService;
import com.datagami.edudron.content.service.ExamReviewService;
import com.datagami.edudron.content.service.QuestionService;
import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exams")
@Tag(name = "Exams", description = "Exam management endpoints for administrators")
public class ExamController {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamController.class);
    
    @Autowired
    private ExamService examService;
    
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    
    @Autowired
    private ExamReviewService examReviewService;
    
    @Autowired
    private QuestionService questionService;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
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
            }
        }
        return restTemplate;
    }
    
    @PostMapping
    @Operation(summary = "Create exam", description = "Create a new exam")
    public ResponseEntity<Assessment> createExam(@RequestBody Map<String, Object> request) {
        String courseId = (String) request.get("courseId");
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String instructions = (String) request.get("instructions");
        String classId = (String) request.get("classId"); // Optional - for class-level assignment
        String sectionId = (String) request.get("sectionId"); // Optional - for section-level assignment
        
        @SuppressWarnings("unchecked")
        List<String> moduleIds = (List<String>) request.get("moduleIds");
        
        Assessment.ReviewMethod reviewMethod = null;
        if (request.get("reviewMethod") != null) {
            try {
                reviewMethod = Assessment.ReviewMethod.valueOf((String) request.get("reviewMethod"));
            } catch (IllegalArgumentException e) {
                reviewMethod = Assessment.ReviewMethod.INSTRUCTOR;
            }
        }
        
        Boolean randomizeQuestions = request.get("randomizeQuestions") != null ? 
            (Boolean) request.get("randomizeQuestions") : false;
        Boolean randomizeMcqOptions = request.get("randomizeMcqOptions") != null ? 
            (Boolean) request.get("randomizeMcqOptions") : false;
        
        // Proctoring parameters
        Boolean enableProctoring = request.get("enableProctoring") != null ? 
            (Boolean) request.get("enableProctoring") : false;
        Assessment.ProctoringMode proctoringMode = null;
        if (request.get("proctoringMode") != null) {
            try {
                proctoringMode = Assessment.ProctoringMode.valueOf((String) request.get("proctoringMode"));
            } catch (IllegalArgumentException e) {
                proctoringMode = null;
            }
        }
        Integer photoIntervalSeconds = request.get("photoIntervalSeconds") != null ? 
            (Integer) request.get("photoIntervalSeconds") : 120;
        Boolean requireIdentityVerification = request.get("requireIdentityVerification") != null ? 
            (Boolean) request.get("requireIdentityVerification") : false;
        Boolean blockCopyPaste = request.get("blockCopyPaste") != null ? 
            (Boolean) request.get("blockCopyPaste") : false;
        Boolean blockTabSwitch = request.get("blockTabSwitch") != null ? 
            (Boolean) request.get("blockTabSwitch") : false;
        Integer maxTabSwitchesAllowed = request.get("maxTabSwitchesAllowed") != null ? 
            (Integer) request.get("maxTabSwitchesAllowed") : 3;
        
        // Timing mode parameter
        String timingMode = (String) request.get("timingMode");
        
        Assessment exam = examService.createExam(courseId, title, description, instructions, moduleIds, reviewMethod, classId, sectionId, 
            randomizeQuestions, randomizeMcqOptions, enableProctoring, proctoringMode, photoIntervalSeconds, 
            requireIdentityVerification, blockCopyPaste, blockTabSwitch, maxTabSwitchesAllowed, timingMode);
        return ResponseEntity.status(HttpStatus.CREATED).body(exam);
    }
    
    @PostMapping("/batch-generate")
    @Operation(summary = "Batch generate exams", description = "Create separate exams for each selected section with randomized question selection from the question bank")
    public ResponseEntity<BatchExamGenerationResponse> batchGenerateExams(
            @RequestBody @jakarta.validation.Valid BatchExamGenerationRequest request) {
        try {
            logger.info("Batch generating exams for course {} with {} sections", 
                request.getCourseId(), request.getSectionIds().size());
            
            BatchExamGenerationResponse response = examService.batchGenerateExamsForSections(request);
            
            if (response.getTotalCreated() == 0 && !response.getErrors().isEmpty()) {
                return ResponseEntity.badRequest().body(response);
            }
            
            logger.info("Successfully created {} exams out of {} requested", 
                response.getTotalCreated(), response.getTotalRequested());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for batch generate: {}", e.getMessage());
            BatchExamGenerationResponse errorResponse = new BatchExamGenerationResponse(request.getSectionIds().size());
            errorResponse.addError(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Failed to batch generate exams", e);
            BatchExamGenerationResponse errorResponse = new BatchExamGenerationResponse(request.getSectionIds().size());
            errorResponse.addError("Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @GetMapping
    @Operation(summary = "List exams", description = "Get all exams, optionally including archived ones")
    public ResponseEntity<List<Assessment>> getAllExams(
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        List<Assessment> exams = examService.getAllExams(includeArchived);
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get exam", description = "Get exam details by ID with unified questions")
    public ResponseEntity<ExamDetailDTO> getExam(@PathVariable String id) {
        Assessment exam = examService.getExamById(id);
        ExamDetailDTO dto = ExamDetailDTO.fromAssessment(exam);
        return ResponseEntity.ok(dto);
    }
    
    @GetMapping("/courses/{courseId}/sections")
    @Operation(summary = "Get course sections", description = "Get all sections for a course to assign exams")
    public ResponseEntity<List<Map<String, Object>>> getCourseSections(@PathVariable String courseId) {
        try {
            // Call student service to get sections
            String url = gatewayUrl + "/api/sections/course/" + courseId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
            
            ResponseEntity<Object[]> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Object[].class
            );
            
            if (response.getBody() != null) {
                List<Map<String, Object>> sections = new ArrayList<>();
                for (Object section : response.getBody()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sectionMap = (Map<String, Object>) section;
                    sections.add(sectionMap);
                }
                return ResponseEntity.ok(sections);
            }
            return ResponseEntity.ok(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Failed to fetch sections for course: {}", courseId, e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    @GetMapping("/courses/{courseId}/classes")
    @Operation(summary = "Get course classes", description = "Get all classes for a course to assign exams")
    public ResponseEntity<List<Map<String, Object>>> getCourseClasses(@PathVariable String courseId) {
        try {
            // Call student service to get classes
            String url = gatewayUrl + "/api/classes/course/" + courseId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
            
            ResponseEntity<Object[]> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Object[].class
            );
            
            if (response.getBody() != null) {
                List<Map<String, Object>> classes = new ArrayList<>();
                for (Object cls : response.getBody()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> classMap = (Map<String, Object>) cls;
                    classes.add(classMap);
                }
                return ResponseEntity.ok(classes);
            }
            return ResponseEntity.ok(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Failed to fetch classes for course: {}", courseId, e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update exam", description = "Update an existing exam")
    public ResponseEntity<ExamDetailDTO> updateExam(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String instructions = (String) request.get("instructions");
        String classId = request.containsKey("classId") ? (String) request.get("classId") : null;
        String sectionId = request.containsKey("sectionId") ? (String) request.get("sectionId") : null;
        
        @SuppressWarnings("unchecked")
        List<String> moduleIds = (List<String>) request.get("moduleIds");
        
        Assessment.ReviewMethod reviewMethod = null;
        if (request.get("reviewMethod") != null) {
            try {
                reviewMethod = Assessment.ReviewMethod.valueOf((String) request.get("reviewMethod"));
            } catch (IllegalArgumentException e) {
                // Ignore invalid review method
            }
        }
        
        Boolean randomizeQuestions = request.get("randomizeQuestions") != null ? 
            (Boolean) request.get("randomizeQuestions") : null;
        Boolean randomizeMcqOptions = request.get("randomizeMcqOptions") != null ? 
            (Boolean) request.get("randomizeMcqOptions") : null;
        
        // Proctoring parameters
        Boolean enableProctoring = request.get("enableProctoring") != null ? 
            (Boolean) request.get("enableProctoring") : null;
        Assessment.ProctoringMode proctoringMode = null;
        if (request.get("proctoringMode") != null) {
            try {
                proctoringMode = Assessment.ProctoringMode.valueOf((String) request.get("proctoringMode"));
            } catch (IllegalArgumentException e) {
                // Ignore invalid proctoring mode
            }
        }
        Integer photoIntervalSeconds = request.get("photoIntervalSeconds") != null ? 
            (Integer) request.get("photoIntervalSeconds") : null;
        Boolean requireIdentityVerification = request.get("requireIdentityVerification") != null ? 
            (Boolean) request.get("requireIdentityVerification") : null;
        Boolean blockCopyPaste = request.get("blockCopyPaste") != null ? 
            (Boolean) request.get("blockCopyPaste") : null;
        Boolean blockTabSwitch = request.get("blockTabSwitch") != null ? 
            (Boolean) request.get("blockTabSwitch") : null;
        Integer maxTabSwitchesAllowed = request.get("maxTabSwitchesAllowed") != null ? 
            (Integer) request.get("maxTabSwitchesAllowed") : null;
        
        // Timing mode
        Assessment.TimingMode timingMode = null;
        if (request.get("timingMode") != null) {
            try {
                timingMode = Assessment.TimingMode.valueOf((String) request.get("timingMode"));
            } catch (IllegalArgumentException e) {
                // Ignore invalid timing mode
            }
        }
        Integer timeLimitSeconds = request.get("timeLimitSeconds") != null ? 
            ((Number) request.get("timeLimitSeconds")).intValue() : null;
        
        // Update the exam
        examService.updateExam(id, title, description, instructions, moduleIds, reviewMethod, classId, sectionId, 
            randomizeQuestions, randomizeMcqOptions, enableProctoring, proctoringMode, photoIntervalSeconds, 
            requireIdentityVerification, blockCopyPaste, blockTabSwitch, maxTabSwitchesAllowed, timingMode, timeLimitSeconds);
        
        // Reload the exam with questions to return complete data
        Assessment exam = examService.getExamById(id);
        ExamDetailDTO dto = ExamDetailDTO.fromAssessment(exam);
        return ResponseEntity.ok(dto);
    }
    
    @PostMapping("/{id}/generate")
    @Operation(summary = "Generate exam with AI", description = "Generate exam questions using AI based on selected modules. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features.")
    public ResponseEntity<ExamDetailDTO> generateExamWithAI(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        // This check is also done in ExamService.generateExamWithAI, but we check here for better error messages
        String userRole = examService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        Integer numberOfQuestions = request.get("numberOfQuestions") != null ? 
            ((Number) request.get("numberOfQuestions")).intValue() : 10;
        String difficulty = (String) request.get("difficulty");
        
        examService.generateExamWithAI(id, numberOfQuestions, difficulty);
        // Reload with questions
        Assessment exam = examService.getExamById(id);
        ExamDetailDTO dto = ExamDetailDTO.fromAssessment(exam);
        return ResponseEntity.ok(dto);
    }
    
    @PutMapping("/{id}/schedule")
    @Operation(summary = "Schedule exam", description = "Schedule an exam with start and end times")
    public ResponseEntity<ExamDetailDTO> scheduleExam(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        
        String startTimeStr = request.get("startTime");
        String endTimeStr = request.get("endTime");
        
        if (startTimeStr == null || endTimeStr == null) {
            return ResponseEntity.badRequest().build();
        }
        
        // Parse date-time strings with timezone (ISO 8601 format: "2026-01-16T19:00:00+05:30")
        // Frontend now sends timezone-aware datetimes to support students in different timezones
        OffsetDateTime startTime;
        OffsetDateTime endTime;
        
        try {
            // Try parsing as OffsetDateTime (with timezone) - this is the expected format now
            startTime = OffsetDateTime.parse(startTimeStr);
            endTime = OffsetDateTime.parse(endTimeStr);
        } catch (Exception e) {
            // Fallback: if timezone is missing, parse as LocalDateTime and assume UTC
            // This maintains backward compatibility but frontend should always send timezone
            try {
                LocalDateTime startLocal = LocalDateTime.parse(startTimeStr);
                LocalDateTime endLocal = LocalDateTime.parse(endTimeStr);
                startTime = startLocal.atOffset(ZoneOffset.UTC);
                endTime = endLocal.atOffset(ZoneOffset.UTC);
                logger.warn("Received datetime without timezone, assuming UTC. startTime={}, endTime={}", startTimeStr, endTimeStr);
            } catch (Exception e2) {
                logger.error("Failed to parse date-time strings: startTime={}, endTime={}", startTimeStr, endTimeStr, e2);
                return ResponseEntity.badRequest().build();
            }
        }
        
        examService.scheduleExam(id, startTime, endTime);
        // Reload with questions
        Assessment exam = examService.getExamById(id);
        ExamDetailDTO dto = ExamDetailDTO.fromAssessment(exam);
        return ResponseEntity.ok(dto);
    }
    
    @PutMapping("/{id}/inline-questions/{questionId}/tentative-answer")
    @Operation(summary = "Update tentative answer", description = "Update the tentative answer for an inline subjective question")
    public ResponseEntity<QuizQuestion> updateTentativeAnswer(
            @PathVariable String id,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> request) {
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        QuizQuestion question = quizQuestionRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        if (!question.getAssessmentId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        
        String editedTentativeAnswer = (String) request.get("editedTentativeAnswer");
        if (editedTentativeAnswer != null) {
            question.setEditedTentativeAnswer(editedTentativeAnswer);
        }
        
        if (request.get("useTentativeAnswerForGrading") != null) {
            question.setUseTentativeAnswerForGrading((Boolean) request.get("useTentativeAnswerForGrading"));
        }
        
        QuizQuestion updated = quizQuestionRepository.save(question);
        return ResponseEntity.ok(updated);
    }
    
    @GetMapping("/live")
    @Operation(summary = "Get live exams", description = "Get all currently live exams")
    public ResponseEntity<List<Assessment>> getLiveExams() {
        List<Assessment> exams = examService.getLiveExams();
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/scheduled")
    @Operation(summary = "Get scheduled exams", description = "Get all scheduled (upcoming) exams")
    public ResponseEntity<List<Assessment>> getScheduledExams() {
        List<Assessment> exams = examService.getScheduledExams();
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/{id}/submissions")
    @Operation(summary = "Get all submissions", description = "Get all submissions for an exam")
    public ResponseEntity<List<Map<String, Object>>> getSubmissions(@PathVariable String id) {
        try {
            String url = gatewayUrl + "/api/assessments/" + id + "/submissions";
            
            // Use ParameterizedTypeReference to properly deserialize List
            org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>> responseType = 
                new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {};
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
            );
            
            List<Map<String, Object>> submissions = response.getBody();
            if (submissions == null) {
                submissions = new ArrayList<>();
            }
            
            // Filter out deeply nested JSON fields to prevent nesting depth errors
            List<Map<String, Object>> simplifiedSubmissions = new ArrayList<>();
            for (Map<String, Object> submission : submissions) {
                Map<String, Object> simplified = new java.util.HashMap<>(submission);
                // Remove deeply nested JSON fields that cause nesting depth issues
                simplified.remove("answersJson");
                simplified.remove("aiReviewFeedback");
                simplifiedSubmissions.add(simplified);
            }
            
            return ResponseEntity.ok(simplifiedSubmissions);
        } catch (Exception e) {
            logger.error("Failed to fetch submissions", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    @GetMapping("/{id}/submissions/{submissionId}")
    @Operation(summary = "Get submission details", description = "Get full submission details including answers")
    public ResponseEntity<Map<String, Object>> getSubmissionDetails(
            @PathVariable String id,
            @PathVariable String submissionId) {
        try {
            logger.info("Fetching submission details for examId: {}, submissionId: {}", id, submissionId);
            String url = gatewayUrl + "/api/student/exams/submissions/" + submissionId;
            logger.info("Calling student service at: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> submission = response.getBody();
            if (submission == null) {
                logger.warn("Submission not found: {}", submissionId);
                return ResponseEntity.notFound().build();
            }
            
            logger.info("Successfully fetched submission details. Has answersJson: {}", submission.containsKey("answersJson"));
            // Return full submission with answersJson included
            return ResponseEntity.ok(submission);
        } catch (Exception e) {
            logger.error("Failed to fetch submission details for examId: {}, submissionId: {}", id, submissionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/submissions/{submissionId}/review")
    @Operation(summary = "Review submission with AI", description = "Trigger AI review for a submission")
    public ResponseEntity<?> reviewSubmission(
            @PathVariable String id,
            @PathVariable String submissionId) {
        try {
            examReviewService.reviewSubmissionWithAI(submissionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to review submission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/submissions/{submissionId}/regrade")
    @Operation(summary = "Re-grade submission", description = "Re-trigger AI grading for a specific submission (admin only)")
    public ResponseEntity<?> regradeSubmission(
            @PathVariable String id,
            @PathVariable String submissionId) {
        try {
            logger.info("Re-grading submission {} for exam {}", submissionId, id);
            JsonNode result = examReviewService.reviewSubmissionWithAI(submissionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to re-grade submission {} for exam {}", submissionId, id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to re-grade submission: " + e.getMessage()));
        }
    }
    
    @PostMapping("/{id}/submissions/regrade-bulk")
    @Operation(summary = "Re-grade multiple submissions", description = "Re-trigger AI grading for multiple submissions (admin only)")
    public ResponseEntity<?> regradeBulkSubmissions(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> submissionIds = (List<String>) request.get("submissionIds");
            
            if (submissionIds == null || submissionIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No submission IDs provided"));
            }
            
            logger.info("Bulk re-grading {} submissions for exam {}", submissionIds.size(), id);
            
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (String submissionId : submissionIds) {
                try {
                    examReviewService.reviewSubmissionWithAI(submissionId);
                    results.add(Map.of(
                        "submissionId", submissionId,
                        "status", "success"
                    ));
                    successCount++;
                } catch (Exception e) {
                    logger.error("Failed to re-grade submission {}", submissionId, e);
                    results.add(Map.of(
                        "submissionId", submissionId,
                        "status", "error",
                        "error", e.getMessage()
                    ));
                    failureCount++;
                }
            }
            
            logger.info("Bulk re-grade completed: {} successful, {} failed", successCount, failureCount);
            
            return ResponseEntity.ok(Map.of(
                "totalCount", submissionIds.size(),
                "successCount", successCount,
                "failureCount", failureCount,
                "results", results
            ));
        } catch (Exception e) {
            logger.error("Failed to process bulk re-grade request for exam {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to process bulk re-grade: " + e.getMessage()));
        }
    }
    
    @PostMapping("/{id}/inline-questions")
    @Operation(summary = "Create inline question", description = "Create a new inline quiz question for an exam")
    public ResponseEntity<QuizQuestion> createQuestion(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        String questionTypeStr = (String) request.get("questionType");
        QuizQuestion.QuestionType questionType = QuizQuestion.QuestionType.valueOf(questionTypeStr);
        String questionText = (String) request.get("questionText");
        Integer points = request.get("points") != null ? 
            ((Number) request.get("points")).intValue() : 1;
        String tentativeAnswer = (String) request.get("tentativeAnswer");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsData = (List<Map<String, Object>>) request.get("options");
        List<QuestionService.OptionData> options = null;
        if (optionsData != null) {
            options = new ArrayList<>();
            for (Map<String, Object> optData : optionsData) {
                String text = (String) optData.get("text");
                Boolean isCorrect = optData.get("isCorrect") != null ? 
                    ((Boolean) optData.get("isCorrect")) : false;
                options.add(new QuestionService.OptionData(text, isCorrect));
            }
        }
        
        QuizQuestion question = questionService.createQuestion(id, questionType, questionText, points, options, tentativeAnswer);
        return ResponseEntity.status(HttpStatus.CREATED).body(question);
    }
    
    @PutMapping("/{id}/inline-questions/{questionId}")
    @Operation(summary = "Update inline question", description = "Update an existing inline quiz question (not from question bank)")
    public ResponseEntity<QuizQuestion> updateInlineQuestion(
            @PathVariable String id,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> request) {
        
        String questionText = (String) request.get("questionText");
        Integer points = request.get("points") != null ? 
            ((Number) request.get("points")).intValue() : null;
        String tentativeAnswer = (String) request.get("tentativeAnswer");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsData = (List<Map<String, Object>>) request.get("options");
        List<QuestionService.OptionData> options = null;
        if (optionsData != null) {
            options = new ArrayList<>();
            for (Map<String, Object> optData : optionsData) {
                String text = (String) optData.get("text");
                Boolean isCorrect = optData.get("isCorrect") != null ? 
                    ((Boolean) optData.get("isCorrect")) : false;
                options.add(new QuestionService.OptionData(text, isCorrect));
            }
        }
        
        QuizQuestion question = questionService.updateQuestion(questionId, questionText, points, options, tentativeAnswer);
        return ResponseEntity.ok(question);
    }
    
    @DeleteMapping("/{id}/inline-questions/{questionId}")
    @Operation(summary = "Delete inline question", description = "Delete an inline quiz question from an exam (not from question bank)")
    public ResponseEntity<Void> deleteInlineQuestion(
            @PathVariable String id,
            @PathVariable String questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/inline-questions/reorder")
    @Operation(summary = "Reorder inline questions", description = "Reorder inline quiz questions in an exam")
    public ResponseEntity<Void> reorderQuestions(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        @SuppressWarnings("unchecked")
        List<String> questionIds = (List<String>) request.get("questionIds");
        if (questionIds == null || questionIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        questionService.reorderQuestions(id, questionIds);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete or archive exam", description = "Delete an exam if no submissions, otherwise archive it")
    public ResponseEntity<Map<String, Object>> deleteExam(@PathVariable String id) {
        boolean wasDeleted = examService.deleteExam(id);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("examId", id);
        response.put("action", wasDeleted ? "deleted" : "archived");
        response.put("message", wasDeleted 
            ? "Exam was permanently deleted" 
            : "Exam was archived because it has submissions");
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}/publish")
    @Operation(summary = "Publish exam", description = "Publish an exam to make it available to students")
    public ResponseEntity<ExamDetailDTO> publishExam(@PathVariable String id) {
        examService.publishExam(id);
        // Reload with questions
        Assessment exam = examService.getExamById(id);
        ExamDetailDTO dto = ExamDetailDTO.fromAssessment(exam);
        return ResponseEntity.ok(dto);
    }
    
    @PutMapping("/{id}/complete")
    @Operation(summary = "Complete exam", description = "Mark an exam as completed to prevent further submissions")
    public ResponseEntity<ExamDetailDTO> completeExam(@PathVariable String id) {
        examService.completeExam(id);
        // Reload with questions
        Assessment exam = examService.getExamById(id);
        ExamDetailDTO dto = ExamDetailDTO.fromAssessment(exam);
        return ResponseEntity.ok(dto);
    }
    
    @PutMapping("/{id}/archive")
    @Operation(summary = "Archive exam", description = "Archive an exam (soft delete)")
    public ResponseEntity<Void> archiveExam(@PathVariable String id) {
        examService.archiveExam(id);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}/unarchive")
    @Operation(summary = "Unarchive exam", description = "Restore an archived exam")
    public ResponseEntity<Void> unarchiveExam(@PathVariable String id) {
        examService.unarchiveExam(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/current-status")
    @Operation(summary = "Get real-time exam status", description = "Get exam status based on current time (not cached)")
    public ResponseEntity<Map<String, Object>> getCurrentStatus(@PathVariable String id) {
        try {
            com.datagami.edudron.content.domain.Assessment exam = examService.getExamById(id);
            com.datagami.edudron.content.domain.Assessment.ExamStatus realTimeStatus = examService.getRealTimeStatus(exam);
            boolean isAccessible = examService.isExamAccessible(exam);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("examId", id);
            response.put("currentStatus", realTimeStatus.name());
            response.put("storedStatus", exam.getStatus().name());
            response.put("isAccessible", isAccessible);
            response.put("startTime", exam.getStartTime());
            response.put("endTime", exam.getEndTime());
            response.put("serverTime", java.time.OffsetDateTime.now());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to get current status for exam: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/all-results")
    @Operation(summary = "Get all exam results", description = "Get aggregated results across all exams with statistics")
    public ResponseEntity<List<Map<String, Object>>> getAllResults(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String studentId) {
        try {
            List<com.datagami.edudron.content.domain.Assessment> exams = examService.getAllExams();
            
            // Filter by status if provided
            if (status != null && !status.isEmpty()) {
                try {
                    com.datagami.edudron.content.domain.Assessment.ExamStatus examStatus = 
                        com.datagami.edudron.content.domain.Assessment.ExamStatus.valueOf(status);
                    exams = exams.stream()
                        .filter(e -> e.getStatus() == examStatus)
                        .collect(java.util.stream.Collectors.toList());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid status filter: {}", status);
                }
            }
            
            // Filter by courseId if provided
            if (courseId != null && !courseId.isEmpty()) {
                exams = exams.stream()
                    .filter(e -> courseId.equals(e.getCourseId()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            List<Map<String, Object>> results = new ArrayList<>();
            
            for (com.datagami.edudron.content.domain.Assessment exam : exams) {
                Map<String, Object> examResult = new java.util.HashMap<>();
                examResult.put("examId", exam.getId());
                examResult.put("examTitle", exam.getTitle());
                examResult.put("examDescription", exam.getDescription());
                examResult.put("courseId", exam.getCourseId());
                examResult.put("status", exam.getStatus().name());
                examResult.put("startTime", exam.getStartTime());
                examResult.put("endTime", exam.getEndTime());
                examResult.put("reviewMethod", exam.getReviewMethod().name());
                examResult.put("createdAt", exam.getCreatedAt());
                
                // Get submissions for this exam
                try {
                    String submissionsUrl = gatewayUrl + "/api/assessments/" + exam.getId() + "/submissions";
                    
                    org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>> responseType = 
                        new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {};
                    
                    HttpHeaders submissionHeaders = new HttpHeaders();
                    submissionHeaders.setAccept(java.util.Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));
                    
                    ResponseEntity<List<Map<String, Object>>> submissionsResponse = getRestTemplate().exchange(
                        submissionsUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(submissionHeaders),
                        responseType
                    );
                    
                    List<Map<String, Object>> submissions = submissionsResponse.getBody();
                    if (submissions == null) {
                        submissions = new ArrayList<>();
                    }
                    
                    // Filter by studentId if provided
                    if (studentId != null && !studentId.isEmpty()) {
                        final String filterStudentId = studentId;
                        submissions = submissions.stream()
                            .filter(s -> filterStudentId.equals(s.get("studentId")))
                            .collect(java.util.stream.Collectors.toList());
                    }
                    
                    // Calculate statistics
                    int totalSubmissions = submissions.size();
                    int gradedSubmissions = 0;
                    int passedSubmissions = 0;
                    int pendingReviews = 0;
                    double totalScore = 0;
                    double totalMaxScore = 0;
                    
                    for (Map<String, Object> submission : submissions) {
                        Object scoreObj = submission.get("score");
                        Object maxScoreObj = submission.get("maxScore");
                        Object isPassedObj = submission.get("isPassed");
                        Object reviewStatusObj = submission.get("reviewStatus");
                        
                        if (scoreObj != null && maxScoreObj != null) {
                            gradedSubmissions++;
                            totalScore += ((Number) scoreObj).doubleValue();
                            totalMaxScore += ((Number) maxScoreObj).doubleValue();
                            
                            if (isPassedObj != null && (Boolean) isPassedObj) {
                                passedSubmissions++;
                            }
                        }
                        
                        if (reviewStatusObj != null) {
                            String reviewStatus = reviewStatusObj.toString();
                            if ("PENDING".equals(reviewStatus) || "IN_PROGRESS".equals(reviewStatus)) {
                                pendingReviews++;
                            }
                        }
                    }
                    
                    double avgScore = gradedSubmissions > 0 ? totalScore / gradedSubmissions : 0;
                    double avgMaxScore = gradedSubmissions > 0 ? totalMaxScore / gradedSubmissions : 0;
                    double avgPercentage = avgMaxScore > 0 ? (avgScore / avgMaxScore) * 100 : 0;
                    double passRate = gradedSubmissions > 0 ? (passedSubmissions * 100.0 / gradedSubmissions) : 0;
                    
                    Map<String, Object> statistics = new java.util.HashMap<>();
                    statistics.put("totalSubmissions", totalSubmissions);
                    statistics.put("gradedSubmissions", gradedSubmissions);
                    statistics.put("pendingReviews", pendingReviews);
                    statistics.put("avgScore", Math.round(avgScore * 100.0) / 100.0);
                    statistics.put("avgMaxScore", Math.round(avgMaxScore * 100.0) / 100.0);
                    statistics.put("avgPercentage", Math.round(avgPercentage * 100.0) / 100.0);
                    statistics.put("passRate", Math.round(passRate * 100.0) / 100.0);
                    statistics.put("passedCount", passedSubmissions);
                    
                    examResult.put("statistics", statistics);
                    examResult.put("submissions", submissions);
                    
                } catch (Exception e) {
                    logger.error("Failed to fetch submissions for exam: {}", exam.getId(), e);
                    // Add empty statistics if fetch fails
                    Map<String, Object> emptyStats = new java.util.HashMap<>();
                    emptyStats.put("totalSubmissions", 0);
                    emptyStats.put("gradedSubmissions", 0);
                    emptyStats.put("pendingReviews", 0);
                    emptyStats.put("avgScore", 0);
                    emptyStats.put("avgPercentage", 0);
                    emptyStats.put("passRate", 0);
                    examResult.put("statistics", emptyStats);
                    examResult.put("submissions", new ArrayList<>());
                }
                
                results.add(examResult);
            }
            
            // Sort by creation date (most recent first)
            results.sort((a, b) -> {
                Object aCreated = a.get("createdAt");
                Object bCreated = b.get("createdAt");
                if (aCreated == null || bCreated == null) return 0;
                return ((Comparable) bCreated).compareTo(aCreated);
            });
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Failed to get all exam results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/bulk-review")
    @Operation(summary = "Bulk AI review", description = "Trigger AI review for multiple submissions")
    public ResponseEntity<Map<String, Object>> bulkReview(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> submissionIds = (List<String>) request.get("submissionIds");
            
            if (submissionIds == null || submissionIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (String submissionId : submissionIds) {
                try {
                    examReviewService.reviewSubmissionWithAI(submissionId);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    errors.add("Submission " + submissionId + ": " + e.getMessage());
                    logger.error("Failed to review submission: {}", submissionId, e);
                }
            }
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("total", submissionIds.size());
            response.put("success", successCount);
            response.put("failed", failCount);
            response.put("errors", errors);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to process bulk review", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{examId}/submissions/{submissionId}/manual-grade")
    @Operation(summary = "Manual grade submission", description = "Manually grade an exam submission by updating scores")
    public ResponseEntity<Map<String, Object>> manualGrade(
            @PathVariable String examId,
            @PathVariable String submissionId,
            @RequestBody Map<String, Object> request) {
        try {
            // Delegate to student service via REST call
            String url = gatewayUrl + "/api/student/submissions/" + submissionId + "/manual-grade";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Manually graded submission {} for exam {}", submissionId, examId);
                return ResponseEntity.ok(response.getBody());
            } else {
                return ResponseEntity.status(response.getStatusCode()).build();
            }
        } catch (Exception e) {
            logger.error("Failed to manually grade submission: {} for exam: {}", submissionId, examId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
