package com.datagami.edudron.content.psychtest.repo;

import com.datagami.edudron.content.psychtest.domain.PsychTestQuestionAsked;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface PsychTestQuestionAskedRepository extends JpaRepository<PsychTestQuestionAsked, String> {
    @Query("""
        select a from PsychTestQuestionAsked a
        join fetch a.session s
        join fetch a.question q
        where s.id = :sessionId and s.clientId = :clientId and a.questionNumber = :questionNumber
    """)
    Optional<PsychTestQuestionAsked> findBySessionIdAndClientIdAndQuestionNumber(
        @Param("sessionId") String sessionId,
        @Param("clientId") UUID clientId,
        @Param("questionNumber") Integer questionNumber
    );

    @Modifying
    @Query(value = """
        insert into content.psych_test_question_asked
          (id, session_id, question_id, question_number, asked_at, rendered_prompt, rendered_options_json, personalization_source, personalization_json)
        values
          (:id, :sessionId, :questionId, :questionNumber, now(), :renderedPrompt,
           cast(:renderedOptionsJson as jsonb), :personalizationSource, cast(:personalizationJson as jsonb))
        on conflict (session_id, question_number) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("id") String id,
        @Param("sessionId") String sessionId,
        @Param("questionId") String questionId,
        @Param("questionNumber") Integer questionNumber,
        @Param("renderedPrompt") String renderedPrompt,
        @Param("renderedOptionsJson") String renderedOptionsJson,
        @Param("personalizationSource") String personalizationSource,
        @Param("personalizationJson") String personalizationJson
    );
}

