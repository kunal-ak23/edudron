package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class LectureViewSessionDTO {
    private String id;
    private UUID clientId;
    private String enrollmentId;
    private String studentId;
    private String courseId;
    private String lectureId;
    private OffsetDateTime sessionStartedAt;
    private OffsetDateTime sessionEndedAt;
    private Integer durationSeconds;
    private BigDecimal progressAtStart;
    private BigDecimal progressAtEnd;
    private Boolean isCompletedInSession;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Constructors
    public LectureViewSessionDTO() {}

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
    public void setSessionEndedAt(OffsetDateTime sessionEndedAt) { this.sessionEndedAt = sessionEndedAt; }

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
}
