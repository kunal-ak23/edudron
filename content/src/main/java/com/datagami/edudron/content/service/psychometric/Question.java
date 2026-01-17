package com.datagami.edudron.content.service.psychometric;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Question Schema for Hybrid Psychometric Test
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Question {
    private String id;
    private String text;
    private QuestionType type;
    private List<String> options; // For Likert or forced choice
    private List<ScoringTag> scoringTags; // What this question measures
    private String module; // CORE, SCIENCE, COMMERCE, ARTS, etc.
    private Integer order; // Order within module
    
    public enum QuestionType {
        LIKERT, // Strongly Agree → Strongly Disagree
        FORCED_CHOICE, // A/B choice
        MICRO_SUBJECTIVE // Single tap or 1-line max
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public QuestionType getType() { return type; }
    public void setType(QuestionType type) { this.type = type; }
    
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    
    public List<ScoringTag> getScoringTags() { return scoringTags; }
    public void setScoringTags(List<ScoringTag> scoringTags) { this.scoringTags = scoringTags; }
    
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
}
