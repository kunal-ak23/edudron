package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.ProjectAttachment;

import java.time.OffsetDateTime;

public class ProjectAttachmentDTO {
    private String id;
    private String projectId;
    private String groupId;
    private String context;
    private String fileUrl;
    private String fileName;
    private Long fileSizeBytes;
    private String mimeType;
    private String uploadedBy;
    private OffsetDateTime createdAt;

    public ProjectAttachmentDTO() {}

    public static ProjectAttachmentDTO fromEntity(ProjectAttachment a) {
        ProjectAttachmentDTO dto = new ProjectAttachmentDTO();
        dto.setId(a.getId());
        dto.setProjectId(a.getProjectId());
        dto.setGroupId(a.getGroupId());
        dto.setContext(a.getContext() != null ? a.getContext().name() : null);
        dto.setFileUrl(a.getFileUrl());
        dto.setFileName(a.getFileName());
        dto.setFileSizeBytes(a.getFileSizeBytes());
        dto.setMimeType(a.getMimeType());
        dto.setUploadedBy(a.getUploadedBy());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
