package com.datagami.edudron.content.simulation.dto;

import java.util.Map;

public class YearEndReviewDTO {
    private int year;
    private String band;
    private Map<String, Object> metrics;
    private Map<String, String> feedback;  // {board: "...", customers: "...", investors: "..."}
    private String promotionTitle;  // non-null if promoted
    private boolean fired;

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
}
