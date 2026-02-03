package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public class TransferEnrollmentRequest {
    @NotBlank(message = "Enrollment ID is required")
    private String enrollmentId;

    /** Destination section (batch). If set, class and institute are derived from the section. */
    private String destinationSectionId;

    /** Destination class only (no section). If set and destinationSectionId is blank, enrollment moves to class with batchId = null. */
    private String destinationClassId;

    private String destinationCourseId; // Optional: for cross-course transfer

    public TransferEnrollmentRequest() {}

    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

    public String getDestinationSectionId() { return destinationSectionId; }
    public void setDestinationSectionId(String destinationSectionId) { this.destinationSectionId = destinationSectionId; }

    public String getDestinationClassId() { return destinationClassId; }
    public void setDestinationClassId(String destinationClassId) { this.destinationClassId = destinationClassId; }

    public String getDestinationCourseId() { return destinationCourseId; }
    public void setDestinationCourseId(String destinationCourseId) { this.destinationCourseId = destinationCourseId; }
}
