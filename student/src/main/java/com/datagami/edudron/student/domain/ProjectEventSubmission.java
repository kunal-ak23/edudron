package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_event_submission", schema = "student")
public class ProjectEventSubmission {
    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "submission_url", columnDefinition = "text")
    private String submissionUrl;

    @Column(name = "submission_text", columnDefinition = "text")
    private String submissionText;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum SubmissionStatus {
        PENDING, SUBMITTED, REVIEWED, NEEDS_REVISION, APPROVED
    }

    public ProjectEventSubmission() {
        this.submittedAt = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = com.datagami.edudron.common.UlidGenerator.nextUlid();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // ALL getters and setters for every field
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getSubmissionUrl() { return submissionUrl; }
    public void setSubmissionUrl(String submissionUrl) { this.submissionUrl = submissionUrl; }
    public String getSubmissionText() { return submissionText; }
    public void setSubmissionText(String submissionText) { this.submissionText = submissionText; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
