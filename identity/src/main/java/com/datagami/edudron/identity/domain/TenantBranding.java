package com.datagami.edudron.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_branding", schema = "common")
public class TenantBranding {
    
    @Id
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "client_id", nullable = false, unique = true)
    private UUID clientId;
    
    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#3b82f6";
    
    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor = "#64748b";
    
    @Column(name = "accent_color", length = 7)
    private String accentColor;
    
    @Column(name = "background_color", length = 7)
    private String backgroundColor = "#ffffff";
    
    @Column(name = "surface_color", length = 7)
    private String surfaceColor = "#f8fafc";
    
    @Column(name = "text_primary_color", length = 7)
    private String textPrimaryColor = "#0f172a";
    
    @Column(name = "text_secondary_color", length = 7)
    private String textSecondaryColor = "#64748b";
    
    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;
    
    @Column(name = "favicon_url", columnDefinition = "text")
    private String faviconUrl;
    
    @Column(name = "font_family", length = 100)
    private String fontFamily = "Inter";
    
    @Column(name = "font_heading", length = 100)
    private String fontHeading;
    
    @Column(name = "border_radius", length = 20)
    private String borderRadius = "0.5rem";
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Constructors
    public TenantBranding() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public String getSurfaceColor() { return surfaceColor; }
    public void setSurfaceColor(String surfaceColor) { this.surfaceColor = surfaceColor; }

    public String getTextPrimaryColor() { return textPrimaryColor; }
    public void setTextPrimaryColor(String textPrimaryColor) { this.textPrimaryColor = textPrimaryColor; }

    public String getTextSecondaryColor() { return textSecondaryColor; }
    public void setTextSecondaryColor(String textSecondaryColor) { this.textSecondaryColor = textSecondaryColor; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getFaviconUrl() { return faviconUrl; }
    public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontHeading() { return fontHeading; }
    public void setFontHeading(String fontHeading) { this.fontHeading = fontHeading; }

    public String getBorderRadius() { return borderRadius; }
    public void setBorderRadius(String borderRadius) { this.borderRadius = borderRadius; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

