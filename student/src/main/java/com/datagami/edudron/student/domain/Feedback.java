package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback", schema = "student")
public class Feedback {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String lectureId;

    @Column(nullable = false)
    private String courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackType type; // LIKE, DISLIKE

    @Column(columnDefinition = "text")
    private String comment; // Optional comment

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public enum FeedbackType {
        LIKE, DISLIKE
    }

    // Constructors
    public Feedback() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

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

    public FeedbackType getType() { return type; }
    public void setType(FeedbackType type) { this.type = type; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

