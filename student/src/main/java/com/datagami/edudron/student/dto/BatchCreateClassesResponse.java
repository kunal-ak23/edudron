package com.datagami.edudron.student.dto;

import java.util.List;

public class BatchCreateClassesResponse {
    private List<ClassDTO> classes;
    private int totalCreated;
    private String message;

    public BatchCreateClassesResponse() {}

    public BatchCreateClassesResponse(List<ClassDTO> classes, int totalCreated, String message) {
        this.classes = classes;
        this.totalCreated = totalCreated;
        this.message = message;
    }

    // Getters and Setters
    public List<ClassDTO> getClasses() { return classes; }
    public void setClasses(List<ClassDTO> classes) { this.classes = classes; }

    public int getTotalCreated() { return totalCreated; }
    public void setTotalCreated(int totalCreated) { this.totalCreated = totalCreated; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
