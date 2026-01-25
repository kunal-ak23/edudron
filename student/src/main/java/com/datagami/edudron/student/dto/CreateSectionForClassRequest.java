package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public class CreateSectionForClassRequest {
    @NotBlank(message = "Section name is required")
    private String name;
    
    private String description;
    
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
}
