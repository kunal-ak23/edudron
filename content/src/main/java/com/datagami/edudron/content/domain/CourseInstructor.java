package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_instructors", schema = "content")
public class CourseInstructor {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String courseId;

    @Column(nullable = false)
    private String instructorUserId; // Reference to idp.users

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstructorRole role;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course course;

    public enum InstructorRole {
        PRIMARY, CO_INSTRUCTOR
    }

    // Constructors
    public CourseInstructor() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(String instructorUserId) { this.instructorUserId = instructorUserId; }

    public InstructorRole getRole() { return role; }
    public void setRole(InstructorRole role) { this.role = role; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
}

