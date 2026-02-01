package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.ExamQuestion;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.domain.QuestionBankOption;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.domain.QuizOption;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for exam detail response that unifies questions from both sources:
 * - QuizQuestion (inline questions)
 * - ExamQuestion (questions from question bank)
 */
public class ExamDetailDTO {
    private String id;
    private UUID clientId;
    private String courseId;
    private String classId;
    private String sectionId;
    private String lectureId;
    private String assessmentType;
    private String title;
    private String description;
    private String instructions;
    private Integer passingScorePercentage;
    private Integer maxAttempts;
    private Integer timeLimitSeconds;
    private Boolean isRequired;
    private Boolean isPublished;
    private Integer sequence;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // Exam-specific fields
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String status;
    private String reviewMethod;
    private List<String> moduleIds;
    private Boolean randomizeQuestions;
    private Boolean randomizeMcqOptions;
    
    // Proctoring fields
    private Boolean enableProctoring;
    private String proctoringMode;
    private Integer photoIntervalSeconds;
    private Boolean requireIdentityVerification;
    private Boolean blockCopyPaste;
    private Boolean blockTabSwitch;
    private Integer maxTabSwitchesAllowed;
    private String timingMode;
    private Boolean archived;
    
    // Unified questions list
    private List<QuestionDTO> questions = new ArrayList<>();
    
    public ExamDetailDTO() {}
    
    public static ExamDetailDTO fromAssessment(Assessment exam) {
        ExamDetailDTO dto = new ExamDetailDTO();
        
        dto.id = exam.getId();
        dto.clientId = exam.getClientId();
        dto.courseId = exam.getCourseId();
        dto.classId = exam.getClassId();
        dto.sectionId = exam.getSectionId();
        dto.lectureId = exam.getLectureId();
        dto.assessmentType = exam.getAssessmentType() != null ? exam.getAssessmentType().name() : null;
        dto.title = exam.getTitle();
        dto.description = exam.getDescription();
        dto.instructions = exam.getInstructions();
        dto.passingScorePercentage = exam.getPassingScorePercentage();
        dto.maxAttempts = exam.getMaxAttempts();
        dto.timeLimitSeconds = exam.getTimeLimitSeconds();
        dto.isRequired = exam.getIsRequired();
        dto.isPublished = exam.getIsPublished();
        dto.sequence = exam.getSequence();
        dto.createdAt = exam.getCreatedAt();
        dto.updatedAt = exam.getUpdatedAt();
        
        // Exam-specific fields
        dto.startTime = exam.getStartTime();
        dto.endTime = exam.getEndTime();
        dto.status = exam.getStatus() != null ? exam.getStatus().name() : null;
        dto.reviewMethod = exam.getReviewMethod() != null ? exam.getReviewMethod().name() : null;
        dto.moduleIds = exam.getModuleIds();
        dto.randomizeQuestions = exam.getRandomizeQuestions();
        dto.randomizeMcqOptions = exam.getRandomizeMcqOptions();
        
        // Proctoring fields
        dto.enableProctoring = exam.getEnableProctoring();
        dto.proctoringMode = exam.getProctoringMode() != null ? exam.getProctoringMode().name() : null;
        dto.photoIntervalSeconds = exam.getPhotoIntervalSeconds();
        dto.requireIdentityVerification = exam.getRequireIdentityVerification();
        dto.blockCopyPaste = exam.getBlockCopyPaste();
        dto.blockTabSwitch = exam.getBlockTabSwitch();
        dto.maxTabSwitchesAllowed = exam.getMaxTabSwitchesAllowed();
        dto.timingMode = exam.getTimingMode() != null ? exam.getTimingMode().name() : null;
        dto.archived = exam.getArchived();
        
        // Build unified questions list from both sources
        dto.questions = new ArrayList<>();
        
        // Add questions from ExamQuestion (from question bank)
        if (exam.getExamQuestions() != null) {
            for (ExamQuestion eq : exam.getExamQuestions()) {
                QuestionDTO qDto = QuestionDTO.fromExamQuestion(eq);
                if (qDto != null) {
                    dto.questions.add(qDto);
                }
            }
        }
        
        // Add questions from QuizQuestion (inline questions) - for backward compatibility
        if (exam.getQuestions() != null) {
            for (QuizQuestion qq : exam.getQuestions()) {
                QuestionDTO qDto = QuestionDTO.fromQuizQuestion(qq);
                if (qDto != null) {
                    dto.questions.add(qDto);
                }
            }
        }
        
        // Sort by sequence
        dto.questions.sort((a, b) -> {
            int seqA = a.getSequence() != null ? a.getSequence() : 0;
            int seqB = b.getSequence() != null ? b.getSequence() : 0;
            return Integer.compare(seqA, seqB);
        });
        
        return dto;
    }
    
    // Inner DTO for questions
    public static class QuestionDTO {
        private String id;
        private String questionText;
        private String questionType;
        private Integer points;
        private Integer sequence;
        private String tentativeAnswer;
        private String editedTentativeAnswer;
        private Boolean useTentativeAnswerForGrading;
        private String explanation;
        private String difficultyLevel;
        private List<OptionDTO> options = new ArrayList<>();
        
        // Source tracking (for editing purposes)
        private String sourceType; // "EXAM_QUESTION" or "QUIZ_QUESTION"
        private String questionBankId; // Only for EXAM_QUESTION
        
