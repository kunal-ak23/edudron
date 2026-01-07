package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.SubLesson;
import java.time.OffsetDateTime;
import java.util.UUID;

public class SubLessonDTO {
    private String id;
    private UUID clientId;
    private String lectureId;
    private String title;
    private String description;
    private SubLesson.ContentType contentType;
    private Integer sequence;
    private Integer durationSeconds;
    private String fileUrl;
    private Long fileSizeBytes;
    private String mimeType;
    private String textContent;
    private String externalUrl;
    private String embeddedCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public SubLesson.ContentType getContentType() { return contentType; }
    public void setContentType(SubLesson.ContentType contentType) { this.contentType = contentType; }
    
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
    
    public String getEmbeddedCode() { return embeddedCode; }
    public void setEmbeddedCode(String embeddedCode) { this.embeddedCode = embeddedCode; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}


