package com.datagami.edudron.student.dto;

import java.util.List;

public class BulkTransferEnrollmentResponse {
    private List<EnrollmentDTO> successes;
    private List<TransferEnrollmentError> errors;

    public BulkTransferEnrollmentResponse() {}

    public BulkTransferEnrollmentResponse(List<EnrollmentDTO> successes, List<TransferEnrollmentError> errors) {
        this.successes = successes;
        this.errors = errors;
    }

    public List<EnrollmentDTO> getSuccesses() { return successes; }
    public void setSuccesses(List<EnrollmentDTO> successes) { this.successes = successes; }

    public List<TransferEnrollmentError> getErrors() { return errors; }
    public void setErrors(List<TransferEnrollmentError> errors) { this.errors = errors; }
}
