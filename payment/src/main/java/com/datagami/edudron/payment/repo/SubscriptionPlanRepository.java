package com.datagami.edudron.payment.repo;

import com.datagami.edudron.payment.domain.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
    
    List<SubscriptionPlan> findByClientIdAndIsActive(UUID clientId, Boolean isActive);
    
    List<SubscriptionPlan> findByClientId(UUID clientId);
    
    Optional<SubscriptionPlan> findByIdAndClientId(String id, UUID clientId);
}


