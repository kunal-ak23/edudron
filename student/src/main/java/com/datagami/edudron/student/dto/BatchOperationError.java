package com.datagami.edudron.student.dto;

public class BatchOperationError {
    private int index;
    private String field;
    private String message;

    public BatchOperationError() {}

    public BatchOperationError(int index, String field, String message) {
        this.index = index;
        this.field = field;
        this.message = message;
    }

    // Getters and Setters
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
