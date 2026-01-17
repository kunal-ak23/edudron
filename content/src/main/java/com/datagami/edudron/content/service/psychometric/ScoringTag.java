package com.datagami.edudron.content.service.psychometric;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Scoring Tag - defines what a question measures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoringTag {
    private String category; // RIASEC, INDICATOR, STREAM
    private String name; // R, I, A, S, E, C, or math_confidence, etc.
    private Map<String, Integer> valueMapping; // Answer value → score contribution
    
    public ScoringTag() {}
    
    public ScoringTag(String category, String name, Map<String, Integer> valueMapping) {
        this.category = category;
        this.name = name;
        this.valueMapping = valueMapping;
    }
    
    // Getters and Setters
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Map<String, Integer> getValueMapping() { return valueMapping; }
    public void setValueMapping(Map<String, Integer> valueMapping) { this.valueMapping = valueMapping; }
}
