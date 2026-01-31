package com.datagami.edudron.content.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assessments", schema = "content")
public class Assessment {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(name = "course_id", nullable = false, insertable = true, updatable = true)
    private String courseId;

    @Column(name = "class_id", insertable = true, updatable = true)
    private String classId;

    @Column(name = "section_id", insertable = true, updatable = true)
    private String sectionId;
    
    @Column(name = "lecture_id", insertable = true, updatable = true)
    private String lectureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentType assessmentType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(nullable = false)
    private Integer passingScorePercentage = 70;

    private Integer maxAttempts;
    private Integer timeLimitSeconds;

    @Column(nullable = false)
    private Boolean isRequired = false;

    @Column(nullable = false)
    private Boolean isPublished = false;

    @Column(nullable = false)
    private Integer sequence = 0;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Relationships (read-only, for navigation only)
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course course;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", insertable = false, updatable = false)
    private Section section;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", insertable = false, updatable = false)
    private Lecture lecture;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    public enum AssessmentType {
        QUIZ, ASSIGNMENT, PRACTICE_TEST, PROJECT, EXAM
    }
    
    public enum ExamStatus {
        DRAFT, SCHEDULED, LIVE, COMPLETED
    }
    
    public enum ReviewMethod {
        INSTRUCTOR, AI, BOTH
    }
    
    public enum ProctoringMode {
        DISABLED, BASIC_MONITORING, WEBCAM_RECORDING, LIVE_PROCTORING
    }
    
    /**
     * Timing mode for exams:
     * - FIXED_WINDOW: Exam ends at endTime for all students. Late joiners get less time.
     *                 Time remaining = min(timeLimitSeconds, endTime - now)
     * - FLEXIBLE_START: Each student gets full timeLimitSeconds from when they start.
     *                   Time remaining = timeLimitSeconds - (now - startedAt)
     */
    public enum TimingMode {
        FIXED_WINDOW, FLEXIBLE_START
    }
    
    // Exam-specific fields
    @Column(name = "start_time")
    private OffsetDateTime startTime;
    
    @Column(name = "end_time")
    private OffsetDateTime endTime;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExamStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "review_method", length = 20)
    private ReviewMethod reviewMethod;
    
    @Column(name = "module_ids", columnDefinition = "text[]")
    private List<String> moduleIds = new ArrayList<>();
    
    @Column(name = "randomize_questions", nullable = false)
    private Boolean randomizeQuestions = false;
    
    @Column(name = "randomize_mcq_options", nullable = false)
    private Boolean randomizeMcqOptions = false;
    
    // Proctoring fields
    @Column(name = "enable_proctoring", nullable = false)
    private Boolean enableProctoring = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "proctoring_mode", length = 30)
    private ProctoringMode proctoringMode;
    
    @Column(name = "photo_interval_seconds")
    private Integer photoIntervalSeconds = 120; // default 2 minutes
    
    @Column(name = "require_identity_verification")
    private Boolean requireIdentityVerification = false;
    
    @Column(name = "block_copy_paste")
    private Boolean blockCopyPaste = false;
    
    @Column(name = "block_tab_switch")
    private Boolean blockTabSwitch = false;
    
    @Column(name = "max_tab_switches_allowed")
    private Integer maxTabSwitchesAllowed = 3;
    
    // Timing mode for exams
    @Enumerated(EnumType.STRING)
    @Column(name = "timing_mode", length = 20)
    private TimingMode timingMode = TimingMode.FIXED_WINDOW;
    
    // Relationship to exam questions (from question bank)
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ExamQuestion> examQuestions = new ArrayList<>();

    // Constructors
    public Assessment() {
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

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public AssessmentType getAssessmentType() { return assessmentType; }
    public void setAssessmentType(AssessmentType assessmentType) { this.assessmentType = assessmentType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public Integer getPassingScorePercentage() { return passingScorePercentage; }
    public void setPassingScorePercentage(Integer passingScorePercentage) { this.passingScorePercentage = passingScorePercentage; }

    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(Integer timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }

    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public Section getSection() { return section; }
    public void setSection(Section section) { this.section = section; }

    public Lecture getLecture() { return lecture; }
    public void setLecture(Lecture lecture) { this.lecture = lecture; }

    public List<QuizQuestion> getQuestions() { return questions; }
    public void setQuestions(List<QuizQuestion> questions) { this.questions = questions; }

    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }

    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }

    public ExamStatus getStatus() { return status; }
    public void setStatus(ExamStatus status) { this.status = status; }

    public ReviewMethod getReviewMethod() { return reviewMethod; }
    public void setReviewMethod(ReviewMethod reviewMethod) { this.reviewMethod = reviewMethod; }

    public List<String> getModuleIds() { return moduleIds; }
    public void setModuleIds(List<String> moduleIds) { this.moduleIds = moduleIds; }
    
    public Boolean getRandomizeQuestions() { return randomizeQuestions; }
    public void setRandomizeQuestions(Boolean randomizeQuestions) { this.randomizeQuestions = randomizeQuestions; }
    
    public Boolean getRandomizeMcqOptions() { return randomizeMcqOptions; }
    public void setRandomizeMcqOptions(Boolean randomizeMcqOptions) { this.randomizeMcqOptions = randomizeMcqOptions; }
    
    public Boolean getEnableProctoring() { return enableProctoring; }
    public void setEnableProctoring(Boolean enableProctoring) { this.enableProctoring = enableProctoring; }
    
    public ProctoringMode getProctoringMode() { return proctoringMode; }
    public void setProctoringMode(ProctoringMode proctoringMode) { this.proctoringMode = proctoringMode; }
    
    public Integer getPhotoIntervalSeconds() { return photoIntervalSeconds; }
    public void setPhotoIntervalSeconds(Integer photoIntervalSeconds) { this.photoIntervalSeconds = photoIntervalSeconds; }
    
    public Boolean getRequireIdentityVerification() { return requireIdentityVerification; }
    public void setRequireIdentityVerification(Boolean requireIdentityVerification) { this.requireIdentityVerification = requireIdentityVerification; }
    
    public Boolean getBlockCopyPaste() { return blockCopyPaste; }
    public void setBlockCopyPaste(Boolean blockCopyPaste) { this.blockCopyPaste = blockCopyPaste; }
    
    public Boolean getBlockTabSwitch() { return blockTabSwitch; }
    public void setBlockTabSwitch(Boolean blockTabSwitch) { this.blockTabSwitch = blockTabSwitch; }
    
    public Integer getMaxTabSwitchesAllowed() { return maxTabSwitchesAllowed; }
    public void setMaxTabSwitchesAllowed(Integer maxTabSwitchesAllowed) { this.maxTabSwitchesAllowed = maxTabSwitchesAllowed; }
    
    public TimingMode getTimingMode() { return timingMode; }
    public void setTimingMode(TimingMode timingMode) { this.timingMode = timingMode; }
    
    public List<ExamQuestion> getExamQuestions() { return examQuestions; }
    public void setExamQuestions(List<ExamQuestion> examQuestions) { this.examQuestions = examQuestions; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

