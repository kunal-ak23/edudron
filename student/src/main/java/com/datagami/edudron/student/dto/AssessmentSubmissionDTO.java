package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AssessmentSubmissionDTO {
    private String id;
    private UUID clientId;
    private String studentId;
    private String enrollmentId;
    private String assessmentId;
    private String courseId;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal percentage;
    private Boolean isPassed;
    private JsonNode answersJson;
    private OffsetDateTime submittedAt;
    private OffsetDateTime gradedAt;
    private OffsetDateTime createdAt;
    
    // Exam-specific fields
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Integer timeRemainingSeconds;
    private String reviewStatus;
    private JsonNode aiReviewFeedback;

    // Constructors
    public AssessmentSubmissionDTO() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }

    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

    public Boolean getIsPassed() { return isPassed; }
    public void setIsPassed(Boolean isPassed) { this.isPassed = isPassed; }

    public JsonNode getAnswersJson() { return answersJson; }
    public void setAnswersJson(JsonNode answersJson) { this.answersJson = answersJson; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(OffsetDateTime gradedAt) { this.gradedAt = gradedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public Integer getTimeRemainingSeconds() { return timeRemainingSeconds; }
    public void setTimeRemainingSeconds(Integer timeRemainingSeconds) { this.timeRemainingSeconds = timeRemainingSeconds; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public JsonNode getAiReviewFeedback() { return aiReviewFeedback; }
    public void setAiReviewFeedback(JsonNode aiReviewFeedback) { this.aiReviewFeedback = aiReviewFeedback; }
}


