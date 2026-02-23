package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

/**
 * DTO for aggregated lecture engagement data from database queries.
 * This avoids loading all sessions into memory.
 */
public class LectureEngagementAggregateDTO {
    private String lectureId;
    private Long totalViews;
    private Long uniqueViewers;
    private Double averageDurationSeconds;
    private Long completedSessions;
    private Long totalSessions;
    private Long shortDurationSessions; // Sessions < 10% of lecture duration
    private String courseId;

    public LectureEngagementAggregateDTO() {
    }

    public LectureEngagementAggregateDTO(String lectureId, String courseId, Long totalViews, Long uniqueViewers,
            Double averageDurationSeconds, Long completedSessions,
            Long totalSessions, Long shortDurationSessions) {
        this.lectureId = lectureId;
        this.courseId = courseId;
        this.totalViews = totalViews;
        this.uniqueViewers = uniqueViewers;
        this.averageDurationSeconds = averageDurationSeconds;
        this.completedSessions = completedSessions;
        this.totalSessions = totalSessions;
        this.shortDurationSessions = shortDurationSessions;
    }

    // Getters and Setters
    public String getLectureId() {
        return lectureId;
    }

    public void setLectureId(String lectureId) {
        this.lectureId = lectureId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public Long getTotalViews() {
        return totalViews;
    }

    public void setTotalViews(Long totalViews) {
        this.totalViews = totalViews;
    }

    public Long getUniqueViewers() {
        return uniqueViewers;
    }

    public void setUniqueViewers(Long uniqueViewers) {
        this.uniqueViewers = uniqueViewers;
    }

    public Double getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(Double averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public Long getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(Long completedSessions) {
        this.completedSessions = completedSessions;
    }

    public Long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Long totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Long getShortDurationSessions() {
        return shortDurationSessions;
    }

    public void setShortDurationSessions(Long shortDurationSessions) {
        this.shortDurationSessions = shortDurationSessions;
    }

    /**
     * Calculate completion rate as percentage
     */
    public BigDecimal getCompletionRate() {
        if (totalSessions == null || totalSessions == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedSessions)
                .divide(BigDecimal.valueOf(totalSessions), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate skip rate as percentage (based on short duration sessions)
     */
    public BigDecimal getSkipRate() {
        if (totalSessions == null || totalSessions == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(shortDurationSessions)
                .divide(BigDecimal.valueOf(totalSessions), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Get average duration as integer (rounded)
     */
    public Integer getAverageDurationSecondsInt() {
        return averageDurationSeconds != null ? averageDurationSeconds.intValue() : 0;
    }
}
