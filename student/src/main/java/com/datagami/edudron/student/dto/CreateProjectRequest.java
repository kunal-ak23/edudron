package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public class CreateProjectRequest {

    private String courseId;

    @NotBlank(message = "Section ID is required")
    private String sectionId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private Integer maxMarks;
    private OffsetDateTime submissionCutoff;
    private Boolean lateSubmissionAllowed;

    public CreateProjectRequest() {}

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Integer maxMarks) { this.maxMarks = maxMarks; }

    public OffsetDateTime getSubmissionCutoff() { return submissionCutoff; }
    public void setSubmissionCutoff(OffsetDateTime submissionCutoff) { this.submissionCutoff = submissionCutoff; }

    public Boolean getLateSubmissionAllowed() { return lateSubmissionAllowed; }
    public void setLateSubmissionAllowed(Boolean lateSubmissionAllowed) { this.lateSubmissionAllowed = lateSubmissionAllowed; }
}
