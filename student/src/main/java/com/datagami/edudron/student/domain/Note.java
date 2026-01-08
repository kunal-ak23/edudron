package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notes", schema = "student")
public class Note {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String lectureId;

    @Column(nullable = false)
    private String courseId;

    // Highlight information
    @Column(columnDefinition = "text")
    private String highlightedText; // The selected text

    @Column(length = 50)
    private String highlightColor; // Color hex code (e.g., "#FFEB3B", "#FFC107", etc.)

    // Note content
    @Column(columnDefinition = "text")
    private String noteText; // User's note about the highlight

    // Position information (for reference)
    @Column(columnDefinition = "text")
    private String context; // Surrounding context or content reference

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public Note() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getHighlightedText() { return highlightedText; }
    public void setHighlightedText(String highlightedText) { this.highlightedText = highlightedText; }

    public String getHighlightColor() { return highlightColor; }
    public void setHighlightColor(String highlightColor) { this.highlightColor = highlightColor; }

    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

