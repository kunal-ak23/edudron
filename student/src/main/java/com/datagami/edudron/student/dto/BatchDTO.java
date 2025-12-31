package com.datagami.edudron.student.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class BatchDTO {
    private String id;
    private UUID clientId;
    private String name;
    private String description;
    private String courseId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private Boolean isActive;
    private Long studentCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Long getStudentCount() { return studentCount; }
    public void setStudentCount(Long studentCount) { this.studentCount = studentCount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

