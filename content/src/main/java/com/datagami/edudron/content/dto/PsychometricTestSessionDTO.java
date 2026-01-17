package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.PsychometricTestSession;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PsychometricTestSessionDTO {
    private String id;
    private UUID clientId;
    private String studentId; // ULID
    private PsychometricTestSession.Status status;
    private PsychometricTestSession.Phase currentPhase;
    private JsonNode identifiedFields;
    private JsonNode conversationHistory;
    private String paymentId;
    private Boolean paymentRequired;
    private PsychometricTestSession.PaymentStatus paymentStatus;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public PsychometricTestSession.Status getStatus() { return status; }
    public void setStatus(PsychometricTestSession.Status status) { this.status = status; }
    
    public PsychometricTestSession.Phase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(PsychometricTestSession.Phase currentPhase) { this.currentPhase = currentPhase; }
    
    public JsonNode getIdentifiedFields() { return identifiedFields; }
    public void setIdentifiedFields(JsonNode identifiedFields) { this.identifiedFields = identifiedFields; }
    
    public JsonNode getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(JsonNode conversationHistory) { this.conversationHistory = conversationHistory; }
    
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public Boolean getPaymentRequired() { return paymentRequired; }
    public void setPaymentRequired(Boolean paymentRequired) { this.paymentRequired = paymentRequired; }
    
    public PsychometricTestSession.PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PsychometricTestSession.PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
