package com.datagami.edudron.student.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for comprehensive class analytics.
 * Aggregates metrics across ALL sections and ALL courses in the class.
 */
public class ClassAnalyticsDTO {
    private String classId;
    private String className;
    private String instituteId;
    private Integer totalSections;
    private Integer totalCourses; // Number of unique courses across all sections
    private Long totalViewingSessions; // Aggregated across all sections and all courses
    private Long uniqueStudentsEngaged;
    private Integer averageTimePerLectureSeconds;
    private BigDecimal overallCompletionRate;
    private List<LectureEngagementSummaryDTO> lectureEngagements; // Lectures from ALL courses
    private List<SkippedLectureDTO> skippedLectures; // Skipped lectures across all courses
    private List<ActivityTimelinePointDTO> activityTimeline; // Timeline across all courses
    private List<SectionComparisonDTO> sectionComparison; // Compare sections (aggregated per section)
    private List<CourseBreakdownDTO> courseBreakdown; // Per-course metrics

    // Constructors
    public ClassAnalyticsDTO() {}

    // Getters and Setters
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getInstituteId() { return instituteId; }
    public void setInstituteId(String instituteId) { this.instituteId = instituteId; }

    public Integer getTotalSections() { return totalSections; }
    public void setTotalSections(Integer totalSections) { this.totalSections = totalSections; }

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

    public List<SectionComparisonDTO> getSectionComparison() { return sectionComparison; }
    public void setSectionComparison(List<SectionComparisonDTO> sectionComparison) { 
        this.sectionComparison = sectionComparison; 
    }

    public List<CourseBreakdownDTO> getCourseBreakdown() { return courseBreakdown; }
    public void setCourseBreakdown(List<CourseBreakdownDTO> courseBreakdown) { 
        this.courseBreakdown = courseBreakdown; 
    }
}
