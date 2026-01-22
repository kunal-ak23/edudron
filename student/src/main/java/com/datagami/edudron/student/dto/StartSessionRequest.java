package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

public class StartSessionRequest {
    private String courseId;
    private String lectureId;
    private BigDecimal progressAtStart;

    // Constructors
    public StartSessionRequest() {}

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public BigDecimal getProgressAtStart() { return progressAtStart; }
    public void setProgressAtStart(BigDecimal progressAtStart) { this.progressAtStart = progressAtStart; }
}
