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

import java.time.Instant;

/**
 * Global filter to log incoming requests and responses.
 * Logs method, path, response status, and timing information.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String START_TIME = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String traceId = MDC.get("traceId");

        long startTime = Instant.now().toEpochMilli();
        exchange.getAttributes().put(START_TIME, startTime);

        log.info("Incoming request: method={}, path={}, traceId={}", method, path, traceId);

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    ServerHttpResponse response = exchange.getResponse();
                    Long start = exchange.getAttribute(START_TIME);
                    long duration = start != null ? Instant.now().toEpochMilli() - start : 0;
                    
                    log.info("Request completed: method={}, path={}, status={}, duration={}ms, traceId={}",
                            method, path, response.getStatusCode(), duration, traceId);
                })
                .doOnError(throwable -> {
                    Long start = exchange.getAttribute(START_TIME);
                    long duration = start != null ? Instant.now().toEpochMilli() - start : 0;
                    
                    log.error("Request failed: method={}, path={}, error={}, duration={}ms, traceId={}",
                            method, path, throwable.getMessage(), duration, traceId, throwable);
                });
    }

    @Override
    public int getOrder() {
        // Run after other filters to log final state
        return Ordered.LOWEST_PRECEDENCE;
    }
}

