package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

public class LectureEngagementSummaryDTO {
    private String lectureId;
    private String lectureTitle;
    private Long totalViews;
    private Long uniqueViewers;
    private Integer averageDurationSeconds;
    private BigDecimal completionRate;
    private BigDecimal skipRate;

    // Constructors
    public LectureEngagementSummaryDTO() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getLectureTitle() { return lectureTitle; }
    public void setLectureTitle(String lectureTitle) { this.lectureTitle = lectureTitle; }

    public Long getTotalViews() { return totalViews; }
    public void setTotalViews(Long totalViews) { this.totalViews = totalViews; }

    public Long getUniqueViewers() { return uniqueViewers; }
    public void setUniqueViewers(Long uniqueViewers) { this.uniqueViewers = uniqueViewers; }

    public Integer getAverageDurationSeconds() { return averageDurationSeconds; }
    public void setAverageDurationSeconds(Integer averageDurationSeconds) { this.averageDurationSeconds = averageDurationSeconds; }

    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }

    public BigDecimal getSkipRate() { return skipRate; }
    public void setSkipRate(BigDecimal skipRate) { this.skipRate = skipRate; }
}
