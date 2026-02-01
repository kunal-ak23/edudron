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
        final String requestPath = request.getRequestURI();
        
        log.info("=== JwtAuthenticationFilter START === Path: {}", requestPath);
        log.info("X-Client-Id header: {}", tenantHeader);
        log.info("Authorization header present: {}", authHeader != null);
        
        // Always set tenant context from X-Client-Id header if provided
        // This ensures consistency between frontend requests and service-to-service calls
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            TenantContext.setClientId(tenantHeader);
            log.info("Set TenantContext from X-Client-Id header: {}", tenantHeader);
        }
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No auth header - proceed with tenant from X-Client-Id if set
            log.info("No Bearer token, proceeding with TenantContext: {}", TenantContext.getClientId());
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
                
                log.info("JWT valid - tenantId from token: {}, role: {}, username: {}", tenantId, role, username);
                
                // If X-Client-Id header was not provided, fall back to JWT tenant
                if (tenantHeader == null || tenantHeader.isBlank()) {
                    boolean isPlaceholderTenant = tenantId != null && 
                        ("PENDING_TENANT_SELECTION".equals(tenantId) || "SYSTEM".equals(tenantId));
                    
                    if (tenantId != null && !isPlaceholderTenant) {
                        TenantContext.setClientId(tenantId);
                        log.info("Set TenantContext from JWT (no X-Client-Id header): {}", tenantId);
                    } else if (isPlaceholderTenant) {
                        log.warn("JWT token has placeholder tenant ({}) but no X-Client-Id header provided", tenantId);
                    }
                } else {
                    log.info("Using X-Client-Id header over JWT tenant. Header: {}, JWT: {}", tenantHeader, tenantId);
                }
                
                log.info("Final TenantContext: {}", TenantContext.getClientId());
                
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
            log.info("=== JwtAuthenticationFilter END === Path: {}", requestPath);
        }
    }
}

