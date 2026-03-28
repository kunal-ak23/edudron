package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public class CertificateVerificationDTO {
    private String credentialId;
    private String studentName;
    private String courseName;
    private String institutionName;
    private String institutionLogoUrl;
    private OffsetDateTime issuedAt;
    private boolean valid;
    private boolean revoked;
    private OffsetDateTime revokedAt;
    private String revokedReason;
    private String pdfUrl;
    private Map<String, Object> scores;
    private CertificateVisibilityDTO visibility;

    public CertificateVerificationDTO() {}

    // Getters and Setters
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

    public String getInstitutionLogoUrl() { return institutionLogoUrl; }
    public void setInstitutionLogoUrl(String institutionLogoUrl) { this.institutionLogoUrl = institutionLogoUrl; }

    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public Map<String, Object> getScores() { return scores; }
    public void setScores(Map<String, Object> scores) { this.scores = scores; }

    public CertificateVisibilityDTO getVisibility() { return visibility; }
    public void setVisibility(CertificateVisibilityDTO visibility) { this.visibility = visibility; }
}
