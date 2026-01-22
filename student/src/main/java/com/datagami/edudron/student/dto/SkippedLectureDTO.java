package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

public class SkippedLectureDTO {
    private String lectureId;
    private String lectureTitle;
    private Integer lectureDurationSeconds;
    private Long totalSessions;
    private Long skippedSessions;
    private BigDecimal skipRate;
    private Integer averageDurationSeconds;
    private String skipReason; // "DURATION_THRESHOLD" or "QUICK_COMPLETION"

    // Constructors
    public SkippedLectureDTO() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getLectureTitle() { return lectureTitle; }
    public void setLectureTitle(String lectureTitle) { this.lectureTitle = lectureTitle; }

    public Integer getLectureDurationSeconds() { return lectureDurationSeconds; }
    public void setLectureDurationSeconds(Integer lectureDurationSeconds) { this.lectureDurationSeconds = lectureDurationSeconds; }

    public Long getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Long totalSessions) { this.totalSessions = totalSessions; }

    public Long getSkippedSessions() { return skippedSessions; }
    public void setSkippedSessions(Long skippedSessions) { this.skippedSessions = skippedSessions; }

    public BigDecimal getSkipRate() { return skipRate; }
    public void setSkipRate(BigDecimal skipRate) { this.skipRate = skipRate; }

    public Integer getAverageDurationSeconds() { return averageDurationSeconds; }
    public void setAverageDurationSeconds(Integer averageDurationSeconds) { this.averageDurationSeconds = averageDurationSeconds; }

    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
}
