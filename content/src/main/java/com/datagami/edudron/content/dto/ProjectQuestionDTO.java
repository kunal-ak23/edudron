package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.ProjectQuestionBank;

import java.time.OffsetDateTime;
import java.util.List;

public class ProjectQuestionDTO {
    private String id;
    private String courseId;
    private String title;
    private String problemStatement;
    private List<String> keyTechnologies;
    private List<String> tags;
    private String difficulty;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ProjectQuestionDTO() {}

    public static ProjectQuestionDTO fromEntity(ProjectQuestionBank e) {
        ProjectQuestionDTO dto = new ProjectQuestionDTO();
        dto.setId(e.getId());
        dto.setCourseId(e.getCourseId());
        dto.setTitle(e.getTitle());
        dto.setProblemStatement(e.getProblemStatement());
        dto.setKeyTechnologies(e.getKeyTechnologies());
        dto.setTags(e.getTags());
        dto.setDifficulty(e.getDifficulty());
        dto.setIsActive(e.getIsActive());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getProblemStatement() { return problemStatement; }
    public void setProblemStatement(String problemStatement) { this.problemStatement = problemStatement; }

    public List<String> getKeyTechnologies() { return keyTechnologies; }
    public void setKeyTechnologies(List<String> keyTechnologies) { this.keyTechnologies = keyTechnologies; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
