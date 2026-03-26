package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.ProjectEventFeedback;
import java.time.OffsetDateTime;

public class ProjectEventFeedbackDTO {
    private String id;
    private String submissionId;
    private String eventId;
    private String groupId;
    private String comment;
    private String feedbackBy;
    private OffsetDateTime feedbackAt;
    private String status;

    public static ProjectEventFeedbackDTO fromEntity(ProjectEventFeedback f) {
        ProjectEventFeedbackDTO dto = new ProjectEventFeedbackDTO();
        dto.setId(f.getId());
        dto.setSubmissionId(f.getSubmissionId());
        dto.setEventId(f.getEventId());
        dto.setGroupId(f.getGroupId());
        dto.setComment(f.getComment());
        dto.setFeedbackBy(f.getFeedbackBy());
        dto.setFeedbackAt(f.getFeedbackAt());
        dto.setStatus(f.getStatus() != null ? f.getStatus().name() : null);
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getFeedbackBy() { return feedbackBy; }
    public void setFeedbackBy(String feedbackBy) { this.feedbackBy = feedbackBy; }
    public OffsetDateTime getFeedbackAt() { return feedbackAt; }
    public void setFeedbackAt(OffsetDateTime feedbackAt) { this.feedbackAt = feedbackAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
