package com.datagami.edudron.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter to ensure custom headers (especially X-Sync-Timestamp) are forwarded
 * to downstream services. Spring Cloud Gateway should forward X-* headers by default,
 * but this filter explicitly ensures they are preserved.
 */
@Component
public class HeaderForwardingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Spring Cloud Gateway forwards all headers by default, including X-* headers.
        // This filter ensures headers are explicitly preserved.
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        
        // Explicitly preserve X-Sync-Timestamp if present (for cart sync operations)
        String syncTimestamp = request.getHeaders().getFirst("X-Sync-Timestamp");
        if (syncTimestamp != null) {
            requestBuilder.header("X-Sync-Timestamp", syncTimestamp);
        }
        
        // Build the modified request (preserves all original headers)
        ServerHttpRequest modifiedRequest = requestBuilder.build();
        
        // Continue with the filter chain
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run after TenantContextFilter and JwtAuthenticationFilter
        // but before the actual routing
        return -98;
    }
}

