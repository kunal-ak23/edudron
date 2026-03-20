package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public class SubmitProjectRequest {

    @NotBlank(message = "Submission URL is required")
    private String submissionUrl;

    public SubmitProjectRequest() {}

    public String getSubmissionUrl() { return submissionUrl; }
    public void setSubmissionUrl(String submissionUrl) { this.submissionUrl = submissionUrl; }
}
