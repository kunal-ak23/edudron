package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class LectureAnalyticsDTO {
    private String lectureId;
    private String lectureTitle;
    private Long totalViews;
    private Long uniqueViewers;
    private Integer averageSessionDurationSeconds;
    private BigDecimal completionRate;
    private BigDecimal skipRate;
    private OffsetDateTime firstViewAt;
    private OffsetDateTime lastViewAt;
    private List<StudentLectureEngagementDTO> studentEngagements;
    private List<LectureViewSessionDTO> recentSessions;

    // Constructors
    public LectureAnalyticsDTO() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getLectureTitle() { return lectureTitle; }
    public void setLectureTitle(String lectureTitle) { this.lectureTitle = lectureTitle; }

    public Long getTotalViews() { return totalViews; }
    public void setTotalViews(Long totalViews) { this.totalViews = totalViews; }

    public Long getUniqueViewers() { return uniqueViewers; }
    public void setUniqueViewers(Long uniqueViewers) { this.uniqueViewers = uniqueViewers; }

    public Integer getAverageSessionDurationSeconds() { return averageSessionDurationSeconds; }
    public void setAverageSessionDurationSeconds(Integer averageSessionDurationSeconds) { this.averageSessionDurationSeconds = averageSessionDurationSeconds; }

    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }

    public BigDecimal getSkipRate() { return skipRate; }
    public void setSkipRate(BigDecimal skipRate) { this.skipRate = skipRate; }

    public OffsetDateTime getFirstViewAt() { return firstViewAt; }
    public void setFirstViewAt(OffsetDateTime firstViewAt) { this.firstViewAt = firstViewAt; }

    public OffsetDateTime getLastViewAt() { return lastViewAt; }
    public void setLastViewAt(OffsetDateTime lastViewAt) { this.lastViewAt = lastViewAt; }

    public List<StudentLectureEngagementDTO> getStudentEngagements() { return studentEngagements; }
    public void setStudentEngagements(List<StudentLectureEngagementDTO> studentEngagements) { this.studentEngagements = studentEngagements; }

    public List<LectureViewSessionDTO> getRecentSessions() { return recentSessions; }
    public void setRecentSessions(List<LectureViewSessionDTO> recentSessions) { this.recentSessions = recentSessions; }
}
