package com.datagami.edudron.identity.dto;

import com.datagami.edudron.identity.domain.TenantFeatureType;

/**
 * DTO representing a single tenant feature with its effective value and metadata.
 */
public class TenantFeatureDto {
    private TenantFeatureType feature;
    private Boolean enabled; // Effective value (default or override)
    private Boolean isOverridden; // Whether tenant has custom value
    private Boolean defaultValue; // The default value for this feature

    public TenantFeatureDto() {}

    public TenantFeatureDto(TenantFeatureType feature, Boolean enabled, Boolean isOverridden, Boolean defaultValue) {
        this.feature = feature;
        this.enabled = enabled;
        this.isOverridden = isOverridden;
        this.defaultValue = defaultValue;
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

    public Boolean getIsOverridden() {
        return isOverridden;
    }

    public void setIsOverridden(Boolean isOverridden) {
        this.isOverridden = isOverridden;
    }

    public Boolean getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Boolean defaultValue) {
        this.defaultValue = defaultValue;
    }
}
