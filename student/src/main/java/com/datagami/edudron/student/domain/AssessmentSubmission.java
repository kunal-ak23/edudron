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

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public AssessmentSubmission() {
        this.submittedAt = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
    }

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
}

