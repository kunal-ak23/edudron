package com.datagami.edudron.content.simulation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class SimulationStateDTO {
    private String phase;  // DECISION, YEAR_END_REVIEW, DEBRIEF, FIRED
    private int currentYear;
    private int currentDecision;
    private int totalDecisions;
    private int totalYears;
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

    // v3: Budget and advisor fields
    private BigDecimal currentBudget;
    private Map<String, Object> financialReport;
    private Map<String, Object> advisorDialog;
    private Map<String, Object> advisorReaction;

    // Opening narrative for the current year (shown at start of each year)
    private String openingNarrative;

    // Dashboard panel fields: decision history, quality counts, insights
    private List<Map<String, Object>> decisionHistory;
    private Integer goodDecisionCount;
    private Integer badDecisionCount;
    private Integer neutralDecisionCount;
    private List<String> keyInsights;

    // Getters and Setters
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public int getCurrentYear() { return currentYear; }
    public void setCurrentYear(int currentYear) { this.currentYear = currentYear; }

    public int getCurrentDecision() { return currentDecision; }
    public void setCurrentDecision(int currentDecision) { this.currentDecision = currentDecision; }

    public int getTotalDecisions() { return totalDecisions; }
    public void setTotalDecisions(int totalDecisions) { this.totalDecisions = totalDecisions; }

    public int getTotalYears() { return totalYears; }
    public void setTotalYears(int totalYears) { this.totalYears = totalYears; }

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

    public BigDecimal getCurrentBudget() { return currentBudget; }
    public void setCurrentBudget(BigDecimal currentBudget) { this.currentBudget = currentBudget; }

    public Map<String, Object> getFinancialReport() { return financialReport; }
    public void setFinancialReport(Map<String, Object> financialReport) { this.financialReport = financialReport; }

    public Map<String, Object> getAdvisorDialog() { return advisorDialog; }
    public void setAdvisorDialog(Map<String, Object> advisorDialog) { this.advisorDialog = advisorDialog; }

    public Map<String, Object> getAdvisorReaction() { return advisorReaction; }
    public void setAdvisorReaction(Map<String, Object> advisorReaction) { this.advisorReaction = advisorReaction; }

    public String getOpeningNarrative() { return openingNarrative; }
    public void setOpeningNarrative(String openingNarrative) { this.openingNarrative = openingNarrative; }

    public List<Map<String, Object>> getDecisionHistory() { return decisionHistory; }
    public void setDecisionHistory(List<Map<String, Object>> decisionHistory) { this.decisionHistory = decisionHistory; }

    public Integer getGoodDecisionCount() { return goodDecisionCount; }
    public void setGoodDecisionCount(Integer goodDecisionCount) { this.goodDecisionCount = goodDecisionCount; }

    public Integer getBadDecisionCount() { return badDecisionCount; }
    public void setBadDecisionCount(Integer badDecisionCount) { this.badDecisionCount = badDecisionCount; }

    public Integer getNeutralDecisionCount() { return neutralDecisionCount; }
    public void setNeutralDecisionCount(Integer neutralDecisionCount) { this.neutralDecisionCount = neutralDecisionCount; }

    public List<String> getKeyInsights() { return keyInsights; }
    public void setKeyInsights(List<String> keyInsights) { this.keyInsights = keyInsights; }
}
