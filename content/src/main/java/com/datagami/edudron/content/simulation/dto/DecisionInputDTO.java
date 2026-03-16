package com.datagami.edudron.content.simulation.dto;

import java.util.Map;

public class DecisionInputDTO {
    private String nodeId;
    private String choiceId;                // for NARRATIVE_CHOICE direct selection
    private Map<String, Object> input;      // for interactive types (slider values, rankings, etc.)

    // Getters and Setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getChoiceId() { return choiceId; }
    public void setChoiceId(String choiceId) { this.choiceId = choiceId; }

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }
}
