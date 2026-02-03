package com.datagami.edudron.student.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for bulk grading submissions.
 */
public class BulkGradeResponse {
    private int gradedCount;
    private List<BulkGradeError> errors = new ArrayList<>();

    public BulkGradeResponse() {}

    public BulkGradeResponse(int gradedCount, List<BulkGradeError> errors) {
        this.gradedCount = gradedCount;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getGradedCount() {
        return gradedCount;
    }

    public void setGradedCount(int gradedCount) {
        this.gradedCount = gradedCount;
    }

    public List<BulkGradeError> getErrors() {
        return errors;
    }

    public void setErrors(List<BulkGradeError> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public static class BulkGradeError {
        private String submissionId;
        private String message;

        public BulkGradeError() {}

        public BulkGradeError(String submissionId, String message) {
            this.submissionId = submissionId;
            this.message = message;
        }

        public String getSubmissionId() { return submissionId; }
        public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
