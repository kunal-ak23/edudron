package com.datagami.edudron.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreatePaymentRequest {
    @NotBlank(message = "Course ID is required")
    private String courseId;
    
    @NotNull(message = "Amount is required")
    private Long amountPaise;
    
    private String currency = "INR";
    
    private String paymentMethod;

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public Long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(Long amountPaise) { this.amountPaise = amountPaise; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}

