package com.datagami.edudron.payment.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.payment.domain.Subscription;
import com.datagami.edudron.payment.domain.SubscriptionPlan;
import com.datagami.edudron.payment.dto.CreateSubscriptionRequest;
import com.datagami.edudron.payment.dto.SubscriptionDTO;
import com.datagami.edudron.payment.repo.SubscriptionPlanRepository;
import com.datagami.edudron.payment.repo.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SubscriptionService {
    
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    
    @Autowired
    private SubscriptionPlanRepository planRepository;
    
    public SubscriptionDTO createSubscription(String studentId, CreateSubscriptionRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify plan exists and is active
        SubscriptionPlan plan = planRepository.findByIdAndClientId(request.getPlanId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + request.getPlanId()));
        
        if (!plan.getIsActive()) {
            throw new IllegalArgumentException("Plan is not active");
        }
        
        // Check if student already has an active subscription
        subscriptionRepository.findByClientIdAndStudentIdAndStatus(clientId, studentId, Subscription.Status.ACTIVE)
            .ifPresent(sub -> {
                throw new IllegalArgumentException("Student already has an active subscription");
            });
        
        Subscription subscription = new Subscription();
        subscription.setId(UlidGenerator.nextUlid());
        subscription.setClientId(clientId);
        subscription.setStudentId(studentId);
        subscription.setPlanId(request.getPlanId());
        subscription.setStatus(Subscription.Status.ACTIVE);
        subscription.setStartDate(LocalDate.now());
        
        // Calculate end date based on billing period
        if ("MONTHLY".equals(plan.getBillingPeriod())) {
            subscription.setEndDate(LocalDate.now().plusMonths(1));
            subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
        } else if ("YEARLY".equals(plan.getBillingPeriod())) {
            subscription.setEndDate(LocalDate.now().plusYears(1));
            subscription.setNextBillingDate(LocalDate.now().plusYears(1));
        }
        
        Subscription saved = subscriptionRepository.save(subscription);
        return toDTO(saved);
    }
    
    public SubscriptionDTO getSubscription(String subscriptionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Subscription subscription = subscriptionRepository.findByIdAndClientId(subscriptionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
        
        return toDTO(subscription);
    }
    
    public List<SubscriptionDTO> getStudentSubscriptions(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Subscription> subscriptions = subscriptionRepository.findByClientIdAndStudentId(clientId, studentId);
        return subscriptions.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public SubscriptionDTO getActiveSubscription(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Subscription subscription = subscriptionRepository.findByClientIdAndStudentIdAndStatus(clientId, studentId, Subscription.Status.ACTIVE)
            .orElse(null);
        
        return subscription != null ? toDTO(subscription) : null;
    }
    
    public void cancelSubscription(String subscriptionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Subscription subscription = subscriptionRepository.findByIdAndClientId(subscriptionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
        
        subscription.setStatus(Subscription.Status.CANCELLED);
        subscriptionRepository.save(subscription);
    }
    
    private SubscriptionDTO toDTO(Subscription subscription) {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setId(subscription.getId());
        dto.setClientId(subscription.getClientId());
        dto.setStudentId(subscription.getStudentId());
        dto.setPlanId(subscription.getPlanId());
        dto.setStatus(subscription.getStatus().name());
        dto.setStartDate(subscription.getStartDate());
        dto.setEndDate(subscription.getEndDate());
        dto.setNextBillingDate(subscription.getNextBillingDate());
        dto.setCreatedAt(subscription.getCreatedAt());
        dto.setUpdatedAt(subscription.getUpdatedAt());
        return dto;
    }
}

