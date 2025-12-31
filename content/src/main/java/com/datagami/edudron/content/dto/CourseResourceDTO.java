package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.CourseResource;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CourseResourceDTO {
    private String id;
    private UUID clientId;
    private String courseId;
    private CourseResource.ResourceType resourceType;
    private String title;
    private String description;
    private String fileUrl;
    private Long fileSizeBytes;
    private Boolean isDownloadable;
    private Integer downloadCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public CourseResource.ResourceType getResourceType() { return resourceType; }
    public void setResourceType(CourseResource.ResourceType resourceType) { this.resourceType = resourceType; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    
    public Boolean getIsDownloadable() { return isDownloadable; }
    public void setIsDownloadable(Boolean isDownloadable) { this.isDownloadable = isDownloadable; }
    
    public Integer getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Integer downloadCount) { this.downloadCount = downloadCount; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

