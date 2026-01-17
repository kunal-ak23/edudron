package com.datagami.edudron.content.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "psychometric_test_sessions", schema = "content")
public class PsychometricTestSession {
    @Id
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "student_id", nullable = false, length = 26)
    private String studentId; // ULID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 30)
    private Phase currentPhase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "identified_fields", columnDefinition = "jsonb")
    private JsonNode identifiedFields; // Array of {field: string, score: number}

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversation_history", columnDefinition = "jsonb")
    private JsonNode conversationHistory; // Array of {role: string, content: string, timestamp: string}

    @Column(name = "payment_id", length = 26)
    private String paymentId; // ULID, nullable

    @Column(name = "payment_required", nullable = false)
    private Boolean paymentRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum Status {
        IN_PROGRESS, COMPLETED, ABANDONED
    }

    public enum Phase {
        INITIAL_EXPLORATION, FIELD_DEEP_DIVE, COMPLETED
    }

    public enum PaymentStatus {
        NOT_REQUIRED, PENDING, COMPLETED
    }

    // Constructors
    public PsychometricTestSession() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.status = Status.IN_PROGRESS;
        this.currentPhase = Phase.INITIAL_EXPLORATION;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Phase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(Phase currentPhase) { this.currentPhase = currentPhase; }

    public JsonNode getIdentifiedFields() { return identifiedFields; }
    public void setIdentifiedFields(JsonNode identifiedFields) { this.identifiedFields = identifiedFields; }

    public JsonNode getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(JsonNode conversationHistory) { this.conversationHistory = conversationHistory; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public Boolean getPaymentRequired() { return paymentRequired; }
    public void setPaymentRequired(Boolean paymentRequired) { this.paymentRequired = paymentRequired; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
