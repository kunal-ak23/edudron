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
@Table(name = "psych_test_question_asked", schema = "content")
public class PsychTestQuestionAsked {
    @Id
    private String id; // ULID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PsychTestSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private PsychTestQuestion question;

    @Column(name = "question_number", nullable = false)
    private Integer questionNumber;

    @Column(name = "asked_at", nullable = false)
    private OffsetDateTime askedAt = OffsetDateTime.now();

    @Column(name = "rendered_prompt", nullable = false, columnDefinition = "text")
    private String renderedPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rendered_options_json", columnDefinition = "jsonb")
    private JsonNode renderedOptionsJson;

    @Column(name = "personalization_source", length = 20)
    private String personalizationSource = "RAW";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personalization_json", columnDefinition = "jsonb")
    private JsonNode personalizationJson;

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

    public PsychTestQuestion getQuestion() {
        return question;
    }

    public void setQuestion(PsychTestQuestion question) {
        this.question = question;
    }

    public Integer getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(Integer questionNumber) {
        this.questionNumber = questionNumber;
    }

    public OffsetDateTime getAskedAt() {
        return askedAt;
    }

    public void setAskedAt(OffsetDateTime askedAt) {
        this.askedAt = askedAt;
    }

    public String getRenderedPrompt() {
        return renderedPrompt;
    }

    public void setRenderedPrompt(String renderedPrompt) {
        this.renderedPrompt = renderedPrompt;
    }

    public JsonNode getRenderedOptionsJson() {
        return renderedOptionsJson;
    }

    public void setRenderedOptionsJson(JsonNode renderedOptionsJson) {
        this.renderedOptionsJson = renderedOptionsJson;
    }

    public String getPersonalizationSource() {
        return personalizationSource;
    }

    public void setPersonalizationSource(String personalizationSource) {
        this.personalizationSource = personalizationSource;
    }

    public JsonNode getPersonalizationJson() {
        return personalizationJson;
    }

    public void setPersonalizationJson(JsonNode personalizationJson) {
        this.personalizationJson = personalizationJson;
    }
}

