package com.datagami.edudron.student.dto;

import java.util.List;

public class SubmitEventRequest {
    private String submissionUrl;
    private String submissionText;
    private List<SubmitProjectRequest.AttachmentInfo> attachments;

    public SubmitEventRequest() {}

    public String getSubmissionUrl() { return submissionUrl; }
    public void setSubmissionUrl(String submissionUrl) { this.submissionUrl = submissionUrl; }
    public String getSubmissionText() { return submissionText; }
    public void setSubmissionText(String submissionText) { this.submissionText = submissionText; }
    public List<SubmitProjectRequest.AttachmentInfo> getAttachments() { return attachments; }
    public void setAttachments(List<SubmitProjectRequest.AttachmentInfo> attachments) { this.attachments = attachments; }
}
