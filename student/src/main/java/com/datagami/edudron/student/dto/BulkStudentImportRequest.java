package com.datagami.edudron.student.dto;

import java.util.List;

public class BulkStudentImportRequest {
    private Boolean autoGeneratePassword = true;
    private Boolean upsertExisting = false;
    private Boolean autoEnroll = false;
    private List<String> defaultCourseIds; // Optional: enroll all imported students to these courses

    // Getters and Setters
    public Boolean getAutoGeneratePassword() {
        return autoGeneratePassword;
    }

    public void setAutoGeneratePassword(Boolean autoGeneratePassword) {
        this.autoGeneratePassword = autoGeneratePassword;
    }

    public Boolean getUpsertExisting() {
        return upsertExisting;
    }

    public void setUpsertExisting(Boolean upsertExisting) {
        this.upsertExisting = upsertExisting;
    }

    public Boolean getAutoEnroll() {
        return autoEnroll;
    }

    public void setAutoEnroll(Boolean autoEnroll) {
        this.autoEnroll = autoEnroll;
    }

    public List<String> getDefaultCourseIds() {
        return defaultCourseIds;
    }

    public void setDefaultCourseIds(List<String> defaultCourseIds) {
        this.defaultCourseIds = defaultCourseIds;
    }
}

