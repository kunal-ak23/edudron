package com.datagami.edudron.content.simulation.dto;

import com.datagami.edudron.content.simulation.domain.SimulationPlay;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public class SimulationPlayDTO {
    private String id;
    private String simulationId;
    private String simulationTitle;
    private int attemptNumber;
    private boolean isPrimary;
    private String status;
    private Integer currentYear;
    private Integer currentDecision;
    private String currentRole;
    private Integer cumulativeScore;
    private Integer finalScore;
    private String performanceBand;
    private Integer consecutiveStruggling;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    public static SimulationPlayDTO fromEntity(SimulationPlay play, String simulationTitle) {
        SimulationPlayDTO dto = new SimulationPlayDTO();
        dto.setId(play.getId());
        dto.setSimulationId(play.getSimulationId());
        dto.setSimulationTitle(simulationTitle);
        dto.setAttemptNumber(play.getAttemptNumber() != null ? play.getAttemptNumber() : 1);
        dto.setPrimary(play.getIsPrimary() != null && play.getIsPrimary());
        dto.setStatus(play.getStatus() != null ? play.getStatus().name() : null);
        dto.setCurrentYear(play.getCurrentYear());
        dto.setCurrentDecision(play.getCurrentDecision());
        dto.setCurrentRole(play.getCurrentRole());
        dto.setCumulativeScore(play.getCumulativeScore());
        dto.setFinalScore(play.getFinalScore());
        dto.setPerformanceBand(play.getPerformanceBand());
        dto.setConsecutiveStruggling(play.getConsecutiveStruggling());
        dto.setStartedAt(play.getStartedAt());
        dto.setCompletedAt(play.getCompletedAt());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSimulationId() { return simulationId; }
    public void setSimulationId(String simulationId) { this.simulationId = simulationId; }

    public String getSimulationTitle() { return simulationTitle; }
    public void setSimulationTitle(String simulationTitle) { this.simulationTitle = simulationTitle; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    @JsonProperty("isPrimary")
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getCurrentYear() { return currentYear; }
    public void setCurrentYear(Integer currentYear) { this.currentYear = currentYear; }

    public Integer getCurrentDecision() { return currentDecision; }
    public void setCurrentDecision(Integer currentDecision) { this.currentDecision = currentDecision; }

    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }

    public Integer getCumulativeScore() { return cumulativeScore; }
    public void setCumulativeScore(Integer cumulativeScore) { this.cumulativeScore = cumulativeScore; }

    public Integer getFinalScore() { return finalScore; }
    public void setFinalScore(Integer finalScore) { this.finalScore = finalScore; }

    public String getPerformanceBand() { return performanceBand; }
    public void setPerformanceBand(String performanceBand) { this.performanceBand = performanceBand; }

    public Integer getConsecutiveStruggling() { return consecutiveStruggling; }
    public void setConsecutiveStruggling(Integer consecutiveStruggling) { this.consecutiveStruggling = consecutiveStruggling; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
