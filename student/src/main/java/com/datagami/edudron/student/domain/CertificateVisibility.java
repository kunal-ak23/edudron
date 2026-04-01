package com.datagami.edudron.student.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "certificate_visibility", schema = "certificate")
public class CertificateVisibility {
    @Id
    @Column(name = "id")
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "student_id", nullable = false, length = 26)
    private String studentId;

    @Column(name = "certificate_id", nullable = false, length = 26)
    private String certificateId;

    @Column(name = "show_scores")
    private boolean showScores = true;

    @Column(name = "show_project_details")
    private boolean showProjectDetails = true;

    @Column(name = "show_overall_percentage")
    private boolean showOverallPercentage = true;

    @Column(name = "show_course_name")
    private boolean showCourseName = true;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id", insertable = false, updatable = false)
    private Certificate certificate;

    public CertificateVisibility() {}

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = com.datagami.edudron.common.UlidGenerator.nextUlid();
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getCertificateId() { return certificateId; }
    public void setCertificateId(String certificateId) { this.certificateId = certificateId; }

    public boolean isShowScores() { return showScores; }
    public void setShowScores(boolean showScores) { this.showScores = showScores; }

    public boolean isShowProjectDetails() { return showProjectDetails; }
    public void setShowProjectDetails(boolean showProjectDetails) { this.showProjectDetails = showProjectDetails; }

    public boolean isShowOverallPercentage() { return showOverallPercentage; }
    public void setShowOverallPercentage(boolean showOverallPercentage) { this.showOverallPercentage = showOverallPercentage; }

    public boolean isShowCourseName() { return showCourseName; }
    public void setShowCourseName(boolean showCourseName) { this.showCourseName = showCourseName; }

    public Certificate getCertificate() { return certificate; }
    public void setCertificate(Certificate certificate) { this.certificate = certificate; }
}
