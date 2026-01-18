package com.datagami.edudron.content.psychtest.repo;

import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PsychTestAnswerRepository extends JpaRepository<PsychTestAnswer, String> {
    @Query("""
        select a from PsychTestAnswer a
        join fetch a.question q
        where a.session.id = :sessionId
        order by a.answeredAt asc
    """)
    List<PsychTestAnswer> findBySessionIdOrdered(@Param("sessionId") String sessionId);

    @Query("""
        select a from PsychTestAnswer a
        where a.session.id = :sessionId and a.question.id = :questionId
    """)
    Optional<PsychTestAnswer> findBySessionIdAndQuestionId(@Param("sessionId") String sessionId, @Param("questionId") String questionId);
}

