package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public class AddToSectionRequest {
    @NotBlank(message = "Student ID is required")
    private String studentId;

    @NotBlank(message = "Destination section ID is required")
    private String destinationSectionId;

    private String destinationClassId;

    public AddToSectionRequest() {}

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getDestinationSectionId() { return destinationSectionId; }
    public void setDestinationSectionId(String destinationSectionId) { this.destinationSectionId = destinationSectionId; }

    public String getDestinationClassId() { return destinationClassId; }
    public void setDestinationClassId(String destinationClassId) { this.destinationClassId = destinationClassId; }
}
