package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class SubmitProjectRequest {

    @NotBlank(message = "Submission URL is required")
    private String submissionUrl;

    private List<AttachmentInfo> attachments;

    public SubmitProjectRequest() {}

    public String getSubmissionUrl() { return submissionUrl; }
    public void setSubmissionUrl(String submissionUrl) { this.submissionUrl = submissionUrl; }

    public List<AttachmentInfo> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentInfo> attachments) { this.attachments = attachments; }

    public static class AttachmentInfo {
        private String fileUrl;
        private String fileName;
        private Long fileSizeBytes;
        private String mimeType;

        public AttachmentInfo() {}

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public Long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    }
}
