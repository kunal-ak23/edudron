package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.PsychometricTestResult;
import com.datagami.edudron.content.domain.PsychometricTestSession;
import com.datagami.edudron.content.repo.PsychometricTestResultRepository;
import com.datagami.edudron.content.repo.PsychometricTestSessionRepository;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class PsychometricTestService {
    
    private static final Logger logger = LoggerFactory.getLogger(PsychometricTestService.class);
    
    private static final double FIELD_SCORE_THRESHOLD = 0.6; // Threshold to transition to deep dive
    private static final int MIN_EXPLORATION_QUESTIONS = 3;
    private static final int MAX_EXPLORATION_QUESTIONS = 5;
    private static final int DEEP_DIVE_QUESTIONS = 5;
    
    @Autowired
    private PsychometricTestSessionRepository sessionRepository;
    
    @Autowired
    private PsychometricTestResultRepository resultRepository;
    
    @Autowired
    private PsychometricTestAIService aiService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
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
     * Check if psychometric test feature is enabled for the current tenant.
     */
    public boolean isFeatureEnabled() {
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
                Object enabled = feature.get("enabled");
                return enabled != null && Boolean.TRUE.equals(enabled);
            }
            return false;
        } catch (Exception e) {
            logger.warn("Failed to check psychometric test feature availability, defaulting to false", e);
            return false;
        }
    }
    
    /**
     * Start a new psychometric test session.
     */
    public PsychometricTestSession startTest(String studentId) {
        if (!isFeatureEnabled()) {
            throw new IllegalStateException("Psychometric test feature is not enabled for this tenant");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Check for existing in-progress session
        Optional<PsychometricTestSession> existingSession = sessionRepository
            .findFirstByStudentIdAndClientIdAndStatusOrderByCreatedAtDesc(
                studentId, clientId, PsychometricTestSession.Status.IN_PROGRESS
            );
        
        if (existingSession.isPresent()) {
            logger.info("Found existing in-progress session: {}", existingSession.get().getId());
            return existingSession.get();
        }
        
        // Create new session
        PsychometricTestSession session = new PsychometricTestSession();
        session.setId(UlidGenerator.nextUlid());
        session.setClientId(clientId);
        session.setStudentId(studentId);
        session.setStatus(PsychometricTestSession.Status.IN_PROGRESS);
        session.setCurrentPhase(PsychometricTestSession.Phase.INITIAL_EXPLORATION);
        session.setStartedAt(OffsetDateTime.now());
        
        // Initialize conversation history with welcome message and first question
        ArrayNode conversationHistory = objectMapper.createArrayNode();
        
        // Add welcome message
        ObjectNode welcomeMsg = objectMapper.createObjectNode();
        welcomeMsg.put("role", "assistant");
        welcomeMsg.put("content", "Welcome to the psychometric test! I'll help you discover your career and field inclinations through a series of questions. Let's begin!");
        welcomeMsg.put("timestamp", OffsetDateTime.now().toString());
        conversationHistory.add(welcomeMsg);
        
        // Generate and add first question
        String firstQuestion = aiService.generateInitialQuestion();
        ObjectNode questionMsg = objectMapper.createObjectNode();
        questionMsg.put("role", "assistant");
        questionMsg.put("content", firstQuestion);
        questionMsg.put("timestamp", OffsetDateTime.now().toString());
        conversationHistory.add(questionMsg);
        
        session.setConversationHistory(conversationHistory);
        
        PsychometricTestSession saved = sessionRepository.save(session);
        logger.info("Started new psychometric test session: {} for student: {}", saved.getId(), studentId);
        return saved;
    }
    
    /**
     * Submit an answer and generate the next question.
     */
    public PsychometricTestSession submitAnswer(String sessionId, String answer) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        PsychometricTestSession session = sessionRepository.findByIdAndClientId(sessionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Test session not found: " + sessionId));
        
        if (session.getStatus() != PsychometricTestSession.Status.IN_PROGRESS) {
            throw new IllegalStateException("Test session is not in progress");
        }
        
        // Add user answer to conversation history
        ArrayNode conversationHistory = (ArrayNode) session.getConversationHistory();
        if (conversationHistory == null) {
            conversationHistory = objectMapper.createArrayNode();
        }
        
        ObjectNode answerMsg = objectMapper.createObjectNode();
        answerMsg.put("role", "user");
        answerMsg.put("content", answer);
        answerMsg.put("timestamp", OffsetDateTime.now().toString());
        conversationHistory.add(answerMsg);
        
        session.setConversationHistory(conversationHistory);
        
        // Determine next action based on phase
        if (session.getCurrentPhase() == PsychometricTestSession.Phase.INITIAL_EXPLORATION) {
            // Check if we should transition to deep dive
            int questionCount = countQuestions(conversationHistory);
            
            if (questionCount >= MIN_EXPLORATION_QUESTIONS) {
                // Analyze field inclinations
                Map<String, Double> fieldScores = aiService.analyzeFieldInclination(conversationHistory.toString());
                
                // Check if we have a strong field
                Optional<Map.Entry<String, Double>> topField = fieldScores.entrySet().stream()
                    .max(Map.Entry.comparingByValue());
                
                if (topField.isPresent() && topField.get().getValue() >= FIELD_SCORE_THRESHOLD) {
                    // Transition to deep dive
                    session.setCurrentPhase(PsychometricTestSession.Phase.FIELD_DEEP_DIVE);
                    session.setIdentifiedFields(convertFieldScoresToJson(fieldScores));
                    
                    // Add transition message
                    ObjectNode transitionMsg = objectMapper.createObjectNode();
                    transitionMsg.put("role", "assistant");
                    transitionMsg.put("content", "Based on your responses, I've identified a strong interest in " + 
                                      topField.get().getKey() + ". Let me ask you some deeper questions about this field.");
                    transitionMsg.put("timestamp", OffsetDateTime.now().toString());
                    conversationHistory.add(transitionMsg);
                    
                    logger.info("Transitioned to deep dive phase for session: {} with field: {}", sessionId, topField.get().getKey());
                } else if (questionCount >= MAX_EXPLORATION_QUESTIONS) {
                    // Force transition even without strong field
                    session.setCurrentPhase(PsychometricTestSession.Phase.FIELD_DEEP_DIVE);
                    session.setIdentifiedFields(convertFieldScoresToJson(fieldScores));
                    logger.info("Transitioned to deep dive phase after max exploration questions for session: {}", sessionId);
                }
            }
        }
        
        // Generate next question
        String nextQuestion = aiService.generateNextQuestion(
            conversationHistory.toString(),
            session.getCurrentPhase().name()
        );
        
        ObjectNode questionMsg = objectMapper.createObjectNode();
        questionMsg.put("role", "assistant");
        questionMsg.put("content", nextQuestion);
        questionMsg.put("timestamp", OffsetDateTime.now().toString());
        conversationHistory.add(questionMsg);
        
        session.setConversationHistory(conversationHistory);
        
        return sessionRepository.save(session);
    }
    
    /**
     * Complete the test and generate results.
     */
    public PsychometricTestResult completeTest(String sessionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        PsychometricTestSession session = sessionRepository.findByIdAndClientId(sessionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Test session not found: " + sessionId));
        
        if (session.getStatus() != PsychometricTestSession.Status.IN_PROGRESS) {
            throw new IllegalStateException("Test session is not in progress");
        }
        
        // Mark session as completed
        session.setStatus(PsychometricTestSession.Status.COMPLETED);
        session.setCurrentPhase(PsychometricTestSession.Phase.COMPLETED);
        session.setCompletedAt(OffsetDateTime.now());
        sessionRepository.save(session);
        
        // Analyze final field scores
        Map<String, Double> fieldScores = new HashMap<>();
        if (session.getIdentifiedFields() != null) {
            session.getIdentifiedFields().fields().forEachRemaining(entry -> {
                try {
                    fieldScores.put(entry.getKey(), entry.getValue().asDouble());
                } catch (Exception e) {
                    logger.warn("Failed to parse field score", e);
                }
            });
        }
        
        // If no fields identified, analyze from conversation
        if (fieldScores.isEmpty() && session.getConversationHistory() != null) {
            Map<String, Double> analyzedScores = aiService.analyzeFieldInclination(session.getConversationHistory().toString());
            fieldScores.putAll(analyzedScores);
        }
        
        // Generate comprehensive results
        Map<String, Object> results = aiService.generateTestResults(
            session.getConversationHistory() != null ? session.getConversationHistory().toString() : "{}",
            fieldScores
        );
        
        // Create result entity
        PsychometricTestResult result = new PsychometricTestResult();
        result.setId(UlidGenerator.nextUlid());
        result.setClientId(clientId);
        result.setStudentId(session.getStudentId());
        result.setSessionId(sessionId);
        result.setFieldScores(convertFieldScoresToJson(fieldScores));
        result.setPrimaryField((String) results.get("primaryField"));
        result.setSecondaryFields((JsonNode) results.get("secondaryFields"));
        result.setRecommendations((JsonNode) results.get("recommendations"));
        result.setTestSummary((String) results.get("testSummary"));
        
        PsychometricTestResult saved = resultRepository.save(result);
        logger.info("Completed psychometric test session: {} with result: {}", sessionId, saved.getId());
        return saved;
    }
    
    /**
     * Get current test status.
     */
    public PsychometricTestSession getTestStatus(String sessionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return sessionRepository.findByIdAndClientId(sessionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Test session not found: " + sessionId));
    }
    
    /**
     * Get test result by session ID.
     */
    public PsychometricTestResult getTestResult(String sessionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return resultRepository.findBySessionIdAndClientId(sessionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Test result not found for session: " + sessionId));
    }
    
    /**
     * Get all test results for a student.
     */
    public List<PsychometricTestResult> getStudentResults(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return resultRepository.findByStudentIdAndClientIdOrderByCreatedAtDesc(studentId, clientId);
    }
    
    /**
     * Check if payment is required (future implementation).
     */
    public boolean checkPaymentRequired(String studentId) {
        // TODO: Implement payment check based on tenant settings
        return false;
    }
    
    /**
     * Verify payment before starting test (future implementation).
     */
    public void verifyPayment(String sessionId, String paymentId) {
        // TODO: Implement payment verification
        logger.info("Payment verification not yet implemented for session: {}, payment: {}", sessionId, paymentId);
    }
    
    // Helper methods
    
    private int countQuestions(ArrayNode conversationHistory) {
        int count = 0;
        for (JsonNode msg : conversationHistory) {
            if (msg.has("role") && "assistant".equals(msg.get("role").asText())) {
                count++;
            }
        }
        return count;
    }
    
    private JsonNode convertFieldScoresToJson(Map<String, Double> fieldScores) {
        ObjectNode json = objectMapper.createObjectNode();
        fieldScores.forEach(json::put);
        return json;
    }
}
