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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class UserUtil {

    private static final Logger log = LoggerFactory.getLogger(UserUtil.class);

    private static volatile RestTemplate restTemplate;
    private static final Object restTemplateLock = new Object();
    private static String gatewayUrl = System.getenv("GATEWAY_URL");

    // Cache user responses by email for 5 minutes to avoid repeated HTTP calls
    private static final ConcurrentHashMap<String, CachedUser> userCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    static {
        if (gatewayUrl == null || gatewayUrl.isEmpty()) {
            gatewayUrl = "http://localhost:8080"; // Default fallback
        }
    }

    private static RestTemplate getRestTemplate() {
        // Double-checked locking for thread safety
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate template = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new com.datagami.edudron.common.TenantContextRestTemplateInterceptor());
                    // Add interceptor to forward JWT token
                    interceptors.add((request, body, execution) -> {
                        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                                .getRequestAttributes();
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
                    template.setInterceptors(interceptors);
                    restTemplate = template;
                }
            }
        }
        return restTemplate;
    }

    /**
     * Get cached user response, or fetch from identity service if not cached/expired.
     */
    private static UserResponse getCachedUserResponse(String email) {
        CachedUser cached = userCache.get(email);
        if (cached != null && !cached.isExpired()) {
            return cached.user;
        }

        // Fetch from identity service
        try {
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = getRestTemplate().exchange(
                    meUrl,
                    HttpMethod.GET,
                    entity,
                    UserResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserResponse user = response.getBody();
                userCache.put(email, new CachedUser(user));

                // Evict old entries periodically (keep cache bounded)
                if (userCache.size() > 5000) {
                    userCache.entrySet().removeIf(e -> e.getValue().isExpired());
                }

                return user;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user from identity service for email {}. {}",
                    email, e.getMessage());
            // Return stale cached data on failure rather than falling back to email
            if (cached != null) {
                log.info("Returning stale cached user for {} (expired {}ms ago)",
                        email, System.currentTimeMillis() - cached.timestamp - CACHE_TTL_MS);
                return cached.user;
            }
        }

        return null;
    }

    /**
     * Get the current user ID from SecurityContext.
     * The JWT subject contains the email, so we need to look up the user ID from
     * identity service.
     * This ensures consistency with enrollment records which use user ID, not
     * email.
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User not authenticated");
        }

        String email = authentication.getName(); // This is the email from JWT subject
        log.debug("Getting user ID for email: {}", email);

        UserResponse user = getCachedUserResponse(email);
        if (user != null && user.getId() != null) {
            log.debug("Resolved user ID {} for email {}", user.getId(), email);
            return user.getId();
        }

        // Fallback: use email as identifier (for backward compatibility)
        log.warn(
                "Using email as user ID (fallback). This may cause enrollment lookup issues if enrollments use user ID instead of email.");
        return email;
    }

    /**
     * Get the current user email from identity service.
     */
    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null ||
                "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        String email = authentication.getName();
        UserResponse user = getCachedUserResponse(email);
        if (user != null && user.getEmail() != null) {
            return user.getEmail();
        }

        return email;
    }

    /**
     * Get the current user's role from identity service.
     * Returns null if unable to determine role.
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null ||
                "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        String email = authentication.getName();
        UserResponse user = getCachedUserResponse(email);
        if (user != null) {
            return user.getRole();
        }
        return null;
    }

    // Cached user entry with TTL
    private static class CachedUser {
        final UserResponse user;
        final long timestamp;

        CachedUser(UserResponse user) {
            this.user = user;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    // Helper class for user response
    private static class UserResponse {
        private String id;
        private String email;
        private String role;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
