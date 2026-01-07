package com.datagami.edudron.content.dto;

import java.util.List;

public class CourseRequirements {
    private String title;
    private String description;
    private Integer numberOfModules;
    private Integer lecturesPerModule;
    private String difficultyLevel;
    private String language;
    private String categoryId;
    private List<String> tags;
    private Integer estimatedDurationMinutes;
    private Boolean certificateEligible;
    private Integer maxCompletionDays;
    
    // Getters and Setters
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
    
    public Integer getNumberOfModules() {
        return numberOfModules;
    }
    
    public void setNumberOfModules(Integer numberOfModules) {
        this.numberOfModules = numberOfModules;
    }
    
    public Integer getLecturesPerModule() {
        return lecturesPerModule;
    }
    
    public void setLecturesPerModule(Integer lecturesPerModule) {
        this.lecturesPerModule = lecturesPerModule;
    }
    
    public String getDifficultyLevel() {
        return difficultyLevel;
    }
    
    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getCategoryId() {
        return categoryId;
    }
    
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }
    
    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }
    
    public Boolean getCertificateEligible() {
        return certificateEligible;
    }
    
    public void setCertificateEligible(Boolean certificateEligible) {
        this.certificateEligible = certificateEligible;
    }
    
    public Integer getMaxCompletionDays() {
        return maxCompletionDays;
    }
    
    public void setMaxCompletionDays(Integer maxCompletionDays) {
        this.maxCompletionDays = maxCompletionDays;
    }
}


