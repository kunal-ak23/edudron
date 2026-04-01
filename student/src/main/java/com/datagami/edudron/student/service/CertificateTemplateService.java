package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.CertificateTemplate;
import com.datagami.edudron.student.dto.CertificateTemplateDTO;
import com.datagami.edudron.student.repo.CertificateTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CertificateTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateTemplateService.class);

    private final CertificateTemplateRepository templateRepository;
    private final StudentAuditService auditService;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                      StudentAuditService auditService) {
        this.templateRepository = templateRepository;
        this.auditService = auditService;
    }

    /**
     * List all templates available to the current tenant:
     * tenant-specific templates + system defaults (client_id IS NULL, is_default=true).
     */
    @Transactional(readOnly = true)
    public List<CertificateTemplateDTO> listTemplates() {
        UUID clientId = getClientId();

        List<CertificateTemplate> tenantTemplates = templateRepository.findByClientIdAndIsActiveTrue(clientId);
        List<CertificateTemplate> systemDefaults = templateRepository.findByClientIdIsNullAndIsDefaultTrueAndIsActiveTrue();

        List<CertificateTemplate> all = new ArrayList<>(tenantTemplates);
        all.addAll(systemDefaults);

        return all.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a tenant-scoped template (is_default=false).
     */
    @Transactional
    public CertificateTemplateDTO createTemplate(CertificateTemplateDTO dto) {
        UUID clientId = getClientId();

        CertificateTemplate template = new CertificateTemplate();
        template.setClientId(clientId);
        template.setName(dto.getName());
        template.setDescription(dto.getDescription());
        template.setConfig(dto.getConfig());
        template.setBackgroundImageUrl(dto.getBackgroundImageUrl());
        template.setDefault(false); // tenant templates are never system defaults
        template.setActive(true);

        template = templateRepository.save(template);

        log.info("Created certificate template '{}' (id={}) for tenant {}", template.getName(), template.getId(), clientId);
        auditService.logCrud(clientId, "CREATE", "CertificateTemplate", template.getId(),
                null, null, Map.of("name", template.getName()));

        return toDTO(template);
    }

    /**
     * Update an existing template.
     */
    @Transactional
    public CertificateTemplateDTO updateTemplate(String id, CertificateTemplateDTO dto) {
        UUID clientId = getClientId();

        CertificateTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        // Only allow updating tenant-owned templates
        if (template.getClientId() == null || !template.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Cannot update system default or another tenant's template");
        }

        if (dto.getName() != null) template.setName(dto.getName());
        if (dto.getDescription() != null) template.setDescription(dto.getDescription());
        if (dto.getConfig() != null) template.setConfig(dto.getConfig());
        if (dto.getBackgroundImageUrl() != null) template.setBackgroundImageUrl(dto.getBackgroundImageUrl());

        template = templateRepository.save(template);

        log.info("Updated certificate template '{}' (id={}) for tenant {}", template.getName(), id, clientId);
        auditService.logCrud(clientId, "UPDATE", "CertificateTemplate", id,
                null, null, Map.of("name", template.getName()));

        return toDTO(template);
    }

    /**
     * Get template entity by ID (internal use).
     */
    @Transactional(readOnly = true)
    public CertificateTemplate getTemplateEntity(String id) {
        CertificateTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        // System defaults (null clientId) are accessible to all tenants
        // Tenant-scoped templates must belong to the current tenant
        if (template.getClientId() != null) {
            UUID currentClientId = UUID.fromString(TenantContext.getClientId());
            if (!template.getClientId().equals(currentClientId)) {
                throw new IllegalArgumentException("Template not found: " + id);
            }
        }
        return template;
    }

    public CertificateTemplateDTO toDTO(CertificateTemplate template) {
        CertificateTemplateDTO dto = new CertificateTemplateDTO();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setConfig(template.getConfig());
        dto.setBackgroundImageUrl(template.getBackgroundImageUrl());
        dto.setDefault(template.isDefault());
        dto.setCreatedAt(template.getCreatedAt());
        return dto;
    }

    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }
}
