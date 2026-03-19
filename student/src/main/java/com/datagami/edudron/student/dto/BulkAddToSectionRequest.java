package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BulkAddToSectionRequest {
    @NotEmpty(message = "Student IDs are required")
    private List<String> studentIds;

    @NotBlank(message = "Destination section ID is required")
    private String destinationSectionId;

    private String destinationClassId;

    public BulkAddToSectionRequest() {}

    public List<String> getStudentIds() { return studentIds; }
    public void setStudentIds(List<String> studentIds) { this.studentIds = studentIds; }

    public String getDestinationSectionId() { return destinationSectionId; }
    public void setDestinationSectionId(String destinationSectionId) { this.destinationSectionId = destinationSectionId; }

    public String getDestinationClassId() { return destinationClassId; }
    public void setDestinationClassId(String destinationClassId) { this.destinationClassId = destinationClassId; }
}
