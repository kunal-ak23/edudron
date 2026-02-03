package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.domain.QuestionBankOption;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.QuestionBankOptionRepository;
import com.datagami.edudron.content.repo.QuestionBankRepository;
import com.datagami.edudron.content.repo.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing the Question Bank - standalone questions that can be reused across exams.
 * Questions can be tagged to multiple modules (sections).
 */
@Service
@Transactional
public class QuestionBankService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionBankService.class);
    
    @Autowired
    private QuestionBankRepository questionBankRepository;
    
    @Autowired
    private QuestionBankOptionRepository optionRepository;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private ContentAuditService auditService;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();
    
    private RestTemplate getRestTemplate() {
        // Double-checked locking for thread safety
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    logger.debug("Initializing RestTemplate for identity service calls. Gateway URL: {}", gatewayUrl);
                    RestTemplate template = new RestTemplate();
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
                    template.setInterceptors(interceptors);
                    restTemplate = template;
                }
            }
        }
        return restTemplate;
    }
    
    /**
     * Create a new question in the question bank with multiple module support.
     */
    public QuestionBank createQuestion(String courseId, List<String> moduleIds, List<String> subModuleIds,
                                       QuestionBank.QuestionType questionType, String questionText,
                                       Integer points, QuestionBank.DifficultyLevel difficultyLevel,
                                       String explanation, List<String> tags,
                                       List<OptionData> options, String tentativeAnswer) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        // Verify course exists
        courseRepository.findByIdAndClientId(courseId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        
        // Verify at least one module is provided
        if (moduleIds == null || moduleIds.isEmpty()) {
            throw new IllegalArgumentException("At least one module must be specified");
        }
        
        // Verify all modules exist
        for (String moduleId : moduleIds) {
            sectionRepository.findByIdAndClientId(moduleId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));
        }
        
        // Validate question type requirements
        validateQuestionType(questionType, options, tentativeAnswer);
        
        QuestionBank question = new QuestionBank();
        question.setId(UlidGenerator.nextUlid());
        question.setClientId(clientId);
        question.setCourseId(courseId);
        question.setModuleIds(moduleIds);
        question.setSubModuleIds(subModuleIds != null ? subModuleIds : new ArrayList<>());
        question.setQuestionType(questionType);
        question.setQuestionText(questionText);
        question.setDefaultPoints(points != null ? points : 1);
        question.setDifficultyLevel(difficultyLevel);
        question.setExplanation(explanation);
        question.setTags(tags != null ? tags : new ArrayList<>());
        question.setTentativeAnswer(tentativeAnswer);
        question.setIsActive(true);
        
        QuestionBank saved = questionBankRepository.save(question);
        
        // Create options for MULTIPLE_CHOICE and TRUE_FALSE questions
        if (shouldHaveOptions(questionType) && options != null) {
            List<QuestionBankOption> questionOptions = createOptions(saved.getId(), clientId, options);
            optionRepository.saveAll(questionOptions);
        }
        
        // Reload with options
        QuestionBank reloaded = questionBankRepository.findByIdAndClientIdWithOptions(saved.getId(), clientId)
            .orElse(saved);
        
        logger.info("Created question bank question {} for course {} modules {}", 
            reloaded.getId(), courseId, moduleIds);
        auditService.logCrud("CREATE", "QuestionBank", saved.getId(), getCurrentUserId(), getCurrentUserEmail(),
            Map.of("courseId", courseId, "moduleIds", moduleIds != null ? moduleIds : List.of()));
        return reloaded;
    }
    
    /**
     * Legacy create method for single module (backward compatibility)
     */
    public QuestionBank createQuestion(String courseId, String moduleId, List<String> subModuleIds,
                                       QuestionBank.QuestionType questionType, String questionText,
                                       Integer points, QuestionBank.DifficultyLevel difficultyLevel,
                                       String explanation, List<String> tags,
                                       List<OptionData> options, String tentativeAnswer) {
        return createQuestion(courseId, List.of(moduleId), subModuleIds, questionType, questionText,
            points, difficultyLevel, explanation, tags, options, tentativeAnswer);
    }
    
    /**
     * Update an existing question in the question bank.
     */
    public QuestionBank updateQuestion(String questionId, String questionText, Integer points,
                                       QuestionBank.DifficultyLevel difficultyLevel, String explanation,
                                       List<String> tags, List<OptionData> options, String tentativeAnswer,
                                       List<String> subModuleIds, List<String> moduleIds) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        QuestionBank question = questionBankRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        if (questionText != null) {
            question.setQuestionText(questionText);
        }
        if (points != null) {
            question.setDefaultPoints(points);
        }
        if (difficultyLevel != null) {
            question.setDifficultyLevel(difficultyLevel);
        }
        if (explanation != null) {
            question.setExplanation(explanation);
        }
        if (tags != null) {
            question.setTags(tags);
        }
        if (tentativeAnswer != null) {
            question.setTentativeAnswer(tentativeAnswer);
        }
        if (subModuleIds != null) {
            question.setSubModuleIds(subModuleIds);
        }
        if (moduleIds != null && !moduleIds.isEmpty()) {
            // Verify all modules exist
            for (String moduleId : moduleIds) {
                sectionRepository.findByIdAndClientId(moduleId, clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));
            }
            question.setModuleIds(moduleIds);
        }
        
        // Update options for MULTIPLE_CHOICE and TRUE_FALSE questions
        if (shouldHaveOptions(question.getQuestionType()) && options != null) {
            optionRepository.deleteByQuestionIdAndClientId(questionId, clientId);
            List<QuestionBankOption> questionOptions = createOptions(questionId, clientId, options);
            optionRepository.saveAll(questionOptions);
        }
        
        QuestionBank updated = questionBankRepository.save(question);
        
        // Reload with options
        QuestionBank reloaded = questionBankRepository.findByIdAndClientIdWithOptions(questionId, clientId)
            .orElse(updated);
        
        logger.info("Updated question bank question {}", questionId);
        auditService.logCrud("UPDATE", "QuestionBank", questionId, getCurrentUserId(), getCurrentUserEmail(),
            Map.of("courseId", question.getCourseId()));
        return reloaded;
    }
    
    /**
     * Legacy update method without moduleIds (backward compatibility)
     */
    public QuestionBank updateQuestion(String questionId, String questionText, Integer points,
                                       QuestionBank.DifficultyLevel difficultyLevel, String explanation,
                                       List<String> tags, List<OptionData> options, String tentativeAnswer,
                                       List<String> subModuleIds) {
        return updateQuestion(questionId, questionText, points, difficultyLevel, explanation,
            tags, options, tentativeAnswer, subModuleIds, null);
    }
    
    /**
     * Soft delete a question (mark as inactive).
     */
    public void deleteQuestion(String questionId) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        QuestionBank question = questionBankRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        question.setIsActive(false);
        questionBankRepository.save(question);
        auditService.logCrud("UPDATE", "QuestionBank", questionId, getCurrentUserId(), getCurrentUserEmail(),
            Map.of("action", "SOFT_DELETE", "isActive", false));
        logger.info("Soft deleted question bank question {}", questionId);
    }
    
    /**
     * Hard delete a question (permanently remove).
     */
    public void hardDeleteQuestion(String questionId) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        QuestionBank question = questionBankRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        optionRepository.deleteByQuestionIdAndClientId(questionId, clientId);
        auditService.logCrud("DELETE", "QuestionBank", questionId, getCurrentUserId(), getCurrentUserEmail(),
            Map.of("courseId", question.getCourseId()));
        questionBankRepository.delete(question);
        logger.info("Hard deleted question bank question {}", questionId);
    }
    
    /**
     * Get a single question by ID.
     */
    public QuestionBank getQuestion(String questionId) {
        UUID clientId = getClientId();
        return questionBankRepository.findByIdAndClientIdWithOptions(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
    }
    
    /**
     * Get all questions that contain a specific module in their moduleIds.
     */
    public List<QuestionBank> getQuestionsByModule(String moduleId) {
        UUID clientId = getClientId();
        return questionBankRepository.findByModuleIdContainedAndClientIdWithOptionsNative(moduleId, clientId);
    }
    
    /**
     * Get all questions for a course.
     */
    public List<QuestionBank> getQuestionsByCourse(String courseId) {
        UUID clientId = getClientId();
        return questionBankRepository.findByCourseIdAndClientIdAndIsActiveTrueOrderByCreatedAtDesc(courseId, clientId);
    }
    
    /**
     * Get questions by course with pagination.
     */
    public Page<QuestionBank> getQuestionsByCourse(String courseId, Pageable pageable) {
        UUID clientId = getClientId();
        return questionBankRepository.findByCourseIdAndClientIdAndIsActiveTrue(courseId, clientId, pageable);
    }
    
    /**
     * Get questions that contain a specific module and have a specific difficulty.
     */
    public List<QuestionBank> getQuestionsByModuleAndDifficulty(String moduleId, QuestionBank.DifficultyLevel difficulty) {
        UUID clientId = getClientId();
        return questionBankRepository.findByModuleIdContainedAndDifficultyLevelAndClientIdAndIsActiveTrue(
            moduleId, difficulty.name(), clientId);
    }
    
    /**
     * Get questions where moduleIds overlaps with any of the given module IDs (for exam generation).
     */
    public List<QuestionBank> getQuestionsByModules(List<String> moduleIds) {
        UUID clientId = getClientId();
        // Convert list to PostgreSQL array string format: {id1,id2,id3}
        String moduleIdsArray = "{" + String.join(",", moduleIds) + "}";
        return questionBankRepository.findByModuleIdsOverlapAndClientIdWithOptionsNative(moduleIdsArray, clientId);
    }
    
    /**
     * Get questions that contain a specific sub-module (Lecture) ID in their subModuleIds.
     */
    public List<QuestionBank> getQuestionsBySubModule(String subModuleId) {
        UUID clientId = getClientId();
        return questionBankRepository.findBySubModuleIdContainedAndClientIdAndIsActiveTrue(subModuleId, clientId);
    }
    
    /**
     * Search questions by keyword in question text.
     */
    public List<QuestionBank> searchQuestions(String courseId, String keyword) {
        UUID clientId = getClientId();
        return questionBankRepository.searchByQuestionText(courseId, clientId, keyword);
    }
    
    /**
     * Get question count that contains a specific module.
     */
    public long countByModule(String moduleId) {
        UUID clientId = getClientId();
        return questionBankRepository.countByModuleIdContainedAndClientIdAndIsActiveTrue(moduleId, clientId);
    }
    
    /**
     * Get question count by course.
     */
    public long countByCourse(String courseId) {
        UUID clientId = getClientId();
        return questionBankRepository.countByCourseIdAndClientIdAndIsActiveTrue(courseId, clientId);
    }
    
    /**
     * Bulk create questions with multiple module support.
     */
    public List<QuestionBank> bulkCreateQuestions(String courseId, List<String> moduleIds, List<QuestionData> questionsData) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        // Verify course
        courseRepository.findByIdAndClientId(courseId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        
        // Verify all modules exist
        for (String moduleId : moduleIds) {
            sectionRepository.findByIdAndClientId(moduleId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));
        }
        
        List<QuestionBank> createdQuestions = new ArrayList<>();
        
        for (QuestionData data : questionsData) {
            QuestionBank question = new QuestionBank();
            question.setId(UlidGenerator.nextUlid());
            question.setClientId(clientId);
            question.setCourseId(courseId);
            // Use moduleIds from data if provided, otherwise use the default moduleIds
            List<String> questionModuleIds = data.getModuleIds() != null && !data.getModuleIds().isEmpty() 
                ? data.getModuleIds() : moduleIds;
            question.setModuleIds(questionModuleIds);
            question.setSubModuleIds(data.getSubModuleIds() != null ? data.getSubModuleIds() : new ArrayList<>());
            question.setQuestionType(data.getQuestionType());
            question.setQuestionText(data.getQuestionText());
            question.setDefaultPoints(data.getPoints() != null ? data.getPoints() : 1);
            question.setDifficultyLevel(data.getDifficultyLevel());
            question.setExplanation(data.getExplanation());
            question.setTags(data.getTags() != null ? data.getTags() : new ArrayList<>());
            question.setTentativeAnswer(data.getTentativeAnswer());
            question.setIsActive(true);
            
            QuestionBank saved = questionBankRepository.save(question);
            
            if (shouldHaveOptions(data.getQuestionType()) && data.getOptions() != null) {
                List<QuestionBankOption> options = createOptions(saved.getId(), clientId, data.getOptions());
                optionRepository.saveAll(options);
            }
            
            createdQuestions.add(saved);
        }
        
        logger.info("Bulk created {} questions for course {} modules {}", 
            createdQuestions.size(), courseId, moduleIds);
        
        return createdQuestions;
    }
    
    /**
     * Legacy bulk create for single module (backward compatibility)
     */
    public List<QuestionBank> bulkCreateQuestions(String courseId, String moduleId, List<QuestionData> questionsData) {
        return bulkCreateQuestions(courseId, List.of(moduleId), questionsData);
    }
    
    // Helper methods
    
    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }
    
    private void validateWriteAccess() {
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("You do not have permission to modify the question bank");
        }
    }
    
    private boolean shouldHaveOptions(QuestionBank.QuestionType questionType) {
        return questionType == QuestionBank.QuestionType.MULTIPLE_CHOICE 
            || questionType == QuestionBank.QuestionType.TRUE_FALSE;
    }
    
    private void validateQuestionType(QuestionBank.QuestionType questionType, 
                                      List<OptionData> options, String tentativeAnswer) {
        if (questionType == QuestionBank.QuestionType.MULTIPLE_CHOICE) {
            if (options == null || options.size() < 2) {
                throw new IllegalArgumentException("Multiple choice questions require at least 2 options");
            }
            long correctCount = options.stream().filter(OptionData::isCorrect).count();
            if (correctCount == 0) {
                throw new IllegalArgumentException("Multiple choice questions require at least one correct answer");
            }
        } else if (questionType == QuestionBank.QuestionType.TRUE_FALSE) {
            // TRUE_FALSE questions will auto-generate options if not provided
        }
    }
    
    private List<QuestionBankOption> createOptions(String questionId, UUID clientId, List<OptionData> optionsData) {
        List<QuestionBankOption> options = new ArrayList<>();
        for (int i = 0; i < optionsData.size(); i++) {
            OptionData data = optionsData.get(i);
            QuestionBankOption option = new QuestionBankOption();
            option.setId(UlidGenerator.nextUlid());
            option.setClientId(clientId);
            option.setQuestionId(questionId);
            option.setOptionText(data.getText());
            option.setIsCorrect(data.isCorrect());
            option.setSequence(i + 1);
            options.add(option);
        }
        return options;
    }
    
    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName()))
                return auth.getName();
        } catch (Exception e) {
            logger.debug("Could not determine user ID: {}", e.getMessage());
        }
        return null;
    }
    
    private String getCurrentUserEmail() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) return null;
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object email = response.getBody().get("email");
                return email != null ? email.toString() : null;
            }
        } catch (Exception e) {
            logger.debug("Could not determine user email: {}", e.getMessage());
        }
        return null;
    }
    
    private String getCurrentUserRole() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return null;
            }
            
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object role = response.getBody().get("role");
                return role != null ? role.toString() : null;
            }
        } catch (Exception e) {
            logger.debug("Could not determine user role: {}", e.getMessage());
        }
        return null;
    }
    
    // Data classes
    
    public static class OptionData {
        private String text;
        private boolean correct;
        
        public OptionData() {}
        
        public OptionData(String text, boolean correct) {
            this.text = text;
            this.correct = correct;
        }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }
    }
    
    public static class QuestionData {
        private QuestionBank.QuestionType questionType;
        private String questionText;
        private Integer points;
        private QuestionBank.DifficultyLevel difficultyLevel;
        private String explanation;
        private List<String> tags;
        private List<OptionData> options;
        private String tentativeAnswer;
        private List<String> subModuleIds; // Support for multiple sub-modules (lectures) per question
        private List<String> moduleIds; // Support for multiple modules per question
        
        public QuestionBank.QuestionType getQuestionType() { return questionType; }
        public void setQuestionType(QuestionBank.QuestionType questionType) { this.questionType = questionType; }
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        public Integer getPoints() { return points; }
        public void setPoints(Integer points) { this.points = points; }
        public QuestionBank.DifficultyLevel getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(QuestionBank.DifficultyLevel difficultyLevel) { this.difficultyLevel = difficultyLevel; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public List<OptionData> getOptions() { return options; }
        public void setOptions(List<OptionData> options) { this.options = options; }
        public String getTentativeAnswer() { return tentativeAnswer; }
        public void setTentativeAnswer(String tentativeAnswer) { this.tentativeAnswer = tentativeAnswer; }
        public List<String> getSubModuleIds() { return subModuleIds; }
        public void setSubModuleIds(List<String> subModuleIds) { this.subModuleIds = subModuleIds; }
        public List<String> getModuleIds() { return moduleIds; }
        public void setModuleIds(List<String> moduleIds) { this.moduleIds = moduleIds; }
    }
}
