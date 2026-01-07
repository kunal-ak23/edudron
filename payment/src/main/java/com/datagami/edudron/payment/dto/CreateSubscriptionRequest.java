package com.datagami.edudron.payment.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateSubscriptionRequest {
    @NotBlank(message = "Plan ID is required")
    private String planId;

    // Getters and Setters
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
}


