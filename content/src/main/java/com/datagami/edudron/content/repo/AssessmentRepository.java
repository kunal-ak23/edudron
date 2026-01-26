package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, String> {
    
    List<Assessment> findByCourseIdAndClientIdOrderBySequenceAsc(String courseId, UUID clientId);
    
    List<Assessment> findByLectureIdAndClientIdOrderBySequenceAsc(String lectureId, UUID clientId);
    
    Optional<Assessment> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT DISTINCT a FROM Assessment a LEFT JOIN FETCH a.questions WHERE a.id = :id AND a.clientId = :clientId")
    Optional<Assessment> findByIdAndClientIdWithQuestions(@Param("id") String id, @Param("clientId") UUID clientId);
    
    @Query("SELECT MAX(a.sequence) FROM Assessment a WHERE a.courseId = :courseId AND a.clientId = :clientId")
    Integer findMaxSequenceByCourseIdAndClientId(@Param("courseId") String courseId, @Param("clientId") UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
    
    void deleteByLectureIdAndClientId(String lectureId, UUID clientId);
    
    // Exam-specific queries
    List<Assessment> findByAssessmentTypeAndClientIdOrderByCreatedAtDesc(Assessment.AssessmentType assessmentType, UUID clientId);
    
    @Query("SELECT a FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.clientId = :clientId AND a.status = :status ORDER BY a.startTime ASC")
    List<Assessment> findByAssessmentTypeAndStatusAndClientIdOrderByStartTimeAsc(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("status") Assessment.ExamStatus status,
        @Param("clientId") UUID clientId
    );
    
    @Query("SELECT a FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.clientId = :clientId AND a.status IN :statuses ORDER BY a.startTime ASC")
    List<Assessment> findByAssessmentTypeAndStatusInAndClientIdOrderByStartTimeAsc(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("statuses") List<Assessment.ExamStatus> statuses,
        @Param("clientId") UUID clientId
    );
    
    // Query for scheduled tasks that need to work across all tenants
    @Query("SELECT a FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.status IN :statuses AND a.startTime IS NOT NULL AND a.endTime IS NOT NULL ORDER BY a.startTime ASC")
    List<Assessment> findByAssessmentTypeAndStatusInOrderByStartTimeAsc(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("statuses") List<Assessment.ExamStatus> statuses
    );
}


