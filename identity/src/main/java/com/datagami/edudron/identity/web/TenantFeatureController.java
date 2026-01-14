package com.datagami.edudron.identity.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.domain.TenantFeatureType;
import com.datagami.edudron.identity.dto.TenantFeatureDto;
import com.datagami.edudron.identity.dto.TenantFeatureSettingsDto;
import com.datagami.edudron.identity.service.TenantFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant/features")
@Tag(name = "Tenant Features", description = "Manage tenant-level feature flags")
public class TenantFeatureController {
    
    private static final Logger log = LoggerFactory.getLogger(TenantFeatureController.class);
    
    @Autowired
    private TenantFeatureService tenantFeatureService;
    
    @GetMapping
    @Operation(summary = "Get all features", description = "Get all features with their effective values for the current tenant")
    public ResponseEntity<List<TenantFeatureDto>> getAllFeatures() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        UUID clientId = UUID.fromString(clientIdStr);
        log.info("Getting all features for client: {}", clientId);
        
        List<TenantFeatureDto> features = tenantFeatureService.getAllFeaturesAsDto(clientId);
        return ResponseEntity.ok(features);
    }
    
    @GetMapping("/{feature}")
    @Operation(summary = "Get specific feature", description = "Get a specific feature with its effective value")
    public ResponseEntity<TenantFeatureDto> getFeature(@PathVariable String feature) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            TenantFeatureType featureType = TenantFeatureType.valueOf(feature);
            UUID clientId = UUID.fromString(clientIdStr);
            log.info("Getting feature {} for client: {}", feature, clientId);
            
            TenantFeatureDto dto = tenantFeatureService.getFeatureAsDto(clientId, featureType);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid feature type: {}", feature);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{feature}")
    @Operation(summary = "Update feature", description = "Create or update a feature override for the current tenant")
    public ResponseEntity<TenantFeatureDto> updateFeature(
            @PathVariable String feature,
            @RequestBody Map<String, Boolean> request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            TenantFeatureType featureType = TenantFeatureType.valueOf(feature);
            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest().build();
            }
            
            UUID clientId = UUID.fromString(clientIdStr);
            log.info("Updating feature {} to {} for client: {}", feature, enabled, clientId);
            
            tenantFeatureService.setFeatureEnabled(clientId, featureType, enabled);
            TenantFeatureDto dto = tenantFeatureService.getFeatureAsDto(clientId, featureType);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid feature type: {}", feature);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{feature}")
    @Operation(summary = "Reset feature to default", description = "Delete feature override and use default value")
    public ResponseEntity<Void> resetFeature(@PathVariable String feature) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            TenantFeatureType featureType = TenantFeatureType.valueOf(feature);
            UUID clientId = UUID.fromString(clientIdStr);
            log.info("Resetting feature {} to default for client: {}", feature, clientId);
            
            tenantFeatureService.resetFeatureToDefault(clientId, featureType);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid feature type: {}", feature);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/student-self-enrollment")
    @Operation(summary = "Get student self-enrollment feature", description = "Convenience endpoint for student self-enrollment feature")
    public ResponseEntity<Boolean> getStudentSelfEnrollment() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        UUID clientId = UUID.fromString(clientIdStr);
        boolean enabled = tenantFeatureService.isStudentSelfEnrollmentEnabled(clientId);
        return ResponseEntity.ok(enabled);
    }
}
