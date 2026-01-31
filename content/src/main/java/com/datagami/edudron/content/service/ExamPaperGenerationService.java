package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.ExamQuestion;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.ExamQuestionRepository;
import com.datagami.edudron.content.repo.QuestionBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating exam papers by selecting questions from the question bank.
 */
@Service
@Transactional
public class ExamPaperGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamPaperGenerationService.class);
    
    @Autowired
    private ExamQuestionRepository examQuestionRepository;
    
    @Autowired
    private QuestionBankRepository questionBankRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
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
        return restTemplate;
    }
    
    /**
     * Add questions from the question bank to an exam.
     */
    public List<ExamQuestion> addQuestionsToExam(String examId, List<String> questionIds) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        Assessment exam = assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        // Get current max sequence
        Integer maxSequence = examQuestionRepository.findMaxSequenceByExamIdAndClientId(examId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        List<ExamQuestion> addedQuestions = new ArrayList<>();
        
        for (String questionId : questionIds) {
            // Check if question already exists in exam
            if (examQuestionRepository.existsByExamIdAndQuestionIdAndClientId(examId, questionId, clientId)) {
                logger.warn("Question {} already exists in exam {}, skipping", questionId, examId);
                continue;
            }
            
            // Verify question exists
            questionBankRepository.findByIdAndClientId(questionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
            
            ExamQuestion examQuestion = new ExamQuestion();
            examQuestion.setId(UlidGenerator.nextUlid());
            examQuestion.setClientId(clientId);
            examQuestion.setExamId(examId);
            examQuestion.setQuestionId(questionId);
            examQuestion.setSequence(nextSequence++);
            
            addedQuestions.add(examQuestionRepository.save(examQuestion));
        }
        
        logger.info("Added {} questions to exam {}", addedQuestions.size(), examId);
        return addedQuestions;
    }
    
    /**
     * Add a single question to an exam with optional points override.
     */
    public ExamQuestion addQuestionToExam(String examId, String questionId, Integer pointsOverride) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        Assessment exam = assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        // Check if question already exists in exam
        if (examQuestionRepository.existsByExamIdAndQuestionIdAndClientId(examId, questionId, clientId)) {
            throw new IllegalArgumentException("Question already exists in this exam");
        }
        
        // Verify question exists
        questionBankRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        Integer maxSequence = examQuestionRepository.findMaxSequenceByExamIdAndClientId(examId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setId(UlidGenerator.nextUlid());
        examQuestion.setClientId(clientId);
        examQuestion.setExamId(examId);
        examQuestion.setQuestionId(questionId);
        examQuestion.setSequence(nextSequence);
        examQuestion.setPointsOverride(pointsOverride);
        
        ExamQuestion saved = examQuestionRepository.save(examQuestion);
        logger.info("Added question {} to exam {} at sequence {}", questionId, examId, nextSequence);
        return saved;
    }
    
    /**
     * Remove a question from an exam.
     */
    public void removeQuestionFromExam(String examId, String questionId) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        ExamQuestion examQuestion = examQuestionRepository.findByExamIdAndQuestionIdAndClientId(examId, questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found in exam"));
        
        int deletedSequence = examQuestion.getSequence();
        
        examQuestionRepository.delete(examQuestion);
        
        // Update sequences of remaining questions
        examQuestionRepository.decrementSequencesAfter(examId, clientId, deletedSequence);
        
        logger.info("Removed question {} from exam {}", questionId, examId);
    }
    
    /**
     * Reorder questions in an exam.
     */
    public void reorderExamQuestions(String examId, List<String> questionIds) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        for (int i = 0; i < questionIds.size(); i++) {
            String questionId = questionIds.get(i);
            ExamQuestion examQuestion = examQuestionRepository.findByExamIdAndQuestionIdAndClientId(examId, questionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found in exam: " + questionId));
            
            examQuestion.setSequence(i + 1);
            examQuestionRepository.save(examQuestion);
        }
        
        logger.info("Reordered {} questions in exam {}", questionIds.size(), examId);
    }
    
    /**
     * Update points override for a question in an exam.
     */
    public ExamQuestion updateQuestionPoints(String examId, String questionId, Integer pointsOverride) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        ExamQuestion examQuestion = examQuestionRepository.findByExamIdAndQuestionIdAndClientId(examId, questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found in exam"));
        
        examQuestion.setPointsOverride(pointsOverride);
        
        return examQuestionRepository.save(examQuestion);
    }
    
    /**
     * Get all questions for an exam with full question data.
     */
    public List<ExamQuestion> getExamQuestions(String examId) {
        UUID clientId = getClientId();
        return examQuestionRepository.findByExamIdAndClientIdWithQuestions(examId, clientId);
    }
    
    /**
     * Get total points for an exam.
     */
    public Integer getTotalPoints(String examId) {
        UUID clientId = getClientId();
        return examQuestionRepository.calculateTotalPoints(examId, clientId);
    }
    
    /**
     * Get question count for an exam.
     */
    public long getQuestionCount(String examId) {
        UUID clientId = getClientId();
        return examQuestionRepository.countByExamIdAndClientId(examId, clientId);
    }
    
    /**
     * Auto-generate an exam paper based on criteria.
     */
    public List<ExamQuestion> generateExamPaper(String examId, GenerationCriteria criteria) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        Assessment exam = assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        // Get module IDs from criteria or from exam
        List<String> moduleIds = criteria.getModuleIds();
        if (moduleIds == null || moduleIds.isEmpty()) {
            moduleIds = exam.getModuleIds();
        }
        
        if (moduleIds == null || moduleIds.isEmpty()) {
            throw new IllegalArgumentException("No modules specified for question generation");
        }
        
        // Fetch all available questions from the specified modules
        // Convert list to PostgreSQL array string format for native query
        String moduleIdsArray = "{" + String.join(",", moduleIds) + "}";
        List<QuestionBank> availableQuestions = questionBankRepository.findByModuleIdsOverlapAndClientIdWithOptionsNative(moduleIdsArray, clientId);
        
        // Filter by difficulty if specified
        if (criteria.getDifficultyLevel() != null) {
            availableQuestions = availableQuestions.stream()
                .filter(q -> q.getDifficultyLevel() == criteria.getDifficultyLevel())
                .collect(Collectors.toList());
        }
        
        // Filter by question type if specified
        if (criteria.getQuestionTypes() != null && !criteria.getQuestionTypes().isEmpty()) {
            availableQuestions = availableQuestions.stream()
                .filter(q -> criteria.getQuestionTypes().contains(q.getQuestionType()))
                .collect(Collectors.toList());
        }
        
        // Get existing questions in the exam to avoid duplicates
        Set<String> existingQuestionIds = examQuestionRepository.findByExamIdAndClientIdOrderBySequenceAsc(examId, clientId)
            .stream()
            .map(ExamQuestion::getQuestionId)
            .collect(Collectors.toSet());
        
        // Remove already added questions
        availableQuestions = availableQuestions.stream()
            .filter(q -> !existingQuestionIds.contains(q.getId()))
            .collect(Collectors.toList());
        
        if (availableQuestions.isEmpty()) {
            throw new IllegalStateException("No questions available matching the criteria");
        }
        
        // Determine how many questions to select
        int numQuestions = criteria.getNumberOfQuestions() != null 
            ? Math.min(criteria.getNumberOfQuestions(), availableQuestions.size())
            : availableQuestions.size();
        
        // Select questions
        List<QuestionBank> selectedQuestions;
        if (criteria.isRandomize()) {
            // Randomly select questions
            Collections.shuffle(availableQuestions);
            selectedQuestions = availableQuestions.subList(0, numQuestions);
        } else {
            // Take first N questions (ordered by creation date)
            selectedQuestions = availableQuestions.subList(0, numQuestions);
        }
        
        // If we need to distribute by difficulty
        if (criteria.getDifficultyDistribution() != null && !criteria.getDifficultyDistribution().isEmpty()) {
            selectedQuestions = selectByDifficultyDistribution(availableQuestions, criteria.getDifficultyDistribution(), numQuestions);
        }
        
        // Clear existing questions if specified
        if (criteria.isClearExisting()) {
            examQuestionRepository.deleteByExamIdAndClientId(examId, clientId);
        }
        
        // Add selected questions to exam
        Integer maxSequence = examQuestionRepository.findMaxSequenceByExamIdAndClientId(examId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        List<ExamQuestion> addedQuestions = new ArrayList<>();
        for (QuestionBank question : selectedQuestions) {
            ExamQuestion examQuestion = new ExamQuestion();
            examQuestion.setId(UlidGenerator.nextUlid());
            examQuestion.setClientId(clientId);
            examQuestion.setExamId(examId);
            examQuestion.setQuestionId(question.getId());
            examQuestion.setSequence(nextSequence++);
            
            addedQuestions.add(examQuestionRepository.save(examQuestion));
        }
        
        logger.info("Generated exam paper for exam {} with {} questions", examId, addedQuestions.size());
        return addedQuestions;
    }
    
    private List<QuestionBank> selectByDifficultyDistribution(List<QuestionBank> questions, 
                                                               Map<QuestionBank.DifficultyLevel, Integer> distribution,
                                                               int totalQuestions) {
        List<QuestionBank> selected = new ArrayList<>();
        Map<QuestionBank.DifficultyLevel, List<QuestionBank>> byDifficulty = questions.stream()
            .filter(q -> q.getDifficultyLevel() != null)
            .collect(Collectors.groupingBy(QuestionBank::getDifficultyLevel));
        
        for (Map.Entry<QuestionBank.DifficultyLevel, Integer> entry : distribution.entrySet()) {
            List<QuestionBank> available = byDifficulty.getOrDefault(entry.getKey(), new ArrayList<>());
            Collections.shuffle(available);
            int count = Math.min(entry.getValue(), available.size());
            selected.addAll(available.subList(0, count));
        }
        
        // If we haven't filled the quota, add more randomly
        if (selected.size() < totalQuestions) {
            Set<String> selectedIds = selected.stream().map(QuestionBank::getId).collect(Collectors.toSet());
            List<QuestionBank> remaining = questions.stream()
                .filter(q -> !selectedIds.contains(q.getId()))
                .collect(Collectors.toList());
            Collections.shuffle(remaining);
            int moreNeeded = totalQuestions - selected.size();
            selected.addAll(remaining.subList(0, Math.min(moreNeeded, remaining.size())));
        }
        
        return selected;
    }
    
    /**
     * Clear all questions from an exam.
     */
    public void clearExamQuestions(String examId) {
        validateWriteAccess();
        UUID clientId = getClientId();
        
        assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        examQuestionRepository.deleteByExamIdAndClientId(examId, clientId);
        logger.info("Cleared all questions from exam {}", examId);
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
            throw new IllegalArgumentException("You do not have permission to modify exam questions");
        }
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
    
    /**
     * Criteria for auto-generating exam papers.
     */
    public static class GenerationCriteria {
        private List<String> moduleIds;
        private Integer numberOfQuestions;
        private QuestionBank.DifficultyLevel difficultyLevel;
        private List<QuestionBank.QuestionType> questionTypes;
        private Map<QuestionBank.DifficultyLevel, Integer> difficultyDistribution;
        private boolean randomize = true;
        private boolean clearExisting = false;
        
        public List<String> getModuleIds() { return moduleIds; }
        public void setModuleIds(List<String> moduleIds) { this.moduleIds = moduleIds; }
        
        public Integer getNumberOfQuestions() { return numberOfQuestions; }
        public void setNumberOfQuestions(Integer numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }
        
        public QuestionBank.DifficultyLevel getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(QuestionBank.DifficultyLevel difficultyLevel) { this.difficultyLevel = difficultyLevel; }
        
        public List<QuestionBank.QuestionType> getQuestionTypes() { return questionTypes; }
        public void setQuestionTypes(List<QuestionBank.QuestionType> questionTypes) { this.questionTypes = questionTypes; }
        
        public Map<QuestionBank.DifficultyLevel, Integer> getDifficultyDistribution() { return difficultyDistribution; }
        public void setDifficultyDistribution(Map<QuestionBank.DifficultyLevel, Integer> difficultyDistribution) { this.difficultyDistribution = difficultyDistribution; }
        
        public boolean isRandomize() { return randomize; }
        public void setRandomize(boolean randomize) { this.randomize = randomize; }
        
        public boolean isClearExisting() { return clearExisting; }
        public void setClearExisting(boolean clearExisting) { this.clearExisting = clearExisting; }
    }
}
