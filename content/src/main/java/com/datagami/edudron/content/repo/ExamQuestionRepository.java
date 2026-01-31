package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, String> {
    
    /**
     * Find all questions for an exam, ordered by sequence
     */
    List<ExamQuestion> findByExamIdAndClientIdOrderBySequenceAsc(String examId, UUID clientId);
    
    /**
     * Find questions with full question data loaded
     */
    @Query("SELECT DISTINCT eq FROM ExamQuestion eq " +
           "LEFT JOIN FETCH eq.question q " +
           "LEFT JOIN FETCH q.options " +
           "WHERE eq.examId = :examId AND eq.clientId = :clientId " +
           "ORDER BY eq.sequence ASC")
    List<ExamQuestion> findByExamIdAndClientIdWithQuestions(@Param("examId") String examId, @Param("clientId") UUID clientId);
    
    /**
     * Find a single exam question by ID and client
     */
    Optional<ExamQuestion> findByIdAndClientId(String id, UUID clientId);
    
    /**
     * Find by exam and question IDs
     */
    Optional<ExamQuestion> findByExamIdAndQuestionIdAndClientId(String examId, String questionId, UUID clientId);
    
    /**
     * Check if a question is already in an exam
     */
    boolean existsByExamIdAndQuestionIdAndClientId(String examId, String questionId, UUID clientId);
    
    /**
     * Count questions in an exam
     */
    long countByExamIdAndClientId(String examId, UUID clientId);
    
    /**
     * Get the maximum sequence number for an exam
     */
    @Query("SELECT MAX(eq.sequence) FROM ExamQuestion eq WHERE eq.examId = :examId AND eq.clientId = :clientId")
    Integer findMaxSequenceByExamIdAndClientId(@Param("examId") String examId, @Param("clientId") UUID clientId);
    
    /**
     * Delete all questions from an exam
     */
    void deleteByExamIdAndClientId(String examId, UUID clientId);
    
    /**
     * Delete a specific question from an exam
     */
    void deleteByExamIdAndQuestionIdAndClientId(String examId, String questionId, UUID clientId);
    
    /**
     * Update sequence numbers after a deletion
     */
    @Modifying
    @Query("UPDATE ExamQuestion eq SET eq.sequence = eq.sequence - 1 WHERE eq.examId = :examId AND eq.clientId = :clientId AND eq.sequence > :deletedSequence")
    void decrementSequencesAfter(@Param("examId") String examId, @Param("clientId") UUID clientId, @Param("deletedSequence") int deletedSequence);
    
    /**
     * Calculate total points for an exam
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN eq.pointsOverride IS NOT NULL THEN eq.pointsOverride ELSE q.defaultPoints END), 0) " +
           "FROM ExamQuestion eq JOIN eq.question q WHERE eq.examId = :examId AND eq.clientId = :clientId")
    Integer calculateTotalPoints(@Param("examId") String examId, @Param("clientId") UUID clientId);
}
