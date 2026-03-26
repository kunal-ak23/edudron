package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public class EventFeedbackRequest {
    @NotBlank(message = "Comment is required")
    private String comment;

    @NotBlank(message = "Status is required")
    private String status; // REVIEWED or NEEDS_REVISION

    public EventFeedbackRequest() {}

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
