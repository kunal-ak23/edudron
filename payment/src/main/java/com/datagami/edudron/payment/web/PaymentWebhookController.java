package com.datagami.edudron.payment.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.payment.domain.Payment;
import com.datagami.edudron.payment.domain.PaymentWebhook;
import com.datagami.edudron.payment.integration.RazorpayClientWrapper;
import com.datagami.edudron.payment.repo.PaymentRepository;
import com.datagami.edudron.payment.repo.PaymentWebhookRepository;
import com.datagami.edudron.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Payment Webhooks", description = "Payment gateway webhook endpoints")
public class PaymentWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentWebhookController.class);

    @Autowired
    private PaymentWebhookRepository webhookRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RazorpayClientWrapper razorpayClient;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/razorpay")
    @Operation(summary = "Razorpay webhook", description = "Handle Razorpay payment webhooks")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload) {
        
        try {
            String clientIdStr = TenantContext.getClientId();
            UUID clientId = clientIdStr != null ? UUID.fromString(clientIdStr) : null;
            
            // Parse webhook payload
            JsonNode payloadJson = objectMapper.readTree(payload);
            String eventType = payloadJson.get("event").asText();
            JsonNode eventData = payloadJson.get("payload");
            
            // Store webhook
            PaymentWebhook webhook = new PaymentWebhook();
            webhook.setId(UlidGenerator.nextUlid());
            webhook.setClientId(clientId);
            webhook.setProvider("RAZORPAY");
            webhook.setEventType(eventType);
            webhook.setPayloadJson(payloadJson);
            if (eventData != null && eventData.has("payment")) {
                JsonNode payment = eventData.get("payment");
                if (payment.has("entity")) {
                    JsonNode entity = payment.get("entity");
                    if (entity.has("id")) {
                        webhook.setProviderEventId(entity.get("id").asText());
                    }
                }
            }
            webhookRepository.save(webhook);
            
            // Process payment events
            if ("payment.captured".equals(eventType) || "payment.authorized".equals(eventType)) {
                if (eventData != null && eventData.has("payment")) {
                    JsonNode payment = eventData.get("payment");
                    JsonNode entity = payment.get("entity");
                    String razorpayPaymentId = entity.get("id").asText();
                    String orderId = entity.has("order_id") ? entity.get("order_id").asText() : null;
                    
                    // Find payment by provider order ID
                    if (orderId != null) {
                        paymentRepository.findByProviderOrderId(orderId).ifPresent(p -> {
                            p.setProviderPaymentId(razorpayPaymentId);
                            p.setStatus(Payment.Status.SUCCESS);
                            p.setPaidAt(java.time.OffsetDateTime.now());
                            paymentRepository.save(p);
                        });
                    }
                }
            } else if ("payment.failed".equals(eventType)) {
                if (eventData != null && eventData.has("payment")) {
                    JsonNode payment = eventData.get("payment");
                    JsonNode entity = payment.get("entity");
                    String razorpayPaymentId = entity.get("id").asText();
                    String orderId = entity.has("order_id") ? entity.get("order_id").asText() : null;
                    String errorDescription = entity.has("error_description") ? entity.get("error_description").asText() : "Payment failed";
                    
                    if (orderId != null) {
                        paymentRepository.findByProviderOrderId(orderId).ifPresent(p -> {
                            p.setProviderPaymentId(razorpayPaymentId);
                            p.setStatus(Payment.Status.FAILED);
                            p.setFailureReason(errorDescription);
                            paymentRepository.save(p);
                        });
                    }
                }
            }
            
            webhook.setProcessed(true);
            webhook.setProcessedAt(java.time.OffsetDateTime.now());
            webhookRepository.save(webhook);
            
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            logger.error("Error processing Razorpay webhook", e);
            return ResponseEntity.status(500).body("Error processing webhook");
        }
    }
}

