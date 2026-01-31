package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.QuestionBank;
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
public interface QuestionBankRepository extends JpaRepository<QuestionBank, String> {
    
    /**
     * Find questions that contain a specific module ID in their moduleIds array
     * Uses native query with PostgreSQL array contains operator
     */
    @Query(value = "SELECT * FROM content.question_bank WHERE :moduleId = ANY(module_ids) AND client_id = :clientId AND is_active = true ORDER BY created_at DESC", nativeQuery = true)
    List<QuestionBank> findByModuleIdContainedAndClientIdAndIsActiveTrue(
        @Param("moduleId") String moduleId, @Param("clientId") UUID clientId);
    
    /**
     * Find questions by course ID
     */
    List<QuestionBank> findByCourseIdAndClientIdAndIsActiveTrueOrderByCreatedAtDesc(
        String courseId, UUID clientId);
    
    /**
     * Find questions by course ID with pagination
     */
    Page<QuestionBank> findByCourseIdAndClientIdAndIsActiveTrue(
        String courseId, UUID clientId, Pageable pageable);
    
    /**
     * Find questions that contain a specific module ID and have a specific difficulty
     */
    @Query(value = "SELECT * FROM content.question_bank WHERE :moduleId = ANY(module_ids) AND difficulty_level = :difficulty AND client_id = :clientId AND is_active = true ORDER BY created_at DESC", nativeQuery = true)
    List<QuestionBank> findByModuleIdContainedAndDifficultyLevelAndClientIdAndIsActiveTrue(
        @Param("moduleId") String moduleId, @Param("difficulty") String difficulty, @Param("clientId") UUID clientId);
    
    /**
     * Find questions by course and difficulty level
     */
    List<QuestionBank> findByCourseIdAndDifficultyLevelAndClientIdAndIsActiveTrueOrderByCreatedAtDesc(
        String courseId, QuestionBank.DifficultyLevel difficultyLevel, UUID clientId);
    
    /**
     * Find a single question by ID and client
     */
    Optional<QuestionBank> findByIdAndClientId(String id, UUID clientId);
    
    /**
     * Find question with options eagerly loaded
     */
    @Query("SELECT DISTINCT q FROM QuestionBank q LEFT JOIN FETCH q.options WHERE q.id = :id AND q.clientId = :clientId")
    Optional<QuestionBank> findByIdAndClientIdWithOptions(@Param("id") String id, @Param("clientId") UUID clientId);
    
    /**
     * Find questions that contain a specific module ID with options eagerly loaded
     */
    @Query(value = "SELECT DISTINCT q.* FROM content.question_bank q WHERE :moduleId = ANY(q.module_ids) AND q.client_id = :clientId AND q.is_active = true ORDER BY q.created_at DESC", nativeQuery = true)
    List<QuestionBank> findByModuleIdContainedAndClientIdWithOptionsNative(@Param("moduleId") String moduleId, @Param("clientId") UUID clientId);
    
    /**
     * Find questions where moduleIds array overlaps with the given list of module IDs
     * Uses native query with PostgreSQL array overlap operator (&&)
     */
    @Query(value = "SELECT DISTINCT q.* FROM content.question_bank q WHERE q.module_ids && CAST(:moduleIds AS text[]) AND q.client_id = :clientId AND q.is_active = true ORDER BY q.created_at DESC", nativeQuery = true)
    List<QuestionBank> findByModuleIdsOverlapAndClientIdWithOptionsNative(@Param("moduleIds") String moduleIds, @Param("clientId") UUID clientId);
    
    /**
     * Find questions that contain a specific module ID and question type
     */
    @Query(value = "SELECT * FROM content.question_bank WHERE :moduleId = ANY(module_ids) AND question_type = :questionType AND client_id = :clientId AND is_active = true ORDER BY created_at DESC", nativeQuery = true)
    List<QuestionBank> findByModuleIdContainedAndQuestionTypeAndClientIdAndIsActiveTrue(
        @Param("moduleId") String moduleId, @Param("questionType") String questionType, @Param("clientId") UUID clientId);
    
    /**
     * Count questions that contain a specific module ID
     */
    @Query(value = "SELECT COUNT(*) FROM content.question_bank WHERE :moduleId = ANY(module_ids) AND client_id = :clientId AND is_active = true", nativeQuery = true)
    long countByModuleIdContainedAndClientIdAndIsActiveTrue(@Param("moduleId") String moduleId, @Param("clientId") UUID clientId);
    
    /**
     * Count questions by course
     */
    long countByCourseIdAndClientIdAndIsActiveTrue(String courseId, UUID clientId);
    
    /**
     * Count questions that contain a specific module ID and difficulty
     */
    @Query(value = "SELECT COUNT(*) FROM content.question_bank WHERE :moduleId = ANY(module_ids) AND difficulty_level = :difficulty AND client_id = :clientId AND is_active = true", nativeQuery = true)
    long countByModuleIdContainedAndDifficultyLevelAndClientIdAndIsActiveTrue(
        @Param("moduleId") String moduleId, @Param("difficulty") String difficulty, @Param("clientId") UUID clientId);
    
    /**
     * Search questions by text (question text contains keyword)
     */
    @Query("SELECT q FROM QuestionBank q WHERE q.courseId = :courseId AND q.clientId = :clientId AND q.isActive = true AND LOWER(q.questionText) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY q.createdAt DESC")
    List<QuestionBank> searchByQuestionText(@Param("courseId") String courseId, @Param("clientId") UUID clientId, @Param("keyword") String keyword);
    
    /**
     * Find questions by sub-module (Lecture) ID
     */
    List<QuestionBank> findBySubModuleIdAndClientIdAndIsActiveTrueOrderByCreatedAtDesc(
        String subModuleId, UUID clientId);
    
    /**
     * Find all questions for a course with pagination (for table view)
     */
    @Query("SELECT q FROM QuestionBank q WHERE q.courseId = :courseId AND q.clientId = :clientId AND q.isActive = true ORDER BY q.createdAt DESC")
    Page<QuestionBank> findAllByCourseIdAndClientIdPaginated(@Param("courseId") String courseId, @Param("clientId") UUID clientId, Pageable pageable);
}
