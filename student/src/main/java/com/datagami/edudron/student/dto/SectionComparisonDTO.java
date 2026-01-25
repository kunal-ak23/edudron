package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

/**
 * DTO for comparing sections within a class.
 * Shows aggregate metrics for each section (across all courses in that section).
 */
public class SectionComparisonDTO {
    private String sectionId;
    private String sectionName;
    private Long totalStudents;
    private Long activeStudents; // Students active in last 30 days
    private BigDecimal averageCompletionRate; // Across all courses
    private Integer averageTimeSpentSeconds; // Across all courses

    // Constructors
    public SectionComparisonDTO() {}

    public SectionComparisonDTO(String sectionId, Long totalStudents, Long activeStudents,
                               BigDecimal averageCompletionRate, Integer averageTimeSpentSeconds) {
        this.sectionId = sectionId;
        this.totalStudents = totalStudents;
        this.activeStudents = activeStudents;
        this.averageCompletionRate = averageCompletionRate;
        this.averageTimeSpentSeconds = averageTimeSpentSeconds;
    }

    // Getters and Setters
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public Long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Long totalStudents) { this.totalStudents = totalStudents; }

    public Long getActiveStudents() { return activeStudents; }
    public void setActiveStudents(Long activeStudents) { this.activeStudents = activeStudents; }

    public BigDecimal getAverageCompletionRate() { return averageCompletionRate; }
    public void setAverageCompletionRate(BigDecimal averageCompletionRate) { 
        this.averageCompletionRate = averageCompletionRate; 
    }

    public Integer getAverageTimeSpentSeconds() { return averageTimeSpentSeconds; }
    public void setAverageTimeSpentSeconds(Integer averageTimeSpentSeconds) { 
        this.averageTimeSpentSeconds = averageTimeSpentSeconds; 
    }
}
