package com.datagami.edudron.content.simulation.dto;

import java.util.List;
import java.util.Map;

public class YearEndReviewDTO {
    private int year;
    private String band;
    private Map<String, Object> metrics;
    private Map<String, String> feedback;  // {board: "...", customers: "...", investors: "..."}
    private String promotionTitle;  // non-null if promoted
    private boolean fired;
    private List<DecisionHighlightDTO> decisionHighlights;
    private String crossDecisionInsight;
    private String warningSignal; // nullable, only for STRUGGLING band

    public static class DecisionHighlightDTO {
        private String decisionId;
        private String label;
        private String impact; // "positive", "negative", "neutral"
        private String summary;

        public String getDecisionId() { return decisionId; }
        public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }

    // Getters and Setters
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getBand() { return band; }
    public void setBand(String band) { this.band = band; }

    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }

    public Map<String, String> getFeedback() { return feedback; }
    public void setFeedback(Map<String, String> feedback) { this.feedback = feedback; }

    public String getPromotionTitle() { return promotionTitle; }
    public void setPromotionTitle(String promotionTitle) { this.promotionTitle = promotionTitle; }

    public boolean isFired() { return fired; }
    public void setFired(boolean fired) { this.fired = fired; }

    public List<DecisionHighlightDTO> getDecisionHighlights() { return decisionHighlights; }
    public void setDecisionHighlights(List<DecisionHighlightDTO> decisionHighlights) { this.decisionHighlights = decisionHighlights; }

    public String getCrossDecisionInsight() { return crossDecisionInsight; }
    public void setCrossDecisionInsight(String crossDecisionInsight) { this.crossDecisionInsight = crossDecisionInsight; }

    public String getWarningSignal() { return warningSignal; }
    public void setWarningSignal(String warningSignal) { this.warningSignal = warningSignal; }
}
