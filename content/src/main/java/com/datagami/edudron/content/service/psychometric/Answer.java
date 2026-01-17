package com.datagami.edudron.content.service.psychometric;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Answer Schema for Hybrid Psychometric Test
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Answer {
    private String questionId;
    private String value; // Selected option or text response
    private Long timestamp;
    
    public Answer() {}
    
    public Answer(String questionId, String value) {
        this.questionId = questionId;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
