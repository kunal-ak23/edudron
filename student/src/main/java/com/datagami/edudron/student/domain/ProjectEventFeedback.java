package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_event_feedback", schema = "student")
public class ProjectEventFeedback {
    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "submission_id", nullable = false)
    private String submissionId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "comment", nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "feedback_by", nullable = false)
    private String feedbackBy;

    @Column(name = "feedback_at", nullable = false)
    private OffsetDateTime feedbackAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FeedbackStatus status = FeedbackStatus.REVIEWED;

    public enum FeedbackStatus {
        REVIEWED, NEEDS_REVISION
    }

    public ProjectEventFeedback() {
        this.feedbackAt = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = com.datagami.edudron.common.UlidGenerator.nextUlid();
        if (feedbackAt == null) feedbackAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
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
    public FeedbackStatus getStatus() { return status; }
    public void setStatus(FeedbackStatus status) { this.status = status; }
}
