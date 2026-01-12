package com.datagami.edudron.student.dto;

import java.util.List;

public class BatchEnrollmentRequest {
    private List<String> courseIds;

    public BatchEnrollmentRequest() {}

    public BatchEnrollmentRequest(List<String> courseIds) {
        this.courseIds = courseIds;
    }

    // Getters and Setters
    public List<String> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(List<String> courseIds) {
        this.courseIds = courseIds;
    }
}

