package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.QuestionBankOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionBankOptionRepository extends JpaRepository<QuestionBankOption, String> {
    
    /**
     * Find options by question ID
     */
    List<QuestionBankOption> findByQuestionIdAndClientIdOrderBySequenceAsc(String questionId, UUID clientId);
    
    /**
     * Find a single option by ID and client
     */
    Optional<QuestionBankOption> findByIdAndClientId(String id, UUID clientId);
    
    /**
     * Delete all options for a question
     */
    void deleteByQuestionIdAndClientId(String questionId, UUID clientId);
}
