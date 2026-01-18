package com.datagami.edudron.content.psychtest.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "psych_test_result", schema = "content")
public class PsychTestResult {
    @Id
    private String id; // ULID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PsychTestSession session;

    @Column(name = "overall_confidence", nullable = false, length = 10)
    private String overallConfidence; // HIGH|MEDIUM|LOW

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_scores_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode domainScoresJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_domains_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode topDomainsJson;

    @Column(name = "stream_suggestion", columnDefinition = "text")
    private String streamSuggestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "career_fields_json", columnDefinition = "jsonb")
    private JsonNode careerFieldsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_courses_json", columnDefinition = "jsonb")
    private JsonNode recommendedCoursesJson;

    @Column(name = "report_text", columnDefinition = "text")
    private String reportText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanations_json", columnDefinition = "jsonb")
    private JsonNode explanationsJson;

    @Column(name = "test_version", nullable = false, length = 20)
    private String testVersion;

    @Column(name = "bank_version", nullable = false, length = 20)
    private String bankVersion;

    @Column(name = "scoring_version", nullable = false, length = 20)
    private String scoringVersion;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PsychTestSession getSession() {
        return session;
    }

    public void setSession(PsychTestSession session) {
        this.session = session;
    }

    public String getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(String overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public JsonNode getDomainScoresJson() {
        return domainScoresJson;
    }

    public void setDomainScoresJson(JsonNode domainScoresJson) {
        this.domainScoresJson = domainScoresJson;
    }

    public JsonNode getTopDomainsJson() {
        return topDomainsJson;
    }

    public void setTopDomainsJson(JsonNode topDomainsJson) {
        this.topDomainsJson = topDomainsJson;
    }

    public String getStreamSuggestion() {
        return streamSuggestion;
    }

    public void setStreamSuggestion(String streamSuggestion) {
        this.streamSuggestion = streamSuggestion;
    }

    public JsonNode getCareerFieldsJson() {
        return careerFieldsJson;
    }

    public void setCareerFieldsJson(JsonNode careerFieldsJson) {
        this.careerFieldsJson = careerFieldsJson;
    }

    public JsonNode getRecommendedCoursesJson() {
        return recommendedCoursesJson;
    }

    public void setRecommendedCoursesJson(JsonNode recommendedCoursesJson) {
        this.recommendedCoursesJson = recommendedCoursesJson;
    }

    public String getReportText() {
        return reportText;
    }

    public void setReportText(String reportText) {
        this.reportText = reportText;
    }

    public JsonNode getExplanationsJson() {
        return explanationsJson;
    }

    public void setExplanationsJson(JsonNode explanationsJson) {
        this.explanationsJson = explanationsJson;
    }

    public String getTestVersion() {
        return testVersion;
    }

    public void setTestVersion(String testVersion) {
        this.testVersion = testVersion;
    }

    public String getBankVersion() {
        return bankVersion;
    }

    public void setBankVersion(String bankVersion) {
        this.bankVersion = bankVersion;
    }

    public String getScoringVersion() {
        return scoringVersion;
    }

    public void setScoringVersion(String scoringVersion) {
        this.scoringVersion = scoringVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

