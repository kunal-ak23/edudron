package com.datagami.edudron.student.dto;

public class StudentImportRowResult {
    private Long rowNumber;
    private String email;
    private String name;
    private Boolean success;
    private String studentId; // If successful
    private String errorMessage; // If failed

    public StudentImportRowResult() {}

    public StudentImportRowResult(Long rowNumber, String email, String name, Boolean success, String studentId, String errorMessage) {
        this.rowNumber = rowNumber;
        this.email = email;
        this.name = name;
        this.success = success;
        this.studentId = studentId;
        this.errorMessage = errorMessage;
    }

    // Getters and Setters
    public Long getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Long rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

