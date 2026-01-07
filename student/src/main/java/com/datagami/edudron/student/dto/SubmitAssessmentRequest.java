package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SubmitAssessmentRequest {
    @NotBlank(message = "Assessment ID is required")
    private String assessmentId;

    @NotNull(message = "Answers are required")
    private JsonNode answers;

    // Constructors
    public SubmitAssessmentRequest() {}

    // Getters and Setters
    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public JsonNode getAnswers() { return answers; }
    public void setAnswers(JsonNode answers) { this.answers = answers; }
}


