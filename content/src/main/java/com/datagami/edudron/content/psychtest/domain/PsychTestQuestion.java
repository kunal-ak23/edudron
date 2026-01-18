package com.datagami.edudron.content.psychtest.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "psych_test_question", schema = "content")
public class PsychTestQuestion {
    public enum Type {
        LIKERT,
        SCENARIO_MCQ,
        OPEN_ENDED
    }

    @Id
    private String id; // varchar(26)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "domain_tags", columnDefinition = "text[]")
    private List<String> domainTags = new ArrayList<>();

    @Column(name = "reverse_scored", nullable = false)
    private Boolean reverseScored = false;

    @Column(name = "grade_band", length = 20)
    private String gradeBand;

    @Column(nullable = false)
    private Double weight = 1.0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "bank_version", nullable = false, length = 20)
    private String bankVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private JsonNode metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<String> getDomainTags() {
        return domainTags;
    }

    public void setDomainTags(List<String> domainTags) {
        this.domainTags = domainTags;
    }

    public Boolean getReverseScored() {
        return reverseScored;
    }

    public void setReverseScored(Boolean reverseScored) {
        this.reverseScored = reverseScored;
    }

    public String getGradeBand() {
        return gradeBand;
    }

    public void setGradeBand(String gradeBand) {
        this.gradeBand = gradeBand;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getBankVersion() {
        return bankVersion;
    }

    public void setBankVersion(String bankVersion) {
        this.bankVersion = bankVersion;
    }

    public JsonNode getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(JsonNode metadataJson) {
        this.metadataJson = metadataJson;
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
}

