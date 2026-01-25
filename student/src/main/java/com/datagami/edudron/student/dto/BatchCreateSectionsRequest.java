package com.datagami.edudron.student.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class BatchCreateSectionsRequest {
    @NotEmpty(message = "At least one section is required")
    @Size(min = 1, max = 50, message = "Must have between 1 and 50 sections")
    @Valid
    private List<CreateSectionRequest> sections;

    // Getters and Setters
    public List<CreateSectionRequest> getSections() { return sections; }
    public void setSections(List<CreateSectionRequest> sections) { this.sections = sections; }
}
