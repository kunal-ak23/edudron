package com.datagami.edudron.content.dto;

public class TestAvailabilityDTO {
    private Boolean enabled;
    private Boolean paymentRequired;
    private String message;
    
    public TestAvailabilityDTO() {}
    
    public TestAvailabilityDTO(Boolean enabled, Boolean paymentRequired, String message) {
        this.enabled = enabled;
        this.paymentRequired = paymentRequired;
        this.message = message;
    }
    
    // Getters and Setters
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Boolean getPaymentRequired() { return paymentRequired; }
    public void setPaymentRequired(Boolean paymentRequired) { this.paymentRequired = paymentRequired; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
