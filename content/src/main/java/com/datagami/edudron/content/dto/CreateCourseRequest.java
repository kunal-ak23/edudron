package com.datagami.edudron.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CreateCourseRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    
    private String thumbnailUrl;
    private String previewVideoUrl;
    
    @NotNull(message = "isFree is required")
    private Boolean isFree = false;
    
    private Long pricePaise;
    private String currency = "INR";
    
    private String categoryId;
    private List<String> tags;
    private String difficultyLevel;
    private String language = "en";
    
    private Boolean certificateEligible = false;
    private Integer maxCompletionDays;
    
    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
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
    
    public Boolean getCertificateEligible() { return certificateEligible; }
    public void setCertificateEligible(Boolean certificateEligible) { this.certificateEligible = certificateEligible; }
    
    public Integer getMaxCompletionDays() { return maxCompletionDays; }
    public void setMaxCompletionDays(Integer maxCompletionDays) { this.maxCompletionDays = maxCompletionDays; }
}

