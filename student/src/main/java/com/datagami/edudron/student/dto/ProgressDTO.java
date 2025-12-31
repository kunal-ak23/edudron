package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ProgressDTO {
    private String id;
    private UUID clientId;
    private String enrollmentId;
    private String studentId;
    private String courseId;
    private String sectionId;
    private String lectureId;
    private Boolean isCompleted;
    private BigDecimal progressPercentage;
    private Integer timeSpentSeconds;
    private OffsetDateTime lastAccessedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Constructors
    public ProgressDTO() {}

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

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }

    public BigDecimal getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(BigDecimal progressPercentage) { this.progressPercentage = progressPercentage; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public OffsetDateTime getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(OffsetDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

