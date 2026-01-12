package com.datagami.edudron.student.dto;

import java.util.List;

public class BulkEnrollmentResult {
    private Long totalStudents;
    private Long enrolledStudents;
    private Long skippedStudents;
    private Long failedStudents;
    private List<String> enrolledStudentIds;
    private List<String> errorMessages;

    public BulkEnrollmentResult() {}

    // Getters and Setters
    public Long getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(Long totalStudents) {
        this.totalStudents = totalStudents;
    }

    public Long getEnrolledStudents() {
        return enrolledStudents;
    }

    public void setEnrolledStudents(Long enrolledStudents) {
        this.enrolledStudents = enrolledStudents;
    }

    public Long getSkippedStudents() {
        return skippedStudents;
    }

    public void setSkippedStudents(Long skippedStudents) {
        this.skippedStudents = skippedStudents;
    }

    public Long getFailedStudents() {
        return failedStudents;
    }

    public void setFailedStudents(Long failedStudents) {
        this.failedStudents = failedStudents;
    }

    public List<String> getEnrolledStudentIds() {
        return enrolledStudentIds;
    }

    public void setEnrolledStudentIds(List<String> enrolledStudentIds) {
        this.enrolledStudentIds = enrolledStudentIds;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(List<String> errorMessages) {
        this.errorMessages = errorMessages;
    }
}

