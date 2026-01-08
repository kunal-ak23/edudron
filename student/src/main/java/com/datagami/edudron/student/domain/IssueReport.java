package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "issue_reports", schema = "student")
public class IssueReport {
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
    @Column(nullable = false, length = 50)
    private IssueType issueType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.OPEN;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public enum IssueType {
        CONTENT_ERROR,
        TECHNICAL_ISSUE,
        VIDEO_PLAYBACK,
        AUDIO_QUALITY,
        TRANSCRIPT_ERROR,
        OTHER
    }

    public enum IssueStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED
    }

    // Constructors
    public IssueReport() {
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

    public IssueType getIssueType() { return issueType; }
    public void setIssueType(IssueType issueType) { this.issueType = issueType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

