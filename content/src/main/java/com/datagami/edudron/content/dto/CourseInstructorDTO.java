package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.CourseInstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CourseInstructorDTO {
    private String id;
    private UUID clientId;
    private String courseId;
    private String instructorUserId;
    private CourseInstructor.InstructorRole role;
    private String bio;
    private OffsetDateTime createdAt;
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(String instructorUserId) { this.instructorUserId = instructorUserId; }
    
    public CourseInstructor.InstructorRole getRole() { return role; }
    public void setRole(CourseInstructor.InstructorRole role) { this.role = role; }
    
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