        public static QuestionDTO fromExamQuestion(ExamQuestion eq) {
            if (eq == null || eq.getQuestion() == null) {
                return null;
            }
            
            QuestionBank qb = eq.getQuestion();
            QuestionDTO dto = new QuestionDTO();
            
            dto.id = eq.getId(); // Use ExamQuestion ID for deletion/editing
            dto.questionText = qb.getQuestionText();
            dto.questionType = qb.getQuestionType() != null ? qb.getQuestionType().name() : null;
            dto.points = eq.getEffectivePoints(); // Use effective points (override or default)
            dto.sequence = eq.getSequence();
            dto.tentativeAnswer = qb.getTentativeAnswer();
            dto.editedTentativeAnswer = null; // ExamQuestion doesn't have this
            dto.useTentativeAnswerForGrading = false;
            dto.explanation = qb.getExplanation();
            dto.difficultyLevel = qb.getDifficultyLevel() != null ? qb.getDifficultyLevel().name() : null;
            dto.sourceType = "EXAM_QUESTION";
            dto.questionBankId = qb.getId();
            
            // Convert options
            if (qb.getOptions() != null) {
                int seq = 1;
                for (QuestionBankOption opt : qb.getOptions()) {
                    OptionDTO optDto = new OptionDTO();
                    optDto.id = opt.getId();
                    optDto.optionText = opt.getOptionText();
                    optDto.isCorrect = opt.getIsCorrect();
                    optDto.sequence = opt.getSequence() != null ? opt.getSequence() : seq++;
                    dto.options.add(optDto);
                }
            }
            
            return dto;
        }
        
        public static QuestionDTO fromQuizQuestion(QuizQuestion qq) {
            if (qq == null) {
                return null;
            }
            
            QuestionDTO dto = new QuestionDTO();
            
            dto.id = qq.getId();
            dto.questionText = qq.getQuestionText();
            dto.questionType = qq.getQuestionType() != null ? qq.getQuestionType().name() : null;
            dto.points = qq.getPoints();
            dto.sequence = qq.getSequence();
            dto.tentativeAnswer = qq.getTentativeAnswer();
            dto.editedTentativeAnswer = qq.getEditedTentativeAnswer();
            dto.useTentativeAnswerForGrading = qq.getUseTentativeAnswerForGrading();
            dto.explanation = qq.getExplanation();
            dto.difficultyLevel = null; // QuizQuestion doesn't have difficulty level
            dto.sourceType = "QUIZ_QUESTION";
            dto.questionBankId = null;
            
            // Convert options
            if (qq.getOptions() != null) {
                int seq = 1;
                for (QuizOption opt : qq.getOptions()) {
                    OptionDTO optDto = new OptionDTO();
                    optDto.id = opt.getId();
                    optDto.optionText = opt.getOptionText();
                    optDto.isCorrect = opt.getIsCorrect();
                    optDto.sequence = opt.getSequence() != null ? opt.getSequence() : seq++;
                    dto.options.add(optDto);
                }
            }
            
            return dto;
        }
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        
        public Integer getPoints() { return points; }
        public void setPoints(Integer points) { this.points = points; }
        
        public Integer getSequence() { return sequence; }
        public void setSequence(Integer sequence) { this.sequence = sequence; }
        
        public String getTentativeAnswer() { return tentativeAnswer; }
        public void setTentativeAnswer(String tentativeAnswer) { this.tentativeAnswer = tentativeAnswer; }
        
        public String getEditedTentativeAnswer() { return editedTentativeAnswer; }
        public void setEditedTentativeAnswer(String editedTentativeAnswer) { this.editedTentativeAnswer = editedTentativeAnswer; }
        
        public Boolean getUseTentativeAnswerForGrading() { return useTentativeAnswerForGrading; }
        public void setUseTentativeAnswerForGrading(Boolean useTentativeAnswerForGrading) { this.useTentativeAnswerForGrading = useTentativeAnswerForGrading; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public String getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }
        
        public List<OptionDTO> getOptions() { return options; }
        public void setOptions(List<OptionDTO> options) { this.options = options; }
        
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        
        public String getQuestionBankId() { return questionBankId; }
        public void setQuestionBankId(String questionBankId) { this.questionBankId = questionBankId; }
    }
    
    // Inner DTO for options
    public static class OptionDTO {
        private String id;
        private String optionText;
        private Boolean isCorrect;
        private Integer sequence;
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getOptionText() { return optionText; }
        public void setOptionText(String optionText) { this.optionText = optionText; }
        
        public Boolean getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
        
        public Integer getSequence() { return sequence; }
        public void setSequence(Integer sequence) { this.sequence = sequence; }
    }
    
    // Getters and Setters for main DTO
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }
    
    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }
    
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    
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
    
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getReviewMethod() { return reviewMethod; }
    public void setReviewMethod(String reviewMethod) { this.reviewMethod = reviewMethod; }
    
    public List<String> getModuleIds() { return moduleIds; }
    public void setModuleIds(List<String> moduleIds) { this.moduleIds = moduleIds; }
    
    public Boolean getRandomizeQuestions() { return randomizeQuestions; }
    public void setRandomizeQuestions(Boolean randomizeQuestions) { this.randomizeQuestions = randomizeQuestions; }
    
    public Boolean getRandomizeMcqOptions() { return randomizeMcqOptions; }
    public void setRandomizeMcqOptions(Boolean randomizeMcqOptions) { this.randomizeMcqOptions = randomizeMcqOptions; }
    
    public Boolean getEnableProctoring() { return enableProctoring; }
    public void setEnableProctoring(Boolean enableProctoring) { this.enableProctoring = enableProctoring; }
    
    public String getProctoringMode() { return proctoringMode; }
    public void setProctoringMode(String proctoringMode) { this.proctoringMode = proctoringMode; }
    
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
    
    public String getTimingMode() { return timingMode; }
    public void setTimingMode(String timingMode) { this.timingMode = timingMode; }
    
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    
    public List<QuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDTO> questions) { this.questions = questions; }
}
