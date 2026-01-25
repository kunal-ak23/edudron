package com.datagami.edudron.student.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class BatchCreateClassesRequest {
    @NotEmpty(message = "At least one class is required")
    @Size(min = 1, max = 50, message = "Must have between 1 and 50 classes")
    @Valid
    private List<CreateClassRequest> classes;

    // Getters and Setters
    public List<CreateClassRequest> getClasses() { return classes; }
    public void setClasses(List<CreateClassRequest> classes) { this.classes = classes; }
}
