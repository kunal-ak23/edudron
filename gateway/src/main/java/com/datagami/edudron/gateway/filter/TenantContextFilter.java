package com.datagami.edudron.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter to extract and forward X-Client-Id header for tenant context.
 * Ensures the header is forwarded to downstream services and added to MDC for logging.
 */
@Component
public class TenantContextFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
    private static final String CLIENT_ID_HEADER = "X-Client-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientId = request.getHeaders().getFirst(CLIENT_ID_HEADER);

        if (clientId != null && !clientId.isBlank()) {
            log.debug("Tenant context found: clientId={} for path={}", clientId, request.getURI().getPath());
            // Add to MDC for logging
            MDC.put("clientId", clientId);
            // Also store in exchange attributes for reactive propagation
            exchange.getAttributes().put("clientId", clientId);
        } else {
            log.debug("No tenant context (X-Client-Id) found in request to {}", request.getURI().getPath());
        }

        // Forward request with all headers intact
        return chain.filter(exchange)
                .doOnEach(signal -> {
                    // Update MDC on each signal
                    if (clientId != null && !clientId.isBlank()) {
                        MDC.put("clientId", clientId);
                    }
                })
                .doFinally(signalType -> {
                    // Clean up MDC
                    MDC.remove("clientId");
                });
    }

    @Override
    public int getOrder() {
        // Run early in the filter chain, after JWT filter
        return -99;
    }
}


