package com.datagami.edudron.student.repo;

import com.datagami.edudron.common.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommonEventRepository extends JpaRepository<Event, String> {
    
    // Find events by client
    Page<Event> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);
    
    // Find events by type
    Page<Event> findByClientIdAndEventTypeOrderByCreatedAtDesc(
        UUID clientId, String eventType, Pageable pageable);
    
    // Find events by user
    Page<Event> findByClientIdAndUserIdOrderByCreatedAtDesc(
        UUID clientId, String userId, Pageable pageable);
    
    // Find events by trace ID (for request correlation)
    List<Event> findByTraceIdOrderByCreatedAtAsc(String traceId);
    
    // Find events in time range
    @Query("SELECT e FROM Event e WHERE e.clientId = :clientId " +
           "AND e.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY e.createdAt DESC")
    Page<Event> findEventsByClientAndTimeRange(
        @Param("clientId") UUID clientId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime,
        Pageable pageable);
    
    // Find error events
    @Query("SELECT e FROM Event e WHERE e.clientId = :clientId " +
           "AND e.eventType = 'ERROR' " +
           "ORDER BY e.createdAt DESC")
    Page<Event> findErrorEvents(@Param("clientId") UUID clientId, Pageable pageable);
}
