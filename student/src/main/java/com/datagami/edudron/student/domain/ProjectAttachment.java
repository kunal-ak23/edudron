package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_attachment", schema = "student")
public class ProjectAttachment {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "group_id")
    private String groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 30)
    private AttachmentContext context = AttachmentContext.STATEMENT;

    @Column(name = "file_url", nullable = false, columnDefinition = "text")
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum AttachmentContext {
        STATEMENT, SUBMISSION
    }

    public ProjectAttachment() {
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = com.datagami.edudron.common.UlidGenerator.nextUlid();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public AttachmentContext getContext() { return context; }
    public void setContext(AttachmentContext context) { this.context = context; }

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
