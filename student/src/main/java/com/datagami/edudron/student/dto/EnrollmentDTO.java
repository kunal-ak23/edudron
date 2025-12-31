package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class EnrollmentDTO {
    private String id;
    private UUID clientId;
    private String studentId;
    private String courseId;
    private String batchId;
    private OffsetDateTime enrolledAt;

    // Constructors
    public EnrollmentDTO() {}

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

