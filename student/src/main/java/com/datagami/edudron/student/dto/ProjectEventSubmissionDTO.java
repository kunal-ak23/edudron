package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.ProjectEventSubmission;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProjectEventSubmissionDTO {
    private String id;
    private String projectId;
    private String eventId;
    private String groupId;
    private String submissionUrl;
    private String submissionText;
    private String submittedBy;
    private OffsetDateTime submittedAt;
    private Integer version;
    private String status;
    private List<ProjectAttachmentDTO> attachments = new ArrayList<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ProjectEventSubmissionDTO fromEntity(ProjectEventSubmission s) {
        ProjectEventSubmissionDTO dto = new ProjectEventSubmissionDTO();
        dto.setId(s.getId());
        dto.setProjectId(s.getProjectId());
        dto.setEventId(s.getEventId());
        dto.setGroupId(s.getGroupId());
        dto.setSubmissionUrl(s.getSubmissionUrl());
        dto.setSubmissionText(s.getSubmissionText());
        dto.setSubmittedBy(s.getSubmittedBy());
        dto.setSubmittedAt(s.getSubmittedAt());
        dto.setVersion(s.getVersion());
        dto.setStatus(s.getStatus() != null ? s.getStatus().name() : null);
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    // ALL getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ProjectAttachmentDTO> getAttachments() { return attachments; }
    public void setAttachments(List<ProjectAttachmentDTO> attachments) { this.attachments = attachments; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
