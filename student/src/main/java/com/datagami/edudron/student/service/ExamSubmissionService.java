package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExamSubmissionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamSubmissionService.class);
    
    @Autowired
    private AssessmentSubmissionRepository submissionRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private CommonEventService eventService;
    
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
     * Start an exam attempt - initialize with timer
     */
    public AssessmentSubmission startExam(String studentId, String courseId, String examId, Integer timeLimitSeconds, Integer maxAttempts) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify enrollment
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("Student is not enrolled in this course");
        }
        Enrollment enrollment = enrollments.get(0);
        
        // Check if there's an existing in-progress submission
        List<AssessmentSubmission> existingSubmissions = submissionRepository
            .findByClientIdAndStudentIdAndAssessmentId(clientId, studentId, examId);
        
        AssessmentSubmission submission;
        if (!existingSubmissions.isEmpty()) {
            // Check if there's an in-progress submission (not completed)
            submission = existingSubmissions.stream()
                .filter(s -> s.getCompletedAt() == null)
                .findFirst()
                .orElse(null);
            
            if (submission != null) {
                // Resume existing attempt
                logger.info("Resuming exam attempt: {} for student: {}", examId, studentId);
                return submission;
            }
            
            // Check maxAttempts limit (if exam has one)
            if (maxAttempts != null && maxAttempts > 0) {
                // Count completed submissions
                long completedCount = existingSubmissions.stream()
                    .filter(s -> s.getCompletedAt() != null)
                    .count();
                
                if (completedCount >= maxAttempts) {
                    throw new IllegalStateException(
                        String.format("Maximum attempts (%d) reached for this exam", maxAttempts));
                }
                
                logger.info("Student {} attempting exam {} (attempt {}/{})", 
                    studentId, examId, completedCount + 1, maxAttempts);
            } else if (!existingSubmissions.isEmpty()) {
                // Log warning if multiple attempts without limit
                long completedCount = existingSubmissions.stream()
                    .filter(s -> s.getCompletedAt() != null)
                    .count();
                if (completedCount > 0) {
                    logger.warn("Student {} attempting exam {} again. Completed attempts: {} (no maxAttempts limit set)", 
                        studentId, examId, completedCount);
                }
            }
        }
        
        // Create new submission
        submission = new AssessmentSubmission();
        submission.setId(UlidGenerator.nextUlid());
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setEnrollmentId(enrollment.getId());
        submission.setAssessmentId(examId);
        submission.setCourseId(courseId);
        submission.setStartedAt(OffsetDateTime.now());
        submission.setTimeRemainingSeconds(timeLimitSeconds);
        submission.setReviewStatus(AssessmentSubmission.ReviewStatus.PENDING);
        
        // Initialize empty answers JSON
        submission.setAnswersJson(objectMapper.createObjectNode());
        
        // Generate and store randomization if enabled
        try {
            generateRandomization(submission, examId);
        } catch (Exception e) {
            logger.error("Failed to generate randomization for exam: {}, student: {}", examId, studentId, e);
            // Continue without randomization rather than failing the exam start
        }
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        logger.info("Started exam attempt: {} for student: {}", examId, studentId);
        return saved;
    }
    
    /**
     * Save progress (auto-save answers)
     */
    public AssessmentSubmission saveProgress(String submissionId, JsonNode answers, Integer timeRemainingSeconds) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        if (submission.getCompletedAt() != null) {
            throw new IllegalStateException("Cannot save progress for completed exam");
        }
        
        submission.setAnswersJson(answers);
        if (timeRemainingSeconds != null) {
            submission.setTimeRemainingSeconds(timeRemainingSeconds);
        }
        
        return submissionRepository.save(submission);
    }
    
    /**
     * Submit exam - final submission
     */
    public AssessmentSubmission submitExam(String submissionId, JsonNode answers) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        if (submission.getCompletedAt() != null) {
            throw new IllegalStateException("Exam has already been submitted");
        }
        
        submission.setAnswersJson(answers);
        submission.setCompletedAt(OffsetDateTime.now());
        submission.setSubmittedAt(OffsetDateTime.now());
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        logger.info("Submitted exam: {} for submission: {}", submission.getAssessmentId(), submissionId);
        
        // Log assessment submission event
        Integer score = saved.getScore() != null ? saved.getScore().intValue() : null;
        Integer maxScore = saved.getMaxScore() != null ? saved.getMaxScore().intValue() : null;
        Boolean passed = saved.getIsPassed() != null ? saved.getIsPassed() : false;
        Map<String, Object> submissionData = Map.of(
            "submissionId", saved.getId(),
            "enrollmentId", saved.getEnrollmentId(),
            "timeRemainingSeconds", saved.getTimeRemainingSeconds() != null ? saved.getTimeRemainingSeconds() : 0,
            "reviewStatus", saved.getReviewStatus() != null ? saved.getReviewStatus().name() : "PENDING"
        );
        eventService.logAssessmentSubmission(
            saved.getStudentId(),
            saved.getAssessmentId(),
            saved.getCourseId(),
            score,
            maxScore,
            passed,
            submissionData
        );
        
        return saved;
    }
    
    /**
     * Get submission status
     */
    public AssessmentSubmission getSubmissionStatus(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        return submission;
    }
    
    /**
     * Get submission by exam ID for current student
     */
    public AssessmentSubmission getSubmissionByExamId(String studentId, String examId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return submissionRepository
            .findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(clientId, studentId, examId)
            .orElse(null);
    }
    
    /**
     * Manually grade a submission - allows instructors to set scores and feedback
     */
    public AssessmentSubmission manualGrade(String submissionId, Double score, Double maxScore, 
                                           Boolean isPassed, String instructorFeedback) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        // Update scores - convert Double to BigDecimal
        if (score != null) {
            submission.setScore(java.math.BigDecimal.valueOf(score));
        }
        if (maxScore != null) {
            submission.setMaxScore(java.math.BigDecimal.valueOf(maxScore));
        }
        
        // Calculate percentage if both scores are set
        if (submission.getScore() != null && submission.getMaxScore() != null && 
            submission.getMaxScore().compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal percentage = submission.getScore()
                .divide(submission.getMaxScore(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(100));
            submission.setPercentage(percentage);
        }
        
        // Set pass/fail status
        if (isPassed != null) {
            submission.setIsPassed(isPassed);
        }
        
        // Update review status to instructor reviewed
        submission.setReviewStatus(AssessmentSubmission.ReviewStatus.INSTRUCTOR_REVIEWED);
        submission.setGradedAt(OffsetDateTime.now());
        
        // Store instructor feedback if provided
        if (instructorFeedback != null && !instructorFeedback.isEmpty()) {
            try {
                // Create or update instructor feedback in aiReviewFeedback JSON
                JsonNode existingFeedback = submission.getAiReviewFeedback();
                if (existingFeedback == null || existingFeedback.isNull()) {
                    existingFeedback = objectMapper.createObjectNode();
                }
                
                if (existingFeedback.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) existingFeedback)
                        .put("instructorFeedback", instructorFeedback);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) existingFeedback)
                        .put("manuallyGraded", true);
                    submission.setAiReviewFeedback(existingFeedback);
                }
            } catch (Exception e) {
                logger.error("Failed to add instructor feedback to submission", e);
            }
        }
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        logger.info("Manually graded submission: {} with score: {}/{}", submissionId, score, maxScore);
        return saved;
    }
    
    /**
     * Generate randomization for questions and MCQ options
     */
    private void generateRandomization(AssessmentSubmission submission, String examId) {
        try {
            // Fetch exam details from content service
            String examUrl = gatewayUrl + "/api/exams/" + examId;
            ResponseEntity<JsonNode> examResponse = getRestTemplate().exchange(
                examUrl,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                JsonNode.class
            );
            
            if (!examResponse.getStatusCode().is2xxSuccessful() || examResponse.getBody() == null) {
                logger.warn("Failed to fetch exam details for randomization: {}", examId);
                return;
            }
            
            JsonNode exam = examResponse.getBody();
            boolean randomizeQuestions = exam.has("randomizeQuestions") && exam.get("randomizeQuestions").asBoolean();
            boolean randomizeMcqOptions = exam.has("randomizeMcqOptions") && exam.get("randomizeMcqOptions").asBoolean();
            
            if (!randomizeQuestions && !randomizeMcqOptions) {
                // No randomization needed
                return;
            }
            
            JsonNode questionsNode = exam.get("questions");
            if (questionsNode == null || !questionsNode.isArray()) {
                logger.warn("No questions found in exam for randomization: {}", examId);
                return;
            }
            
            List<JsonNode> questionsList = new ArrayList<>();
            questionsNode.forEach(questionsList::add);
            
            // Generate question randomization
            if (randomizeQuestions && !questionsList.isEmpty()) {
                List<String> questionIds = questionsList.stream()
                    .map(q -> q.get("id").asText())
                    .collect(Collectors.toList());
                
                // Shuffle the question IDs
                Collections.shuffle(questionIds);
                
                // Store as JSON array
                ArrayNode questionOrderArray = objectMapper.createArrayNode();
                questionIds.forEach(questionOrderArray::add);
                submission.setQuestionOrder(questionOrderArray);
                
                logger.info("Generated randomized question order for exam: {}, count: {}", examId, questionIds.size());
            }
            
            // Generate MCQ option randomization
            if (randomizeMcqOptions) {
                ObjectNode mcqOptionOrders = objectMapper.createObjectNode();
                
                for (JsonNode question : questionsList) {
                    String questionType = question.has("questionType") ? question.get("questionType").asText() : "";
                    
                    if ("MULTIPLE_CHOICE".equals(questionType) || "TRUE_FALSE".equals(questionType)) {
                        JsonNode optionsNode = question.get("options");
                        if (optionsNode != null && optionsNode.isArray() && optionsNode.size() > 0) {
                            List<String> optionIds = new ArrayList<>();
                            optionsNode.forEach(option -> optionIds.add(option.get("id").asText()));
                            
                            // Shuffle the option IDs
                            Collections.shuffle(optionIds);
                            
                            // Store as JSON array for this question
                            ArrayNode optionOrderArray = objectMapper.createArrayNode();
                            optionIds.forEach(optionOrderArray::add);
                            
                            String questionId = question.get("id").asText();
                            mcqOptionOrders.set(questionId, optionOrderArray);
                        }
                    }
                }
                
                if (mcqOptionOrders.size() > 0) {
                    submission.setMcqOptionOrders(mcqOptionOrders);
                    logger.info("Generated randomized MCQ options for exam: {}, questions: {}", examId, mcqOptionOrders.size());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error generating randomization for exam: {}", examId, e);
            throw e;
        }
    }
}
