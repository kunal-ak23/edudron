package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quiz_options", schema = "content")
public class QuizOption {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "question_id", nullable = false, insertable = true, updatable = true)
    private String questionId;

    @Column(nullable = false, columnDefinition = "text")
    private String optionText;

    @Column(nullable = false)
    private Boolean isCorrect = false;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private QuizQuestion question;

    // Constructors
    public QuizOption() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public QuizQuestion getQuestion() { return question; }
    public void setQuestion(QuizQuestion question) { this.question = question; }
}

