package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_prerequisites", schema = "content")
public class CoursePrerequisite {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "course_id", nullable = false, insertable = true, updatable = true)
    private String courseId;

    @Column(name = "prerequisite_course_id", nullable = false, insertable = true, updatable = true)
    private String prerequisiteCourseId;

    @Column(nullable = false)
    private Boolean isRequired = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_course_id", insertable = false, updatable = false)
    private Course prerequisiteCourse;

    // Constructors
    public CoursePrerequisite() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public void setPrerequisiteCourseId(String prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Course getPrerequisiteCourse() { return prerequisiteCourse; }
    public void setPrerequisiteCourse(Course prerequisiteCourse) { this.prerequisiteCourse = prerequisiteCourse; }
}

