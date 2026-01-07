package com.datagami.edudron.content.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class SectionDTO {
    private String id;
    private UUID clientId;
    private String courseId;
    private String title;
    private String description;
    private Integer sequence;
    private Boolean isPublished;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<LectureDTO> lectures;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<LectureDTO> getLectures() { return lectures; }
    public void setLectures(List<LectureDTO> lectures) { this.lectures = lectures; }
}


