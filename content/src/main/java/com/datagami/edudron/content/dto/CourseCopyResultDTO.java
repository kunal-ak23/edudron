package com.datagami.edudron.content.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class CourseCopyResultDTO {
    
    private String newCourseId;
    private String sourceCourseId;
    private UUID targetClientId;
    private Map<String, Integer> copiedEntities;  // {"sections": 12, "lectures": 48, ...}
    private OffsetDateTime completedAt;
    private String duration;  // "5.2s" or "2m 15s"
    
    // Getters and setters
    public String getNewCourseId() {
        return newCourseId;
    }
    
    public void setNewCourseId(String newCourseId) {
        this.newCourseId = newCourseId;
    }
    
    public String getSourceCourseId() {
        return sourceCourseId;
    }
    
    public void setSourceCourseId(String sourceCourseId) {
        this.sourceCourseId = sourceCourseId;
    }
    
    public UUID getTargetClientId() {
        return targetClientId;
    }
    
    public void setTargetClientId(UUID targetClientId) {
        this.targetClientId = targetClientId;
    }
    
    public Map<String, Integer> getCopiedEntities() {
        return copiedEntities;
    }
    
    public void setCopiedEntities(Map<String, Integer> copiedEntities) {
        this.copiedEntities = copiedEntities;
    }
    
    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public String getDuration() {
        return duration;
    }
    
    public void setDuration(String duration) {
        this.duration = duration;
    }
}
