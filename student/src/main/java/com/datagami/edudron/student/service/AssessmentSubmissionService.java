package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.dto.AssessmentSubmissionDTO;
import com.datagami.edudron.student.dto.SubmitAssessmentRequest;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssessmentSubmissionService {
    
    @Autowired
    private AssessmentSubmissionRepository submissionRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    public AssessmentSubmissionDTO submitAssessment(String studentId, String courseId, SubmitAssessmentRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify enrollment - handle potential duplicates
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("Student is not enrolled in this course");
        }
        Enrollment enrollment = enrollments.get(0); // Use first (most recent) enrollment if duplicates exist
        
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(UlidGenerator.nextUlid());
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setEnrollmentId(enrollment.getId());
        submission.setAssessmentId(request.getAssessmentId());
        submission.setCourseId(courseId);
        submission.setAnswersJson(request.getAnswers());
        
        // TODO: Calculate score based on assessment type and answers
        // For now, we'll leave scoring to be done manually or by a separate grading service
        submission.setGradedAt(null);
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        return toDTO(saved);
    }
    
    public AssessmentSubmissionDTO gradeSubmission(String submissionId, BigDecimal score, BigDecimal maxScore) {
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
        
        submission.setScore(score);
        submission.setMaxScore(maxScore);
        
        if (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentage = score.divide(maxScore, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            submission.setPercentage(percentage);
            // Consider passed if >= 60%
            submission.setIsPassed(percentage.compareTo(BigDecimal.valueOf(60)) >= 0);
        }
        
        submission.setGradedAt(java.time.OffsetDateTime.now());
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        return toDTO(saved);
    }
    
    public List<AssessmentSubmissionDTO> getStudentSubmissions(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<AssessmentSubmission> submissions = submissionRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        return submissions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public Page<AssessmentSubmissionDTO> getStudentSubmissions(String studentId, String courseId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<AssessmentSubmission> submissions = submissionRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId, pageable);
        return submissions.map(this::toDTO);
    }
    
    public AssessmentSubmissionDTO getLatestSubmission(String studentId, String assessmentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository
            .findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(clientId, studentId, assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("No submission found"));
        
        return toDTO(submission);
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
        
        // Exam-specific fields
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


