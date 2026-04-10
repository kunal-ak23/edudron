package com.datagami.edudron.content.simulation.dto;

import java.util.List;
import java.util.Map;

public class SimulationSuggestionResponse {
    private String concept;
    private String subject;
    private String audience;
    private String suggestedTitle;
    private String description;
    private String scenarioPremise;
    private String recommendedCareerPath;
    private List<String> learningGoals;
    private List<String> generationNotes;
    private List<Map<String, Object>> existingSimulations;

    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getSuggestedTitle() { return suggestedTitle; }
    public void setSuggestedTitle(String suggestedTitle) { this.suggestedTitle = suggestedTitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScenarioPremise() { return scenarioPremise; }
    public void setScenarioPremise(String scenarioPremise) { this.scenarioPremise = scenarioPremise; }
    public String getRecommendedCareerPath() { return recommendedCareerPath; }
    public void setRecommendedCareerPath(String recommendedCareerPath) { this.recommendedCareerPath = recommendedCareerPath; }
    public List<String> getLearningGoals() { return learningGoals; }
    public void setLearningGoals(List<String> learningGoals) { this.learningGoals = learningGoals; }
    public List<String> getGenerationNotes() { return generationNotes; }
    public void setGenerationNotes(List<String> generationNotes) { this.generationNotes = generationNotes; }
    public List<Map<String, Object>> getExistingSimulations() { return existingSimulations; }
    public void setExistingSimulations(List<Map<String, Object>> existingSimulations) { this.existingSimulations = existingSimulations; }
}
