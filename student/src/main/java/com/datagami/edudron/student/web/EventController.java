package com.datagami.edudron.student.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.domain.Event;
import com.datagami.edudron.student.repo.CommonEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Controller for querying events.
 * Useful for debugging, analytics, and auditing.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {
    
    private final CommonEventRepository eventRepository;
    
    public EventController(CommonEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }
    
    /**
     * Get all events for the current client, paginated.
     */
    @GetMapping
    public ResponseEntity<Page<Event>> getAllEvents(
            @PageableDefault(size = 50) Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        Page<Event> events = eventRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get events by type.
     */
    @GetMapping("/type/{eventType}")
    public ResponseEntity<Page<Event>> getEventsByType(
            @PathVariable String eventType,
            @PageableDefault(size = 50) Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        Page<Event> events = eventRepository.findByClientIdAndEventTypeOrderByCreatedAtDesc(
            clientId, eventType, pageable);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get events by user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<Event>> getEventsByUser(
            @PathVariable String userId,
            @PageableDefault(size = 50) Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        Page<Event> events = eventRepository.findByClientIdAndUserIdOrderByCreatedAtDesc(
            clientId, userId, pageable);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get events by trace ID (for request correlation).
     */
    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<Event>> getEventsByTraceId(@PathVariable String traceId) {
        List<Event> events = eventRepository.findByTraceIdOrderByCreatedAtAsc(traceId);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get events in a time range.
     */
    @GetMapping("/range")
    public ResponseEntity<Page<Event>> getEventsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @PageableDefault(size = 50) Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        Page<Event> events = eventRepository.findEventsByClientAndTimeRange(
            clientId, startTime, endTime, pageable);
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get error events.
     */
    @GetMapping("/errors")
    public ResponseEntity<Page<Event>> getErrorEvents(
            @PageableDefault(size = 50) Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        Page<Event> events = eventRepository.findErrorEvents(clientId, pageable);
        return ResponseEntity.ok(events);
    }
}
