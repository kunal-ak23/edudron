package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.dto.CreateInstituteRequest;
import com.datagami.edudron.student.dto.InstituteDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
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
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class InstituteService {
    
    private static final Logger logger = LoggerFactory.getLogger(InstituteService.class);
    
    @Autowired
    private InstituteRepository instituteRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            logger.debug("Initializing RestTemplate for identity service calls. Gateway URL: {}", gatewayUrl);
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            // Add interceptor to forward JWT token (Authorization header)
            interceptors.add((request, body, execution) -> {
                // Get current request to extract Authorization header
                ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
    
    public InstituteDTO createInstitute(CreateInstituteRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Check if code already exists for this client
        if (instituteRepository.existsByClientIdAndCode(clientId, request.getCode())) {
            throw new IllegalArgumentException("Institute with code '" + request.getCode() + "' already exists");
        }
        
        Institute institute = new Institute();
        institute.setId(UlidGenerator.nextUlid());
        institute.setClientId(clientId);
        institute.setName(request.getName());
        institute.setCode(request.getCode());
        institute.setType(request.getType());
        institute.setAddress(request.getAddress());
        institute.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        
        Institute saved = instituteRepository.save(institute);
        return toDTO(saved, clientId);
    }
    
    public InstituteDTO getInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        return toDTO(institute, clientId);
    }
    
    public List<InstituteDTO> getAllInstitutes() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Institute> institutes = instituteRepository.findByClientId(clientId);
        
        // Filter institutes for INSTRUCTOR users - only show assigned institutes
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole)) {
            List<String> userInstituteIds = getCurrentUserInstituteIds();
            if (userInstituteIds != null && !userInstituteIds.isEmpty()) {
                institutes = institutes.stream()
                    .filter(institute -> userInstituteIds.contains(institute.getId()))
                    .collect(Collectors.toList());
                logger.debug("Filtered institutes for INSTRUCTOR user - showing {} out of {} total institutes", 
                    institutes.size(), instituteRepository.findByClientId(clientId).size());
            } else {
                logger.warn("INSTRUCTOR user has no assigned institutes - returning empty list");
                institutes = new ArrayList<>();
            }
        }
        
        return institutes.stream()
            .map(institute -> toDTO(institute, clientId))
            .collect(Collectors.toList());
    }
    
    public List<InstituteDTO> getActiveInstitutes() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Institute> institutes = instituteRepository.findByClientIdAndIsActive(clientId, true);
        
        // Filter institutes for INSTRUCTOR users - only show assigned institutes
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole)) {
            List<String> userInstituteIds = getCurrentUserInstituteIds();
            if (userInstituteIds != null && !userInstituteIds.isEmpty()) {
                institutes = institutes.stream()
                    .filter(institute -> userInstituteIds.contains(institute.getId()))
                    .collect(Collectors.toList());
                logger.debug("Filtered active institutes for INSTRUCTOR user - showing {} out of {} total active institutes", 
                    institutes.size(), instituteRepository.findByClientIdAndIsActive(clientId, true).size());
            } else {
                logger.warn("INSTRUCTOR user has no assigned institutes - returning empty list");
                institutes = new ArrayList<>();
            }
        }
        
        return institutes.stream()
            .map(institute -> toDTO(institute, clientId))
            .collect(Collectors.toList());
    }
    
    public InstituteDTO updateInstitute(String instituteId, CreateInstituteRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        // Check if code is being changed and if new code already exists
        if (!institute.getCode().equals(request.getCode())) {
            if (instituteRepository.existsByClientIdAndCode(clientId, request.getCode())) {
                throw new IllegalArgumentException("Institute with code '" + request.getCode() + "' already exists");
            }
        }
        
        institute.setName(request.getName());
        institute.setCode(request.getCode());
        institute.setType(request.getType());
        institute.setAddress(request.getAddress());
        if (request.getIsActive() != null) {
            institute.setIsActive(request.getIsActive());
        }
        
        Institute saved = instituteRepository.save(institute);
        return toDTO(saved, clientId);
    }
    
    public void deactivateInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        institute.setIsActive(false);
        instituteRepository.save(institute);
    }
    
    private InstituteDTO toDTO(Institute institute, UUID clientId) {
        InstituteDTO dto = new InstituteDTO();
        dto.setId(institute.getId());
        dto.setClientId(institute.getClientId());
        dto.setName(institute.getName());
        dto.setCode(institute.getCode());
        dto.setType(institute.getType());
        dto.setAddress(institute.getAddress());
        dto.setIsActive(institute.getIsActive());
        dto.setCreatedAt(institute.getCreatedAt());
        dto.setUpdatedAt(institute.getUpdatedAt());
        
        // Get class count
        long classCount = classRepository.countByInstituteId(institute.getId());
        dto.setClassCount(classCount);
        
        return dto;
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
    
    /**
     * Get the current user's assigned institute IDs from the identity service
     * Returns empty list if unable to determine or user has no assigned institutes
     */
    @SuppressWarnings("unchecked")
    private List<String> getCurrentUserInstituteIds() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return new ArrayList<>();
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
                Object instituteIds = response.getBody().get("instituteIds");
                if (instituteIds instanceof List) {
                    return (List<String>) instituteIds;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not determine user institute IDs: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
}

