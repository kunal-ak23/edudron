package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.InstructorAssignment.AssignmentType;
import java.util.List;

public class CreateInstructorAssignmentRequest {
    private String instructorUserId;
    private AssignmentType assignmentType;
    
    // Only one of these should be set based on assignmentType
    private String classId;    // For CLASS assignments
    private String sectionId;  // For SECTION assignments
    private String courseId;   // For COURSE assignments
    
    // For COURSE assignments, optionally scope to specific classes/sections
    private List<String> scopedClassIds;
    private List<String> scopedSectionIds;

    // Constructors
    public CreateInstructorAssignmentRequest() {}

    // Getters and Setters
    public String getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(String instructorUserId) { this.instructorUserId = instructorUserId; }

    public AssignmentType getAssignmentType() { return assignmentType; }
    public void setAssignmentType(AssignmentType assignmentType) { this.assignmentType = assignmentType; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public List<String> getScopedClassIds() { return scopedClassIds; }
    public void setScopedClassIds(List<String> scopedClassIds) { this.scopedClassIds = scopedClassIds; }

    public List<String> getScopedSectionIds() { return scopedSectionIds; }
    public void setScopedSectionIds(List<String> scopedSectionIds) { this.scopedSectionIds = scopedSectionIds; }
}
