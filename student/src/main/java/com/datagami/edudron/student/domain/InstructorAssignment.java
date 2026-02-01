package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "instructor_assignments", schema = "student")
public class InstructorAssignment {
    @Id
    @Column(name = "id")
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "instructor_user_id", nullable = false)
    private String instructorUserId; // Reference to idp.users

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private AssignmentType assignmentType;

    // Only one of these will be set based on assignmentType
    @Column(name = "class_id")
    private String classId;    // For CLASS assignments

    @Column(name = "section_id")
    private String sectionId;  // For SECTION assignments

    @Column(name = "course_id")
    private String courseId;   // For COURSE assignments

    // For COURSE assignments, optionally scope to specific classes/sections
    @Column(name = "scoped_class_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> scopedClassIds;

    @Column(name = "scoped_section_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> scopedSectionIds;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum AssignmentType {
        CLASS,    // Access all sections in class + all courses assigned to class
        SECTION,  // Access specific section + all courses assigned to section
        COURSE    // Access specific course, optionally scoped to classes/sections
    }

    // Constructors
    public InstructorAssignment() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

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

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
