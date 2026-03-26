package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.Project;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProjectDTO {
    private String id;
    private UUID clientId;
    private String courseId;
    private String sectionId;
    private String title;
    private String description;
    private Integer maxMarks;
    private OffsetDateTime submissionCutoff;
    private Boolean lateSubmissionAllowed;
    private String status;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ProjectAttachmentDTO> statementAttachments = new ArrayList<>();

    public ProjectDTO() {}

    public static ProjectDTO fromEntity(Project p) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(p.getId());
        dto.setClientId(p.getClientId());
        dto.setCourseId(p.getCourseId());
        dto.setSectionId(p.getSectionId());
        dto.setTitle(p.getTitle());
        dto.setDescription(p.getDescription());
        dto.setMaxMarks(p.getMaxMarks());
        dto.setSubmissionCutoff(p.getSubmissionCutoff());
        dto.setLateSubmissionAllowed(p.getLateSubmissionAllowed());
        dto.setStatus(p.getStatus() != null ? p.getStatus().name() : null);
        dto.setCreatedBy(p.getCreatedBy());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Integer maxMarks) { this.maxMarks = maxMarks; }

    public OffsetDateTime getSubmissionCutoff() { return submissionCutoff; }
    public void setSubmissionCutoff(OffsetDateTime submissionCutoff) { this.submissionCutoff = submissionCutoff; }

    public Boolean getLateSubmissionAllowed() { return lateSubmissionAllowed; }
    public void setLateSubmissionAllowed(Boolean lateSubmissionAllowed) { this.lateSubmissionAllowed = lateSubmissionAllowed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<ProjectAttachmentDTO> getStatementAttachments() { return statementAttachments; }
    public void setStatementAttachments(List<ProjectAttachmentDTO> statementAttachments) { this.statementAttachments = statementAttachments; }
}
