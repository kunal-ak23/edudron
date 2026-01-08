package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.Feedback;
import java.time.OffsetDateTime;
import java.util.UUID;

public class FeedbackDTO {
    private String id;
    private UUID clientId;
    private String studentId;
    private String lectureId;
    private String courseId;
    private Feedback.FeedbackType type;
    private String comment;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Constructors
    public FeedbackDTO() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public Feedback.FeedbackType getType() { return type; }
    public void setType(Feedback.FeedbackType type) { this.type = type; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

