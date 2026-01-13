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
        final String path = request.getURI().getPath();
        final String method = request.getMethod().name();
        
        // Try to get traceId from multiple sources
        String traceIdTemp = (String) exchange.getAttributes().get("traceId");
        if (traceIdTemp == null) {
            traceIdTemp = MDC.get("traceId");
        }
        if (traceIdTemp == null) {
            traceIdTemp = request.getHeaders().getFirst("X-Request-Id");
        }
        final String traceId = traceIdTemp;

        long startTime = Instant.now().toEpochMilli();
        exchange.getAttributes().put(START_TIME, startTime);

        log.info("Incoming request: method={}, path={}, traceId={}", method, path, traceId);

        // Log request headers for debugging
        final String authHeader = request.getHeaders().getFirst("Authorization");
        final String clientIdHeader = request.getHeaders().getFirst("X-Client-Id");
        log.debug("Request headers - Authorization: {}, X-Client-Id: {}, path={}, traceId={}", 
                authHeader != null ? "present" : "missing", 
                clientIdHeader != null ? clientIdHeader : "missing", 
                path, traceId);

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    ServerHttpResponse response = exchange.getResponse();
                    Long start = exchange.getAttribute(START_TIME);
                    long duration = start != null ? Instant.now().toEpochMilli() - start : 0;
                    
                    if (response.getStatusCode() != null) {
                        int statusCode = response.getStatusCode().value();
                        
                        // Log authentication/authorization failures with more detail
                        if (statusCode == 401 || statusCode == 403) {
                            log.warn("Authentication/Authorization failure from downstream: method={}, path={}, status={}, duration={}ms, traceId={}, hasAuthHeader={}, hasClientId={}", 
                                    method, path, statusCode, duration, traceId, 
                                    authHeader != null, clientIdHeader != null);
                        } else {
                            log.info("Request completed: method={}, path={}, status={}, duration={}ms, traceId={}",
                                    method, path, statusCode, duration, traceId);
                        }
                    } else {
                        log.info("Request completed: method={}, path={}, status=null, duration={}ms, traceId={}",
                                method, path, duration, traceId);
                    }
                })
                .doOnError(throwable -> {
                    Long start = exchange.getAttribute(START_TIME);
                    long duration = start != null ? Instant.now().toEpochMilli() - start : 0;
                    
                    log.error("Request failed: method={}, path={}, error={}, duration={}ms, traceId={}, hasAuthHeader={}, hasClientId={}",
                            method, path, throwable.getMessage(), duration, traceId, 
                            authHeader != null, clientIdHeader != null, throwable);
                });
    }

    @Override
    public int getOrder() {
        // Run after other filters to log final state
        return Ordered.LOWEST_PRECEDENCE;
    }
}

