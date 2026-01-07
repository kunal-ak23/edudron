package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.CourseGenerationIndex;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CourseGenerationIndexDTO {
    private String id;
    private UUID clientId;
    private String title;
    private String description;
    private CourseGenerationIndex.IndexType indexType;
    private String fileUrl;
    private Long fileSizeBytes;
    private String mimeType;
    private String extractedText;
    private String writingFormat;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CourseGenerationIndex.IndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(CourseGenerationIndex.IndexType indexType) {
        this.indexType = indexType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getWritingFormat() {
        return writingFormat;
    }

    public void setWritingFormat(String writingFormat) {
        this.writingFormat = writingFormat;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


