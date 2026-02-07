package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.domain.TenantFeatureType;
import com.datagami.edudron.identity.dto.TenantFeatureDto;
import com.datagami.edudron.identity.entity.TenantFeature;
import com.datagami.edudron.identity.repo.TenantFeatureRepository;
import com.datagami.edudron.identity.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TenantFeatureService {
    
    private final TenantFeatureRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityAuditService auditService;

    @Autowired
    public TenantFeatureService(TenantFeatureRepository repository) {
        this.repository = repository;
    }

    /**
     * Check if a feature is enabled for a tenant.
     * Returns the override value if exists, otherwise returns the default value.
     */
    public boolean isFeatureEnabled(UUID clientId, TenantFeatureType feature) {
        Optional<TenantFeature> override = repository.findByClientIdAndFeature(clientId, feature);
        if (override.isPresent()) {
            return Boolean.TRUE.equals(override.get().getEnabled());
        }
        return feature.getDefaultValue();
    }

    /**
     * Set a feature override for a tenant.
     * Creates a new override or updates existing one.
     */
    @CacheEvict(value = "tenantFeature", key = "#clientId.toString() + '::' + #feature.name()")
    public TenantFeature setFeatureEnabled(UUID clientId, TenantFeatureType feature, Boolean enabled) {
        Optional<TenantFeature> existing = repository.findByClientIdAndFeature(clientId, feature);
        
        TenantFeature tenantFeature;
        if (existing.isPresent()) {
            tenantFeature = existing.get();
            tenantFeature.setEnabled(enabled);
        } else {
            tenantFeature = new TenantFeature();
            tenantFeature.setId(UUID.randomUUID());
            tenantFeature.setClientId(clientId);
            tenantFeature.setFeature(feature);
            tenantFeature.setEnabled(enabled);
        }
        
        TenantFeature saved = repository.save(tenantFeature);
        auditService.logCrud(clientId, existing.isPresent() ? "UPDATE" : "CREATE", "TenantFeature",
            saved.getId().toString(), getCurrentUserId(), getCurrentUserEmail(),
            Map.of("clientId", clientId.toString(), "feature", feature.name(), "enabled", enabled));
        return saved;
    }

    /**
     * Reset a feature to its default value by deleting the override.
     */
    @CacheEvict(value = "tenantFeature", key = "#clientId.toString() + '::' + #feature.name()")
    public void resetFeatureToDefault(UUID clientId, TenantFeatureType feature) {
        repository.deleteByClientIdAndFeature(clientId, feature);
        auditService.logCrud(clientId, "DELETE", "TenantFeature", clientId + "::" + feature.name(),
            getCurrentUserId(), getCurrentUserEmail(), Map.of("clientId", clientId.toString(), "feature", feature.name()));
    }

    private String getCurrentUserId() {
        User u = getCurrentUser();
        return u != null ? u.getId() : null;
    }

    private String getCurrentUserEmail() {
        User u = getCurrentUser();
        return u != null ? u.getEmail() : null;
    }

    private User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
            String clientIdStr = TenantContext.getClientId();
            if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                UUID cid = UUID.fromString(clientIdStr);
                Optional<User> user = userRepository.findByEmailAndClientId(auth.getName(), cid);
                if (user.isPresent()) return user.get();
            }
            return userRepository.findByEmailAndRoleAndActiveTrue(auth.getName(), User.Role.SYSTEM_ADMIN).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get all features with their effective values for a tenant.
     */
    public Map<TenantFeatureType, Boolean> getAllFeatures(UUID clientId) {
        List<TenantFeature> overrides = repository.findByClientId(clientId);
        Map<TenantFeatureType, Boolean> overrideMap = overrides.stream()
            .collect(Collectors.toMap(
                TenantFeature::getFeature,
                tf -> Boolean.TRUE.equals(tf.getEnabled())
            ));

        Map<TenantFeatureType, Boolean> result = new HashMap<>();
        for (TenantFeatureType feature : TenantFeatureType.values()) {
            result.put(feature, overrideMap.getOrDefault(feature, feature.getDefaultValue()));
        }
        return result;
    }

    /**
     * Get all features as DTOs with metadata (effective value, isOverridden, defaultValue).
     */
    public List<TenantFeatureDto> getAllFeaturesAsDto(UUID clientId) {
        List<TenantFeature> overrides = repository.findByClientId(clientId);
        Map<TenantFeatureType, TenantFeature> overrideMap = overrides.stream()
            .collect(Collectors.toMap(
                TenantFeature::getFeature,
                tf -> tf
            ));

        List<TenantFeatureDto> result = new ArrayList<>();
        for (TenantFeatureType feature : TenantFeatureType.values()) {
            TenantFeature override = overrideMap.get(feature);
            boolean isOverridden = override != null;
            boolean effectiveValue = isOverridden 
                ? Boolean.TRUE.equals(override.getEnabled())
                : feature.getDefaultValue();
            
            TenantFeatureDto dto = new TenantFeatureDto(
                feature,
                effectiveValue,
                isOverridden,
                feature.getDefaultValue()
            );
            result.add(dto);
        }
        return result;
    }

    /**
     * Get a specific feature as DTO.
     */
    @Cacheable(value = "tenantFeature", key = "#clientId.toString() + '::' + #feature.name()")
    public TenantFeatureDto getFeatureAsDto(UUID clientId, TenantFeatureType feature) {
        Optional<TenantFeature> override = repository.findByClientIdAndFeature(clientId, feature);
        boolean isOverridden = override.isPresent();
        boolean effectiveValue = isOverridden 
            ? Boolean.TRUE.equals(override.get().getEnabled())
            : feature.getDefaultValue();
        
        return new TenantFeatureDto(
            feature,
            effectiveValue,
            isOverridden,
            feature.getDefaultValue()
        );
    }

    /**
     * Convenience method for checking student self-enrollment feature.
     */
    public boolean isStudentSelfEnrollmentEnabled(UUID clientId) {
        return isFeatureEnabled(clientId, TenantFeatureType.STUDENT_SELF_ENROLLMENT);
    }
}
