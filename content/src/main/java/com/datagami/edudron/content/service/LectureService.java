package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.domain.Section;
import com.datagami.edudron.content.dto.LectureContentDTO;
import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.repo.LectureContentRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import com.datagami.edudron.content.repo.SectionRepository;
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
public class LectureService {
    
    private static final Logger log = LoggerFactory.getLogger(LectureService.class);
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    @Autowired
    private MediaUploadService mediaUploadService;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            log.debug("Initializing RestTemplate for identity service calls. Gateway URL: {}", gatewayUrl);
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
                            log.debug("Propagated Authorization header (JWT token) to identity service: {}", request.getURI());
                        } else {
                            log.debug("Authorization header already present in request to {}", request.getURI());
                        }
                    } else {
                        log.debug("No Authorization header found in current request");
                    }
                } else {
                    log.debug("No ServletRequestAttributes found - cannot forward JWT token");
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
            log.debug("RestTemplate initialized with TenantContextRestTemplateInterceptor and JWT token forwarding");
        }
        return restTemplate;
    }
    
    public LectureDTO createLecture(String sectionId, String title, String description, 
                                   Lecture.ContentType contentType) {
        return createLecture(sectionId, title, description, contentType, null);
    }
    
    public LectureDTO createLecture(String sectionId, String title, String description, 
                                   Lecture.ContentType contentType, Integer durationSeconds) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot create lectures
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot create lectures");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify section exists
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        // Get next sequence
        Integer maxSequence = lectureRepository.findMaxSequenceBySectionIdAndClientId(sectionId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        Lecture lecture = new Lecture();
        lecture.setId(UlidGenerator.nextUlid());
        lecture.setClientId(clientId);
        lecture.setSectionId(sectionId);
        lecture.setCourseId(section.getCourseId());
        lecture.setTitle(title);
        lecture.setDescription(description);
        lecture.setContentType(contentType);
        lecture.setSequence(nextSequence);
        if (durationSeconds != null) {
            lecture.setDurationSeconds(durationSeconds);
        }
        
        Lecture saved = lectureRepository.save(lecture);
        return toDTO(saved);
    }
    
    public List<LectureDTO> getLecturesBySection(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Lecture> lectures = lectureRepository.findBySectionIdAndClientIdOrderBySequenceAsc(sectionId, clientId);
        return lectures.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public LectureDTO getLectureById(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Lecture lecture = lectureRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + id));
        
        return toDTO(lecture);
    }
    
    public LectureDTO updateLecture(String id, String title, String description, 
                                   Integer durationSeconds, Boolean isPreview, 
                                   Lecture.ContentType contentType, Boolean isPublished) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot update lectures
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot update lectures");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Lecture lecture = lectureRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + id));
        
        if (title != null) {
            lecture.setTitle(title);
        }
        if (description != null) {
            lecture.setDescription(description);
        }
        if (durationSeconds != null) {
            lecture.setDurationSeconds(durationSeconds);
        }
        if (isPreview != null) {
            lecture.setIsPreview(isPreview);
        }
        if (contentType != null) {
            lecture.setContentType(contentType);
        }
        if (isPublished != null) {
            lecture.setIsPublished(isPublished);
        }
        
        Lecture saved = lectureRepository.save(lecture);
        return toDTO(saved);
    }
    
    public void deleteLecture(String id) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot delete lectures
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot delete lectures");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Lecture lecture = lectureRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + id));
        
        // Load all lecture content to clean up Azure storage files
        List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(id, clientId);
        
        // Delete all Azure storage files before deleting the lecture
        for (LectureContent content : contents) {
            // Delete video URL
            if (content.getVideoUrl() != null) {
                mediaUploadService.deleteMedia(content.getVideoUrl());
            }
            // Delete file URL
            if (content.getFileUrl() != null) {
                mediaUploadService.deleteMedia(content.getFileUrl());
            }
            // Delete transcript URL
            if (content.getTranscriptUrl() != null) {
                mediaUploadService.deleteMedia(content.getTranscriptUrl());
            }
            // Delete thumbnail URL
            if (content.getThumbnailUrl() != null) {
                mediaUploadService.deleteMedia(content.getThumbnailUrl());
            }
            // Delete subtitle URLs (JSON array)
            if (content.getSubtitleUrls() != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode subtitleUrls = content.getSubtitleUrls();
                    if (subtitleUrls.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode subtitleUrl : subtitleUrls) {
                            if (subtitleUrl.isTextual() && subtitleUrl.asText() != null) {
                                mediaUploadService.deleteMedia(subtitleUrl.asText());
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue with deletion
                    System.err.println("Failed to delete subtitle URLs for content " + content.getId() + ": " + e.getMessage());
                }
            }
        }
        
        // Delete the lecture (JPA cascade will delete LectureContent records)
        lectureRepository.delete(lecture);
    }
    
    private LectureDTO toDTO(Lecture lecture) {
        LectureDTO dto = new LectureDTO();
        dto.setId(lecture.getId());
        dto.setClientId(lecture.getClientId());
        dto.setSectionId(lecture.getSectionId());
        dto.setCourseId(lecture.getCourseId());
        dto.setTitle(lecture.getTitle());
        dto.setDescription(lecture.getDescription());
        dto.setContentType(lecture.getContentType());
        dto.setSequence(lecture.getSequence());
        dto.setDurationSeconds(lecture.getDurationSeconds());
        dto.setIsPreview(lecture.getIsPreview());
        dto.setIsPublished(lecture.getIsPublished());
        dto.setCreatedAt(lecture.getCreatedAt());
        dto.setUpdatedAt(lecture.getUpdatedAt());
        
        // Load and include lecture contents
        List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
            lecture.getId(), lecture.getClientId());
        List<LectureContentDTO> contentDTOs = contents.stream()
            .map(this::toContentDTO)
            .collect(Collectors.toList());
        dto.setContents(contentDTOs);
        
        return dto;
    }
    
    private LectureContentDTO toContentDTO(LectureContent content) {
        LectureContentDTO dto = new LectureContentDTO();
        dto.setId(content.getId());
        dto.setClientId(content.getClientId());
        dto.setLectureId(content.getLectureId());
        dto.setContentType(content.getContentType());
        dto.setTitle(content.getTitle());
        dto.setDescription(content.getDescription());
        dto.setFileUrl(content.getFileUrl());
        dto.setFileSizeBytes(content.getFileSizeBytes());
        dto.setMimeType(content.getMimeType());
        dto.setVideoUrl(content.getVideoUrl());
        dto.setTranscriptUrl(content.getTranscriptUrl());
        dto.setSubtitleUrls(content.getSubtitleUrls());
        dto.setThumbnailUrl(content.getThumbnailUrl());
        dto.setTextContent(content.getTextContent());
        dto.setExternalUrl(content.getExternalUrl());
        dto.setEmbeddedCode(content.getEmbeddedCode());
        dto.setSequence(content.getSequence());
        dto.setCreatedAt(content.getCreatedAt());
        dto.setUpdatedAt(content.getUpdatedAt());
        return dto;
    }
    
    /**
     * Get the current user's role from the identity service
     * Returns null if unable to determine role (e.g., anonymous user)
     */
    public String getCurrentUserRole() {
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
            log.debug("Could not determine user role: {}", e.getMessage());
        }
        return null;
    }
}

