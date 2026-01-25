package com.datagami.edudron.student.dto;

import java.util.List;

public class BatchCreateSectionsResponse {
    private List<SectionDTO> sections;
    private int totalCreated;
    private String message;

    public BatchCreateSectionsResponse() {}

    public BatchCreateSectionsResponse(List<SectionDTO> sections, int totalCreated, String message) {
        this.sections = sections;
        this.totalCreated = totalCreated;
        this.message = message;
    }

    // Getters and Setters
    public List<SectionDTO> getSections() { return sections; }
    public void setSections(List<SectionDTO> sections) { this.sections = sections; }

    public int getTotalCreated() { return totalCreated; }
    public void setTotalCreated(int totalCreated) { this.totalCreated = totalCreated; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
