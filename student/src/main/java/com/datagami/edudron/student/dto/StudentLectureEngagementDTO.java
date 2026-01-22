package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;

public class StudentLectureEngagementDTO {
    private String studentId;
    private String studentEmail;
    private Long totalSessions;
    private Integer totalDurationSeconds;
    private Integer averageSessionDurationSeconds;
    private OffsetDateTime firstViewAt;
    private OffsetDateTime lastViewAt;
    private Boolean isCompleted;
    private Integer completionPercentage;

    // Constructors
    public StudentLectureEngagementDTO() {}

    // Getters and Setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public Long getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Long totalSessions) { this.totalSessions = totalSessions; }

    public Integer getTotalDurationSeconds() { return totalDurationSeconds; }
    public void setTotalDurationSeconds(Integer totalDurationSeconds) { this.totalDurationSeconds = totalDurationSeconds; }

    public Integer getAverageSessionDurationSeconds() { return averageSessionDurationSeconds; }
    public void setAverageSessionDurationSeconds(Integer averageSessionDurationSeconds) { this.averageSessionDurationSeconds = averageSessionDurationSeconds; }

    public OffsetDateTime getFirstViewAt() { return firstViewAt; }
    public void setFirstViewAt(OffsetDateTime firstViewAt) { this.firstViewAt = firstViewAt; }

    public OffsetDateTime getLastViewAt() { return lastViewAt; }
    public void setLastViewAt(OffsetDateTime lastViewAt) { this.lastViewAt = lastViewAt; }

    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }

    public Integer getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(Integer completionPercentage) { this.completionPercentage = completionPercentage; }
}
