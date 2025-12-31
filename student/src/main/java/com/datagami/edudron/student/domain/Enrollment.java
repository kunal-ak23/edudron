package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollments", schema = "student")
public class Enrollment {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String courseId;

    private String batchId;

    @Column(nullable = false)
    private OffsetDateTime enrolledAt;

    // Constructors
    public Enrollment() {
        this.enrolledAt = OffsetDateTime.now();
    }

    public Enrollment(String id, UUID clientId, String studentId, String courseId) {
        this.id = id;
        this.clientId = clientId;
        this.studentId = studentId;
        this.courseId = courseId;
        this.enrolledAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public OffsetDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(OffsetDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
}

