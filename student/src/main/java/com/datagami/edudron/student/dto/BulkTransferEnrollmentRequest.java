package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BulkTransferEnrollmentRequest {
    @NotEmpty(message = "At least one enrollment ID is required")
    private List<String> enrollmentIds;

    private String destinationSectionId; // If set, transfer to section (class derived from section)

    private String destinationClassId; // If set and destinationSectionId blank, transfer to class only (batchId = null)

    private String destinationCourseId; // Optional: for cross-course transfer, applied to all

    public BulkTransferEnrollmentRequest() {}

    public BulkTransferEnrollmentRequest(List<String> enrollmentIds, String destinationSectionId, String destinationClassId, String destinationCourseId) {
        this.enrollmentIds = enrollmentIds;
        this.destinationSectionId = destinationSectionId;
        this.destinationClassId = destinationClassId;
        this.destinationCourseId = destinationCourseId;
    }

    public String getDestinationClassId() { return destinationClassId; }
    public void setDestinationClassId(String destinationClassId) { this.destinationClassId = destinationClassId; }

    public List<String> getEnrollmentIds() { return enrollmentIds; }
    public void setEnrollmentIds(List<String> enrollmentIds) { this.enrollmentIds = enrollmentIds; }

    public String getDestinationSectionId() { return destinationSectionId; }
    public void setDestinationSectionId(String destinationSectionId) { this.destinationSectionId = destinationSectionId; }

    public String getDestinationCourseId() { return destinationCourseId; }
    public void setDestinationCourseId(String destinationCourseId) { this.destinationCourseId = destinationCourseId; }
}
