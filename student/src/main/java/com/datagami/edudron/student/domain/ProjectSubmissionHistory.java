package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_submission_history", schema = "student")
public class ProjectSubmissionHistory {

    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "submission_url", nullable = false)
    private String submissionUrl;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "action", nullable = false)
    private String action; // SUBMIT or EDIT

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getSubmissionUrl() { return submissionUrl; }
    public void setSubmissionUrl(String submissionUrl) { this.submissionUrl = submissionUrl; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
