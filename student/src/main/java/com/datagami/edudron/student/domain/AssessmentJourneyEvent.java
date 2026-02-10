package com.datagami.edudron.student.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event recorded during the student assessment journey (from "Take test" to submit).
 * Used for timeline/flowchart visible to teachers and admins.
 */
@Entity
@Table(name = "assessment_journey_events", schema = "student")
public class AssessmentJourneyEvent {

    @Id
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "submission_id", length = 26)
    private String submissionId; // Nullable for events before submission exists

    @Column(name = "assessment_id", length = 26)
    private String assessmentId;

    @Column(name = "student_id", length = 26)
    private String studentId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "severity", length = 20)
    private String severity; // INFO, WARNING, VIOLATION

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AssessmentJourneyEvent() {
        this.createdAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public JsonNode getMetadata() { return metadata; }
    public void setMetadata(JsonNode metadata) { this.metadata = metadata; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
