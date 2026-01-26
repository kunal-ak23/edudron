package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, String> {
    
    List<QuizQuestion> findByAssessmentIdAndClientIdOrderBySequenceAsc(String assessmentId, UUID clientId);
    
    @Query("SELECT DISTINCT q FROM QuizQuestion q LEFT JOIN FETCH q.options WHERE q.assessmentId = :assessmentId AND q.clientId = :clientId ORDER BY q.sequence ASC")
    List<QuizQuestion> findByAssessmentIdAndClientIdWithOptions(@Param("assessmentId") String assessmentId, @Param("clientId") UUID clientId);
    
    Optional<QuizQuestion> findByIdAndClientId(String id, UUID clientId);
    
    void deleteByAssessmentIdAndClientId(String assessmentId, UUID clientId);
}


