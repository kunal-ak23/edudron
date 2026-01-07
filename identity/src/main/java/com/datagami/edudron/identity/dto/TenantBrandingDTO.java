package com.datagami.edudron.identity.dto;

import java.util.UUID;

public class TenantBrandingDTO {
    private String id;
    private UUID clientId;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String backgroundColor;
    private String surfaceColor;
    private String textPrimaryColor;
    private String textSecondaryColor;
    private String logoUrl;
    private String faviconUrl;
    private String fontFamily;
    private String fontHeading;
    private String borderRadius;
    private Boolean isActive;

    // Constructors
    public TenantBrandingDTO() {}

    public TenantBrandingDTO(String id, UUID clientId, String primaryColor, String secondaryColor,
                            String accentColor, String backgroundColor, String surfaceColor,
                            String textPrimaryColor, String textSecondaryColor, String logoUrl,
                            String faviconUrl, String fontFamily, String fontHeading,
                            String borderRadius, Boolean isActive) {
        this.id = id;
        this.clientId = clientId;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.accentColor = accentColor;
        this.backgroundColor = backgroundColor;
        this.surfaceColor = surfaceColor;
        this.textPrimaryColor = textPrimaryColor;
        this.textSecondaryColor = textSecondaryColor;
        this.logoUrl = logoUrl;
        this.faviconUrl = faviconUrl;
        this.fontFamily = fontFamily;
        this.fontHeading = fontHeading;
        this.borderRadius = borderRadius;
        this.isActive = isActive;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
}


