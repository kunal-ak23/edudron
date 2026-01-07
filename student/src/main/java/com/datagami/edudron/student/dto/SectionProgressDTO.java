package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

public class SectionProgressDTO {
    private String sectionId;
    private String sectionName;
    private String classId;
    private long totalStudents;
    private long totalLectures;
    private long completedLectures;
    private BigDecimal averageCompletionPercentage;
    private int totalTimeSpentSeconds;
    private List<StudentProgressDTO> studentProgress;

    // Getters and Setters
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public long getTotalLectures() { return totalLectures; }
    public void setTotalLectures(long totalLectures) { this.totalLectures = totalLectures; }

    public long getCompletedLectures() { return completedLectures; }
    public void setCompletedLectures(long completedLectures) { this.completedLectures = completedLectures; }

    public BigDecimal getAverageCompletionPercentage() { return averageCompletionPercentage; }
    public void setAverageCompletionPercentage(BigDecimal averageCompletionPercentage) { this.averageCompletionPercentage = averageCompletionPercentage; }

    public int getTotalTimeSpentSeconds() { return totalTimeSpentSeconds; }
    public void setTotalTimeSpentSeconds(int totalTimeSpentSeconds) { this.totalTimeSpentSeconds = totalTimeSpentSeconds; }

    public List<StudentProgressDTO> getStudentProgress() { return studentProgress; }
    public void setStudentProgress(List<StudentProgressDTO> studentProgress) { this.studentProgress = studentProgress; }
}


