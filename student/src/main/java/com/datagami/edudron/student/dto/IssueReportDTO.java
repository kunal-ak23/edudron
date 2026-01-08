package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.IssueReport;
import java.time.OffsetDateTime;
import java.util.UUID;

public class IssueReportDTO {
    private String id;
    private UUID clientId;
    private String studentId;
    private String lectureId;
    private String courseId;
    private IssueReport.IssueType issueType;
    private String description;
    private IssueReport.IssueStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Constructors
    public IssueReportDTO() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public IssueReport.IssueType getIssueType() { return issueType; }
    public void setIssueType(IssueReport.IssueType issueType) { this.issueType = issueType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public IssueReport.IssueStatus getStatus() { return status; }
    public void setStatus(IssueReport.IssueStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

