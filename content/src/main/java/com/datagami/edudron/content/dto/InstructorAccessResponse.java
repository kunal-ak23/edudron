package com.datagami.edudron.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for GET /api/instructor-assignments/instructor/{id}/access.
 * Typed fields ensure Jackson deserializes JSON arrays as List&lt;String&gt;,
 * avoiding Map&lt;String, Object&gt; type ambiguity that can yield size 0.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstructorAccessResponse {

    private String instructorUserId;
    private List<String> allowedCourseIds = new ArrayList<>();
    private List<String> allowedClassIds = new ArrayList<>();
    private List<String> allowedSectionIds = new ArrayList<>();
    private List<String> scopedCourseIds = new ArrayList<>();
    private boolean sectionOnlyAccess;
    private List<String> directCourseIds = new ArrayList<>();
    private List<String> derivedCourseIds = new ArrayList<>();
    private List<String> directClassIds = new ArrayList<>();
    private List<String> inheritedClassIds = new ArrayList<>();
    private List<String> directSectionIds = new ArrayList<>();
    private List<String> inheritedSectionIds = new ArrayList<>();

    public String getInstructorUserId() {
        return instructorUserId;
    }

    public void setInstructorUserId(String instructorUserId) {
        this.instructorUserId = instructorUserId;
    }

    public List<String> getAllowedCourseIds() {
        return allowedCourseIds;
    }

    public void setAllowedCourseIds(List<String> allowedCourseIds) {
        this.allowedCourseIds = allowedCourseIds != null ? allowedCourseIds : new ArrayList<>();
    }

    public List<String> getAllowedClassIds() {
        return allowedClassIds;
    }

    public void setAllowedClassIds(List<String> allowedClassIds) {
        this.allowedClassIds = allowedClassIds != null ? allowedClassIds : new ArrayList<>();
    }

    public List<String> getAllowedSectionIds() {
        return allowedSectionIds;
    }

    public void setAllowedSectionIds(List<String> allowedSectionIds) {
        this.allowedSectionIds = allowedSectionIds != null ? allowedSectionIds : new ArrayList<>();
    }

    public List<String> getScopedCourseIds() {
        return scopedCourseIds;
    }

    public void setScopedCourseIds(List<String> scopedCourseIds) {
        this.scopedCourseIds = scopedCourseIds != null ? scopedCourseIds : new ArrayList<>();
    }

    public boolean isSectionOnlyAccess() {
        return sectionOnlyAccess;
    }

    public void setSectionOnlyAccess(boolean sectionOnlyAccess) {
        this.sectionOnlyAccess = sectionOnlyAccess;
    }

    public List<String> getDirectCourseIds() {
        return directCourseIds;
    }

    public void setDirectCourseIds(List<String> directCourseIds) {
        this.directCourseIds = directCourseIds != null ? directCourseIds : new ArrayList<>();
    }

    public List<String> getDerivedCourseIds() {
        return derivedCourseIds;
    }

    public void setDerivedCourseIds(List<String> derivedCourseIds) {
        this.derivedCourseIds = derivedCourseIds != null ? derivedCourseIds : new ArrayList<>();
    }

    public List<String> getDirectClassIds() {
        return directClassIds;
    }

    public void setDirectClassIds(List<String> directClassIds) {
        this.directClassIds = directClassIds != null ? directClassIds : new ArrayList<>();
    }

    public List<String> getInheritedClassIds() {
        return inheritedClassIds;
    }

    public void setInheritedClassIds(List<String> inheritedClassIds) {
        this.inheritedClassIds = inheritedClassIds != null ? inheritedClassIds : new ArrayList<>();
    }

    public List<String> getDirectSectionIds() {
        return directSectionIds;
    }

    public void setDirectSectionIds(List<String> directSectionIds) {
        this.directSectionIds = directSectionIds != null ? directSectionIds : new ArrayList<>();
    }

    public List<String> getInheritedSectionIds() {
        return inheritedSectionIds;
    }

    public void setInheritedSectionIds(List<String> inheritedSectionIds) {
        this.inheritedSectionIds = inheritedSectionIds != null ? inheritedSectionIds : new ArrayList<>();
    }

    /**
     * Convert to Map&lt;String, Object&gt; for existing code that uses getSetFromAccess etc.
     * Map values are List&lt;String&gt; so listSize and getSetFromAccess work correctly.
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("instructorUserId", instructorUserId);
        map.put("allowedCourseIds", allowedCourseIds);
        map.put("allowedClassIds", allowedClassIds);
        map.put("allowedSectionIds", allowedSectionIds);
        map.put("scopedCourseIds", scopedCourseIds);
        map.put("sectionOnlyAccess", sectionOnlyAccess);
        map.put("directCourseIds", directCourseIds);
        map.put("derivedCourseIds", derivedCourseIds);
        map.put("directClassIds", directClassIds);
        map.put("inheritedClassIds", inheritedClassIds);
        map.put("directSectionIds", directSectionIds);
        map.put("inheritedSectionIds", inheritedSectionIds);
        return map;
    }
}
