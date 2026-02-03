package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.client.ContentExamClient;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    
    @Autowired
    private ProctoringService proctoringService;
    
    @Autowired
    private ContentExamClient contentExamClient;
    
    /**
     * Timing mode for exams - mirrors the Assessment.TimingMode enum
     */
    public enum TimingMode {
        FIXED_WINDOW,    // Exam ends at endTime for all (late joiners get less time)
        FLEXIBLE_START   // Each student gets full timeLimitSeconds from their start
    }
    
    /**
     * Start an exam attempt - initialize with timer
     * @deprecated Use the overloaded method with timing mode parameters
     */
    @Deprecated
    public AssessmentSubmission startExam(String studentId, String courseId, String examId, Integer timeLimitSeconds, Integer maxAttempts) {
        return startExam(studentId, courseId, examId, timeLimitSeconds, maxAttempts, TimingMode.FIXED_WINDOW, null);
    }
    
    /**
     * Start an exam attempt - initialize with timer based on timing mode
     * 
     * @param studentId The student's ID
     * @param courseId The course ID
     * @param examId The exam ID
     * @param timeLimitSeconds The total time limit in seconds
     * @param maxAttempts Maximum number of attempts allowed
     * @param timingMode The timing mode (FIXED_WINDOW or FLEXIBLE_START)
     * @param examEndTime The exam end time (required for FIXED_WINDOW mode)
     * @return The created or resumed submission
     */
    public AssessmentSubmission startExam(String studentId, String courseId, String examId, 
                                          Integer timeLimitSeconds, Integer maxAttempts,
                                          TimingMode timingMode, OffsetDateTime examEndTime) {
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
                // Resume existing attempt - recalculate time remaining based on timing mode
                Integer updatedTimeRemaining = calculateTimeRemaining(submission, timingMode, timeLimitSeconds, examEndTime);
                if (updatedTimeRemaining != null && updatedTimeRemaining != submission.getTimeRemainingSeconds()) {
                    submission.setTimeRemainingSeconds(updatedTimeRemaining);
                    submission = submissionRepository.save(submission);
                }
                logger.info("Resuming exam attempt: {} for student: {} with {} seconds remaining", 
                    examId, studentId, submission.getTimeRemainingSeconds());
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
        
        // Calculate effective time limit based on timing mode
        Integer effectiveTimeLimit = calculateEffectiveTimeLimit(timingMode, timeLimitSeconds, examEndTime);
        
        // Create new submission
        submission = new AssessmentSubmission();
        submission.setId(UlidGenerator.nextUlid());
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setEnrollmentId(enrollment.getId());
        submission.setAssessmentId(examId);
        submission.setCourseId(courseId);
        submission.setStartedAt(OffsetDateTime.now());
        submission.setTimeRemainingSeconds(effectiveTimeLimit);
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
        logger.info("Started exam attempt: {} for student: {} with {} seconds (timingMode: {})", 
            examId, studentId, effectiveTimeLimit, timingMode);
        return saved;
    }
    
    /**
     * Calculate the effective time limit when starting an exam based on timing mode.
     * 
     * - FIXED_WINDOW: Time is constrained by the exam end time. Late joiners get less time.
     *                 Formula: min(timeLimitSeconds, endTime - now)
     * - FLEXIBLE_START: Each student gets the full duration from when they start.
     *                   Formula: timeLimitSeconds
     */
    private Integer calculateEffectiveTimeLimit(TimingMode timingMode, Integer timeLimitSeconds, OffsetDateTime examEndTime) {
        if (timeLimitSeconds == null) {
            return null; // No time limit
        }
        
        if (timingMode == TimingMode.FLEXIBLE_START) {
            // Student gets full duration regardless of when they start
            return timeLimitSeconds;
        }
        
        // FIXED_WINDOW mode - constrained by end time
        if (examEndTime != null) {
            long secondsToEnd = ChronoUnit.SECONDS.between(OffsetDateTime.now(), examEndTime);
            if (secondsToEnd <= 0) {
                return 0; // Exam has ended
            }
            return (int) Math.min(timeLimitSeconds, secondsToEnd);
        }
        
        // No end time specified, use full time limit
        return timeLimitSeconds;
    }
    
    /**
     * Calculate time remaining for a submission (used when resuming).
     * 
     * - FIXED_WINDOW: Time remaining is min(stored time, endTime - now)
     * - FLEXIBLE_START: Time remaining is timeLimitSeconds - (now - startedAt)
     */
    public Integer calculateTimeRemaining(AssessmentSubmission submission, TimingMode timingMode, 
                                         Integer timeLimitSeconds, OffsetDateTime examEndTime) {
        if (submission.getStartedAt() == null) {
            return submission.getTimeRemainingSeconds();
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        
        if (timingMode == TimingMode.FLEXIBLE_START) {
            // Calculate based on elapsed time since start
            if (timeLimitSeconds != null) {
                long elapsed = ChronoUnit.SECONDS.between(submission.getStartedAt(), now);
                int remaining = Math.max(0, timeLimitSeconds - (int) elapsed);
                return remaining;
            }
            return submission.getTimeRemainingSeconds();
        }
        
        // FIXED_WINDOW mode
        if (examEndTime != null) {
            long secondsToEnd = ChronoUnit.SECONDS.between(now, examEndTime);
            if (secondsToEnd <= 0) {
                return 0; // Exam has ended
            }
            // Return the minimum of stored time and time until end
            Integer storedTime = submission.getTimeRemainingSeconds();
            if (storedTime != null) {
                return (int) Math.min(storedTime, secondsToEnd);
            }
            return (int) secondsToEnd;
        }
        
        return submission.getTimeRemainingSeconds();
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
                                           Boolean isPassed, String instructorFeedback, JsonNode aiReviewFeedback) {
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
        
        // Store aiReviewFeedback with per-question grades if provided
        if (aiReviewFeedback != null) {
            // If there's instructor feedback, merge it into the aiReviewFeedback
            if (instructorFeedback != null && !instructorFeedback.isEmpty() && aiReviewFeedback.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) aiReviewFeedback)
                    .put("instructorFeedback", instructorFeedback);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) aiReviewFeedback)
                .put("manuallyGraded", true);
            submission.setAiReviewFeedback(aiReviewFeedback);
        } else if (instructorFeedback != null && !instructorFeedback.isEmpty()) {
            // No aiReviewFeedback but there's instructor feedback - add it to existing or new
            try {
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
            JsonNode exam = contentExamClient.getExam(examId);
            if (exam == null) {
                logger.warn("Failed to fetch exam details for randomization: {}", examId);
                return;
            }
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
