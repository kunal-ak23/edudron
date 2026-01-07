package com.datagami.edudron.payment.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.payment.domain.SubscriptionPlan;
import com.datagami.edudron.payment.dto.CreateSubscriptionPlanRequest;
import com.datagami.edudron.payment.dto.SubscriptionPlanDTO;
import com.datagami.edudron.payment.repo.SubscriptionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SubscriptionPlanService {
    
    @Autowired
    private SubscriptionPlanRepository planRepository;
    
    public SubscriptionPlanDTO createPlan(CreateSubscriptionPlanRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UlidGenerator.nextUlid());
        plan.setClientId(clientId);
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPricePaise(request.getPricePaise());
        plan.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        plan.setBillingPeriod(request.getBillingPeriod());
        plan.setIsActive(true);
        
        SubscriptionPlan saved = planRepository.save(plan);
        return toDTO(saved);
    }
    
    public SubscriptionPlanDTO getPlan(String planId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        SubscriptionPlan plan = planRepository.findByIdAndClientId(planId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        return toDTO(plan);
    }
    
    public List<SubscriptionPlanDTO> getActivePlans() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<SubscriptionPlan> plans = planRepository.findByClientIdAndIsActive(clientId, true);
        return plans.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<SubscriptionPlanDTO> getAllPlans() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<SubscriptionPlan> plans = planRepository.findByClientId(clientId);
        return plans.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public SubscriptionPlanDTO updatePlan(String planId, CreateSubscriptionPlanRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        SubscriptionPlan plan = planRepository.findByIdAndClientId(planId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPricePaise(request.getPricePaise());
        if (request.getCurrency() != null) {
            plan.setCurrency(request.getCurrency());
        }
        plan.setBillingPeriod(request.getBillingPeriod());
        
        SubscriptionPlan saved = planRepository.save(plan);
        return toDTO(saved);
    }
    
    public void deactivatePlan(String planId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        SubscriptionPlan plan = planRepository.findByIdAndClientId(planId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        
        plan.setIsActive(false);
        planRepository.save(plan);
    }
    
    private SubscriptionPlanDTO toDTO(SubscriptionPlan plan) {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setId(plan.getId());
        dto.setClientId(plan.getClientId());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setPricePaise(plan.getPricePaise());
        dto.setCurrency(plan.getCurrency());
        dto.setBillingPeriod(plan.getBillingPeriod());
        dto.setIsActive(plan.getIsActive());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());
        return dto;
    }
}


