package com.datagami.edudron.content.simulation.dto;

import java.util.List;
import java.util.Map;

public class SimulationDecisionDTO {
    private String decisionId;
    private String narrative;
    private String decisionType;   // NARRATIVE_CHOICE, BUDGET_ALLOCATION, etc.
    private Map<String, Object> decisionConfig;
    private List<ChoiceDTO> choices;
    private List<Map<String, String>> conceptKeywords;

    public static class ChoiceDTO {
        private String id;
        private String text;

        public ChoiceDTO() {}

        public ChoiceDTO(String id, String text) {
            this.id = id;
            this.text = text;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    // Getters and Setters
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }

    public Map<String, Object> getDecisionConfig() { return decisionConfig; }
    public void setDecisionConfig(Map<String, Object> decisionConfig) { this.decisionConfig = decisionConfig; }

    public List<ChoiceDTO> getChoices() { return choices; }
    public void setChoices(List<ChoiceDTO> choices) { this.choices = choices; }

    public List<Map<String, String>> getConceptKeywords() { return conceptKeywords; }
    public void setConceptKeywords(List<Map<String, String>> conceptKeywords) { this.conceptKeywords = conceptKeywords; }
}
