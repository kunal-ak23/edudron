package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

public class CourseAnalyticsDTO {
    private String courseId;
    private String courseTitle;
    private Long totalViewingSessions;
    private Long uniqueStudentsEngaged;
    private Integer averageTimePerLectureSeconds;
    private BigDecimal overallCompletionRate;
    private List<LectureEngagementSummaryDTO> lectureEngagements;
    private List<SkippedLectureDTO> skippedLectures;
    private List<ActivityTimelinePointDTO> activityTimeline;

    // Constructors
    public CourseAnalyticsDTO() {}

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public Long getTotalViewingSessions() { return totalViewingSessions; }
    public void setTotalViewingSessions(Long totalViewingSessions) { this.totalViewingSessions = totalViewingSessions; }

    public Long getUniqueStudentsEngaged() { return uniqueStudentsEngaged; }
    public void setUniqueStudentsEngaged(Long uniqueStudentsEngaged) { this.uniqueStudentsEngaged = uniqueStudentsEngaged; }

    public Integer getAverageTimePerLectureSeconds() { return averageTimePerLectureSeconds; }
    public void setAverageTimePerLectureSeconds(Integer averageTimePerLectureSeconds) { this.averageTimePerLectureSeconds = averageTimePerLectureSeconds; }

    public BigDecimal getOverallCompletionRate() { return overallCompletionRate; }
    public void setOverallCompletionRate(BigDecimal overallCompletionRate) { this.overallCompletionRate = overallCompletionRate; }

    public List<LectureEngagementSummaryDTO> getLectureEngagements() { return lectureEngagements; }
    public void setLectureEngagements(List<LectureEngagementSummaryDTO> lectureEngagements) { this.lectureEngagements = lectureEngagements; }

    public List<SkippedLectureDTO> getSkippedLectures() { return skippedLectures; }
    public void setSkippedLectures(List<SkippedLectureDTO> skippedLectures) { this.skippedLectures = skippedLectures; }

    public List<ActivityTimelinePointDTO> getActivityTimeline() { return activityTimeline; }
    public void setActivityTimeline(List<ActivityTimelinePointDTO> activityTimeline) { this.activityTimeline = activityTimeline; }
}
