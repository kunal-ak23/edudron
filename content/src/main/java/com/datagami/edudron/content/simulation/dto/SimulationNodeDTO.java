package com.datagami.edudron.content.simulation.dto;

import java.util.List;
import java.util.Map;

public class SimulationNodeDTO {
    private String nodeId;
    private String type;           // SCENARIO or TERMINAL
    private String narrative;
    private String decisionType;   // NARRATIVE_CHOICE, BUDGET_ALLOCATION, etc.
    private Map<String, Object> decisionConfig; // mappings removed
    private List<ChoiceDTO> choices;
    private DebriefDTO debrief;    // only on TERMINAL
    private Integer score;          // only on TERMINAL
    private boolean isTerminal;

    public static class ChoiceDTO {
        private String id;
        private String text;
        // NO nextNodeId, NO quality

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

    public static class DebriefDTO {
        private String yourPath;
        private String conceptAtWork;
        private String theGap;
        private String playAgain;

        public DebriefDTO() {}

        public DebriefDTO(String yourPath, String conceptAtWork, String theGap, String playAgain) {
            this.yourPath = yourPath;
            this.conceptAtWork = conceptAtWork;
            this.theGap = theGap;
            this.playAgain = playAgain;
        }

        public String getYourPath() { return yourPath; }
        public void setYourPath(String yourPath) { this.yourPath = yourPath; }

        public String getConceptAtWork() { return conceptAtWork; }
        public void setConceptAtWork(String conceptAtWork) { this.conceptAtWork = conceptAtWork; }

        public String getTheGap() { return theGap; }
        public void setTheGap(String theGap) { this.theGap = theGap; }

        public String getPlayAgain() { return playAgain; }
        public void setPlayAgain(String playAgain) { this.playAgain = playAgain; }
    }

    // Getters and Setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }

    public Map<String, Object> getDecisionConfig() { return decisionConfig; }
    public void setDecisionConfig(Map<String, Object> decisionConfig) { this.decisionConfig = decisionConfig; }

    public List<ChoiceDTO> getChoices() { return choices; }
    public void setChoices(List<ChoiceDTO> choices) { this.choices = choices; }

    public DebriefDTO getDebrief() { return debrief; }
    public void setDebrief(DebriefDTO debrief) { this.debrief = debrief; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public boolean isTerminal() { return isTerminal; }
    public void setTerminal(boolean terminal) { isTerminal = terminal; }
}
