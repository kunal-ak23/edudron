package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "learning_objectives", schema = "content")
public class LearningObjective {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String courseId;

    @Column(nullable = false, columnDefinition = "text")
    private String objectiveText;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course course;

    // Constructors
    public LearningObjective() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getObjectiveText() { return objectiveText; }
    public void setObjectiveText(String objectiveText) { this.objectiveText = objectiveText; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
}

