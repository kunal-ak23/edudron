package com.datagami.edudron.content.dto;

import com.datagami.edudron.content.domain.Assessment;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for batch exam generation.
 * Contains the list of created exams and any generation details.
 */
public class BatchExamGenerationResponse {
    
    private int totalRequested;
    private int totalCreated;
    private List<GeneratedExam> exams = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    
    public BatchExamGenerationResponse() {}
    
    public BatchExamGenerationResponse(int totalRequested) {
        this.totalRequested = totalRequested;
    }
    
    public void addExam(Assessment assessment, String sectionName, int questionCount) {
        exams.add(new GeneratedExam(assessment, sectionName, questionCount));
        totalCreated++;
    }
    
    public void addError(String error) {
        errors.add(error);
    }
    
    // Getters and Setters
    public int getTotalRequested() { return totalRequested; }
    public void setTotalRequested(int totalRequested) { this.totalRequested = totalRequested; }
    
    public int getTotalCreated() { return totalCreated; }
    public void setTotalCreated(int totalCreated) { this.totalCreated = totalCreated; }
    
    public List<GeneratedExam> getExams() { return exams; }
    public void setExams(List<GeneratedExam> exams) { this.exams = exams; }
    
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    
    /**
     * Details of a single generated exam.
     */
    public static class GeneratedExam {
        private String examId;
        private String title;
        private String sectionId;
        private String sectionName;
        private int questionCount;
        private String status;
        
        public GeneratedExam() {}
        
        public GeneratedExam(Assessment assessment, String sectionName, int questionCount) {
            this.examId = assessment.getId();
            this.title = assessment.getTitle();
            this.sectionId = assessment.getSectionId();
            this.sectionName = sectionName;
            this.questionCount = questionCount;
            this.status = assessment.getStatus() != null ? assessment.getStatus().name() : "DRAFT";
        }
        
        public String getExamId() { return examId; }
        public void setExamId(String examId) { this.examId = examId; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getSectionId() { return sectionId; }
        public void setSectionId(String sectionId) { this.sectionId = sectionId; }
        
        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }
        
        public int getQuestionCount() { return questionCount; }
        public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
