package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CertificateDTO;
import com.datagami.edudron.student.dto.CertificateGenerateRequest;
import com.datagami.edudron.student.dto.CertificateVisibilityDTO;
import com.datagami.edudron.student.service.CertificateService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
@Tag(name = "Certificates", description = "Generate, manage, and download certificates")
public class CertificateController {

    private static final Logger log = LoggerFactory.getLogger(CertificateController.class);

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate certificates", description = "Generate certificates for a batch of students")
    public ResponseEntity<List<CertificateDTO>> generateCertificates(
            @Valid @RequestBody CertificateGenerateRequest request) {
        String userId = UserUtil.getCurrentUserId();
        String userRole = UserUtil.getCurrentUserRole();

        log.info("Generating certificates for course {} by user {}", request.getCourseId(), userId);
        List<CertificateDTO> results = certificateService.generateCertificates(request, userId, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @GetMapping
    @Operation(summary = "List certificates", description = "List certificates with optional filtering by sectionId and courseId")
    public ResponseEntity<Page<CertificateDTO>> listCertificates(
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String courseId,
            Pageable pageable) {
        return ResponseEntity.ok(certificateService.listCertificates(sectionId, courseId, pageable));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download certificate PDF", description = "Download the PDF for a specific certificate")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        byte[] pdfBytes = certificateService.downloadPdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "certificate-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping("/download-all")
    @Operation(summary = "Download all certificates as ZIP", description = "Download all certificates for a section/course as a ZIP file")
    public ResponseEntity<byte[]> downloadAll(
            @RequestParam String sectionId,
            @RequestParam String courseId) {
        byte[] zipBytes = certificateService.downloadAllAsZip(sectionId, courseId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDispositionFormData("attachment", "certificates.zip");
        headers.setContentLength(zipBytes.length);

        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke certificate", description = "Revoke a certificate with a reason")
    public ResponseEntity<Void> revokeCertificate(@PathVariable String id,
                                                   @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "No reason provided");
        String userId = UserUtil.getCurrentUserId();

        certificateService.revokeCertificate(id, reason, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my")
    @Operation(summary = "My certificates", description = "Get the current student's own certificates")
    public ResponseEntity<List<CertificateDTO>> myCertificates() {
        String studentId = UserUtil.getCurrentUserId();
        return ResponseEntity.ok(certificateService.getStudentCertificates(studentId));
    }

    @PutMapping("/{id}/visibility")
    @Operation(summary = "Update visibility", description = "Update visibility settings for a certificate (student's own)")
    public ResponseEntity<CertificateVisibilityDTO> updateVisibility(
            @PathVariable String id,
            @RequestBody CertificateVisibilityDTO request) {
        String studentId = UserUtil.getCurrentUserId();
        return ResponseEntity.ok(certificateService.updateVisibility(id, studentId, request));
    }
}
