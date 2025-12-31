package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.LectureContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public class LectureContentDTO {
    private String id;
    private UUID clientId;
    private String lectureId;
    private LectureContent.ContentType contentType;
    private String title;
    private String description;
    private String fileUrl;
    private Long fileSizeBytes;
    private String mimeType;
    private String videoUrl;
    private String transcriptUrl;
    private JsonNode subtitleUrls;
    private String thumbnailUrl;
    private String textContent;
    private String externalUrl;
    private String embeddedCode;
    private Integer sequence;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }
    
    public LectureContent.ContentType getContentType() { return contentType; }
    public void setContentType(LectureContent.ContentType contentType) { this.contentType = contentType; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    
    public String getTranscriptUrl() { return transcriptUrl; }
    public void setTranscriptUrl(String transcriptUrl) { this.transcriptUrl = transcriptUrl; }
    
    public JsonNode getSubtitleUrls() { return subtitleUrls; }
    public void setSubtitleUrls(JsonNode subtitleUrls) { this.subtitleUrls = subtitleUrls; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
    
    public String getEmbeddedCode() { return embeddedCode; }
    public void setEmbeddedCode(String embeddedCode) { this.embeddedCode = embeddedCode; }
    
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

