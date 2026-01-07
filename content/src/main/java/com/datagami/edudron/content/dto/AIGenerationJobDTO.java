package com.datagami.edudron.content.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AIGenerationJobDTO {
    private String jobId;
    private JobType jobType;
    private JobStatus status;
    private String message;
    private UUID clientId;
    private String userId;
    private Object result; // CourseDTO, SectionDTO, or LectureDTO depending on job type
    private String error;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer progress; // 0-100

    public enum JobType {
        COURSE_GENERATION,
        LECTURE_GENERATION,
        SUB_LECTURE_GENERATION
    }

    public enum JobStatus {
        PENDING,
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }
}

