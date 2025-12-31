package com.datagami.edudron.payment.repo;

import com.datagami.edudron.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    
    List<Payment> findByClientIdAndStudentId(UUID clientId, String studentId);
    
    Page<Payment> findByClientIdAndStudentId(UUID clientId, String studentId, Pageable pageable);
    
    List<Payment> findByClientIdAndCourseId(UUID clientId, String courseId);
    
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    
    Optional<Payment> findByProviderOrderId(String providerOrderId);
    
    Optional<Payment> findByIdAndClientId(String id, UUID clientId);
}

