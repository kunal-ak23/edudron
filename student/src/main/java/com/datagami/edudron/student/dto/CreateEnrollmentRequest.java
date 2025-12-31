package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateEnrollmentRequest {
    @NotBlank(message = "Course ID is required")
    private String courseId;
    
    private String batchId; // Optional: enroll in a specific batch

    // Constructors
    public CreateEnrollmentRequest() {}

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
}

