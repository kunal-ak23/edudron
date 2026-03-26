package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkProjectSetupRequest {

    @NotBlank(message = "Course ID is required")
    private String courseId;

    @NotEmpty(message = "At least one section must be selected")
    private List<String> sectionIds;

    @Min(value = 1, message = "Group size must be at least 1")
    private int groupSize;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private Integer maxMarks = 100;
    private OffsetDateTime submissionCutoff;
    private Boolean lateSubmissionAllowed = false;

    public BulkProjectSetupRequest() {}

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public List<String> getSectionIds() { return sectionIds; }
    public void setSectionIds(List<String> sectionIds) { this.sectionIds = sectionIds; }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

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
