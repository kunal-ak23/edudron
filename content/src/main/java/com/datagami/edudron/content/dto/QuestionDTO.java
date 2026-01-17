package com.datagami.edudron.content.dto;

import java.util.List;

/**
 * DTO for psychometric test questions
 */
public class QuestionDTO {
    private String id;
    private String text;
    private String type; // LIKERT, FORCED_CHOICE, MICRO_SUBJECTIVE
    private List<String> options; // For Likert or forced choice
    private String module; // CORE, SCIENCE, COMMERCE, ARTS
    private Integer order;
    private Integer totalQuestions; // Total questions in current phase
    private Integer currentQuestionNumber; // Current question number
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
    
    public Integer getCurrentQuestionNumber() { return currentQuestionNumber; }
    public void setCurrentQuestionNumber(Integer currentQuestionNumber) { this.currentQuestionNumber = currentQuestionNumber; }
}
