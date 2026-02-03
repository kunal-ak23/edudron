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

    /**
     * True when the instructor has only SECTION-type assignments (no CLASS, no COURSE).
     * Used by content service to show only section-level exams to section-only instructors.
     */
    private boolean sectionOnlyAccess;

    /**
     * Courses from COURSE-type assignments only (direct). Used to distinguish from derived course access.
     */
    private Set<String> directCourseIds;
    /**
     * Courses accessible via class/section (from content service). Inherited/derived from class/section assignments.
     */
    private Set<String> derivedCourseIds;
    /**
     * Classes from CLASS-type assignments only (direct).
     */
    private Set<String> directClassIds;
    /**
     * Classes from SECTION (parent class) or COURSE (scopedClassIds). Inherited, not from CLASS assignment.
     */
    private Set<String> inheritedClassIds;
    /**
     * Sections from SECTION assignments or COURSE (scopedSectionIds). Direct assignment to section or course scope.
     */
    private Set<String> directSectionIds;
    /**
     * Sections from CLASS (all sections in the class). Inherited from class assignment.
     */
    private Set<String> inheritedSectionIds;

    // Constructors
    public InstructorAccessDTO() {}

    public InstructorAccessDTO(String instructorUserId, Set<String> allowedClassIds,
                               Set<String> allowedSectionIds, Set<String> allowedCourseIds,
                               Set<String> scopedCourseIds) {
        this(instructorUserId, allowedClassIds, allowedSectionIds, allowedCourseIds, scopedCourseIds, false,
            null, null, null, null, null, null);
    }

    public InstructorAccessDTO(String instructorUserId, Set<String> allowedClassIds,
                               Set<String> allowedSectionIds, Set<String> allowedCourseIds,
                               Set<String> scopedCourseIds, boolean sectionOnlyAccess) {
        this(instructorUserId, allowedClassIds, allowedSectionIds, allowedCourseIds, scopedCourseIds, sectionOnlyAccess,
            null, null, null, null, null, null);
    }

    public InstructorAccessDTO(String instructorUserId, Set<String> allowedClassIds,
                               Set<String> allowedSectionIds, Set<String> allowedCourseIds,
                               Set<String> scopedCourseIds, boolean sectionOnlyAccess,
                               Set<String> directCourseIds, Set<String> derivedCourseIds,
                               Set<String> directClassIds, Set<String> inheritedClassIds,
                               Set<String> directSectionIds, Set<String> inheritedSectionIds) {
        this.instructorUserId = instructorUserId;
        this.allowedClassIds = allowedClassIds;
        this.allowedSectionIds = allowedSectionIds;
        this.allowedCourseIds = allowedCourseIds;
        this.scopedCourseIds = scopedCourseIds;
        this.sectionOnlyAccess = sectionOnlyAccess;
        this.directCourseIds = directCourseIds;
        this.derivedCourseIds = derivedCourseIds;
        this.directClassIds = directClassIds;
        this.inheritedClassIds = inheritedClassIds;
        this.directSectionIds = directSectionIds;
        this.inheritedSectionIds = inheritedSectionIds;
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

    public boolean isSectionOnlyAccess() { return sectionOnlyAccess; }
    public void setSectionOnlyAccess(boolean sectionOnlyAccess) { this.sectionOnlyAccess = sectionOnlyAccess; }

    public Set<String> getDirectCourseIds() { return directCourseIds; }
    public void setDirectCourseIds(Set<String> directCourseIds) { this.directCourseIds = directCourseIds; }

    public Set<String> getDerivedCourseIds() { return derivedCourseIds; }
    public void setDerivedCourseIds(Set<String> derivedCourseIds) { this.derivedCourseIds = derivedCourseIds; }

    public Set<String> getDirectClassIds() { return directClassIds; }
    public void setDirectClassIds(Set<String> directClassIds) { this.directClassIds = directClassIds; }

    public Set<String> getInheritedClassIds() { return inheritedClassIds; }
    public void setInheritedClassIds(Set<String> inheritedClassIds) { this.inheritedClassIds = inheritedClassIds; }

    public Set<String> getDirectSectionIds() { return directSectionIds; }
    public void setDirectSectionIds(Set<String> directSectionIds) { this.directSectionIds = directSectionIds; }

    public Set<String> getInheritedSectionIds() { return inheritedSectionIds; }
    public void setInheritedSectionIds(Set<String> inheritedSectionIds) { this.inheritedSectionIds = inheritedSectionIds; }

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
