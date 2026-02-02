package com.datagami.edudron.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for batch exam generation.
 * Creates separate exams for each selected section with randomized question selection.
 */
public class BatchExamGenerationRequest {
    
    @NotBlank(message = "Course ID is required")
    private String courseId;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    private String instructions;
    
    @NotEmpty(message = "At least one section must be selected")
    private List<String> sectionIds;
    
    private List<String> moduleIds;
    
    // Generation criteria
    private GenerationCriteria generationCriteria;
    
    // Exam settings
    private ExamSettings examSettings;
    
    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    
    public List<String> getSectionIds() { return sectionIds; }
    public void setSectionIds(List<String> sectionIds) { this.sectionIds = sectionIds; }
    
    public List<String> getModuleIds() { return moduleIds; }
    public void setModuleIds(List<String> moduleIds) { this.moduleIds = moduleIds; }
    
    public GenerationCriteria getGenerationCriteria() { return generationCriteria; }
    public void setGenerationCriteria(GenerationCriteria generationCriteria) { this.generationCriteria = generationCriteria; }
    
    public ExamSettings getExamSettings() { return examSettings; }
    public void setExamSettings(ExamSettings examSettings) { this.examSettings = examSettings; }
    
    /**
     * Criteria for question selection from the question bank.
     */
    public static class GenerationCriteria {
        private Integer numberOfQuestions = 10;
        private String difficultyLevel; // EASY, MEDIUM, HARD or null for any
        private Map<String, Integer> difficultyDistribution; // e.g., {"EASY": 3, "MEDIUM": 5, "HARD": 2}
        private List<String> questionTypes; // MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, ESSAY, MATCHING
        private boolean randomize = true;
        private boolean uniquePerSection = true; // Different question selection per section
        
        public Integer getNumberOfQuestions() { return numberOfQuestions; }
        public void setNumberOfQuestions(Integer numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }
        
        public String getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }
        
        public Map<String, Integer> getDifficultyDistribution() { return difficultyDistribution; }
        public void setDifficultyDistribution(Map<String, Integer> difficultyDistribution) { this.difficultyDistribution = difficultyDistribution; }
        
        public List<String> getQuestionTypes() { return questionTypes; }
        public void setQuestionTypes(List<String> questionTypes) { this.questionTypes = questionTypes; }
        
        public boolean isRandomize() { return randomize; }
        public void setRandomize(boolean randomize) { this.randomize = randomize; }
        
        public boolean isUniquePerSection() { return uniquePerSection; }
        public void setUniquePerSection(boolean uniquePerSection) { this.uniquePerSection = uniquePerSection; }
    }
    
    /**
     * Settings for the generated exams.
     */
    public static class ExamSettings {
        private String reviewMethod = "INSTRUCTOR"; // INSTRUCTOR, AI, BOTH
        private Integer timeLimitSeconds;
        private Integer passingScorePercentage = 70;
        private boolean randomizeQuestions = false;
        private boolean randomizeMcqOptions = false;
        
        // Proctoring settings
        private boolean enableProctoring = false;
        private String proctoringMode = "BASIC_MONITORING"; // DISABLED, BASIC_MONITORING, WEBCAM_RECORDING, LIVE_PROCTORING
        private Integer photoIntervalSeconds = 30;
        private boolean requireIdentityVerification = false;
        private boolean blockCopyPaste = false;
        private boolean blockTabSwitch = false;
        private Integer maxTabSwitchesAllowed = 3;
        
        // Timing mode: FIXED_WINDOW (exam ends at endTime for all) or FLEXIBLE_START (each student gets full duration)
        private String timingMode = "FIXED_WINDOW";
        
        // Start and end times for FIXED_WINDOW mode
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        
        public String getReviewMethod() { return reviewMethod; }
        public void setReviewMethod(String reviewMethod) { this.reviewMethod = reviewMethod; }
        
        public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
        public void setTimeLimitSeconds(Integer timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }
        
        public Integer getPassingScorePercentage() { return passingScorePercentage; }
        public void setPassingScorePercentage(Integer passingScorePercentage) { this.passingScorePercentage = passingScorePercentage; }
        
        public boolean isRandomizeQuestions() { return randomizeQuestions; }
        public void setRandomizeQuestions(boolean randomizeQuestions) { this.randomizeQuestions = randomizeQuestions; }
        
        public boolean isRandomizeMcqOptions() { return randomizeMcqOptions; }
        public void setRandomizeMcqOptions(boolean randomizeMcqOptions) { this.randomizeMcqOptions = randomizeMcqOptions; }
        
        public boolean isEnableProctoring() { return enableProctoring; }
        public void setEnableProctoring(boolean enableProctoring) { this.enableProctoring = enableProctoring; }
        
        public String getProctoringMode() { return proctoringMode; }
        public void setProctoringMode(String proctoringMode) { this.proctoringMode = proctoringMode; }
        
        public Integer getPhotoIntervalSeconds() { return photoIntervalSeconds; }
        public void setPhotoIntervalSeconds(Integer photoIntervalSeconds) { this.photoIntervalSeconds = photoIntervalSeconds; }
        
        public boolean isRequireIdentityVerification() { return requireIdentityVerification; }
        public void setRequireIdentityVerification(boolean requireIdentityVerification) { this.requireIdentityVerification = requireIdentityVerification; }
        
        public boolean isBlockCopyPaste() { return blockCopyPaste; }
        public void setBlockCopyPaste(boolean blockCopyPaste) { this.blockCopyPaste = blockCopyPaste; }
        
        public boolean isBlockTabSwitch() { return blockTabSwitch; }
        public void setBlockTabSwitch(boolean blockTabSwitch) { this.blockTabSwitch = blockTabSwitch; }
        
        public Integer getMaxTabSwitchesAllowed() { return maxTabSwitchesAllowed; }
        public void setMaxTabSwitchesAllowed(Integer maxTabSwitchesAllowed) { this.maxTabSwitchesAllowed = maxTabSwitchesAllowed; }
        
        public String getTimingMode() { return timingMode; }
        public void setTimingMode(String timingMode) { this.timingMode = timingMode; }
        
        public OffsetDateTime getStartTime() { return startTime; }
        public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
        
        public OffsetDateTime getEndTime() { return endTime; }
        public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    }
}
