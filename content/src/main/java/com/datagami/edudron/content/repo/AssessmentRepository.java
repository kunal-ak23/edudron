package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.Assessment;
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
public interface AssessmentRepository extends JpaRepository<Assessment, String> {
    
    List<Assessment> findByCourseIdAndClientIdOrderBySequenceAsc(String courseId, UUID clientId);
    
    List<Assessment> findByLectureIdAndClientIdOrderBySequenceAsc(String lectureId, UUID clientId);
    
    Optional<Assessment> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT DISTINCT a FROM Assessment a LEFT JOIN FETCH a.questions WHERE a.id = :id AND a.clientId = :clientId")
    Optional<Assessment> findByIdAndClientIdWithQuestions(@Param("id") String id, @Param("clientId") UUID clientId);
    
    @Query("SELECT DISTINCT a FROM Assessment a LEFT JOIN FETCH a.examQuestions eq LEFT JOIN FETCH eq.question WHERE a.id = :id AND a.clientId = :clientId")
    Optional<Assessment> findByIdAndClientIdWithExamQuestions(@Param("id") String id, @Param("clientId") UUID clientId);
    
    @Query("SELECT MAX(a.sequence) FROM Assessment a WHERE a.courseId = :courseId AND a.clientId = :clientId")
    Integer findMaxSequenceByCourseIdAndClientId(@Param("courseId") String courseId, @Param("clientId") UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
    
    void deleteByLectureIdAndClientId(String lectureId, UUID clientId);
    
    // Exam-specific queries
    List<Assessment> findByAssessmentTypeAndClientIdOrderByCreatedAtDesc(Assessment.AssessmentType assessmentType, UUID clientId);
    
    // Exam queries excluding archived
    @Query("SELECT a FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.clientId = :clientId AND (a.archived = false OR a.archived IS NULL) ORDER BY a.createdAt DESC")
    List<Assessment> findActiveByAssessmentTypeAndClientIdOrderByCreatedAtDesc(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("clientId") UUID clientId
    );
    
    // Include archived exams when explicitly requested
    @Query("SELECT a FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.clientId = :clientId ORDER BY a.createdAt DESC")
    List<Assessment> findAllByAssessmentTypeAndClientIdIncludingArchivedOrderByCreatedAtDesc(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("clientId") UUID clientId
    );
    
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
    
    // Paginated exam query with filters
    @Query("SELECT a FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.clientId = :clientId " +
           "AND (a.archived = false OR a.archived IS NULL) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:timingMode IS NULL OR a.timingMode = :timingMode) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Assessment> findExamsWithFilters(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("clientId") UUID clientId,
        @Param("status") Assessment.ExamStatus status,
        @Param("timingMode") Assessment.TimingMode timingMode,
        @Param("search") String search,
        Pageable pageable
    );
    
    // Count exams by status for filter badges
    @Query("SELECT a.status, COUNT(a) FROM Assessment a WHERE a.assessmentType = :assessmentType AND a.clientId = :clientId " +
           "AND (a.archived = false OR a.archived IS NULL) GROUP BY a.status")
    List<Object[]> countExamsByStatus(
        @Param("assessmentType") Assessment.AssessmentType assessmentType,
        @Param("clientId") UUID clientId
    );
}


