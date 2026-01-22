package com.datagami.edudron.student.dto;

import java.math.BigDecimal;

public class EndSessionRequest {
    private BigDecimal progressAtEnd;
    private Boolean isCompleted;

    // Constructors
    public EndSessionRequest() {}

    // Getters and Setters
    public BigDecimal getProgressAtEnd() { return progressAtEnd; }
    public void setProgressAtEnd(BigDecimal progressAtEnd) { this.progressAtEnd = progressAtEnd; }

    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }
}
