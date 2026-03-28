package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.client.ContentAssessmentClient;
import com.datagami.edudron.student.client.IdentityUserClient;
import com.datagami.edudron.student.domain.Certificate;
import com.datagami.edudron.student.domain.CertificateTemplate;
import com.datagami.edudron.student.domain.CertificateVisibility;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.CertificateRepository;
import com.datagami.edudron.student.repo.CertificateVisibilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private static final String CREDENTIAL_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CREDENTIAL_RANDOM_LENGTH = 5;
    private static final int BULK_LIMIT_NON_ADMIN = 150;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificateRepository;
    private final CertificateVisibilityRepository visibilityRepository;
    private final CertificateTemplateService templateService;
    private final CertificatePdfGenerator pdfGenerator;
    private final MediaUploadHelper mediaUploadHelper;
    private final IdentityUserClient identityUserClient;
    private final ContentAssessmentClient contentAssessmentClient;
    private final StudentAuditService auditService;

    @Value("${app.verification-base-url:https://student-portal.vercel.app/verify}")
    private String verificationBaseUrl;

    public CertificateService(CertificateRepository certificateRepository,
                              CertificateVisibilityRepository visibilityRepository,
                              CertificateTemplateService templateService,
                              CertificatePdfGenerator pdfGenerator,
                              MediaUploadHelper mediaUploadHelper,
                              IdentityUserClient identityUserClient,
                              ContentAssessmentClient contentAssessmentClient,
                              StudentAuditService auditService) {
        this.certificateRepository = certificateRepository;
        this.visibilityRepository = visibilityRepository;
        this.templateService = templateService;
        this.pdfGenerator = pdfGenerator;
        this.mediaUploadHelper = mediaUploadHelper;
        this.identityUserClient = identityUserClient;
        this.contentAssessmentClient = contentAssessmentClient;
        this.auditService = auditService;
    }

    // -------------------------------------------------------------------------
    // Generate Certificates
    // -------------------------------------------------------------------------

    /**
     * Generate certificates for a batch of students.
     */
    @Transactional
    public List<CertificateDTO> generateCertificates(CertificateGenerateRequest request,
                                                      String issuedByUserId, String userRole) {
        UUID clientId = getClientId();

        List<CertificateGenerateRequest.StudentEntry> students = request.getStudents();
        if (students == null || students.isEmpty()) {
            throw new IllegalArgumentException("Student list is empty");
        }

        // Bulk limit check
        if (students.size() > BULK_LIMIT_NON_ADMIN && !"SYSTEM_ADMIN".equals(userRole)) {
            throw new IllegalArgumentException(
                    "Batch size " + students.size() + " exceeds limit of " + BULK_LIMIT_NON_ADMIN +
                    ". Only SYSTEM_ADMIN can generate more than " + BULK_LIMIT_NON_ADMIN + " certificates at once.");
        }

        CertificateTemplate template = templateService.getTemplateEntity(request.getTemplateId());

        // Fetch course name from Content service
        String courseName = resolveCourseName(request.getCourseId());

        List<CertificateDTO> results = new ArrayList<>();
        int skipped = 0;

        for (CertificateGenerateRequest.StudentEntry entry : students) {
            try {
                // Resolve student user from Identity service by email
                String studentId = resolveStudentId(entry.getEmail());
                if (studentId == null) {
                    log.warn("Student not found for email '{}', skipping certificate generation", entry.getEmail());
                    skipped++;
                    continue;
                }

                // Check for duplicate certificate
                Optional<Certificate> existing = certificateRepository
                        .findByClientIdAndStudentIdAndCourseIdAndIsActiveTrue(clientId, studentId, request.getCourseId());
                if (existing.isPresent()) {
                    log.warn("Certificate already exists for student {} / course {}, skipping",
                            studentId, request.getCourseId());
                    skipped++;
                    continue;
                }

                String credentialId = generateCredentialId();
                String verificationUrl = verificationBaseUrl + "/" + credentialId;
                OffsetDateTime issuedAt = OffsetDateTime.now();

                // Generate PDF
                byte[] pdfBytes = pdfGenerator.generatePdf(
                        template.getConfig(),
                        entry.getName(),
                        courseName,
                        credentialId,
                        verificationUrl,
                        issuedAt
                );

                // Upload PDF to blob storage
                String pdfUrl = mediaUploadHelper.uploadCertificatePdf(
                        clientId.toString(), credentialId, pdfBytes);

                // Create Certificate entity
                Certificate cert = new Certificate();
                cert.setClientId(clientId);
                cert.setStudentId(studentId);
                cert.setCourseId(request.getCourseId());
                cert.setSectionId(request.getSectionId());
                cert.setClassId(request.getClassId());
                cert.setTemplateId(request.getTemplateId());
                cert.setCredentialId(credentialId);
                cert.setQrCodeUrl(verificationUrl);
                cert.setPdfUrl(pdfUrl);
                cert.setIssuedAt(issuedAt);
                cert.setIssuedBy(issuedByUserId);
                cert.setMetadata(Map.of(
                        "studentName", entry.getName() != null ? entry.getName() : "",
                        "studentEmail", entry.getEmail() != null ? entry.getEmail() : "",
                        "courseName", courseName != null ? courseName : ""
                ));

                cert = certificateRepository.save(cert);

                // Create visibility record with all fields visible by default
                CertificateVisibility visibility = new CertificateVisibility();
                visibility.setClientId(clientId);
                visibility.setStudentId(studentId);
                visibility.setCertificateId(cert.getId());
                visibility.setShowScores(true);
                visibility.setShowProjectDetails(true);
                visibility.setShowOverallPercentage(true);
                visibility.setShowCourseName(true);
                visibilityRepository.save(visibility);

                results.add(toDTO(cert));

            } catch (Exception e) {
                log.error("Failed to generate certificate for student '{}': {}",
                        entry.getEmail(), e.getMessage(), e);
                skipped++;
            }
        }

        log.info("Certificate generation complete: {} created, {} skipped out of {} total",
                results.size(), skipped, students.size());

        auditService.logCrud(clientId, "CREATE", "Certificate", request.getCourseId(),
                issuedByUserId, null,
                Map.of("count", results.size(), "skipped", skipped, "courseId", request.getCourseId()));

        return results;
    }

    // -------------------------------------------------------------------------
    // Revoke
    // -------------------------------------------------------------------------

    @Transactional
    public void revokeCertificate(String id, String reason, String revokedByUserId) {
        UUID clientId = getClientId();

        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + id));

        if (!cert.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Certificate not found: " + id);
        }

        cert.setRevokedAt(OffsetDateTime.now());
        cert.setRevokedReason(reason);
        certificateRepository.save(cert);

        log.info("Revoked certificate {} (credential {}) for reason: {}", id, cert.getCredentialId(), reason);
        auditService.logCrud(clientId, "UPDATE", "Certificate", id,
                revokedByUserId, null, Map.of("action", "revoke", "reason", reason));
    }

    // -------------------------------------------------------------------------
    // List / Query
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<CertificateDTO> listCertificates(String sectionId, String courseId, Pageable pageable) {
        UUID clientId = getClientId();

        Page<Certificate> page;
        if (sectionId != null && !sectionId.isBlank() && courseId != null && !courseId.isBlank()) {
            page = certificateRepository.findByClientIdAndSectionIdAndCourseIdAndIsActiveTrue(
                    clientId, sectionId, courseId, pageable);
        } else if (sectionId != null && !sectionId.isBlank()) {
            page = certificateRepository.findByClientIdAndSectionIdAndIsActiveTrue(clientId, sectionId, pageable);
        } else if (courseId != null && !courseId.isBlank()) {
            page = certificateRepository.findByClientIdAndCourseIdAndIsActiveTrue(clientId, courseId, pageable);
        } else {
            page = certificateRepository.findByClientIdAndIsActiveTrue(clientId, pageable);
        }

        return page.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<CertificateDTO> getStudentCertificates(String studentId) {
        UUID clientId = getClientId();
        List<Certificate> certs = certificateRepository.findByClientIdAndStudentIdAndIsActiveTrue(clientId, studentId);
        List<CertificateDTO> dtos = new ArrayList<>();
        for (Certificate cert : certs) {
            dtos.add(toDTO(cert));
        }
        return dtos;
    }

    // -------------------------------------------------------------------------
    // Visibility
    // -------------------------------------------------------------------------

    @Transactional
    public CertificateVisibilityDTO updateVisibility(String certId, String studentId,
                                                      CertificateVisibilityDTO request) {
        UUID clientId = getClientId();

        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certId));

        if (!cert.getClientId().equals(clientId) || !cert.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("Certificate not found or access denied");
        }

        CertificateVisibility visibility = visibilityRepository.findByCertificateId(certId)
                .orElseThrow(() -> new IllegalStateException("Visibility record not found for certificate: " + certId));

        visibility.setShowScores(request.isShowScores());
        visibility.setShowProjectDetails(request.isShowProjectDetails());
        visibility.setShowOverallPercentage(request.isShowOverallPercentage());
        visibility.setShowCourseName(request.isShowCourseName());
        visibilityRepository.save(visibility);

        return toVisibilityDTO(visibility);
    }

    // -------------------------------------------------------------------------
    // Public Verification
    // -------------------------------------------------------------------------

    /**
     * Verify a certificate by credential ID. Public, no authentication required.
     * Respects visibility settings for which data fields to expose.
     */
    @Transactional(readOnly = true)
    public CertificateVerificationDTO verify(String credentialId) {
        Certificate cert = certificateRepository.findByCredentialId(credentialId)
                .orElse(null);

        CertificateVerificationDTO dto = new CertificateVerificationDTO();
        dto.setCredentialId(credentialId);

        if (cert == null) {
            dto.setValid(false);
            return dto;
        }

        dto.setValid(cert.isActive() && !cert.isRevoked());
        dto.setRevoked(cert.isRevoked());
        dto.setRevokedAt(cert.getRevokedAt());
        dto.setRevokedReason(cert.getRevokedReason());
        dto.setIssuedAt(cert.getIssuedAt());
        dto.setPdfUrl(cert.getPdfUrl());

        // Populate names from metadata
        Map<String, Object> meta = cert.getMetadata();
        if (meta != null) {
            dto.setStudentName((String) meta.getOrDefault("studentName", ""));
            dto.setCourseName((String) meta.getOrDefault("courseName", ""));
            dto.setInstitutionName((String) meta.getOrDefault("institutionName", ""));
            dto.setInstitutionLogoUrl((String) meta.getOrDefault("institutionLogoUrl", ""));
        }

        // Apply visibility settings
        CertificateVisibility visibility = visibilityRepository.findByCertificateId(cert.getId())
                .orElse(null);
        if (visibility != null) {
            CertificateVisibilityDTO visDTO = toVisibilityDTO(visibility);
            dto.setVisibility(visDTO);

            // If student hides course name, clear it
            if (!visibility.isShowCourseName()) {
                dto.setCourseName(null);
            }
        }

        return dto;
    }

    // -------------------------------------------------------------------------
    // Download All as ZIP
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public byte[] downloadAllAsZip(String sectionId, String courseId) {
        UUID clientId = getClientId();

        List<Certificate> certs;
        if (sectionId != null && !sectionId.isBlank() && courseId != null && !courseId.isBlank()) {
            certs = certificateRepository.findByClientIdAndCourseIdAndSectionIdAndIsActiveTrue(
                    clientId, courseId, sectionId);
        } else {
            throw new IllegalArgumentException("Both sectionId and courseId are required for bulk download");
        }

        if (certs.isEmpty()) {
            throw new IllegalArgumentException("No certificates found for the given scope");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Certificate cert : certs) {
                if (cert.getPdfUrl() == null || cert.getPdfUrl().isEmpty()) {
                    log.warn("Certificate {} has no PDF URL, skipping in ZIP", cert.getId());
                    continue;
                }

                try {
                    byte[] pdfBytes = mediaUploadHelper.downloadFile(cert.getPdfUrl());
                    String entryName = cert.getCredentialId() + ".pdf";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(pdfBytes);
                    zos.closeEntry();
                } catch (Exception e) {
                    log.warn("Failed to include certificate {} in ZIP: {}", cert.getId(), e.getMessage());
                }
            }

            zos.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to create ZIP archive", e);
        }
    }

    // -------------------------------------------------------------------------
    // Download individual PDF
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public byte[] downloadPdf(String certId) {
        UUID clientId = getClientId();

        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + certId));

        if (!cert.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Certificate not found: " + certId);
        }

        if (cert.getPdfUrl() == null || cert.getPdfUrl().isEmpty()) {
            throw new IllegalStateException("Certificate has no associated PDF file");
        }

        return mediaUploadHelper.downloadFile(cert.getPdfUrl());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generateCredentialId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("EDU-");
            sb.append(Year.now().getValue());
            sb.append("-");
            for (int i = 0; i < CREDENTIAL_RANDOM_LENGTH; i++) {
                sb.append(CREDENTIAL_CHARS.charAt(RANDOM.nextInt(CREDENTIAL_CHARS.length())));
            }
            String id = sb.toString();
            if (certificateRepository.findByCredentialId(id).isEmpty()) {
                return id;
            }
            log.warn("Credential ID collision on '{}', retrying (attempt {})", id, attempt + 1);
        }
        throw new IllegalStateException("Failed to generate unique credential ID after 5 attempts");
    }

    private String resolveCourseName(String courseId) {
        try {
            JsonNode course = contentAssessmentClient.getCourse(courseId);
            if (course != null && course.has("title")) {
                return course.get("title").asText();
            }
        } catch (Exception e) {
            log.warn("Could not resolve course name for {}: {}", courseId, e.getMessage());
        }
        return "Course " + courseId;
    }

    /**
     * Resolve student user ID from email via Identity service.
     * Searches users by email, returning the user ID if found.
     */
    private String resolveStudentId(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            // Use the identity user client to search by email
            // The /idp/users/by-email endpoint may not exist, so we use a search approach
            // For now, we store the email in metadata and use it for lookup
            // The student entries from the request provide both name and email
            // We try to resolve via identity service
            JsonNode user = identityUserClient.getUserByEmail(email);
            if (user != null && user.has("id")) {
                return user.get("id").asText();
            }
        } catch (Exception e) {
            log.warn("Could not resolve student ID for email {}: {}", email, e.getMessage());
        }
        return null;
    }

    CertificateDTO toDTO(Certificate cert) {
        CertificateDTO dto = new CertificateDTO();
        dto.setId(cert.getId());
        dto.setStudentId(cert.getStudentId());
        dto.setCourseId(cert.getCourseId());
        dto.setSectionId(cert.getSectionId());
        dto.setClassId(cert.getClassId());
        dto.setTemplateId(cert.getTemplateId());
        dto.setCredentialId(cert.getCredentialId());
        dto.setQrCodeUrl(cert.getQrCodeUrl());
        dto.setPdfUrl(cert.getPdfUrl());
        dto.setIssuedAt(cert.getIssuedAt());
        dto.setIssuedBy(cert.getIssuedBy());
        dto.setRevoked(cert.isRevoked());
        dto.setRevokedAt(cert.getRevokedAt());
        dto.setRevokedReason(cert.getRevokedReason());
        dto.setMetadata(cert.getMetadata());

        // Populate names from metadata
        Map<String, Object> meta = cert.getMetadata();
        if (meta != null) {
            dto.setStudentName((String) meta.getOrDefault("studentName", ""));
            dto.setStudentEmail((String) meta.getOrDefault("studentEmail", ""));
            dto.setCourseName((String) meta.getOrDefault("courseName", ""));
        }

        // Populate visibility
        CertificateVisibility vis = cert.getVisibility();
        if (vis != null) {
            dto.setVisibility(toVisibilityDTO(vis));
        }

        return dto;
    }

    private CertificateVisibilityDTO toVisibilityDTO(CertificateVisibility vis) {
        CertificateVisibilityDTO dto = new CertificateVisibilityDTO();
        dto.setShowScores(vis.isShowScores());
        dto.setShowProjectDetails(vis.isShowProjectDetails());
        dto.setShowOverallPercentage(vis.isShowOverallPercentage());
        dto.setShowCourseName(vis.isShowCourseName());
        return dto;
    }

    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }
}
