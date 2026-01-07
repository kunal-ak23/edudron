package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizOptionRepository extends JpaRepository<QuizOption, String> {
    
    List<QuizOption> findByQuestionIdAndClientIdOrderBySequenceAsc(String questionId, UUID clientId);
    
    Optional<QuizOption> findByIdAndClientId(String id, UUID clientId);
    
    void deleteByQuestionIdAndClientId(String questionId, UUID clientId);
}


