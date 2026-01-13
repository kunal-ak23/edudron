package com.datagami.edudron.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter to extract and forward JWT tokens.
 * Currently passes through tokens without validation (validation happens in downstream services).
 * Can be enhanced later to validate tokens in the gateway.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        
        // Try to get traceId from multiple sources
        String traceId = (String) exchange.getAttributes().get("traceId");
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId == null) {
            traceId = request.getHeaders().getFirst("X-Request-Id");
        }
        
        String clientId = MDC.get("clientId");
        
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            
            // Log token presence and basic info (without exposing full token)
            log.info("JWT token found: method={}, path={}, tokenLength={}, tokenPrefix={}, traceId={}, clientId={}", 
                    method, path, token.length(), 
                    token.length() > 20 ? token.substring(0, 20) + "..." : token,
                    traceId, clientId);
            
            // Try to extract basic info from token (without validation)
            try {
                String[] tokenParts = token.split("\\.");
                if (tokenParts.length == 3) {
                    log.debug("JWT token structure valid: has 3 parts (header.payload.signature), path={}, traceId={}", 
                            path, traceId);
                } else {
                    log.warn("JWT token structure invalid: expected 3 parts, found {}, path={}, traceId={}", 
                            tokenParts.length, path, traceId);
                }
            } catch (Exception e) {
                log.warn("Failed to parse JWT token structure: path={}, error={}, traceId={}", 
                        path, e.getMessage(), traceId);
            }
            
            // Log all authorization-related headers
            log.debug("Authorization headers - Authorization present: true, path={}, traceId={}", path, traceId);
        } else {
            if (authHeader != null) {
                log.warn("Authorization header present but invalid format (expected 'Bearer <token>'): path={}, headerPrefix={}, traceId={}, clientId={}", 
                        path, authHeader.length() > 20 ? authHeader.substring(0, 20) + "..." : authHeader, traceId, clientId);
            } else {
                log.info("No JWT token found in request: method={}, path={}, traceId={}, clientId={}", 
                        method, path, traceId, clientId);
            }
        }

        // Forward request and log response status
        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    ServerHttpResponse response = exchange.getResponse();
                    if (response.getStatusCode() != null) {
                        int statusCode = response.getStatusCode().value();
                        if (statusCode == 401 || statusCode == 403) {
                            log.warn("Authentication/Authorization failure: method={}, path={}, status={}, traceId={}, clientId={}, hasAuthHeader={}", 
                                    method, path, statusCode, traceId, clientId, authHeader != null);
                        } else {
                            log.debug("Request forwarded successfully: method={}, path={}, status={}, traceId={}", 
                                    method, path, statusCode, traceId);
                        }
                    }
                })
                .doOnError(throwable -> {
                    log.error("Error forwarding request: method={}, path={}, error={}, traceId={}, clientId={}, hasAuthHeader={}", 
                            method, path, throwable.getMessage(), traceId, clientId, authHeader != null, throwable);
                });
    }

    @Override
    public int getOrder() {
        // Run early in the filter chain, but after request ID filter
        return -100;
    }
}


