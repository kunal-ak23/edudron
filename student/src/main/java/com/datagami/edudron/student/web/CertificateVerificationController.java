package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CertificateVerificationDTO;
import com.datagami.edudron.student.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public verification endpoint for certificates.
 * No authentication required — allows anyone with a credential ID to verify.
 */
@RestController
@RequestMapping("/api/verify")
@Tag(name = "Certificate Verification", description = "Public certificate verification")
public class CertificateVerificationController {

    private final CertificateService certificateService;

    public CertificateVerificationController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/{credentialId}")
    @Operation(summary = "Verify certificate", description = "Publicly verify a certificate by its credential ID")
    public ResponseEntity<CertificateVerificationDTO> verify(@PathVariable String credentialId) {
        CertificateVerificationDTO result = certificateService.verify(credentialId);
        return ResponseEntity.ok(result);
    }
}
