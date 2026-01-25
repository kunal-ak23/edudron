package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for comprehensive section analytics.
 * Aggregates metrics across ALL courses assigned to the section.
 */
public class SectionAnalyticsDTO {
    private String sectionId;
    private String sectionName;
    private String classId;
    private String className;
    private Integer totalCourses; // Number of courses assigned to section
    private Long totalViewingSessions; // Aggregated across all courses
    private Long uniqueStudentsEngaged;
    private Integer averageTimePerLectureSeconds;
    private BigDecimal overallCompletionRate;
    private List<LectureEngagementSummaryDTO> lectureEngagements; // Lectures from ALL courses
    private List<SkippedLectureDTO> skippedLectures; // Skipped lectures across all courses
    private List<ActivityTimelinePointDTO> activityTimeline; // Timeline across all courses
    private List<CourseBreakdownDTO> courseBreakdown; // Per-course metrics

    // Constructors
    public SectionAnalyticsDTO() {}

    // Getters and Setters
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public Integer getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Integer totalCourses) { this.totalCourses = totalCourses; }

    public Long getTotalViewingSessions() { return totalViewingSessions; }
    public void setTotalViewingSessions(Long totalViewingSessions) { 
        this.totalViewingSessions = totalViewingSessions; 
    }

    public Long getUniqueStudentsEngaged() { return uniqueStudentsEngaged; }
    public void setUniqueStudentsEngaged(Long uniqueStudentsEngaged) { 
        this.uniqueStudentsEngaged = uniqueStudentsEngaged; 
    }

    public Integer getAverageTimePerLectureSeconds() { return averageTimePerLectureSeconds; }
    public void setAverageTimePerLectureSeconds(Integer averageTimePerLectureSeconds) { 
        this.averageTimePerLectureSeconds = averageTimePerLectureSeconds; 
    }

    public BigDecimal getOverallCompletionRate() { return overallCompletionRate; }
    public void setOverallCompletionRate(BigDecimal overallCompletionRate) { 
        this.overallCompletionRate = overallCompletionRate; 
    }

    public List<LectureEngagementSummaryDTO> getLectureEngagements() { return lectureEngagements; }
    public void setLectureEngagements(List<LectureEngagementSummaryDTO> lectureEngagements) { 
        this.lectureEngagements = lectureEngagements; 
    }

    public List<SkippedLectureDTO> getSkippedLectures() { return skippedLectures; }
    public void setSkippedLectures(List<SkippedLectureDTO> skippedLectures) { 
        this.skippedLectures = skippedLectures; 
    }

    public List<ActivityTimelinePointDTO> getActivityTimeline() { return activityTimeline; }
    public void setActivityTimeline(List<ActivityTimelinePointDTO> activityTimeline) { 
        this.activityTimeline = activityTimeline; 
    }

    public List<CourseBreakdownDTO> getCourseBreakdown() { return courseBreakdown; }
    public void setCourseBreakdown(List<CourseBreakdownDTO> courseBreakdown) { 
        this.courseBreakdown = courseBreakdown; 
    }
}
