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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "psych_test_option", schema = "content")
public class PsychTestOption {
    @Id
    private String id; // varchar(26)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private PsychTestQuestion question;

    @Column(nullable = false, columnDefinition = "text")
    private String label;

    @Column(nullable = false)
    private Integer value;

    @Column(name = "domain_tags", columnDefinition = "text[]")
    private List<String> domainTags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private JsonNode metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PsychTestQuestion getQuestion() {
        return question;
    }

    public void setQuestion(PsychTestQuestion question) {
        this.question = question;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public List<String> getDomainTags() {
        return domainTags;
    }

    public void setDomainTags(List<String> domainTags) {
        this.domainTags = domainTags;
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
}

