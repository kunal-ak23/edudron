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
    @Min(3) @Max(7)
    private Integer targetYears = 5; // default 5, range 3-7
    @Min(4) @Max(8)
    private Integer decisionsPerYear = 6; // default 6, range 4-8

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

    public Integer getTargetYears() { return targetYears; }
    public void setTargetYears(Integer targetYears) { this.targetYears = targetYears; }

    public Integer getDecisionsPerYear() { return decisionsPerYear; }
    public void setDecisionsPerYear(Integer decisionsPerYear) { this.decisionsPerYear = decisionsPerYear; }
}
