package com.datagami.edudron.content.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class LearningObjectiveDTO {
    private String id;
    private UUID clientId;
    private String courseId;
    private String objectiveText;
    private Integer sequence;
    private OffsetDateTime createdAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getObjectiveText() { return objectiveText; }
    public void setObjectiveText(String objectiveText) { this.objectiveText = objectiveText; }
    
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

