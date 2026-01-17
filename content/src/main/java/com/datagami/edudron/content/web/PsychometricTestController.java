package com.datagami.edudron.content.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.PsychometricTestResult;
import com.datagami.edudron.content.domain.PsychometricTestSession;
import com.datagami.edudron.content.dto.*;
import com.datagami.edudron.content.service.PsychometricTestService;
import com.datagami.edudron.content.service.HybridPsychometricTestService;
import com.datagami.edudron.content.service.psychometric.Question;
import com.datagami.edudron.content.service.psychometric.ReportGenerationService;
import com.datagami.edudron.content.service.psychometric.TestResult;
import com.datagami.edudron.content.service.psychometric.SessionState;
import com.datagami.edudron.content.dto.QuestionDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/psychometric-test")
@Tag(name = "Psychometric Test", description = "Psychometric test endpoints for students")
public class PsychometricTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(PsychometricTestController.class);
    
    @Autowired
    private PsychometricTestService testService; // Legacy service
    
    @Autowired
    private HybridPsychometricTestService hybridTestService; // New hybrid service
    
    @Autowired
    private ReportGenerationService reportGenerationService;
    
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
    
    /**
     * Check if psychometric test feature is available and payment status.
     */
    @GetMapping("/availability")
    @Operation(summary = "Check test availability", description = "Check if psychometric test is enabled and payment status")
    public ResponseEntity<TestAvailabilityDTO> checkAvailability() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean enabled = false;
        try {
            String url = gatewayUrl + "/api/tenant/features/PSYCHOMETRIC_TEST";
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> feature = response.getBody();
                Object enabledObj = feature.get("enabled");
                enabled = enabledObj != null && Boolean.TRUE.equals(enabledObj);
            }
        } catch (Exception e) {
            logger.warn("Failed to check psychometric test feature availability", e);
        }
        
        boolean paymentRequired = false; // TODO: Check payment requirement from tenant settings
        
        TestAvailabilityDTO availability = new TestAvailabilityDTO();
        availability.setEnabled(enabled);
        availability.setPaymentRequired(paymentRequired);
        availability.setMessage(enabled ? "Psychometric test is available" : "Psychometric test is not enabled for this tenant");
        
        return ResponseEntity.ok(availability);
    }
    
    /**
     * Start a new psychometric test session.
     */
    @PostMapping("/start")
    @Operation(summary = "Start test", description = "Start a new psychometric test session")
    public ResponseEntity<PsychometricTestSessionDTO> startTest(@RequestBody(required = false) StartTestRequest request) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build();
            }
            
            // Get student ID from authentication
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // TODO: Verify payment if required
            // if (paymentRequired && request.getPaymentId() != null) {
            //     testService.verifyPayment(null, request.getPaymentId());
            // }
            
            // Use hybrid service for new sessions
            PsychometricTestSession session = hybridTestService.startTest(studentId);
            return ResponseEntity.status(HttpStatus.CREATED).body(toSessionDTO(session));
        } catch (IllegalStateException e) {
            logger.error("Failed to start test", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            logger.error("Failed to start test", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Submit an answer and get the next question.
     */
    @PostMapping("/{sessionId}/answer")
    @Operation(summary = "Submit answer", description = "Submit an answer and get the next question")
    public ResponseEntity<PsychometricTestSessionDTO> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody SubmitAnswerRequest request) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verify student owns this session
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            PsychometricTestSession session = testService.getTestStatus(sessionId);
            if (!session.getStudentId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            if (session.getStatus() != PsychometricTestSession.Status.IN_PROGRESS) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            // Use hybrid service
            session = hybridTestService.submitAnswer(sessionId, request.getAnswer());
            return ResponseEntity.ok(toSessionDTO(session));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            logger.error("Invalid state", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to submit answer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get current test status.
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get test status", description = "Get current status of a test session")
    public ResponseEntity<PsychometricTestSessionDTO> getTestStatus(@PathVariable String sessionId) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verify student owns this session
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            PsychometricTestSession session = hybridTestService.getTestStatus(sessionId);
            if (!session.getStudentId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            return ResponseEntity.ok(toSessionDTO(session));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Failed to get test status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Complete the test and get results.
     */
    @PostMapping("/{sessionId}/complete")
    @Operation(summary = "Complete test", description = "Complete the test and generate results")
    public ResponseEntity<PsychometricTestResultDTO> completeTest(@PathVariable String sessionId) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verify student owns this session
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            PsychometricTestSession session = hybridTestService.getTestStatus(sessionId);
            if (!session.getStudentId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            PsychometricTestResult result = hybridTestService.completeTest(sessionId);
            return ResponseEntity.ok(toResultDTO(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to complete test", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get test results by session ID.
     */
    @GetMapping("/results/{sessionId}")
    @Operation(summary = "Get test results", description = "Get test results by session ID")
    public ResponseEntity<PsychometricTestResultDTO> getTestResults(@PathVariable String sessionId) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verify student owns this result
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            PsychometricTestResult result = hybridTestService.getTestResult(sessionId);
            if (!result.getStudentId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            return ResponseEntity.ok(toResultDTO(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Failed to get test results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get all test results for the current student.
     */
    @GetMapping("/results")
    @Operation(summary = "Get all results", description = "Get all test results for the current student")
    public ResponseEntity<List<PsychometricTestResultDTO>> getAllResults() {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            List<PsychometricTestResult> results = hybridTestService.getStudentResults(studentId);
            List<PsychometricTestResultDTO> resultDTOs = results.stream()
                .map(this::toResultDTO)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(resultDTOs);
        } catch (Exception e) {
            logger.error("Failed to get all results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get current question for a session
     */
    @GetMapping("/{sessionId}/question")
    @Operation(summary = "Get current question", description = "Get the current question for a test session")
    public ResponseEntity<QuestionDTO> getCurrentQuestion(@PathVariable String sessionId) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verify student owns this session
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            PsychometricTestSession session = hybridTestService.getTestStatus(sessionId);
            if (!session.getStudentId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            Question question = hybridTestService.getCurrentQuestion(sessionId);
            if (question == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            
            QuestionDTO questionDTO = toQuestionDTO(question, session);
            return ResponseEntity.ok(questionDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Failed to get current question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Generate report for test results
     */
    @GetMapping("/results/{sessionId}/report")
    @Operation(summary = "Get test report", description = "Generate downloadable report for test results")
    public ResponseEntity<Map<String, String>> getTestReport(@PathVariable String sessionId) {
        try {
            // Check feature availability
            if (!testService.isFeatureEnabled()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verify student owns this result
            String studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            PsychometricTestResult result = hybridTestService.getTestResult(sessionId);
            if (!result.getStudentId().equals(studentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Parse test result from recommendations JSON
            TestResult testResult = parseTestResultFromJson(result.getRecommendations());
            
            // Get student name (simplified - should fetch from student service)
            String studentName = "Student"; // TODO: Fetch from student service
            
            // Generate report HTML
            String reportHtml = reportGenerationService.generateReportHtml(result, testResult, studentName);
            
            Map<String, String> response = new java.util.HashMap<>();
            response.put("html", reportHtml);
            response.put("format", "html");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Failed to generate report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Parse TestResult from JSON recommendations
     */
    private TestResult parseTestResultFromJson(JsonNode recommendations) {
        // This is a simplified parser - in production, use proper JSON deserialization
        TestResult testResult = new TestResult();
        if (recommendations != null && recommendations.isObject()) {
            if (recommendations.has("primaryStream")) {
                testResult.setPrimaryStream(recommendations.get("primaryStream").asText());
            }
            if (recommendations.has("secondaryStream")) {
                testResult.setSecondaryStream(recommendations.get("secondaryStream").asText());
            }
            // Add more fields as needed
        }
        return testResult;
    }
    
    /**
     * Get current student ID from authentication context.
     * The JWT subject contains the email, so we need to look up the user ID from identity service.
     */
    private String getCurrentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        
        String email = authentication.getName(); // This is the email from JWT subject
        
        // Try to get user ID from identity service using /me endpoint
        try {
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> user = response.getBody();
                Object userIdObj = user.get("id");
                if (userIdObj != null) {
                    return userIdObj.toString();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve user ID from identity service for email {}. Error: {}", 
                email, e.getMessage());
        }
        
        // If we can't get the user ID, return null (will result in 401)
        logger.error("Could not resolve user ID for email: {}", email);
        return null;
    }
    
    /**
     * Convert session entity to DTO.
     */
    private PsychometricTestSessionDTO toSessionDTO(PsychometricTestSession session) {
        PsychometricTestSessionDTO dto = new PsychometricTestSessionDTO();
        dto.setId(session.getId());
        dto.setClientId(session.getClientId());
        dto.setStudentId(session.getStudentId());
        dto.setStatus(session.getStatus());
        dto.setCurrentPhase(session.getCurrentPhase());
        dto.setIdentifiedFields(session.getIdentifiedFields());
        dto.setConversationHistory(session.getConversationHistory());
        dto.setPaymentId(session.getPaymentId());
        dto.setPaymentRequired(session.getPaymentRequired());
        dto.setPaymentStatus(session.getPaymentStatus());
        dto.setStartedAt(session.getStartedAt());
        dto.setCompletedAt(session.getCompletedAt());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }
    
    /**
     * Convert result entity to DTO.
     */
    private PsychometricTestResultDTO toResultDTO(PsychometricTestResult result) {
        PsychometricTestResultDTO dto = new PsychometricTestResultDTO();
        dto.setId(result.getId());
        dto.setClientId(result.getClientId());
        dto.setStudentId(result.getStudentId());
        dto.setSessionId(result.getSessionId());
        dto.setFieldScores(result.getFieldScores());
        dto.setPrimaryField(result.getPrimaryField());
        dto.setSecondaryFields(result.getSecondaryFields());
        dto.setRecommendations(result.getRecommendations());
        dto.setTestSummary(result.getTestSummary());
        dto.setCreatedAt(result.getCreatedAt());
        return dto;
    }
    
    /**
     * Convert question entity to DTO.
     */
    private QuestionDTO toQuestionDTO(Question question, PsychometricTestSession session) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setText(question.getText());
        dto.setType(question.getType().name());
        dto.setOptions(question.getOptions());
        dto.setModule(question.getModule());
        dto.setOrder(question.getOrder());
        
        // Calculate progress based on phase
        try {
            SessionState state = hybridTestService.getSessionState(session.getId());
            if (state != null) {
                int totalQuestions = 18; // Core questions
                int currentQuestion = 0;
                
                if ("CORE".equals(state.getPhase())) {
                    // Core phase: 1-18
                    totalQuestions = 18;
                    // Use number of answered questions + 1 (for current)
                    int answered = state.getCoreAnswers() != null ? state.getCoreAnswers().size() : 0;
                    currentQuestion = Math.max(answered + 1, state.getCurrentQuestionIndex() != null ? state.getCurrentQuestionIndex() + 1 : 1);
                } else if (state.getPhase().startsWith("ADAPTIVE")) {
                    // Adaptive phase: 19-38 (18 core + up to 20 adaptive)
                    int coreCompleted = state.getCoreAnswers() != null ? state.getCoreAnswers().size() : 18;
                    int adaptiveCompleted = state.getAdaptiveAnswers() != null ? state.getAdaptiveAnswers().size() : 0;
                    currentQuestion = coreCompleted + adaptiveCompleted + 1; // +1 for current question
                    int adaptiveMax = state.getSelectedModules() != null ? state.getSelectedModules().size() * 10 : 20;
                    totalQuestions = 18 + adaptiveMax;
                }
                
                dto.setTotalQuestions(totalQuestions);
                dto.setCurrentQuestionNumber(currentQuestion);
            } else {
                // Fallback
                if ("CORE".equals(question.getModule())) {
                    dto.setTotalQuestions(18);
                    dto.setCurrentQuestionNumber(question.getOrder());
                } else {
                    dto.setTotalQuestions(10);
                    dto.setCurrentQuestionNumber(question.getOrder());
                }
            }
        } catch (Exception e) {
            // Fallback on error
            if ("CORE".equals(question.getModule())) {
                dto.setTotalQuestions(18);
                dto.setCurrentQuestionNumber(question.getOrder());
            } else {
                dto.setTotalQuestions(10);
                dto.setCurrentQuestionNumber(question.getOrder());
            }
        }
        
        return dto;
    }
}
