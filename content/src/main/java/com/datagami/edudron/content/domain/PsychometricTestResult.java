package com.datagami.edudron.content.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "psychometric_test_results", schema = "content")
public class PsychometricTestResult {
    @Id
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "student_id", nullable = false, length = 26)
    private String studentId; // ULID

    @Column(name = "session_id", nullable = false, length = 26)
    private String sessionId; // ULID, FK to PsychometricTestSession

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_scores", columnDefinition = "jsonb")
    private JsonNode fieldScores; // Object: {field: score}

    @Column(name = "primary_field", columnDefinition = "text")
    private String primaryField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_fields", columnDefinition = "jsonb")
    private JsonNode secondaryFields; // Array of field names

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode recommendations; // Array of recommendation objects

    @Column(name = "test_summary", columnDefinition = "text")
    private String testSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public PsychometricTestResult() {
        this.createdAt = OffsetDateTime.now();
    }

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
