package com.datagami.edudron.payment.web;

import com.datagami.edudron.payment.dto.CreateSubscriptionPlanRequest;
import com.datagami.edudron.payment.dto.SubscriptionPlanDTO;
import com.datagami.edudron.payment.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscription-plans")
@Tag(name = "Subscription Plans", description = "Subscription plan management endpoints")
public class SubscriptionPlanController {

    @Autowired
    private SubscriptionPlanService planService;

    @GetMapping
    @Operation(summary = "List active plans", description = "Get all active subscription plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getActivePlans() {
        List<SubscriptionPlanDTO> plans = planService.getActivePlans();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/all")
    @Operation(summary = "List all plans", description = "Get all subscription plans (including inactive)")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        List<SubscriptionPlanDTO> plans = planService.getAllPlans();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get plan", description = "Get subscription plan details by ID")
    public ResponseEntity<SubscriptionPlanDTO> getPlan(@PathVariable String id) {
        SubscriptionPlanDTO plan = planService.getPlan(id);
        return ResponseEntity.ok(plan);
    }

    @PostMapping
    @Operation(summary = "Create plan", description = "Create a new subscription plan")
    public ResponseEntity<SubscriptionPlanDTO> createPlan(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        SubscriptionPlanDTO plan = planService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update plan", description = "Update an existing subscription plan")
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(
            @PathVariable String id,
            @Valid @RequestBody CreateSubscriptionPlanRequest request) {
        SubscriptionPlanDTO plan = planService.updatePlan(id, request);
        return ResponseEntity.ok(plan);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate plan", description = "Deactivate a subscription plan")
    public ResponseEntity<Void> deactivatePlan(@PathVariable String id) {
        planService.deactivatePlan(id);
        return ResponseEntity.noContent().build();
    }
}

