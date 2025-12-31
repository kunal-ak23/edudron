package com.datagami.edudron.payment.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "payment")
public class Subscription {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;
    private LocalDate nextBillingDate;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public enum Status {
        ACTIVE, CANCELLED, EXPIRED, SUSPENDED
    }

    // Constructors
    public Subscription() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalDate getNextBillingDate() { return nextBillingDate; }
    public void setNextBillingDate(LocalDate nextBillingDate) { this.nextBillingDate = nextBillingDate; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

