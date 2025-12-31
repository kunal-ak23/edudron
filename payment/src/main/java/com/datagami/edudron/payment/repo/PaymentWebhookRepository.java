package com.datagami.edudron.payment.repo;

import com.datagami.edudron.payment.domain.PaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, String> {
    
    Optional<PaymentWebhook> findByProviderEventId(String providerEventId);
    
    List<PaymentWebhook> findByClientIdAndProcessed(UUID clientId, Boolean processed);
}

