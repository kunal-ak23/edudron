package com.datagami.edudron.payment.repo;

import com.datagami.edudron.payment.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    
    List<Subscription> findByClientIdAndStudentId(UUID clientId, String studentId);
    
    Optional<Subscription> findByClientIdAndStudentIdAndStatus(UUID clientId, String studentId, Subscription.Status status);
    
    List<Subscription> findByClientIdAndStatus(UUID clientId, Subscription.Status status);
    
    Optional<Subscription> findByIdAndClientId(String id, UUID clientId);
}


