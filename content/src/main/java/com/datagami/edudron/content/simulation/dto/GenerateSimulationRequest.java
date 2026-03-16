package com.datagami.edudron.content.simulation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class GenerateSimulationRequest {
    @NotBlank
    private String concept;

    @NotBlank
    private String subject;

    @NotBlank
    private String audience; // UNDERGRADUATE, MBA, GRADUATE

    private String courseId;
    private String lectureId;
    private String description;
    @Min(10) @Max(30)
    private Integer targetDepth; // default 15, range 10-30
    @Min(2) @Max(4)
    private Integer choicesPerNode; // default 3, range 2-4

    // Getters and Setters
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getTargetDepth() { return targetDepth; }
    public void setTargetDepth(Integer targetDepth) { this.targetDepth = targetDepth; }

    public Integer getChoicesPerNode() { return choicesPerNode; }
    public void setChoicesPerNode(Integer choicesPerNode) { this.choicesPerNode = choicesPerNode; }
}
