package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.CertificateTemplate;
import com.datagami.edudron.student.dto.CertificateTemplateDTO;
import com.datagami.edudron.student.repo.CertificateTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class CertificateTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateTemplateService.class);

    private final CertificateTemplateRepository templateRepository;
    private final StudentAuditService auditService;
    private final MediaUploadHelper mediaUploadHelper;
    private final ObjectMapper objectMapper;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                      StudentAuditService auditService,
                                      MediaUploadHelper mediaUploadHelper,
                                      ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.auditService = auditService;
        this.mediaUploadHelper = mediaUploadHelper;
        this.objectMapper = objectMapper;
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

    /**
     * Export a template as a ZIP containing template.json + assets/.
     */
    @Transactional(readOnly = true)
    public byte[] exportTemplate(String id) {
        CertificateTemplate template = getTemplateEntity(id);

        try {
            Map<String, Object> config = template.getConfig() != null ? new HashMap<>(template.getConfig()) : new HashMap<>();
            List<Map<String, Object>> fields = getFieldsList(config);

            // Collect image URLs and map to asset filenames
            Map<String, String> urlToAsset = new LinkedHashMap<>();
            int assetIndex = 0;

            for (Map<String, Object> field : fields) {
                String imageUrl = (String) field.get("imageUrl");
                if (imageUrl != null && !imageUrl.isBlank() && !urlToAsset.containsKey(imageUrl)) {
                    String ext = imageUrl.contains(".") ? imageUrl.substring(imageUrl.lastIndexOf('.')) : ".png";
                    urlToAsset.put(imageUrl, "asset-" + (assetIndex++) + ext);
                }
            }

            // Also check top-level backgroundImageUrl
            String bgUrl = template.getBackgroundImageUrl();
            if (bgUrl != null && !bgUrl.isBlank() && !urlToAsset.containsKey(bgUrl)) {
                String ext = bgUrl.contains(".") ? bgUrl.substring(bgUrl.lastIndexOf('.')) : ".png";
                urlToAsset.put(bgUrl, "asset-" + (assetIndex++) + ext);
            }

            // Replace absolute URLs with relative paths in config
            for (Map<String, Object> field : fields) {
                String imageUrl = (String) field.get("imageUrl");
                if (imageUrl != null && urlToAsset.containsKey(imageUrl)) {
                    field.put("imageUrl", "assets/" + urlToAsset.get(imageUrl));
                }
            }

            // Build template.json payload
            Map<String, Object> templateJson = new LinkedHashMap<>();
            templateJson.put("name", template.getName());
            templateJson.put("description", template.getDescription());
            templateJson.put("config", config);
            if (bgUrl != null && urlToAsset.containsKey(bgUrl)) {
                templateJson.put("backgroundImageUrl", "assets/" + urlToAsset.get(bgUrl));
            }

            // Build ZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // template.json
                zos.putNextEntry(new ZipEntry("template.json"));
                zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(templateJson));
                zos.closeEntry();

                // Download and add assets
                for (Map.Entry<String, String> entry : urlToAsset.entrySet()) {
                    try {
                        byte[] assetBytes = mediaUploadHelper.downloadFile(entry.getKey());
                        zos.putNextEntry(new ZipEntry("assets/" + entry.getValue()));
                        zos.write(assetBytes);
                        zos.closeEntry();
                    } catch (Exception e) {
                        log.warn("Failed to download asset {} for export, skipping: {}", entry.getKey(), e.getMessage());
                    }
                }
            }

            log.info("Exported template '{}' (id={}) as ZIP ({} bytes, {} assets)",
                    template.getName(), id, baos.size(), urlToAsset.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to export template {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to export template", e);
        }
    }

    /**
     * Import a template from a ZIP file.
     */
    @Transactional
    public CertificateTemplateDTO importTemplate(MultipartFile file) {
        UUID clientId = getClientId();

        try {
            Map<String, byte[]> assets = new HashMap<>();
            byte[] templateJsonBytes = null;

            // Extract ZIP
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(file.getBytes()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    byte[] data = zis.readAllBytes();
                    if ("template.json".equals(entry.getName())) {
                        templateJsonBytes = data;
                    } else if (entry.getName().startsWith("assets/")) {
                        String assetName = entry.getName().substring("assets/".length());
                        if (!assetName.isBlank()) {
                            assets.put(assetName, data);
                        }
                    }
                }
            }

            if (templateJsonBytes == null) {
                throw new IllegalArgumentException("Invalid template ZIP: missing template.json");
            }

            Map<String, Object> templateJson = objectMapper.readValue(templateJsonBytes,
                    new TypeReference<Map<String, Object>>() {});

            String name = (String) templateJson.getOrDefault("name", "Imported Template");
            String description = (String) templateJson.get("description");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) templateJson.getOrDefault("config", new HashMap<>());

            // Upload assets and replace relative paths with blob URLs
            Map<String, String> assetToUrl = new HashMap<>();
            String tenantId = clientId.toString();
            for (Map.Entry<String, byte[]> assetEntry : assets.entrySet()) {
                String assetName = assetEntry.getKey();
                String contentType = guessContentType(assetName);
                String url = mediaUploadHelper.uploadTemplateAsset(tenantId, assetName, assetEntry.getValue(), contentType);
                if (url != null) {
                    assetToUrl.put("assets/" + assetName, url);
                }
            }

            // Replace relative paths in config fields
            List<Map<String, Object>> fields = getFieldsList(config);
            for (Map<String, Object> field : fields) {
                String imageUrl = (String) field.get("imageUrl");
                if (imageUrl != null && assetToUrl.containsKey(imageUrl)) {
                    field.put("imageUrl", assetToUrl.get(imageUrl));
                }
            }

            // Handle top-level backgroundImageUrl
            String bgRelative = (String) templateJson.get("backgroundImageUrl");
            String bgUrl = (bgRelative != null && assetToUrl.containsKey(bgRelative)) ? assetToUrl.get(bgRelative) : null;

            // Create template
            CertificateTemplate template = new CertificateTemplate();
            template.setClientId(clientId);
            template.setName(name);
            template.setDescription(description);
            template.setConfig(config);
            template.setBackgroundImageUrl(bgUrl);
            template.setDefault(false);
            template.setActive(true);

            template = templateRepository.save(template);

            log.info("Imported template '{}' (id={}) for tenant {} with {} assets",
                    name, template.getId(), clientId, assets.size());
            auditService.logCrud(clientId, "CREATE", "CertificateTemplate", template.getId(),
                    null, null, Map.of("name", name, "source", "import"));

            return toDTO(template);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to import template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import template", e);
        }
    }

    /**
     * Delete (soft) a template.
     */
    @Transactional
    public void deleteTemplate(String id) {
        UUID clientId = getClientId();

        CertificateTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        if (template.getClientId() == null || !template.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Cannot delete system default or another tenant's template");
        }

        template.setActive(false);
        templateRepository.save(template);

        log.info("Soft-deleted certificate template '{}' (id={}) for tenant {}", template.getName(), id, clientId);
        auditService.logCrud(clientId, "DELETE", "CertificateTemplate", id,
                null, null, Map.of("name", template.getName()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getFieldsList(Map<String, Object> config) {
        Object fieldsObj = config.get("fields");
        if (fieldsObj instanceof List<?>) {
            return (List<Map<String, Object>>) fieldsObj;
        }
        return new ArrayList<>();
    }

    private String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
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
