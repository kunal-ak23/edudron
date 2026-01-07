package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

public class CourseProgressDTO {
    private String enrollmentId;
    private String courseId;
    private Long totalLectures;
    private Long completedLectures;
    private BigDecimal completionPercentage;
    private Integer totalTimeSpentSeconds;
    private List<ProgressDTO> lectureProgress;
    private List<ProgressDTO> sectionProgress;

    // Constructors
    public CourseProgressDTO() {}

    // Getters and Setters
    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public Long getTotalLectures() { return totalLectures; }
    public void setTotalLectures(Long totalLectures) { this.totalLectures = totalLectures; }

    public Long getCompletedLectures() { return completedLectures; }
    public void setCompletedLectures(Long completedLectures) { this.completedLectures = completedLectures; }

    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(BigDecimal completionPercentage) { this.completionPercentage = completionPercentage; }

    public Integer getTotalTimeSpentSeconds() { return totalTimeSpentSeconds; }
    public void setTotalTimeSpentSeconds(Integer totalTimeSpentSeconds) { this.totalTimeSpentSeconds = totalTimeSpentSeconds; }

    public List<ProgressDTO> getLectureProgress() { return lectureProgress; }
    public void setLectureProgress(List<ProgressDTO> lectureProgress) { this.lectureProgress = lectureProgress; }

    public List<ProgressDTO> getSectionProgress() { return sectionProgress; }
    public void setSectionProgress(List<ProgressDTO> sectionProgress) { this.sectionProgress = sectionProgress; }
}


