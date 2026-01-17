package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.PsychometricTestResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PsychometricTestResultDTO {
    private String id;
    private UUID clientId;
    private String studentId; // ULID
    private String sessionId;
    private JsonNode fieldScores;
    private String primaryField;
    private JsonNode secondaryFields;
    private JsonNode recommendations;
    private String testSummary;
    private OffsetDateTime createdAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public JsonNode getFieldScores() { return fieldScores; }
    public void setFieldScores(JsonNode fieldScores) { this.fieldScores = fieldScores; }
    
    public String getPrimaryField() { return primaryField; }
    public void setPrimaryField(String primaryField) { this.primaryField = primaryField; }
    
    public JsonNode getSecondaryFields() { return secondaryFields; }
    public void setSecondaryFields(JsonNode secondaryFields) { this.secondaryFields = secondaryFields; }
    
    public JsonNode getRecommendations() { return recommendations; }
    public void setRecommendations(JsonNode recommendations) { this.recommendations = recommendations; }
    
    public String getTestSummary() { return testSummary; }
    public void setTestSummary(String testSummary) { this.testSummary = testSummary; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
