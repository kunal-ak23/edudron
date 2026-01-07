package com.datagami.edudron.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("JWT token found in request to {}", request.getURI().getPath());
            // Token is already in the header, just pass it through
            // Future enhancement: validate token here before forwarding
        } else {
            log.debug("No JWT token found in request to {}", request.getURI().getPath());
        }

        // Forward request with all headers intact
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run early in the filter chain, but after request ID filter
        return -100;
    }
}


