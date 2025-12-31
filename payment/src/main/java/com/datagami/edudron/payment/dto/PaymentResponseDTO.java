package com.datagami.edudron.payment.dto;

import java.util.Map;

public class PaymentResponseDTO {
    private PaymentDTO payment;
    private Map<String, Object> paymentGatewayData; // Razorpay order details, etc.

    // Getters and Setters
    public PaymentDTO getPayment() { return payment; }
    public void setPayment(PaymentDTO payment) { this.payment = payment; }

    public Map<String, Object> getPaymentGatewayData() { return paymentGatewayData; }
    public void setPaymentGatewayData(Map<String, Object> paymentGatewayData) { this.paymentGatewayData = paymentGatewayData; }
}

