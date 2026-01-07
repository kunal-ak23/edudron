package com.datagami.edudron.student.security;

import com.datagami.edudron.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            final String token = authHeader.substring(7);
            
            if (jwtUtil.validateToken(token)) {
                String tenantId = jwtUtil.extractTenantId(token);
                String role = jwtUtil.extractRole(token);
                String username = jwtUtil.extractUsername(token);
                
                logger.debug("JWT validated - username: " + username + ", role: " + role + ", tenantId: " + tenantId);
                
                // Set tenant context from token or header
                // For SYSTEM_ADMIN users, token may have "PENDING_TENANT_SELECTION" or "SYSTEM"
                // In that case, use the X-Client-Id header if provided
                boolean isPlaceholderTenant = tenantId != null && 
                    ("PENDING_TENANT_SELECTION".equals(tenantId) || "SYSTEM".equals(tenantId));
                
                if (tenantId != null && !isPlaceholderTenant) {
                    // Use tenant from token if it's a valid tenant ID
                    TenantContext.setClientId(tenantId);
                } else {
                    // Fallback to header if token doesn't have tenant or has placeholder
                    String tenantHeader = request.getHeader("X-Client-Id");
                    if (tenantHeader != null && !tenantHeader.isBlank()) {
                        TenantContext.setClientId(tenantHeader);
                        logger.debug("Using tenant from X-Client-Id header: " + tenantHeader);
                    } else if (isPlaceholderTenant) {
                        // Log warning if we have placeholder but no header
                        logger.warn("JWT token has placeholder tenant (" + tenantId + ") but no X-Client-Id header provided");
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
                    logger.debug("Authentication set for user: " + username);
                } else {
                    logger.warn("JWT token validated but username is null");
                }
            } else {
                logger.warn("JWT token validation failed for request: " + request.getRequestURI());
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: " + e.getMessage(), e);
        } finally {
            chain.doFilter(request, response);
            // Clear tenant context after request
            TenantContext.clear();
        }
    }
}

