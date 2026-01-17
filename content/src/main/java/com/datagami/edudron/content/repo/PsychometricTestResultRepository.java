package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.PsychometricTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PsychometricTestResultRepository extends JpaRepository<PsychometricTestResult, String> {
    
    Optional<PsychometricTestResult> findByIdAndClientId(String id, UUID clientId);
    
    Optional<PsychometricTestResult> findBySessionIdAndClientId(String sessionId, UUID clientId);
    
    List<PsychometricTestResult> findByStudentIdAndClientIdOrderByCreatedAtDesc(String studentId, UUID clientId);
}
