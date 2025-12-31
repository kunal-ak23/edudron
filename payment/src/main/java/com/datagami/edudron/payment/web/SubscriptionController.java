package com.datagami.edudron.payment.web;

import com.datagami.edudron.payment.dto.CreateSubscriptionRequest;
import com.datagami.edudron.payment.dto.SubscriptionDTO;
import com.datagami.edudron.payment.service.SubscriptionService;
import com.datagami.edudron.payment.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscriptions", description = "Subscription management endpoints")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "Create subscription", description = "Create a new subscription for the current student")
    public ResponseEntity<SubscriptionDTO> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        SubscriptionDTO subscription = subscriptionService.createSubscription(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }

    @GetMapping
    @Operation(summary = "List my subscriptions", description = "Get all subscriptions for the current student")
    public ResponseEntity<List<SubscriptionDTO>> getMySubscriptions() {
        String studentId = UserUtil.getCurrentUserId();
        List<SubscriptionDTO> subscriptions = subscriptionService.getStudentSubscriptions(studentId);
        return ResponseEntity.ok(subscriptions);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active subscription", description = "Get the active subscription for the current student")
    public ResponseEntity<SubscriptionDTO> getActiveSubscription() {
        String studentId = UserUtil.getCurrentUserId();
        SubscriptionDTO subscription = subscriptionService.getActiveSubscription(studentId);
        if (subscription == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subscription);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription", description = "Get subscription details by ID")
    public ResponseEntity<SubscriptionDTO> getSubscription(@PathVariable String id) {
        SubscriptionDTO subscription = subscriptionService.getSubscription(id);
        return ResponseEntity.ok(subscription);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancel a subscription")
    public ResponseEntity<Void> cancelSubscription(@PathVariable String id) {
        subscriptionService.cancelSubscription(id);
        return ResponseEntity.noContent().build();
    }
}

