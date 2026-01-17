package com.datagami.edudron.content.service.psychometric;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Test Result Schema for Hybrid Psychometric Test
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestResult {
    private String primaryStream; // Science, Commerce, or Arts
    private String secondaryStream; // Optional, only if scores are close
    private List<RiasecTheme> topRiasecThemes; // Top 2 RIASEC themes
    private List<String> suggestedCareerFields; // 2-3 career fields
    private List<String> suggestedCourseIds; // 2-3 LMS course IDs
    private Map<String, Double> finalRiasecScores;
    private Map<String, Double> finalIndicatorScores;
    private Map<String, Double> finalStreamScores;
    private String reasoning; // AI-generated reasoning
    private String disclaimerVersion; // Version of disclaimer used
    
    public TestResult() {}
    
    // Getters and Setters
    public String getPrimaryStream() { return primaryStream; }
    public void setPrimaryStream(String primaryStream) { this.primaryStream = primaryStream; }
    
    public String getSecondaryStream() { return secondaryStream; }
    public void setSecondaryStream(String secondaryStream) { this.secondaryStream = secondaryStream; }
    
    public List<RiasecTheme> getTopRiasecThemes() { return topRiasecThemes; }
    public void setTopRiasecThemes(List<RiasecTheme> topRiasecThemes) { this.topRiasecThemes = topRiasecThemes; }
    
    public List<String> getSuggestedCareerFields() { return suggestedCareerFields; }
    public void setSuggestedCareerFields(List<String> suggestedCareerFields) { this.suggestedCareerFields = suggestedCareerFields; }
    
    public List<String> getSuggestedCourseIds() { return suggestedCourseIds; }
    public void setSuggestedCourseIds(List<String> suggestedCourseIds) { this.suggestedCourseIds = suggestedCourseIds; }
    
    public Map<String, Double> getFinalRiasecScores() { return finalRiasecScores; }
    public void setFinalRiasecScores(Map<String, Double> finalRiasecScores) { this.finalRiasecScores = finalRiasecScores; }
    
    public Map<String, Double> getFinalIndicatorScores() { return finalIndicatorScores; }
    public void setFinalIndicatorScores(Map<String, Double> finalIndicatorScores) { this.finalIndicatorScores = finalIndicatorScores; }
    
    public Map<String, Double> getFinalStreamScores() { return finalStreamScores; }
    public void setFinalStreamScores(Map<String, Double> finalStreamScores) { this.finalStreamScores = finalStreamScores; }
    
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    
    public String getDisclaimerVersion() { return disclaimerVersion; }
    public void setDisclaimerVersion(String disclaimerVersion) { this.disclaimerVersion = disclaimerVersion; }
}
