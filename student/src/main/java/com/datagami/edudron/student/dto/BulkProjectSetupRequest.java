package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

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
    private Boolean mixSections = false;
    private Map<String, String> sectionNames; // sectionId -> sectionName
    private Map<String, Integer> sectionGroupCounts; // sectionId -> desired number of groups
    private Integer totalGroupCount; // for mixed mode: desired total groups
    private List<String> selectedQuestionIds;
    private List<EventInput> events;
    private Map<String, List<EventInput>> eventsBySectionId;
    private List<SubmitProjectRequest.AttachmentInfo> statementAttachments;

    public static class EventInput {
        private String name;
        private String dateTime;
        private String zoomLink;
        private Boolean hasMarks;
        private Integer maxMarks;
        private Integer sequence;

        public EventInput() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDateTime() { return dateTime; }
        public void setDateTime(String dateTime) { this.dateTime = dateTime; }
        public String getZoomLink() { return zoomLink; }
        public void setZoomLink(String zoomLink) { this.zoomLink = zoomLink; }
        public Boolean getHasMarks() { return hasMarks; }
        public void setHasMarks(Boolean hasMarks) { this.hasMarks = hasMarks; }
        public Integer getMaxMarks() { return maxMarks; }
        public void setMaxMarks(Integer maxMarks) { this.maxMarks = maxMarks; }
        public Integer getSequence() { return sequence; }
        public void setSequence(Integer sequence) { this.sequence = sequence; }
    }

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

    public Map<String, Integer> getSectionGroupCounts() { return sectionGroupCounts; }
    public void setSectionGroupCounts(Map<String, Integer> sectionGroupCounts) { this.sectionGroupCounts = sectionGroupCounts; }

    public Integer getTotalGroupCount() { return totalGroupCount; }
    public void setTotalGroupCount(Integer totalGroupCount) { this.totalGroupCount = totalGroupCount; }

    public Map<String, String> getSectionNames() { return sectionNames; }
    public void setSectionNames(Map<String, String> sectionNames) { this.sectionNames = sectionNames; }

    public Boolean getMixSections() { return mixSections; }
    public void setMixSections(Boolean mixSections) { this.mixSections = mixSections; }

    public List<String> getSelectedQuestionIds() { return selectedQuestionIds; }
    public void setSelectedQuestionIds(List<String> selectedQuestionIds) { this.selectedQuestionIds = selectedQuestionIds; }

    public List<EventInput> getEvents() { return events; }
    public void setEvents(List<EventInput> events) { this.events = events; }

    public Map<String, List<EventInput>> getEventsBySectionId() { return eventsBySectionId; }
    public void setEventsBySectionId(Map<String, List<EventInput>> eventsBySectionId) { this.eventsBySectionId = eventsBySectionId; }

    public List<SubmitProjectRequest.AttachmentInfo> getStatementAttachments() { return statementAttachments; }
    public void setStatementAttachments(List<SubmitProjectRequest.AttachmentInfo> statementAttachments) { this.statementAttachments = statementAttachments; }
}
