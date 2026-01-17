package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.PsychometricTestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PsychometricTestSessionRepository extends JpaRepository<PsychometricTestSession, String> {
    
    Optional<PsychometricTestSession> findByIdAndClientId(String id, UUID clientId);
    
    Optional<PsychometricTestSession> findByIdAndStudentIdAndClientId(String id, String studentId, UUID clientId);
    
    List<PsychometricTestSession> findByStudentIdAndClientIdOrderByCreatedAtDesc(String studentId, UUID clientId);
    
    @Query("SELECT s FROM PsychometricTestSession s WHERE s.studentId = :studentId AND s.clientId = :clientId AND s.status = :status ORDER BY s.createdAt DESC")
    List<PsychometricTestSession> findByStudentIdAndClientIdAndStatusOrderByCreatedAtDesc(
        @Param("studentId") String studentId,
        @Param("clientId") UUID clientId,
        @Param("status") PsychometricTestSession.Status status
    );
    
    Optional<PsychometricTestSession> findFirstByStudentIdAndClientIdAndStatusOrderByCreatedAtDesc(
        String studentId,
        UUID clientId,
        PsychometricTestSession.Status status
    );
}
