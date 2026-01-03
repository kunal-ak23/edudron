package com.datagami.edudron.identity.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.identity.dto.TenantBrandingDTO;
import com.datagami.edudron.identity.service.TenantBrandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenant/branding")
@Tag(name = "Tenant Branding", description = "Manage tenant branding and theming")
public class TenantBrandingController {
    
    private static final Logger log = LoggerFactory.getLogger(TenantBrandingController.class);
    
    @Autowired
    private TenantBrandingService tenantBrandingService;
    
    @GetMapping
    @Operation(summary = "Get tenant branding", description = "Get branding configuration for the current tenant")
    public ResponseEntity<TenantBrandingDTO> getBranding() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        UUID clientId = UUID.fromString(clientIdStr);
        log.info("Getting branding for client: {}", clientId);
        
        TenantBrandingDTO branding = tenantBrandingService.getBrandingByClientId(clientId);
        return ResponseEntity.ok(branding);
    }
    
    @PutMapping
    @Operation(summary = "Update tenant branding", description = "Update branding configuration for the current tenant")
    public ResponseEntity<TenantBrandingDTO> updateBranding(@Valid @RequestBody TenantBrandingDTO brandingDTO) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            return ResponseEntity.badRequest().build();
        }
        
        UUID clientId = UUID.fromString(clientIdStr);
        log.info("Updating branding for client: {}", clientId);
        
        TenantBrandingDTO updatedBranding = tenantBrandingService.updateBranding(clientId, brandingDTO);
        return ResponseEntity.ok(updatedBranding);
    }
}

