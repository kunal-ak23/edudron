package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

/**
 * DTO for per-course performance breakdown within a section or class.
 * Used to show individual course metrics when a section/class has multiple courses.
 */
public class CourseBreakdownDTO {
    private String courseId;
    private String courseTitle;
    private Long totalSessions;
    private Long uniqueStudents;
    private BigDecimal completionRate;
    private Integer averageTimeSpentSeconds;

    // Constructors
    public CourseBreakdownDTO() {}

    public CourseBreakdownDTO(String courseId, Long totalSessions, Long uniqueStudents, 
                             BigDecimal completionRate, Integer averageTimeSpentSeconds) {
        this.courseId = courseId;
        this.totalSessions = totalSessions;
        this.uniqueStudents = uniqueStudents;
        this.completionRate = completionRate;
        this.averageTimeSpentSeconds = averageTimeSpentSeconds;
    }

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public Long getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Long totalSessions) { this.totalSessions = totalSessions; }

    public Long getUniqueStudents() { return uniqueStudents; }
    public void setUniqueStudents(Long uniqueStudents) { this.uniqueStudents = uniqueStudents; }

    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }

    public Integer getAverageTimeSpentSeconds() { return averageTimeSpentSeconds; }
    public void setAverageTimeSpentSeconds(Integer averageTimeSpentSeconds) { 
        this.averageTimeSpentSeconds = averageTimeSpentSeconds; 
    }
}
