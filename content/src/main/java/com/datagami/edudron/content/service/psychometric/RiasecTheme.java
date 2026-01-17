package com.datagami.edudron.content.service.psychometric;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RIASEC Theme with explanation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiasecTheme {
    private String code; // R, I, A, S, E, or C
    private String name; // Full name
    private Double score;
    private String explanation; // Simple explanation
    
    public RiasecTheme() {}
    
    public RiasecTheme(String code, String name, Double score, String explanation) {
        this.code = code;
        this.name = name;
        this.score = score;
        this.explanation = explanation;
    }
    
    // Getters and Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
