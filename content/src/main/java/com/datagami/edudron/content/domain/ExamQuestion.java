package com.datagami.edudron.content.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Join table linking questions from the question bank to specific exams.
 * This allows the same question to be used in multiple exams with different point values.
 */
@Entity
@Table(name = "exam_questions", schema = "content",
    uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "question_id"}))
public class ExamQuestion {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "exam_id", nullable = false)
    private String examId; // FK to Assessment (exam)

    @Column(name = "question_id", nullable = false)
    private String questionId; // FK to QuestionBank

    @Column(nullable = false)
    private Integer sequence; // Order in the exam

    @Column(name = "points_override")
    private Integer pointsOverride; // Optional - override the question bank's default points

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Relationships
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", insertable = false, updatable = false)
    private Assessment exam;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private QuestionBank question;

    // Constructors
    public ExamQuestion() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Get the effective points for this question in the exam.
     * Returns pointsOverride if set, otherwise the question's default points.
     */
    public Integer getEffectivePoints() {
        if (pointsOverride != null) {
            return pointsOverride;
        }
        return question != null ? question.getDefaultPoints() : 1;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getExamId() { return examId; }
    public void setExamId(String examId) { this.examId = examId; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public Integer getPointsOverride() { return pointsOverride; }
    public void setPointsOverride(Integer pointsOverride) { this.pointsOverride = pointsOverride; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Assessment getExam() { return exam; }
    public void setExam(Assessment exam) { this.exam = exam; }

    public QuestionBank getQuestion() { return question; }
    public void setQuestion(QuestionBank question) { this.question = question; }
}
