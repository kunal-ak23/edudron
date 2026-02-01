package com.datagami.edudron.content.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Standalone question bank entity for reusable questions.
 * Questions can be tagged to multiple modules (sections) and optionally a sub-module (lecture).
 * These questions can then be selected to create exam papers.
 */
@Entity
@Table(name = "question_bank", schema = "content")
public class QuestionBank {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Column(name = "module_ids", columnDefinition = "text[]")
    private List<String> moduleIds = new ArrayList<>(); // Maps to Section(s) - supports multiple modules

    @Column(name = "sub_module_ids", columnDefinition = "text[]")
    private List<String> subModuleIds = new ArrayList<>(); // Optional - maps to Lecture(s) for finer categorization

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "default_points", nullable = false)
    private Integer defaultPoints = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", length = 10)
    private DifficultyLevel difficultyLevel;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Tentative answer for subjective questions (SHORT_ANSWER, ESSAY)
    @Column(name = "tentative_answer", columnDefinition = "text")
    private String tentativeAnswer;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Relationships
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course course;

    // Note: Module relationship removed since we now support multiple modules via moduleIds array

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<QuestionBankOption> options = new ArrayList<>();

    public enum QuestionType {
        MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, ESSAY, MATCHING
    }

    public enum DifficultyLevel {
        EASY, MEDIUM, HARD
    }

    // Constructors
    public QuestionBank() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public List<String> getModuleIds() { return moduleIds; }
    public void setModuleIds(List<String> moduleIds) { this.moduleIds = moduleIds != null ? moduleIds : new ArrayList<>(); }

    public List<String> getSubModuleIds() { return subModuleIds; }
    public void setSubModuleIds(List<String> subModuleIds) { this.subModuleIds = subModuleIds != null ? subModuleIds : new ArrayList<>(); }

    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public Integer getDefaultPoints() { return defaultPoints; }
    public void setDefaultPoints(Integer defaultPoints) { this.defaultPoints = defaultPoints; }

    public DifficultyLevel getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(DifficultyLevel difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getTentativeAnswer() { return tentativeAnswer; }
    public void setTentativeAnswer(String tentativeAnswer) { this.tentativeAnswer = tentativeAnswer; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public List<QuestionBankOption> getOptions() { return options; }
    public void setOptions(List<QuestionBankOption> options) { this.options = options; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
