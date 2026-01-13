package com.datagami.edudron.gateway;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Global filter to generate and forward request IDs for tracing.
 * Uses both MDC and Reactor Context for proper propagation in reactive streams.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {
    
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String EXCHANGE_TRACE_ID_KEY = "traceId";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String headerTraceId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        
        final String traceId;
        if (headerTraceId == null || headerTraceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = headerTraceId;
        }
        
        // Store in exchange attributes for access in error handlers
        exchange.getAttributes().put(EXCHANGE_TRACE_ID_KEY, traceId);
        
        // Store in MDC for logging (works in same thread)
        MDC.put(TRACE_ID_KEY, traceId);
        
        // Add trace ID to response headers
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, traceId)
                .build();
        
        // Use Reactor Context to propagate traceId across reactive boundaries
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
                .contextWrite(Context.of(TRACE_ID_KEY, traceId))
                .doOnEach(signal -> {
                    // Update MDC on each signal to ensure it's available for logging
                    if (signal.hasValue() || signal.hasError()) {
                        MDC.put(TRACE_ID_KEY, traceId);
                    }
                })
                .doFinally(signalType -> {
                    MDC.remove(TRACE_ID_KEY);
                });
    }
    
    @Override
    public int getOrder() {
        // Run first in the filter chain
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

