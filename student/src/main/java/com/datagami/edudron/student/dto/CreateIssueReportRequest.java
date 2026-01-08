package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.IssueReport;

public class CreateIssueReportRequest {
    private String lectureId;
    private String courseId;
    private IssueReport.IssueType issueType;
    private String description;

    // Constructors
    public CreateIssueReportRequest() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public IssueReport.IssueType getIssueType() { return issueType; }
    public void setIssueType(IssueReport.IssueType issueType) { this.issueType = issueType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

