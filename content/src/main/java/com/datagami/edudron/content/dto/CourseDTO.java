package com.datagami.edudron.content.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class CourseDTO {
    private String id;
    private UUID clientId;
    private String title;
    private String description;
    private Boolean isPublished;
    private String thumbnailUrl;
    private String previewVideoUrl;
    private Boolean isFree;
    private Long pricePaise;
    private String currency;
    private String categoryId;
    private List<String> tags;
    private String difficultyLevel;
    private String language;
    private Integer totalDurationSeconds;
    private Integer totalLecturesCount;
    private Integer totalStudentsCount;
    private Boolean certificateEligible;
    private Integer maxCompletionDays;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime publishedAt;
    
    // Nested DTOs
    private List<SectionDTO> sections;
    private List<LearningObjectiveDTO> learningObjectives;
    private List<CourseInstructorDTO> instructors;
    private List<CourseResourceDTO> resources;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    public String getPreviewVideoUrl() { return previewVideoUrl; }
    public void setPreviewVideoUrl(String previewVideoUrl) { this.previewVideoUrl = previewVideoUrl; }
    
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
    
    public Long getPricePaise() { return pricePaise; }
    public void setPricePaise(Long pricePaise) { this.pricePaise = pricePaise; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public Integer getTotalDurationSeconds() { return totalDurationSeconds; }
    public void setTotalDurationSeconds(Integer totalDurationSeconds) { this.totalDurationSeconds = totalDurationSeconds; }
    
    public Integer getTotalLecturesCount() { return totalLecturesCount; }
    public void setTotalLecturesCount(Integer totalLecturesCount) { this.totalLecturesCount = totalLecturesCount; }
    
    public Integer getTotalStudentsCount() { return totalStudentsCount; }
    public void setTotalStudentsCount(Integer totalStudentsCount) { this.totalStudentsCount = totalStudentsCount; }
    
    public Boolean getCertificateEligible() { return certificateEligible; }
    public void setCertificateEligible(Boolean certificateEligible) { this.certificateEligible = certificateEligible; }
    
    public Integer getMaxCompletionDays() { return maxCompletionDays; }
    public void setMaxCompletionDays(Integer maxCompletionDays) { this.maxCompletionDays = maxCompletionDays; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    
    public List<SectionDTO> getSections() { return sections; }
    public void setSections(List<SectionDTO> sections) { this.sections = sections; }
    
    public List<LearningObjectiveDTO> getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(List<LearningObjectiveDTO> learningObjectives) { this.learningObjectives = learningObjectives; }
    
    public List<CourseInstructorDTO> getInstructors() { return instructors; }
    public void setInstructors(List<CourseInstructorDTO> instructors) { this.instructors = instructors; }
    
    public List<CourseResourceDTO> getResources() { return resources; }
    public void setResources(List<CourseResourceDTO> resources) { this.resources = resources; }
}

