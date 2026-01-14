package com.datagami.edudron.student.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

public class UserUtil {
    
    private static final Logger log = LoggerFactory.getLogger(UserUtil.class);
    
    private static RestTemplate restTemplate;
    private static String gatewayUrl = System.getenv("GATEWAY_URL");
    
    static {
        if (gatewayUrl == null || gatewayUrl.isEmpty()) {
            gatewayUrl = "http://localhost:8080"; // Default fallback
        }
    }
    
    private static RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new com.datagami.edudron.common.TenantContextRestTemplateInterceptor());
            // Add interceptor to forward JWT token
            interceptors.add((request, body, execution) -> {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().add("Authorization", authHeader);
                        }
                    }
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
    /**
     * Get the current user ID from SecurityContext.
     * The JWT subject contains the email, so we need to look up the user ID from identity service.
     * This ensures consistency with enrollment records which use user ID, not email.
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User not authenticated");
        }
        
        String email = authentication.getName(); // This is the email from JWT subject
        log.debug("Getting user ID for email: {}", email);
        
        // Try to get user ID from identity service using /me endpoint
        try {
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<UserResponse> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                UserResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String userId = response.getBody().getId();
                log.debug("Resolved user ID {} for email {} via /me endpoint", userId, email);
                return userId;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user ID from identity service for email {}. Using email as fallback: {}", 
                email, e.getMessage());
        }
        
        // Fallback: use email as identifier (for backward compatibility)
        // This might cause issues if enrollments use user ID, but it's better than failing
        log.warn("Using email as user ID (fallback). This may cause enrollment lookup issues if enrollments use user ID instead of email.");
        return email;
    }
    
    // Helper class for user response
    private static class UserResponse {
        private String id;
        private String email;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}


