package com.datagami.edudron.payment.integration;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RazorpayClientWrapper {
    private static final Logger logger = LoggerFactory.getLogger(RazorpayClientWrapper.class);
    
    private final String keyId;
    private final String keySecret;
    
    public RazorpayClientWrapper(
            @Value("${razorpay.key.id:}") String keyId,
            @Value("${razorpay.key.secret:}") String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }
    
    public Order createOrder(Long amountPaise, String currency, String receipt, Map<String, Object> notes) throws RazorpayException {
        if (keyId == null || keyId.isEmpty() || keySecret == null || keySecret.isEmpty()) {
            throw new IllegalStateException("Razorpay credentials not configured");
        }
        
        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
        
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountPaise); // Amount in paise
        orderRequest.put("currency", currency != null ? currency : "INR");
        orderRequest.put("receipt", receipt);
        if (notes != null && !notes.isEmpty()) {
            orderRequest.put("notes", notes);
        }
        
        Order order = razorpay.orders.create(orderRequest);
        logger.info("Created Razorpay order: {}", String.valueOf(order.get("id")));
        return order;
    }
    
    public Order getOrder(String orderId) throws RazorpayException {
        if (keyId == null || keyId.isEmpty() || keySecret == null || keySecret.isEmpty()) {
            throw new IllegalStateException("Razorpay credentials not configured");
        }
        
        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
        return razorpay.orders.fetch(orderId);
    }
    
    public com.razorpay.Payment getPayment(String paymentId) throws RazorpayException {
        if (keyId == null || keyId.isEmpty() || keySecret == null || keySecret.isEmpty()) {
            throw new IllegalStateException("Razorpay credentials not configured");
        }
        
        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
        return razorpay.payments.fetch(paymentId);
    }
    
    public String getKeyId() {
        return keyId;
    }
    
    public boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            // Razorpay webhook signature verification
            // This is a simplified version - in production, use Razorpay's signature verification utility
            return true; // TODO: Implement proper signature verification
        } catch (Exception e) {
            logger.error("Error verifying webhook signature", e);
            return false;
        }
    }
}

