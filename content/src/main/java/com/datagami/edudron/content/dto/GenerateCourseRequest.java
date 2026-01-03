package com.datagami.edudron.content.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class GenerateCourseRequest {
    @NotBlank(message = "Prompt is required")
    private String prompt;
    
    // Optional overrides
    private String categoryId;
    private String difficultyLevel;
    private String language;
    private List<String> tags;
    private Boolean certificateEligible;
    private Integer maxCompletionDays;
    
    // Index content IDs to use as reference
    private List<String> referenceIndexIds;
    
    // Writing format ID or direct writing format text
    private String writingFormatId;
    private String writingFormat;
    
    // Getters and Setters
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getCategoryId() {
        return categoryId;
    }
    
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
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
    
    public List<String> getReferenceIndexIds() {
        return referenceIndexIds;
    }
    
    public void setReferenceIndexIds(List<String> referenceIndexIds) {
        this.referenceIndexIds = referenceIndexIds;
    }
    
    public String getWritingFormatId() {
        return writingFormatId;
    }
    
    public void setWritingFormatId(String writingFormatId) {
        this.writingFormatId = writingFormatId;
    }
    
    public String getWritingFormat() {
        return writingFormat;
    }
    
    public void setWritingFormat(String writingFormat) {
        this.writingFormat = writingFormat;
    }
}

