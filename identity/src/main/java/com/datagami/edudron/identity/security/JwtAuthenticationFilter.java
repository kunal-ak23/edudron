package com.datagami.edudron.identity.security;

import com.datagami.edudron.common.TenantContext;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip JWT processing for auth endpoints
        if (request.getRequestURI().startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");
        final String tenantHeader = request.getHeader("X-Client-Id");

        String username = null;
        String jwt = null;
        boolean tokenExpired = false;
        boolean tokenInvalid = false;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (ExpiredJwtException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("JWT token expired for request to " + request.getRequestURI() + 
                        ": expired at " + e.getClaims().getExpiration());
                }
                tokenExpired = true;
            } catch (JwtException | IllegalArgumentException e) {
                logger.warn("JWT token validation failed for request to " + request.getRequestURI() + 
                    ": " + e.getMessage());
                tokenInvalid = true;
            } catch (Exception e) {
                logger.warn("JWT token validation failed for request to " + request.getRequestURI() + 
                    ": " + e.getMessage());
                tokenInvalid = true;
            }
        }

        // If token is expired or invalid and we have a JWT, return 401
        if ((tokenExpired || tokenInvalid) && jwt != null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            String errorMessage = tokenExpired ? "Token expired" : "Invalid token";
            response.getWriter().write("{\"error\":\"" + errorMessage + "\",\"code\":\"UNAUTHORIZED\"}");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(jwt)) {
                // Set tenant context
                if (tenantHeader != null) {
                    TenantContext.setClientId(tenantHeader);
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username, null, new ArrayList<>());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                // Token is invalid (not expired, but validation failed)
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\":\"Invalid token\",\"code\":\"UNAUTHORIZED\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}


