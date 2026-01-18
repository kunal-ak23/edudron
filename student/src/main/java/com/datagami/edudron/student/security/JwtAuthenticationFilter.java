package com.datagami.edudron.student.security;

import com.datagami.edudron.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String requestId = request.getHeader("X-Request-Id");
        boolean isInstitutesEndpoint = requestUri != null && requestUri.contains("/api/institutes");
        
        final String authHeader = request.getHeader("Authorization");
        final String clientIdHeader = request.getHeader("X-Client-Id");
        
        // Enhanced logging for /api/institutes endpoint
        if (isInstitutesEndpoint) {
            logger.info("Processing /api/institutes request: method={}, uri={}, hasAuthorization={}, hasX-Client-Id={}, X-Request-Id={}", 
                    method, requestUri, authHeader != null, clientIdHeader != null, requestId);
        }
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (isInstitutesEndpoint) {
                logger.warn("No valid Authorization header for /api/institutes: method={}, uri={}, authHeaderPresent={}", 
                        method, requestUri, authHeader != null);
            }
            chain.doFilter(request, response);
            return;
        }
        
        try {
            final String token = authHeader.substring(7);
            
            // Log token info for /api/institutes
            if (isInstitutesEndpoint) {
                logger.info("JWT token received for /api/institutes: method={}, tokenLength={}, tokenPrefix={}, X-Request-Id={}", 
                        method, token.length(), 
                        token.length() > 30 ? token.substring(0, 30) + "..." : token, 
                        requestId);
            }
            
            if (jwtUtil.validateToken(token)) {
                String tenantId = jwtUtil.extractTenantId(token);
                String role = jwtUtil.extractRole(token);
                String username = jwtUtil.extractUsername(token);
                
                if (isInstitutesEndpoint) {
                    logger.info("JWT validated for /api/institutes: username={}, role={}, tenantId={}, X-Request-Id={}", 
                            username, role, tenantId, requestId);
                } else {
                }
                
                // Set tenant context from token or header
                // For SYSTEM_ADMIN users, token may have "PENDING_TENANT_SELECTION" or "SYSTEM"
                // In that case, use the X-Client-Id header if provided
                boolean isPlaceholderTenant = tenantId != null && 
                    ("PENDING_TENANT_SELECTION".equals(tenantId) || "SYSTEM".equals(tenantId));
                
                if (tenantId != null && !isPlaceholderTenant) {
                    // Use tenant from token if it's a valid tenant ID
                    TenantContext.setClientId(tenantId);
                    if (isInstitutesEndpoint) {
                        logger.info("Tenant context set from token for /api/institutes: tenantId={}, X-Request-Id={}", 
                                tenantId, requestId);
                    }
                } else {
                    // Fallback to header if token doesn't have tenant or has placeholder
                    String tenantHeader = request.getHeader("X-Client-Id");
                    if (tenantHeader != null && !tenantHeader.isBlank()) {
                        TenantContext.setClientId(tenantHeader);
                        if (isInstitutesEndpoint) {
                            logger.info("Tenant context set from X-Client-Id header for /api/institutes: tenantId={}, X-Request-Id={}", 
                                    tenantHeader, requestId);
                        } else {
                        }
                    } else if (isPlaceholderTenant) {
                        // Log warning if we have placeholder but no header
                        if (isInstitutesEndpoint) {
                            logger.warn("JWT token has placeholder tenant ({}) but no X-Client-Id header for /api/institutes: method={}, X-Request-Id={}", 
                                    tenantId, method, requestId);
                        } else {
                            logger.warn("JWT token has placeholder tenant (" + tenantId + ") but no X-Client-Id header provided");
                        }
                    }
                }
                
                // Set security context - always set if username exists, even if auth already exists
                if (username != null) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        role != null ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)) : Collections.emptyList()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    if (isInstitutesEndpoint) {
                        logger.info("Authentication set for /api/institutes: username={}, role={}, X-Request-Id={}", 
                                username, role, requestId);
                    } else {
                    }
                } else {
                    if (isInstitutesEndpoint) {
                        logger.warn("JWT token validated but username is null for /api/institutes: X-Request-Id={}", requestId);
                    } else {
                        logger.warn("JWT token validated but username is null");
                    }
                }
            } else {
                if (isInstitutesEndpoint) {
                    logger.error("JWT token validation failed for /api/institutes: method={}, uri={}, tokenLength={}, tokenPrefix={}, X-Request-Id={}", 
                            method, requestUri, token.length(), 
                            token.length() > 30 ? token.substring(0, 30) + "..." : token, 
                            requestId);
                } else {
                    logger.warn("JWT token validation failed for request: " + requestUri);
                }
            }
        } catch (Exception e) {
            if (isInstitutesEndpoint) {
                logger.error("Exception during JWT authentication for /api/institutes: method={}, uri={}, error={}, X-Request-Id={}", 
                        method, requestUri, e.getMessage(), requestId, e);
            } else {
                logger.error("Cannot set user authentication: " + e.getMessage(), e);
            }
        } finally {
            chain.doFilter(request, response);
            // Clear tenant context after request
            TenantContext.clear();
        }
    }
}

