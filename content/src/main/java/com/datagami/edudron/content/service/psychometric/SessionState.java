package com.datagami.edudron.content.service.psychometric;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Session State for Hybrid Psychometric Test
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionState {
    private String phase; // CORE, PRIME_DETECTION, ADAPTIVE_MODULE_1, ADAPTIVE_MODULE_2, COMPLETED
    private Integer currentQuestionIndex;
    private String currentQuestionId;
    private List<Answer> coreAnswers; // Answers to core 18 questions
    private List<Answer> adaptiveAnswers; // Answers to adaptive questions
    private Map<String, Double> riasecScores; // R, I, A, S, E, C
    private Map<String, Double> indicatorScores; // math_confidence, language_confidence, etc.
    private Map<String, Double> streamScores; // Science, Commerce, Arts
    private List<String> primeCandidates; // Top 2-3 prime candidates (streams + RIASEC)
    private Double confidenceScore;
    private List<String> selectedModules; // Which adaptive modules were selected
    private Integer currentModuleIndex;
    
    public SessionState() {
        this.phase = "CORE";
        this.currentQuestionIndex = 0;
        this.coreAnswers = new java.util.ArrayList<>();
        this.adaptiveAnswers = new java.util.ArrayList<>();
        this.riasecScores = new java.util.HashMap<>();
        this.indicatorScores = new java.util.HashMap<>();
        this.streamScores = new java.util.HashMap<>();
        this.primeCandidates = new java.util.ArrayList<>();
        this.selectedModules = new java.util.ArrayList<>();
    }
    
    // Getters and Setters
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    
    public Integer getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(Integer currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }
    
    public String getCurrentQuestionId() { return currentQuestionId; }
    public void setCurrentQuestionId(String currentQuestionId) { this.currentQuestionId = currentQuestionId; }
    
    public List<Answer> getCoreAnswers() { return coreAnswers; }
    public void setCoreAnswers(List<Answer> coreAnswers) { this.coreAnswers = coreAnswers; }
    
    public List<Answer> getAdaptiveAnswers() { return adaptiveAnswers; }
    public void setAdaptiveAnswers(List<Answer> adaptiveAnswers) { this.adaptiveAnswers = adaptiveAnswers; }
    
    public Map<String, Double> getRiasecScores() { return riasecScores; }
    public void setRiasecScores(Map<String, Double> riasecScores) { this.riasecScores = riasecScores; }
    
    public Map<String, Double> getIndicatorScores() { return indicatorScores; }
    public void setIndicatorScores(Map<String, Double> indicatorScores) { this.indicatorScores = indicatorScores; }
    
    public Map<String, Double> getStreamScores() { return streamScores; }
    public void setStreamScores(Map<String, Double> streamScores) { this.streamScores = streamScores; }
    
    public List<String> getPrimeCandidates() { return primeCandidates; }
    public void setPrimeCandidates(List<String> primeCandidates) { this.primeCandidates = primeCandidates; }
    
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    
    public List<String> getSelectedModules() { return selectedModules; }
    public void setSelectedModules(List<String> selectedModules) { this.selectedModules = selectedModules; }
    
    public Integer getCurrentModuleIndex() { return currentModuleIndex; }
    public void setCurrentModuleIndex(Integer currentModuleIndex) { this.currentModuleIndex = currentModuleIndex; }
}
