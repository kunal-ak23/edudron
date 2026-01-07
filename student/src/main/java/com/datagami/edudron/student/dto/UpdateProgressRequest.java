package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

public class UpdateProgressRequest {
    private String lectureId;
    private String sectionId;
    private Boolean isCompleted;
    private BigDecimal progressPercentage;
    private Integer timeSpentSeconds;

    // Constructors
    public UpdateProgressRequest() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }

    public BigDecimal getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(BigDecimal progressPercentage) { this.progressPercentage = progressPercentage; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }
}


