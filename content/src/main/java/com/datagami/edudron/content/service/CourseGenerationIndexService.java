package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.CourseGenerationIndex;
import com.datagami.edudron.content.dto.CourseGenerationIndexDTO;
import com.datagami.edudron.content.repo.CourseGenerationIndexRepository;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CourseGenerationIndexService {
    
    private static final Logger logger = LoggerFactory.getLogger(CourseGenerationIndexService.class);
    
    @Autowired
    private CourseGenerationIndexRepository indexRepository;
    
    @Autowired(required = false)
    private MediaAssetService mediaAssetService;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private final Tika tika = new Tika();
    
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            logger.debug("Initializing RestTemplate for identity service calls. Gateway URL: {}", gatewayUrl);
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            // Add interceptor to forward JWT token (Authorization header)
            interceptors.add((request, body, execution) -> {
                // Get current request to extract Authorization header
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        // Only add if not already present
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().add("Authorization", authHeader);
                            logger.debug("Propagated Authorization header (JWT token) to identity service: {}", request.getURI());
                        } else {
                            logger.debug("Authorization header already present in request to {}", request.getURI());
                        }
                    } else {
                        logger.debug("No Authorization header found in current request");
                    }
                } else {
                    logger.debug("No ServletRequestAttributes found - cannot forward JWT token");
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
            logger.debug("RestTemplate initialized with TenantContextRestTemplateInterceptor and JWT token forwarding");
        }
        return restTemplate;
    }
    
    /**
     * Safely parse clientId from TenantContext, handling SYSTEM_ADMIN users
     */
    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        
        // Handle SYSTEM_ADMIN users who have "SYSTEM" or "PENDING_TENANT_SELECTION" as tenantId
        if ("SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            throw new IllegalArgumentException(
                "A tenant must be selected before managing course generation indexes. " +
                "Please select a tenant from your account settings or use a tenant-specific account."
            );
        }
        
        try {
            return UUID.fromString(clientIdStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid tenant ID format: " + clientIdStr, e);
        }
    }
    
    public CourseGenerationIndexDTO uploadReferenceContent(
            String title,
            String description,
            MultipartFile file) throws IOException {
        
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        UUID clientId = getClientId();
        
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must be less than 50MB");
        }
        
        // Upload file to Azure Storage using MediaAssetService
        String fileUrl;
        if (mediaAssetService != null) {
            try {
                com.datagami.edudron.content.domain.MediaAsset asset = 
                    mediaAssetService.uploadAsset(file, com.datagami.edudron.content.domain.MediaAsset.AssetType.DOCUMENT, "system");
                fileUrl = asset.getFileUrl();
            } catch (Exception e) {
                logger.warn("Failed to upload file using MediaAssetService, using placeholder: {}", e.getMessage());
                fileUrl = "uploaded/" + file.getOriginalFilename();
            }
        } else {
            logger.warn("MediaAssetService not available, using placeholder URL");
            fileUrl = "uploaded/" + file.getOriginalFilename();
        }
        
        // Extract text content
        String extractedText = extractTextFromFile(file);
        
        // Create index entity
        CourseGenerationIndex index = new CourseGenerationIndex();
        index.setId(UlidGenerator.nextUlid());
        index.setClientId(clientId);
        index.setTitle(title);
        index.setDescription(description);
        index.setIndexType(CourseGenerationIndex.IndexType.REFERENCE_CONTENT);
        index.setFileUrl(fileUrl);
        index.setFileSizeBytes(file.getSize());
        index.setMimeType(file.getContentType());
        index.setExtractedText(extractedText);
        index.setIsActive(true);
        
        CourseGenerationIndex saved = indexRepository.save(index);
        logger.info("Uploaded reference content index: {}", saved.getId());
        
        return toDTO(saved);
    }
    
    public CourseGenerationIndexDTO createWritingFormat(
            String title,
            String description,
            String writingFormat) {
        
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        UUID clientId = getClientId();
        
        // Create index entity
        CourseGenerationIndex index = new CourseGenerationIndex();
        index.setId(UlidGenerator.nextUlid());
        index.setClientId(clientId);
        index.setTitle(title);
        index.setDescription(description);
        index.setIndexType(CourseGenerationIndex.IndexType.WRITING_FORMAT);
        index.setWritingFormat(writingFormat);
        index.setIsActive(true);
        
        CourseGenerationIndex saved = indexRepository.save(index);
        logger.info("Created writing format index: {}", saved.getId());
        
        return toDTO(saved);
    }
    
    public CourseGenerationIndexDTO uploadWritingFormat(
            String title,
            String description,
            MultipartFile file) throws IOException {
        
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        UUID clientId = getClientId();
        
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must be less than 50MB");
        }
        
        // Extract text content (this will be the writing format)
        String writingFormat = extractTextFromFile(file);
        
        // Create index entity
        CourseGenerationIndex index = new CourseGenerationIndex();
        index.setId(UlidGenerator.nextUlid());
        index.setClientId(clientId);
        index.setTitle(title);
        index.setDescription(description);
        index.setIndexType(CourseGenerationIndex.IndexType.WRITING_FORMAT);
        index.setWritingFormat(writingFormat);
        index.setIsActive(true);
        
        CourseGenerationIndex saved = indexRepository.save(index);
        logger.info("Uploaded writing format index: {}", saved.getId());
        
        return toDTO(saved);
    }
    
    public List<CourseGenerationIndexDTO> getAllActiveIndexes() {
        UUID clientId = getClientId();
        
        List<CourseGenerationIndex> indexes = indexRepository.findByClientIdAndIsActiveTrue(clientId);
        return indexes.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<CourseGenerationIndexDTO> getIndexesByType(CourseGenerationIndex.IndexType indexType) {
        UUID clientId = getClientId();
        
        List<CourseGenerationIndex> indexes = indexRepository.findByClientIdAndIndexTypeAndIsActiveTrue(clientId, indexType);
        return indexes.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public CourseGenerationIndexDTO getIndexById(String id) {
        UUID clientId = getClientId();
        
        CourseGenerationIndex index = indexRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Index not found: " + id));
        
        return toDTO(index);
    }
    
    public void deleteIndex(String id) {
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        UUID clientId = getClientId();
        
        CourseGenerationIndex index = indexRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Index not found: " + id));
        
        indexRepository.delete(index);
        logger.info("Deleted index: {}", id);
    }
    
    /**
     * Get the current user's role from the identity service
     * Returns null if unable to determine role (e.g., anonymous user)
     */
    private String getCurrentUserRole() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return null;
            }
            
            // Get user info from identity service
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object role = response.getBody().get("role");
                return role != null ? role.toString() : null;
            }
        } catch (Exception e) {
            logger.debug("Could not determine user role: {}", e.getMessage());
        }
        return null;
    }
    
    private String extractTextFromFile(MultipartFile file) throws IOException {
        try {
            String extractedText = tika.parseToString(file.getInputStream());
            // Limit extracted text to reasonable size (100K characters)
            if (extractedText.length() > 100000) {
                extractedText = extractedText.substring(0, 100000) + "... [truncated]";
            }
            return extractedText;
        } catch (TikaException e) {
            logger.warn("Failed to extract text from file: {}", file.getOriginalFilename(), e);
            return ""; // Return empty string if extraction fails
        }
    }
    
    private CourseGenerationIndexDTO toDTO(CourseGenerationIndex index) {
        CourseGenerationIndexDTO dto = new CourseGenerationIndexDTO();
        dto.setId(index.getId());
        dto.setClientId(index.getClientId());
        dto.setTitle(index.getTitle());
        dto.setDescription(index.getDescription());
        dto.setIndexType(index.getIndexType());
        dto.setFileUrl(index.getFileUrl());
        dto.setFileSizeBytes(index.getFileSizeBytes());
        dto.setMimeType(index.getMimeType());
        dto.setExtractedText(index.getExtractedText());
        dto.setWritingFormat(index.getWritingFormat());
        dto.setIsActive(index.getIsActive());
        dto.setCreatedAt(index.getCreatedAt());
        dto.setUpdatedAt(index.getUpdatedAt());
        return dto;
    }
}

