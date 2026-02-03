package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for bulk grading submissions for an assessment.
 * Matches the shape used by single POST /api/assessments/submissions/{submissionId}/grade.
 */
public class BulkGradeRequest {
    private List<BulkGradeItem> grades;

    public BulkGradeRequest() {}

    public BulkGradeRequest(List<BulkGradeItem> grades) {
        this.grades = grades;
    }

    public List<BulkGradeItem> getGrades() {
        return grades;
    }

    public void setGrades(List<BulkGradeItem> grades) {
        this.grades = grades;
    }

    public static class BulkGradeItem {
        private String submissionId;
        private BigDecimal score;
        private BigDecimal maxScore;
        private BigDecimal percentage;
        private Boolean isPassed;
        private JsonNode aiReviewFeedback;
        private String reviewStatus;

        public BulkGradeItem() {}

        public String getSubmissionId() { return submissionId; }
        public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }

        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }

        public BigDecimal getPercentage() { return percentage; }
        public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

        public Boolean getIsPassed() { return isPassed; }
        public void setIsPassed(Boolean isPassed) { this.isPassed = isPassed; }

        public JsonNode getAiReviewFeedback() { return aiReviewFeedback; }
        public void setAiReviewFeedback(JsonNode aiReviewFeedback) { this.aiReviewFeedback = aiReviewFeedback; }

        public String getReviewStatus() { return reviewStatus; }
        public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    }
}
