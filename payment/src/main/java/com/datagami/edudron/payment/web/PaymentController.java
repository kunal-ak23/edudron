package com.datagami.edudron.payment.web;

import com.datagami.edudron.payment.dto.CreatePaymentRequest;
import com.datagami.edudron.payment.dto.PaymentDTO;
import com.datagami.edudron.payment.dto.PaymentResponseDTO;
import com.datagami.edudron.payment.service.PaymentService;
import com.datagami.edudron.payment.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment processing endpoints")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create payment", description = "Create a payment for a course")
    public ResponseEntity<PaymentResponseDTO> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        PaymentResponseDTO response = paymentService.createPayment(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List my payments", description = "Get all payments for the current student")
    public ResponseEntity<List<PaymentDTO>> getMyPayments() {
        String studentId = UserUtil.getCurrentUserId();
        List<PaymentDTO> payments = paymentService.getStudentPayments(studentId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/paged")
    @Operation(summary = "List my payments (paginated)", description = "Get paginated payments for the current student")
    public ResponseEntity<Page<PaymentDTO>> getMyPayments(Pageable pageable) {
        String studentId = UserUtil.getCurrentUserId();
        Page<PaymentDTO> payments = paymentService.getStudentPayments(studentId, pageable);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment", description = "Get payment details by ID")
    public ResponseEntity<PaymentDTO> getPayment(@PathVariable String id) {
        PaymentDTO payment = paymentService.getPayment(id);
        return ResponseEntity.ok(payment);
    }
}

