package com.datagami.edudron.identity.service;

import com.datagami.edudron.identity.domain.TenantBranding;
import com.datagami.edudron.identity.dto.TenantBrandingDTO;
import com.datagami.edudron.identity.repo.TenantBrandingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantBrandingService {
    
    private static final Logger log = LoggerFactory.getLogger(TenantBrandingService.class);
    
    @Autowired
    private TenantBrandingRepository tenantBrandingRepository;
    
    @Cacheable(value = "tenantBranding", key = "#clientId")
    @Transactional(readOnly = true)
    public TenantBrandingDTO getBrandingByClientId(UUID clientId) {
        log.info("Getting branding for client: {}", clientId);
        
        return tenantBrandingRepository.findByClientIdAndIsActiveTrue(clientId)
            .map(this::toDTO)
            .orElse(getDefaultBranding(clientId));
    }
    
    @CacheEvict(value = "tenantBranding", key = "#clientId")
    @Transactional
    public TenantBrandingDTO updateBranding(UUID clientId, TenantBrandingDTO brandingDTO) {
        log.info("Updating branding for client: {}", clientId);
        
        TenantBranding existingBranding = tenantBrandingRepository.findByClientId(clientId)
            .orElse(null);
        
        if (existingBranding != null) {
            // Update existing branding
            updateEntityFromDTO(existingBranding, brandingDTO);
            existingBranding.setClientId(clientId);
            tenantBrandingRepository.save(existingBranding);
            return toDTO(existingBranding);
        } else {
            // Create new branding
            TenantBranding newBranding = new TenantBranding();
            newBranding.setId(UUID.randomUUID());
            newBranding.setClientId(clientId);
            updateEntityFromDTO(newBranding, brandingDTO);
            tenantBrandingRepository.save(newBranding);
            return toDTO(newBranding);
        }
    }
    
    private TenantBrandingDTO toDTO(TenantBranding entity) {
        TenantBrandingDTO dto = new TenantBrandingDTO();
        dto.setId(entity.getId().toString());
        dto.setClientId(entity.getClientId());
        dto.setPrimaryColor(entity.getPrimaryColor());
        dto.setSecondaryColor(entity.getSecondaryColor());
        dto.setAccentColor(entity.getAccentColor());
        dto.setBackgroundColor(entity.getBackgroundColor());
        dto.setSurfaceColor(entity.getSurfaceColor());
        dto.setTextPrimaryColor(entity.getTextPrimaryColor());
        dto.setTextSecondaryColor(entity.getTextSecondaryColor());
        dto.setLogoUrl(entity.getLogoUrl());
        dto.setFaviconUrl(entity.getFaviconUrl());
        dto.setFontFamily(entity.getFontFamily());
        dto.setFontHeading(entity.getFontHeading());
        dto.setBorderRadius(entity.getBorderRadius());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }
    
    private void updateEntityFromDTO(TenantBranding entity, TenantBrandingDTO dto) {
        if (dto.getPrimaryColor() != null) entity.setPrimaryColor(dto.getPrimaryColor());
        if (dto.getSecondaryColor() != null) entity.setSecondaryColor(dto.getSecondaryColor());
        if (dto.getAccentColor() != null) entity.setAccentColor(dto.getAccentColor());
        if (dto.getBackgroundColor() != null) entity.setBackgroundColor(dto.getBackgroundColor());
        if (dto.getSurfaceColor() != null) entity.setSurfaceColor(dto.getSurfaceColor());
        if (dto.getTextPrimaryColor() != null) entity.setTextPrimaryColor(dto.getTextPrimaryColor());
        if (dto.getTextSecondaryColor() != null) entity.setTextSecondaryColor(dto.getTextSecondaryColor());
        if (dto.getLogoUrl() != null) entity.setLogoUrl(dto.getLogoUrl());
        if (dto.getFaviconUrl() != null) entity.setFaviconUrl(dto.getFaviconUrl());
        if (dto.getFontFamily() != null) entity.setFontFamily(dto.getFontFamily());
        if (dto.getFontHeading() != null) entity.setFontHeading(dto.getFontHeading());
        if (dto.getBorderRadius() != null) entity.setBorderRadius(dto.getBorderRadius());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
    }
    
    private TenantBrandingDTO getDefaultBranding(UUID clientId) {
        TenantBrandingDTO dto = new TenantBrandingDTO();
        dto.setClientId(clientId);
        dto.setPrimaryColor("#3b82f6"); // Blue
        dto.setSecondaryColor("#64748b"); // Slate
        dto.setAccentColor("#f59e0b"); // Amber
        dto.setBackgroundColor("#ffffff"); // White
        dto.setSurfaceColor("#f8fafc"); // Slate 50
        dto.setTextPrimaryColor("#0f172a"); // Slate 900
        dto.setTextSecondaryColor("#64748b"); // Slate 500
        dto.setFontFamily("Inter");
        dto.setBorderRadius("0.5rem");
        dto.setIsActive(true);
        return dto;
    }
}


