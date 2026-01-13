package com.datagami.edudron.gateway;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter to generate and forward request IDs for tracing.
 * Converts servlet-based filter to reactive filter for Spring Cloud Gateway.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {
    
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        
        MDC.put("traceId", traceId);
        
        // Add trace ID to response headers
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, traceId)
                .build();
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
                .doFinally(signalType -> {
                    MDC.remove("traceId");
                });
    }
    
    @Override
    public int getOrder() {
        // Run first in the filter chain
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

