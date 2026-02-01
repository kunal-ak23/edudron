package com.datagami.edudron.content.security;

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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String tenantHeader = request.getHeader("X-Client-Id");
        
        // Always set tenant context from X-Client-Id header if provided
        // This ensures consistency between frontend requests and service-to-service calls
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            TenantContext.setClientId(tenantHeader);
        }
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No auth header - proceed with tenant from X-Client-Id if set
            try {
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }
        
        try {
            final String token = authHeader.substring(7);
            
            if (jwtUtil.validateToken(token)) {
                String tenantId = jwtUtil.extractTenantId(token);
                String role = jwtUtil.extractRole(token);
                String username = jwtUtil.extractUsername(token);
                
                // If X-Client-Id header was not provided, fall back to JWT tenant
                if (tenantHeader == null || tenantHeader.isBlank()) {
                    boolean isPlaceholderTenant = tenantId != null && 
                        ("PENDING_TENANT_SELECTION".equals(tenantId) || "SYSTEM".equals(tenantId));
                    
                    if (tenantId != null && !isPlaceholderTenant) {
                        TenantContext.setClientId(tenantId);
                    } else if (isPlaceholderTenant) {
                        log.debug("JWT token has placeholder tenant ({}) but no X-Client-Id header provided", tenantId);
                    }
                }
                
                // Set security context
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        role != null ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)) : Collections.emptyList()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } else {
                log.warn("JWT validation failed");
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e);
        } finally {
            chain.doFilter(request, response);
            // Clear tenant context after request
            TenantContext.clear();
        }
    }
}

