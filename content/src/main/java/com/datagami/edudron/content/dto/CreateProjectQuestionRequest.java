package com.datagami.edudron.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateProjectQuestionRequest {

    @NotBlank(message = "Course ID is required")
    private String courseId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Problem statement is required")
    private String problemStatement;

    private String projectNumber;
    private List<String> keyTechnologies;
    private List<String> tags;
    private String difficulty;

    public CreateProjectQuestionRequest() {}

    // Getters and Setters
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

    public String getProjectNumber() { return projectNumber; }
    public void setProjectNumber(String projectNumber) { this.projectNumber = projectNumber; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
}
