package com.datagami.edudron.content.simulation.dto;

import java.util.List;
import java.util.Map;

public class SimulationSuggestionResponse {
    private String concept;
    private String subject;
    private String audience;
    private String description;
    private List<Map<String, Object>> existingSimulations;

    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Map<String, Object>> getExistingSimulations() { return existingSimulations; }
    public void setExistingSimulations(List<Map<String, Object>> existingSimulations) { this.existingSimulations = existingSimulations; }
}
