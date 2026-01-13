package com.datagami.edudron.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for Spring Cloud Gateway.
 * Handles gateway errors (503, 504) and formats error responses consistently.
 */
@Component
@Order(-2) // Higher priority than default error handler
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        // Try to get traceId from multiple sources (exchange attributes, MDC, Reactor Context)
        String traceId = (String) exchange.getAttributes().get("traceId");
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId == null) {
            // Try to get from request header as fallback
            traceId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        }
        
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "Internal Server Error";

        if (ex instanceof ResponseStatusException) {
            ResponseStatusException responseStatusException = (ResponseStatusException) ex;
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            errorMessage = responseStatusException.getReason() != null 
                    ? responseStatusException.getReason() 
                    : status.getReasonPhrase();
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            errorMessage = "Gateway Timeout - Service did not respond in time";
        } else if (ex instanceof org.springframework.web.server.ServerWebInputException) {
            status = HttpStatus.BAD_REQUEST;
            errorMessage = "Bad Request - Invalid input";
        } else {
            // Check for connection errors
            String exceptionMessage = ex.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("Connection refused") || 
                    exceptionMessage.contains("Connection timed out")) {
                    status = HttpStatus.BAD_GATEWAY;
                    errorMessage = "Bad Gateway - Unable to connect to downstream service";
                } else if (exceptionMessage.contains("timeout")) {
                    status = HttpStatus.GATEWAY_TIMEOUT;
                    errorMessage = "Gateway Timeout - Request timed out";
                }
            }
        }

        log.error("Gateway error: status={}, message={}, path={}, traceId={}, error={}, exceptionType={}",
                status, errorMessage, exchange.getRequest().getURI().getPath(), traceId, ex.getMessage(), ex.getClass().getName(), ex);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", errorMessage);
        errorResponse.put("path", exchange.getRequest().getURI().getPath());
        if (traceId != null) {
            errorResponse.put("traceId", traceId);
        }

        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response", e);
            String fallbackJson = String.format(
                    "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                    Instant.now().toString(), status.value(), status.getReasonPhrase(), errorMessage);
            DataBuffer buffer = response.bufferFactory().wrap(fallbackJson.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
    }
}

