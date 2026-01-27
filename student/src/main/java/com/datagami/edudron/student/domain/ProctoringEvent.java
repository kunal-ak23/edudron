package com.datagami.edudron.student.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "proctoring_events", schema = "student")
public class ProctoringEvent {
    @Id
    private String id; // ULID
    
    @Column(nullable = false)
    private UUID clientId;
    
    @Column(name = "submission_id", nullable = false)
    private String submissionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    public enum EventType {
        TAB_SWITCH,
        WINDOW_BLUR,
        WINDOW_FOCUS,
        COPY_ATTEMPT,
        PASTE_ATTEMPT,
        PHOTO_CAPTURED,
        IDENTITY_VERIFIED,
        FULLSCREEN_EXIT,
        FULLSCREEN_ENTER,
        PROCTORING_VIOLATION,
        NO_FACE_DETECTED,
        MULTIPLE_FACES_DETECTED,
        RIGHT_CLICK_BLOCKED,
        KEYBOARD_SHORTCUT_BLOCKED
    }
    
    public enum Severity {
        INFO, WARNING, VIOLATION
    }
    
    // Constructors
    public ProctoringEvent() {
        this.createdAt = OffsetDateTime.now();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
    
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    
    public JsonNode getMetadata() { return metadata; }
    public void setMetadata(JsonNode metadata) { this.metadata = metadata; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
