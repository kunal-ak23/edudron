package com.datagami.edudron.identity.entity;

import com.datagami.edudron.identity.domain.TenantFeatureType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a tenant-specific feature override.
 * If no entry exists for a tenant+feature combination, the default value from TenantFeatureType is used.
 */
@Entity
@Table(name = "tenant_feature", schema = "common", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "feature"}))
public class TenantFeature {
    
    @Id
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 100)
    private TenantFeatureType feature;
    
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public TenantFeature() {}

    public TenantFeature(UUID clientId, TenantFeatureType feature, Boolean enabled) {
        this.clientId = clientId;
        this.feature = feature;
        this.enabled = enabled;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public TenantFeatureType getFeature() {
        return feature;
    }

    public void setFeature(TenantFeatureType feature) {
        this.feature = feature;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
