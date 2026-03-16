package com.datagami.edudron.content.simulation.dto;

public class SimulationStateDTO {
    private String phase;  // DECISION, YEAR_END_REVIEW, DEBRIEF, FIRED
    private int currentYear;
    private int currentDecision;
    private int totalDecisions;
    private String currentRole;
    private int cumulativeScore;
    private int yearScore;
    private String performanceBand;

    // For DECISION phase
    private SimulationDecisionDTO decision;

    // For YEAR_END_REVIEW phase
    private YearEndReviewDTO yearEndReview;

    // For DEBRIEF or FIRED phase
    private DebriefDTO debrief;

    // Opening narrative for the current year (shown at start of each year)
    private String openingNarrative;

    // Getters and Setters
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public int getCurrentYear() { return currentYear; }
    public void setCurrentYear(int currentYear) { this.currentYear = currentYear; }

    public int getCurrentDecision() { return currentDecision; }
    public void setCurrentDecision(int currentDecision) { this.currentDecision = currentDecision; }

    public int getTotalDecisions() { return totalDecisions; }
    public void setTotalDecisions(int totalDecisions) { this.totalDecisions = totalDecisions; }

    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }

    public int getCumulativeScore() { return cumulativeScore; }
    public void setCumulativeScore(int cumulativeScore) { this.cumulativeScore = cumulativeScore; }

    public int getYearScore() { return yearScore; }
    public void setYearScore(int yearScore) { this.yearScore = yearScore; }

    public String getPerformanceBand() { return performanceBand; }
    public void setPerformanceBand(String performanceBand) { this.performanceBand = performanceBand; }

    public SimulationDecisionDTO getDecision() { return decision; }
    public void setDecision(SimulationDecisionDTO decision) { this.decision = decision; }

    public YearEndReviewDTO getYearEndReview() { return yearEndReview; }
    public void setYearEndReview(YearEndReviewDTO yearEndReview) { this.yearEndReview = yearEndReview; }

    public DebriefDTO getDebrief() { return debrief; }
    public void setDebrief(DebriefDTO debrief) { this.debrief = debrief; }

    public String getOpeningNarrative() { return openingNarrative; }
    public void setOpeningNarrative(String openingNarrative) { this.openingNarrative = openingNarrative; }
}
