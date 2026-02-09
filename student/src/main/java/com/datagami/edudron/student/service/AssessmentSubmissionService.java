package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.dto.AssessmentSubmissionDTO;
import com.datagami.edudron.student.dto.BulkGradeRequest;
import com.datagami.edudron.student.dto.BulkGradeResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        return gradeSubmissionWithDetails(submissionId, score, maxScore, null, null, null, null);
    }
    
    public AssessmentSubmissionDTO gradeSubmissionWithDetails(String submissionId, BigDecimal score, 
                                                             BigDecimal maxScore, BigDecimal percentage,
                                                             Boolean isPassed, Object aiReviewFeedback,
                                                             String reviewStatus) {
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
        
        if (score != null) {
            submission.setScore(score);
        }
        if (maxScore != null) {
            submission.setMaxScore(maxScore);
        }
        
        if (percentage != null) {
            submission.setPercentage(percentage);
        } else if (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0 && score != null) {
            BigDecimal calculatedPercentage = score.divide(maxScore, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            submission.setPercentage(calculatedPercentage);
        }
        
        if (isPassed != null) {
            submission.setIsPassed(isPassed);
        } else if (submission.getPercentage() != null) {
            // Consider passed if >= 60% (default)
            submission.setIsPassed(submission.getPercentage().compareTo(BigDecimal.valueOf(60)) >= 0);
        }
        
        if (aiReviewFeedback != null) {
            if (aiReviewFeedback instanceof com.fasterxml.jackson.databind.JsonNode) {
                submission.setAiReviewFeedback((com.fasterxml.jackson.databind.JsonNode) aiReviewFeedback);
            }
        }
        
        if (reviewStatus != null) {
            try {
                submission.setReviewStatus(com.datagami.edudron.student.domain.AssessmentSubmission.ReviewStatus.valueOf(reviewStatus));
            } catch (IllegalArgumentException e) {
                // Invalid status, ignore
            }
        }
        
        submission.setGradedAt(java.time.OffsetDateTime.now());
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        return toDTO(saved);
    }

    public AssessmentSubmissionDTO markSubmissionAsCheating(String submissionId, Boolean markedAsCheating) {
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
        submission.setMarkedAsCheating(Boolean.TRUE.equals(markedAsCheating));
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
    
    public List<AssessmentSubmissionDTO> getSubmissionsByAssessmentId(String assessmentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<AssessmentSubmission> submissions = submissionRepository.findByClientIdAndAssessmentId(clientId, assessmentId);
        return submissions.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Get all assessment/exam submissions for a student (admin/instructor view).
     */
    public List<AssessmentSubmissionDTO> getSubmissionsByStudentId(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        List<AssessmentSubmission> submissions = submissionRepository.findByClientIdAndStudentIdOrderBySubmittedAtDesc(clientId, studentId);
        return submissions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public Page<AssessmentSubmissionDTO> getSubmissionsByAssessmentId(String assessmentId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<AssessmentSubmission> submissions = submissionRepository.findByClientIdAndAssessmentId(clientId, assessmentId, pageable);
        return submissions.map(this::toDTO);
    }
    
    /**
     * Bulk grade submissions for an assessment. Validates tenant and that each submission
     * belongs to the given assessmentId and client; updates score, maxScore, percentage,
     * isPassed, aiReviewFeedback, reviewStatus, gradedAt. Returns graded count and per-item errors.
     */
    public BulkGradeResponse bulkGradeSubmissions(String assessmentId, List<BulkGradeRequest.BulkGradeItem> grades) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        if (grades == null || grades.isEmpty()) {
            return new BulkGradeResponse(0, new ArrayList<>());
        }
        
        List<AssessmentSubmission> toSave = new ArrayList<>();
        List<BulkGradeResponse.BulkGradeError> errors = new ArrayList<>();
        
        for (BulkGradeRequest.BulkGradeItem item : grades) {
            if (item.getSubmissionId() == null || item.getSubmissionId().isBlank()) {
                errors.add(new BulkGradeResponse.BulkGradeError(null, "Missing submissionId"));
                continue;
            }
            Optional<AssessmentSubmission> opt = submissionRepository.findById(item.getSubmissionId());
            if (opt.isEmpty()) {
                errors.add(new BulkGradeResponse.BulkGradeError(item.getSubmissionId(), "Submission not found"));
                continue;
            }
            AssessmentSubmission submission = opt.get();
            if (!submission.getClientId().equals(clientId)) {
                errors.add(new BulkGradeResponse.BulkGradeError(item.getSubmissionId(), "Submission not found"));
                continue;
            }
            if (!assessmentId.equals(submission.getAssessmentId())) {
                errors.add(new BulkGradeResponse.BulkGradeError(item.getSubmissionId(), "Submission does not belong to this assessment"));
                continue;
            }
            
            if (item.getScore() != null) submission.setScore(item.getScore());
            if (item.getMaxScore() != null) submission.setMaxScore(item.getMaxScore());
            if (item.getPercentage() != null) {
                submission.setPercentage(item.getPercentage());
            } else if (item.getMaxScore() != null && item.getMaxScore().compareTo(BigDecimal.ZERO) > 0 && item.getScore() != null) {
                submission.setPercentage(item.getScore().divide(item.getMaxScore(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            }
            if (item.getIsPassed() != null) submission.setIsPassed(item.getIsPassed());
            else if (submission.getPercentage() != null) {
                submission.setIsPassed(submission.getPercentage().compareTo(BigDecimal.valueOf(60)) >= 0);
            }
            if (item.getAiReviewFeedback() != null) {
                submission.setAiReviewFeedback(item.getAiReviewFeedback());
            }
            if (item.getReviewStatus() != null) {
                try {
                    submission.setReviewStatus(AssessmentSubmission.ReviewStatus.valueOf(item.getReviewStatus()));
                } catch (IllegalArgumentException ignored) { }
            }
            submission.setGradedAt(java.time.OffsetDateTime.now());
            toSave.add(submission);
        }
        
        if (!toSave.isEmpty()) {
            submissionRepository.saveAll(toSave);
        }
        
        return new BulkGradeResponse(toSave.size(), errors);
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
        dto.setMarkedAsCheating(submission.getMarkedAsCheating());
        
        return dto;
    }
}


