package com.datagami.edudron.student.dto;

import java.util.Set;

/**
 * DTO representing an instructor's derived access based on their assignments.
 * Used by other services to filter data based on instructor's allowed scope.
 */
public class InstructorAccessDTO {
    private String instructorUserId;
    private Set<String> allowedClassIds;
    private Set<String> allowedSectionIds;
    private Set<String> allowedCourseIds;
    
    // For course assignments with scoped access, we need to track which 
    // courses have scoped restrictions
    private Set<String> scopedCourseIds; // Courses with class/section restrictions

    // Constructors
    public InstructorAccessDTO() {}

    public InstructorAccessDTO(String instructorUserId, Set<String> allowedClassIds, 
                               Set<String> allowedSectionIds, Set<String> allowedCourseIds,
                               Set<String> scopedCourseIds) {
        this.instructorUserId = instructorUserId;
        this.allowedClassIds = allowedClassIds;
        this.allowedSectionIds = allowedSectionIds;
        this.allowedCourseIds = allowedCourseIds;
        this.scopedCourseIds = scopedCourseIds;
    }

    // Getters and Setters
    public String getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(String instructorUserId) { this.instructorUserId = instructorUserId; }

    public Set<String> getAllowedClassIds() { return allowedClassIds; }
    public void setAllowedClassIds(Set<String> allowedClassIds) { this.allowedClassIds = allowedClassIds; }

    public Set<String> getAllowedSectionIds() { return allowedSectionIds; }
    public void setAllowedSectionIds(Set<String> allowedSectionIds) { this.allowedSectionIds = allowedSectionIds; }

    public Set<String> getAllowedCourseIds() { return allowedCourseIds; }
    public void setAllowedCourseIds(Set<String> allowedCourseIds) { this.allowedCourseIds = allowedCourseIds; }

    public Set<String> getScopedCourseIds() { return scopedCourseIds; }
    public void setScopedCourseIds(Set<String> scopedCourseIds) { this.scopedCourseIds = scopedCourseIds; }

    /**
     * Check if instructor can access a specific class
     */
    public boolean canAccessClass(String classId) {
        return allowedClassIds != null && allowedClassIds.contains(classId);
    }

    /**
     * Check if instructor can access a specific section
     */
    public boolean canAccessSection(String sectionId) {
        return allowedSectionIds != null && allowedSectionIds.contains(sectionId);
    }

    /**
     * Check if instructor can access a specific course
     */
    public boolean canAccessCourse(String courseId) {
        return allowedCourseIds != null && allowedCourseIds.contains(courseId);
    }

    /**
     * Check if a course has scoped restrictions (class/section level)
     */
    public boolean isCourseScoped(String courseId) {
        return scopedCourseIds != null && scopedCourseIds.contains(courseId);
    }
}
