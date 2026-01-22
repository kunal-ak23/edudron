package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.dto.AssessmentSubmissionDTO;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
}
