package com.datagami.edudron.payment.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.payment.domain.Payment;
import com.datagami.edudron.payment.dto.CreatePaymentRequest;
import com.datagami.edudron.payment.dto.PaymentDTO;
import com.datagami.edudron.payment.dto.PaymentResponseDTO;
import com.datagami.edudron.payment.integration.RazorpayClientWrapper;
import com.datagami.edudron.payment.repo.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private RazorpayClientWrapper razorpayClient;
    
    public PaymentResponseDTO createPayment(String studentId, CreatePaymentRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Payment payment = new Payment();
        payment.setId(UlidGenerator.nextUlid());
        payment.setClientId(clientId);
        payment.setStudentId(studentId);
        payment.setCourseId(request.getCourseId());
        payment.setAmountPaise(request.getAmountPaise());
        payment.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        payment.setStatus(Payment.Status.PENDING);
        payment.setProvider("RAZORPAY");
        payment.setPaymentMethod(request.getPaymentMethod());
        
        Payment saved = paymentRepository.save(payment);
        
        // Create Razorpay order
        Map<String, Object> notes = new HashMap<>();
        notes.put("payment_id", saved.getId());
        notes.put("student_id", studentId);
        notes.put("course_id", request.getCourseId());
        
        Map<String, Object> gatewayData = new HashMap<>();
        try {
            Order order = razorpayClient.createOrder(
                request.getAmountPaise(),
                request.getCurrency() != null ? request.getCurrency() : "INR",
                saved.getId(),
                notes
            );
            
            saved.setProviderOrderId(order.get("id").toString());
            saved.setStatus(Payment.Status.PROCESSING);
            saved = paymentRepository.save(saved);
            
            gatewayData.put("order_id", order.get("id"));
            gatewayData.put("amount", order.get("amount"));
            gatewayData.put("currency", order.get("currency"));
            gatewayData.put("key_id", razorpayClient.getKeyId());
        } catch (Exception e) {
            logger.error("Error creating Razorpay order", e);
            saved.setStatus(Payment.Status.FAILED);
            saved.setFailureReason("Failed to create payment gateway order: " + e.getMessage());
            saved = paymentRepository.save(saved);
        }
        
        PaymentResponseDTO response = new PaymentResponseDTO();
        response.setPayment(toDTO(saved));
        response.setPaymentGatewayData(gatewayData);
        
        return response;
    }
    
    public PaymentDTO getPayment(String paymentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Payment payment = paymentRepository.findByIdAndClientId(paymentId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        return toDTO(payment);
    }
    
    public List<PaymentDTO> getStudentPayments(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Payment> payments = paymentRepository.findByClientIdAndStudentId(clientId, studentId);
        return payments.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public Page<PaymentDTO> getStudentPayments(String studentId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<Payment> payments = paymentRepository.findByClientIdAndStudentId(clientId, studentId, pageable);
        return payments.map(this::toDTO);
    }
    
    public void updatePaymentStatus(String providerPaymentId, Payment.Status status, String failureReason) {
        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerPaymentId));
        
        payment.setStatus(status);
        if (status == Payment.Status.SUCCESS) {
            payment.setPaidAt(java.time.OffsetDateTime.now());
        } else if (status == Payment.Status.FAILED && failureReason != null) {
            payment.setFailureReason(failureReason);
        }
        
        paymentRepository.save(payment);
    }
    
    private PaymentDTO toDTO(Payment payment) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setClientId(payment.getClientId());
        dto.setStudentId(payment.getStudentId());
        dto.setCourseId(payment.getCourseId());
        dto.setSubscriptionId(payment.getSubscriptionId());
        dto.setAmountPaise(payment.getAmountPaise());
        dto.setCurrency(payment.getCurrency());
        dto.setStatus(payment.getStatus().name());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setProvider(payment.getProvider());
        dto.setProviderPaymentId(payment.getProviderPaymentId());
        dto.setProviderOrderId(payment.getProviderOrderId());
        dto.setFailureReason(payment.getFailureReason());
        dto.setPaidAt(payment.getPaidAt());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setUpdatedAt(payment.getUpdatedAt());
        return dto;
    }
}

