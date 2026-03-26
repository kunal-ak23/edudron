package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "project_question_bank", schema = "content")
public class ProjectQuestionBank {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "problem_statement", nullable = false, columnDefinition = "text")
    private String problemStatement;

    @Column(name = "key_technologies", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> keyTechnologies = new ArrayList<>();

    @Column(name = "tags", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags = new ArrayList<>();

    @Column(name = "project_number", length = 50)
    private String projectNumber;

    @Column(name = "difficulty", length = 20)
    private String difficulty;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public ProjectQuestionBank() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getProblemStatement() { return problemStatement; }
    public void setProblemStatement(String problemStatement) { this.problemStatement = problemStatement; }

    public List<String> getKeyTechnologies() { return keyTechnologies; }
    public void setKeyTechnologies(List<String> keyTechnologies) { this.keyTechnologies = keyTechnologies != null ? keyTechnologies : new ArrayList<>(); }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

    public String getProjectNumber() { return projectNumber; }
    public void setProjectNumber(String projectNumber) { this.projectNumber = projectNumber; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
