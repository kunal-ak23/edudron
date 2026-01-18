package com.datagami.edudron.content.psychtest.repo;

import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PsychTestQuestionRepository extends JpaRepository<PsychTestQuestion, String> {
    @Query("""
        select q from PsychTestQuestion q
        where q.isActive = true
          and q.bankVersion = :bankVersion
    """)
    List<PsychTestQuestion> findActiveByBankVersion(@Param("bankVersion") String bankVersion);
}

