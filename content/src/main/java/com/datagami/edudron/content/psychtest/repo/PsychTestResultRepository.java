package com.datagami.edudron.content.psychtest.repo;

import com.datagami.edudron.content.psychtest.domain.PsychTestResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PsychTestResultRepository extends JpaRepository<PsychTestResult, String> {
    @Query("""
        select r from PsychTestResult r
        join fetch r.session s
        where s.id = :sessionId and s.clientId = :clientId
    """)
    Optional<PsychTestResult> findBySessionIdAndClientId(@Param("sessionId") String sessionId, @Param("clientId") UUID clientId);
}

