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
@Table(name = "psych_test_answer", schema = "content")
public class PsychTestAnswer {
    @Id
    private String id; // ULID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PsychTestSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private PsychTestQuestion question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode answerJson;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt = OffsetDateTime.now();

    @Column(name = "time_spent_ms")
    private Integer timeSpentMs;

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

    public JsonNode getAnswerJson() {
        return answerJson;
    }

    public void setAnswerJson(JsonNode answerJson) {
        this.answerJson = answerJson;
    }

    public OffsetDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(OffsetDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    public Integer getTimeSpentMs() {
        return timeSpentMs;
    }

    public void setTimeSpentMs(Integer timeSpentMs) {
        this.timeSpentMs = timeSpentMs;
    }
}

