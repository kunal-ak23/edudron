package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

public class StudentCourseAnalyticsDTO {
    private String studentId;
    private String studentEmail;
    private String courseId;
    private String courseTitle;
    private Long totalViewingSessions;
    private Integer totalDurationSeconds;
    private Integer averageSessionDurationSeconds;
    private BigDecimal completionPercentage;

    private List<StudentLectureEngagementDTO> lectureActivity;

    // Constructors
    public StudentCourseAnalyticsDTO() {
    }

    // Getters and Setters
    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public Long getTotalViewingSessions() {
        return totalViewingSessions;
    }

    public void setTotalViewingSessions(Long totalViewingSessions) {
        this.totalViewingSessions = totalViewingSessions;
    }

    public Integer getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public void setTotalDurationSeconds(Integer totalDurationSeconds) {
        this.totalDurationSeconds = totalDurationSeconds;
    }

    public Integer getAverageSessionDurationSeconds() {
        return averageSessionDurationSeconds;
    }

    public void setAverageSessionDurationSeconds(Integer averageSessionDurationSeconds) {
        this.averageSessionDurationSeconds = averageSessionDurationSeconds;
    }

    public BigDecimal getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(BigDecimal completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public List<StudentLectureEngagementDTO> getLectureActivity() {
        return lectureActivity;
    }

    public void setLectureActivity(List<StudentLectureEngagementDTO> lectureActivity) {
        this.lectureActivity = lectureActivity;
    }
}
