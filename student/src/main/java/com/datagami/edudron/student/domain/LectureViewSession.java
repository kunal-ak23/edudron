package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lecture_view_sessions", schema = "student")
public class LectureViewSession {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String enrollmentId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String courseId;

    @Column(nullable = false)
    private String lectureId;

    @Column(nullable = false)
    private OffsetDateTime sessionStartedAt;

    private OffsetDateTime sessionEndedAt;

    @Column(nullable = false)
    private Integer durationSeconds = 0;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal progressAtStart = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal progressAtEnd = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean isCompletedInSession = false;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public LectureViewSession() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.sessionStartedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public OffsetDateTime getSessionStartedAt() { return sessionStartedAt; }
    public void setSessionStartedAt(OffsetDateTime sessionStartedAt) { this.sessionStartedAt = sessionStartedAt; }

    public OffsetDateTime getSessionEndedAt() { return sessionEndedAt; }
    public void setSessionEndedAt(OffsetDateTime sessionEndedAt) { 
        this.sessionEndedAt = sessionEndedAt;
        // Calculate duration when session ends
        if (sessionEndedAt != null && sessionStartedAt != null) {
            this.durationSeconds = (int) java.time.Duration.between(sessionStartedAt, sessionEndedAt).getSeconds();
        }
    }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public BigDecimal getProgressAtStart() { return progressAtStart; }
    public void setProgressAtStart(BigDecimal progressAtStart) { this.progressAtStart = progressAtStart; }

    public BigDecimal getProgressAtEnd() { return progressAtEnd; }
    public void setProgressAtEnd(BigDecimal progressAtEnd) { this.progressAtEnd = progressAtEnd; }

    public Boolean getIsCompletedInSession() { return isCompletedInSession; }
    public void setIsCompletedInSession(Boolean isCompletedInSession) { this.isCompletedInSession = isCompletedInSession; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
