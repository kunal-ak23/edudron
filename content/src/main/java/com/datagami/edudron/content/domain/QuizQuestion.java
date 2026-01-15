package com.datagami.edudron.content.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions", schema = "content")
public class QuizQuestion {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "assessment_id", nullable = false, insertable = true, updatable = true)
    private String assessmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType questionType;

    @Column(nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(nullable = false)
    private Integer points = 1;

    @Column(nullable = false)
    private Integer sequence;

    @Column(columnDefinition = "text")
    private String explanation;

    // Tentative answer fields for subjective questions
    @Column(name = "tentative_answer", columnDefinition = "text")
    private String tentativeAnswer;

    @Column(name = "edited_tentative_answer", columnDefinition = "text")
    private String editedTentativeAnswer;

    @Column(name = "use_tentative_answer_for_grading", nullable = false)
    private Boolean useTentativeAnswerForGrading = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Relationships
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", insertable = false, updatable = false)
    private Assessment assessment;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<QuizOption> options = new ArrayList<>();

    public enum QuestionType {
        MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, ESSAY, MATCHING
    }

    // Constructors
    public QuizQuestion() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public List<QuizOption> getOptions() { return options; }
    public void setOptions(List<QuizOption> options) { this.options = options; }

    public String getTentativeAnswer() { return tentativeAnswer; }
    public void setTentativeAnswer(String tentativeAnswer) { this.tentativeAnswer = tentativeAnswer; }

    public String getEditedTentativeAnswer() { return editedTentativeAnswer; }
    public void setEditedTentativeAnswer(String editedTentativeAnswer) { this.editedTentativeAnswer = editedTentativeAnswer; }

    public Boolean getUseTentativeAnswerForGrading() { return useTentativeAnswerForGrading; }
    public void setUseTentativeAnswerForGrading(Boolean useTentativeAnswerForGrading) { this.useTentativeAnswerForGrading = useTentativeAnswerForGrading; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

