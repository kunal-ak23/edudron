package com.datagami.edudron.content.dto;

import java.util.UUID;

public class CourseCopyJobData {
    
    private String sourceCourseId;
    private UUID sourceClientId;
    private UUID targetClientId;
    private String newCourseTitle;
    private Boolean copyPublishedState;
    
    // Progress tracking (optional)
    private String currentStep;
    private Integer totalSteps = 11;
    private Integer completedSteps = 0;
    
    // Getters and setters
    public String getSourceCourseId() {
        return sourceCourseId;
    }
    
    public void setSourceCourseId(String sourceCourseId) {
        this.sourceCourseId = sourceCourseId;
    }
    
    public UUID getSourceClientId() {
        return sourceClientId;
    }
    
    public void setSourceClientId(UUID sourceClientId) {
        this.sourceClientId = sourceClientId;
    }
    
    public UUID getTargetClientId() {
        return targetClientId;
    }
    
    public void setTargetClientId(UUID targetClientId) {
        this.targetClientId = targetClientId;
    }
    
    public String getNewCourseTitle() {
        return newCourseTitle;
    }
    
    public void setNewCourseTitle(String newCourseTitle) {
        this.newCourseTitle = newCourseTitle;
    }
    
    public Boolean getCopyPublishedState() {
        return copyPublishedState;
    }
    
    public void setCopyPublishedState(Boolean copyPublishedState) {
        this.copyPublishedState = copyPublishedState;
    }
    
    public String getCurrentStep() {
        return currentStep;
    }
    
    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }
    
    public Integer getTotalSteps() {
        return totalSteps;
    }
    
    public void setTotalSteps(Integer totalSteps) {
        this.totalSteps = totalSteps;
    }
    
    public Integer getCompletedSteps() {
        return completedSteps;
    }
    
    public void setCompletedSteps(Integer completedSteps) {
        this.completedSteps = completedSteps;
    }
}
