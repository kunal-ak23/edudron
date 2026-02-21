package com.datagami.edudron.student.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assessment_submissions", schema = "student")
public class AssessmentSubmission {
    @Id
    private String id; // ULID

    @Version
    @Column(name = "version")
    private Long version;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String enrollmentId;

    @Column(nullable = false)
    private String assessmentId;

    @Column(nullable = false)
    private String courseId;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(nullable = false)
    private Boolean isPassed = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode answersJson;

    @Column(nullable = false)
    private OffsetDateTime submittedAt;

    private OffsetDateTime gradedAt;

    // Exam-specific fields
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "time_remaining_seconds")
    private Integer timeRemainingSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    private ReviewStatus reviewStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_review_feedback", columnDefinition = "jsonb")
    private JsonNode aiReviewFeedback;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_order", columnDefinition = "jsonb")
    private JsonNode questionOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mcq_option_orders", columnDefinition = "jsonb")
    private JsonNode mcqOptionOrders;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Proctoring fields
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proctoring_data", columnDefinition = "jsonb")
    private JsonNode proctoringData;

    @Column(name = "tab_switch_count")
    private Integer tabSwitchCount = 0;

    @Column(name = "copy_attempt_count")
    private Integer copyAttemptCount = 0;

    @Column(name = "identity_verified")
    private Boolean identityVerified = false;

    @Column(name = "identity_verification_photo_url", length = 500)
    private String identityVerificationPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "proctoring_status", length = 20)
    private ProctoringStatus proctoringStatus;

    @Column(name = "marked_as_cheating")
    private Boolean markedAsCheating = false;

    public enum ReviewStatus {
        PENDING, AI_REVIEWED, INSTRUCTOR_REVIEWED, COMPLETED
    }

    public enum ProctoringStatus {
        CLEAR, FLAGGED, SUSPICIOUS, VIOLATION
    }

    // Constructors
    public AssessmentSubmission() {
        this.submittedAt = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public Boolean getIsPassed() {
        return isPassed;
    }

    public void setIsPassed(Boolean isPassed) {
        this.isPassed = isPassed;
    }

    public JsonNode getAnswersJson() {
        return answersJson;
    }

    public void setAnswersJson(JsonNode answersJson) {
        this.answersJson = answersJson;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getGradedAt() {
        return gradedAt;
    }

    public void setGradedAt(OffsetDateTime gradedAt) {
        this.gradedAt = gradedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getTimeRemainingSeconds() {
        return timeRemainingSeconds;
    }

    public void setTimeRemainingSeconds(Integer timeRemainingSeconds) {
        this.timeRemainingSeconds = timeRemainingSeconds;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public JsonNode getAiReviewFeedback() {
        return aiReviewFeedback;
    }

    public void setAiReviewFeedback(JsonNode aiReviewFeedback) {
        this.aiReviewFeedback = aiReviewFeedback;
    }

    public JsonNode getQuestionOrder() {
        return questionOrder;
    }

    public void setQuestionOrder(JsonNode questionOrder) {
        this.questionOrder = questionOrder;
    }

    public JsonNode getMcqOptionOrders() {
        return mcqOptionOrders;
    }

    public void setMcqOptionOrders(JsonNode mcqOptionOrders) {
        this.mcqOptionOrders = mcqOptionOrders;
    }

    public JsonNode getProctoringData() {
        return proctoringData;
    }

    public void setProctoringData(JsonNode proctoringData) {
        this.proctoringData = proctoringData;
    }

    public Integer getTabSwitchCount() {
        return tabSwitchCount;
    }

    public void setTabSwitchCount(Integer tabSwitchCount) {
        this.tabSwitchCount = tabSwitchCount;
    }

    public Integer getCopyAttemptCount() {
        return copyAttemptCount;
    }

    public void setCopyAttemptCount(Integer copyAttemptCount) {
        this.copyAttemptCount = copyAttemptCount;
    }

    public Boolean getIdentityVerified() {
        return identityVerified;
    }

    public void setIdentityVerified(Boolean identityVerified) {
        this.identityVerified = identityVerified;
    }

    public String getIdentityVerificationPhotoUrl() {
        return identityVerificationPhotoUrl;
    }

    public void setIdentityVerificationPhotoUrl(String identityVerificationPhotoUrl) {
        this.identityVerificationPhotoUrl = identityVerificationPhotoUrl;
    }

    public ProctoringStatus getProctoringStatus() {
        return proctoringStatus;
    }

    public void setProctoringStatus(ProctoringStatus proctoringStatus) {
        this.proctoringStatus = proctoringStatus;
    }

    public Boolean getMarkedAsCheating() {
        return markedAsCheating;
    }

    public void setMarkedAsCheating(Boolean markedAsCheating) {
        this.markedAsCheating = markedAsCheating;
    }
}
