package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.datagami.edudron.content.service.ExamService;
import com.datagami.edudron.content.service.ExamReviewService;
import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
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
        
        Assessment exam = examService.createExam(courseId, title, description, instructions, moduleIds, reviewMethod);
        return ResponseEntity.status(HttpStatus.CREATED).body(exam);
    }
    
    @GetMapping
    @Operation(summary = "List exams", description = "Get all exams")
    public ResponseEntity<List<Assessment>> getAllExams() {
        List<Assessment> exams = examService.getAllExams();
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get exam", description = "Get exam details by ID")
    public ResponseEntity<Assessment> getExam(@PathVariable String id) {
        Assessment exam = examService.getExamById(id);
        return ResponseEntity.ok(exam);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update exam", description = "Update an existing exam")
    public ResponseEntity<Assessment> updateExam(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String instructions = (String) request.get("instructions");
        
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
        
        Assessment exam = examService.updateExam(id, title, description, instructions, moduleIds, reviewMethod);
        return ResponseEntity.ok(exam);
    }
    
    @PostMapping("/{id}/generate")
    @Operation(summary = "Generate exam with AI", description = "Generate exam questions using AI based on selected modules")
    public ResponseEntity<Assessment> generateExamWithAI(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        Integer numberOfQuestions = request.get("numberOfQuestions") != null ? 
            ((Number) request.get("numberOfQuestions")).intValue() : 10;
        String difficulty = (String) request.get("difficulty");
        
        Assessment exam = examService.generateExamWithAI(id, numberOfQuestions, difficulty);
        return ResponseEntity.ok(exam);
    }
    
    @PutMapping("/{id}/schedule")
    @Operation(summary = "Schedule exam", description = "Schedule an exam with start and end times")
    public ResponseEntity<Assessment> scheduleExam(
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
        
        Assessment exam = examService.scheduleExam(id, startTime, endTime);
        return ResponseEntity.ok(exam);
    }
    
    @PutMapping("/{id}/questions/{questionId}/tentative-answer")
    @Operation(summary = "Update tentative answer", description = "Update the tentative answer for a subjective question")
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
            
            ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
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
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete exam", description = "Delete an exam")
    public ResponseEntity<Void> deleteExam(@PathVariable String id) {
        examService.deleteExam(id);
        return ResponseEntity.noContent().build();
    }
}
