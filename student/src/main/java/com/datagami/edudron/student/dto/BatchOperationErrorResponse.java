package com.datagami.edudron.student.dto;

import java.util.List;

public class BatchOperationErrorResponse {
    private String message;
    private List<BatchOperationError> errors;

    public BatchOperationErrorResponse() {}

    public BatchOperationErrorResponse(String message, List<BatchOperationError> errors) {
        this.message = message;
        this.errors = errors;
    }

    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<BatchOperationError> getErrors() { return errors; }
    public void setErrors(List<BatchOperationError> errors) { this.errors = errors; }
}
