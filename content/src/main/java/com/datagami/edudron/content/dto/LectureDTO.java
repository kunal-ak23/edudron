package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.Lecture;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class LectureDTO {
    private String id;
    private UUID clientId;
    private String sectionId;
    private String courseId;
    private String title;
    private String description;
    private Lecture.ContentType contentType;
    private Integer sequence;
    private Integer durationSeconds;
    private Boolean isPreview;
    private Boolean isPublished;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<LectureContentDTO> contents;
    private List<SubLessonDTO> subLessons;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Lecture.ContentType getContentType() { return contentType; }
    public void setContentType(Lecture.ContentType contentType) { this.contentType = contentType; }
    
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    
    public Boolean getIsPreview() { return isPreview; }
    public void setIsPreview(Boolean isPreview) { this.isPreview = isPreview; }
    
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<LectureContentDTO> getContents() { return contents; }
    public void setContents(List<LectureContentDTO> contents) { this.contents = contents; }
    
    public List<SubLessonDTO> getSubLessons() { return subLessons; }
    public void setSubLessons(List<SubLessonDTO> subLessons) { this.subLessons = subLessons; }
}


