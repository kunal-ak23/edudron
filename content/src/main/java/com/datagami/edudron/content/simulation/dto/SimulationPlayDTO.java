package com.datagami.edudron.content.simulation.dto;

import java.time.OffsetDateTime;

public class SimulationPlayDTO {
    private String id;
    private String simulationId;
    private String simulationTitle;
    private int attemptNumber;
    private boolean isPrimary;
    private String status;
    private int decisionsMade;
    private Integer score;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    // Static factory — entity class not yet available, will be wired in Task 5
    // public static SimulationPlayDTO fromEntity(SimulationPlay play, String simulationTitle) { ... }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSimulationId() { return simulationId; }
    public void setSimulationId(String simulationId) { this.simulationId = simulationId; }

    public String getSimulationTitle() { return simulationTitle; }
    public void setSimulationTitle(String simulationTitle) { this.simulationTitle = simulationTitle; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getDecisionsMade() { return decisionsMade; }
    public void setDecisionsMade(int decisionsMade) { this.decisionsMade = decisionsMade; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
