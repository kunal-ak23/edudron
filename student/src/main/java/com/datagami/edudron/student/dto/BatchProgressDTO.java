package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

public class BatchProgressDTO {
    private String batchId;
    private String batchName;
    private String courseId;
    private Long totalStudents;
    private Long totalLectures;
    private Long completedLectures;
    private BigDecimal averageCompletionPercentage;
    private Integer totalTimeSpentSeconds;
    private List<StudentProgressDTO> studentProgress;

    // Getters and Setters
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getBatchName() { return batchName; }
    public void setBatchName(String batchName) { this.batchName = batchName; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public Long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Long totalStudents) { this.totalStudents = totalStudents; }

    public Long getTotalLectures() { return totalLectures; }
    public void setTotalLectures(Long totalLectures) { this.totalLectures = totalLectures; }

    public Long getCompletedLectures() { return completedLectures; }
    public void setCompletedLectures(Long completedLectures) { this.completedLectures = completedLectures; }

    public BigDecimal getAverageCompletionPercentage() { return averageCompletionPercentage; }
    public void setAverageCompletionPercentage(BigDecimal averageCompletionPercentage) { this.averageCompletionPercentage = averageCompletionPercentage; }

    public Integer getTotalTimeSpentSeconds() { return totalTimeSpentSeconds; }
    public void setTotalTimeSpentSeconds(Integer totalTimeSpentSeconds) { this.totalTimeSpentSeconds = totalTimeSpentSeconds; }

    public List<StudentProgressDTO> getStudentProgress() { return studentProgress; }
    public void setStudentProgress(List<StudentProgressDTO> studentProgress) { this.studentProgress = studentProgress; }
}


