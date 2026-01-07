package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

public class StudentProgressDTO {
    private String studentId;
    private String studentName;
    private Long completedLectures;
    private BigDecimal completionPercentage;
    private Integer timeSpentSeconds;

    // Getters and Setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public Long getCompletedLectures() { return completedLectures; }
    public void setCompletedLectures(Long completedLectures) { this.completedLectures = completedLectures; }

    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(BigDecimal completionPercentage) { this.completionPercentage = completionPercentage; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }
}


