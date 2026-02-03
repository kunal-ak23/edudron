package com.datagami.edudron.student.dto;

public class TransferEnrollmentError {
    private int index;
    private String enrollmentId;
    private String message;

    public TransferEnrollmentError() {}

    public TransferEnrollmentError(int index, String enrollmentId, String message) {
        this.index = index;
        this.enrollmentId = enrollmentId;
        this.message = message;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
