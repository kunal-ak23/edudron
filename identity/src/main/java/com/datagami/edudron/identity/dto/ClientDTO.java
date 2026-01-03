package com.datagami.edudron.identity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ClientDTO {
    private UUID id;
    private String slug;
    private String name;
    private String gstin;
    private Boolean isActive;
    private OffsetDateTime createdAt;

    // Constructors
    public ClientDTO() {}

    public ClientDTO(UUID id, String slug, String name, String gstin, Boolean isActive, OffsetDateTime createdAt) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.gstin = gstin;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

