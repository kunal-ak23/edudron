package com.datagami.edudron.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients", schema = "common")
public class Client {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String gstin;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public Client() {}

    public Client(UUID id, String slug, String name, String gstin) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.gstin = gstin;
        this.isActive = true;
        this.createdAt = OffsetDateTime.now();
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


