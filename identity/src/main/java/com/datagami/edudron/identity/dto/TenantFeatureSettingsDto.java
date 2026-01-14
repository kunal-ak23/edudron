package com.datagami.edudron.identity.dto;

import com.datagami.edudron.identity.domain.TenantFeatureType;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO for bulk operations on tenant features.
 */
public class TenantFeatureSettingsDto {
    private Map<TenantFeatureType, Boolean> features;

    public TenantFeatureSettingsDto() {
        this.features = new HashMap<>();
    }

    public TenantFeatureSettingsDto(Map<TenantFeatureType, Boolean> features) {
        this.features = features != null ? features : new HashMap<>();
    }

    public Map<TenantFeatureType, Boolean> getFeatures() {
        return features;
    }

    public void setFeatures(Map<TenantFeatureType, Boolean> features) {
        this.features = features != null ? features : new HashMap<>();
    }
}
