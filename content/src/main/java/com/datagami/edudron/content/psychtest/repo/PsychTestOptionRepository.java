package com.datagami.edudron.content.psychtest.repo;

import com.datagami.edudron.content.psychtest.domain.PsychTestOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PsychTestOptionRepository extends JpaRepository<PsychTestOption, String> {
    @Query("""
        select o from PsychTestOption o
        join fetch o.question q
        where q.id = :questionId
    """)
    List<PsychTestOption> findByQuestionId(@Param("questionId") String questionId);
}

