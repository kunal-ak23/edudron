package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_announcements", schema = "content")
public class CourseAnnouncement {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "course_id", nullable = false, insertable = true, updatable = true)
    private String courseId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private Boolean isPinned = false;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course course;

    // Constructors
    public CourseAnnouncement() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Boolean getIsPinned() { return isPinned; }
    public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

