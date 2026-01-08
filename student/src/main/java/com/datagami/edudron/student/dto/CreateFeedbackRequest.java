package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.Feedback;

public class CreateFeedbackRequest {
    private String lectureId;
    private String courseId;
    private Feedback.FeedbackType type;
    private String comment; // Optional

    // Constructors
    public CreateFeedbackRequest() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public Feedback.FeedbackType getType() { return type; }
    public void setType(Feedback.FeedbackType type) { this.type = type; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

