package com.datagami.edudron.content.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CourseCopyRequest {
    
    @NotNull(message = "Target client ID is required")
    private UUID targetClientId;
    
    private String newCourseTitle;  // Optional, defaults to "Copy of {original}"
    
    private Boolean copyPublishedState = false;  // Should copied course be published?
    
    // Getters and setters
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
}
