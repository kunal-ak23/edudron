package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public class CreateEnrollmentRequest {
    @NotBlank(message = "Course ID is required")
    private String courseId;
    
    @JsonAlias({"sectionId"}) // Accept both "batchId" and "sectionId" from frontend
    private String batchId; // Now represents Section ID (kept for backward compatibility)
    
    private String instituteId;
    
    private String classId; // Optional: enroll in a specific batch

    // Constructors
    public CreateEnrollmentRequest() {}

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getInstituteId() { return instituteId; }
    public void setInstituteId(String instituteId) { this.instituteId = instituteId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }
}

