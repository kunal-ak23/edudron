package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.AssessmentSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentSubmissionRepository extends JpaRepository<AssessmentSubmission, String> {
    
    List<AssessmentSubmission> findByClientIdAndStudentIdAndAssessmentId(UUID clientId, String studentId, String assessmentId);
    
    List<AssessmentSubmission> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
    
    Page<AssessmentSubmission> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId, Pageable pageable);
    
    List<AssessmentSubmission> findByClientIdAndEnrollmentId(UUID clientId, String enrollmentId);
    
    Optional<AssessmentSubmission> findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
        UUID clientId, String studentId, String assessmentId);
}


