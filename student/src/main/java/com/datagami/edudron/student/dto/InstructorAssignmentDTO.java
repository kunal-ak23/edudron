package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.InstructorAssignment.AssignmentType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class InstructorAssignmentDTO {
    private String id;
    private UUID clientId;
    private String instructorUserId;
    private String instructorName; // Populated from identity service
    private String instructorEmail; // Populated from identity service
    private AssignmentType assignmentType;
    private String classId;
    private String className; // Populated for display
    private String sectionId;
    private String sectionName; // Populated for display
    private String courseId;
    private String courseName; // Populated for display
    private List<String> scopedClassIds;
    private List<String> scopedSectionIds;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Constructors
    public InstructorAssignmentDTO() {}

    public InstructorAssignmentDTO(String id, UUID clientId, String instructorUserId, 
                                   AssignmentType assignmentType, String classId, 
                                   String sectionId, String courseId,
                                   List<String> scopedClassIds, List<String> scopedSectionIds,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.clientId = clientId;
        this.instructorUserId = instructorUserId;
        this.assignmentType = assignmentType;
        this.classId = classId;
        this.sectionId = sectionId;
        this.courseId = courseId;
        this.scopedClassIds = scopedClassIds;
        this.scopedSectionIds = scopedSectionIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(String instructorUserId) { this.instructorUserId = instructorUserId; }

    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }

    public String getInstructorEmail() { return instructorEmail; }
    public void setInstructorEmail(String instructorEmail) { this.instructorEmail = instructorEmail; }

    public AssignmentType getAssignmentType() { return assignmentType; }
    public void setAssignmentType(AssignmentType assignmentType) { this.assignmentType = assignmentType; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public List<String> getScopedClassIds() { return scopedClassIds; }
    public void setScopedClassIds(List<String> scopedClassIds) { this.scopedClassIds = scopedClassIds; }

    public List<String> getScopedSectionIds() { return scopedSectionIds; }
    public void setScopedSectionIds(List<String> scopedSectionIds) { this.scopedSectionIds = scopedSectionIds; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
