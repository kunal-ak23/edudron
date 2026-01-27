package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProctoringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProctoringEventRepository extends JpaRepository<ProctoringEvent, String> {
    
    List<ProctoringEvent> findByClientIdAndSubmissionIdOrderByCreatedAtAsc(UUID clientId, String submissionId);
    
    List<ProctoringEvent> findByClientIdAndSubmissionIdAndSeverityOrderByCreatedAtAsc(
        UUID clientId, String submissionId, ProctoringEvent.Severity severity);
    
    @Query("SELECT COUNT(e) FROM ProctoringEvent e WHERE e.clientId = :clientId AND e.submissionId = :submissionId AND e.severity = :severity")
    long countByClientIdAndSubmissionIdAndSeverity(
        @Param("clientId") UUID clientId, 
        @Param("submissionId") String submissionId, 
        @Param("severity") ProctoringEvent.Severity severity);
    
    @Query("SELECT COUNT(e) FROM ProctoringEvent e WHERE e.clientId = :clientId AND e.submissionId = :submissionId AND e.eventType = :eventType")
    long countByClientIdAndSubmissionIdAndEventType(
        @Param("clientId") UUID clientId, 
        @Param("submissionId") String submissionId, 
        @Param("eventType") ProctoringEvent.EventType eventType);
}
