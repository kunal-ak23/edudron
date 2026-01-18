package com.datagami.edudron.content.psychtest.repo;

import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PsychTestSessionRepository extends JpaRepository<PsychTestSession, String> {
    Optional<PsychTestSession> findFirstByClientIdAndUserIdAndStatusOrderByCreatedAtDesc(
        UUID clientId,
        String userId,
        PsychTestSession.Status status
    );

    Optional<PsychTestSession> findByIdAndClientId(String id, UUID clientId);
}

