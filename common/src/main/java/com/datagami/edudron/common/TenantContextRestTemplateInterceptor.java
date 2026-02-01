package com.datagami.edudron.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;

/**
 * RestTemplate interceptor that automatically propagates TenantContext (X-Client-Id header)
 * to downstream service calls and ensures JSON content negotiation.
 * 
 * This ensures that when a service makes internal calls to other services:
 * 1. The tenant context is automatically propagated without manual header management
 * 2. The Accept header is set to application/json to prevent XML responses
 */
public class TenantContextRestTemplateInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(TenantContextRestTemplateInterceptor.class);
    
    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        
        // Set Accept header to JSON if not already set (prevents XML responses)
        if (!request.getHeaders().containsKey(HttpHeaders.ACCEPT)) {
            request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
            log.debug("Set Accept: application/json for request to {}", request.getURI());
        }
        
        // Propagate tenant context
        String clientId = TenantContext.getClientId();
        if (clientId != null && !clientId.isBlank()) {
            // Only add header if it's not already present (allows manual override if needed)
            if (!request.getHeaders().containsKey(CLIENT_ID_HEADER)) {
                request.getHeaders().add(CLIENT_ID_HEADER, clientId);
                log.debug("Propagated tenant context (X-Client-Id: {}) to {}", clientId, request.getURI());
            } else {
                log.debug("X-Client-Id header already present in request to {}, skipping propagation", request.getURI());
            }
        } else {
            log.debug("No tenant context available for request to {}", request.getURI());
        }
        
        return execution.execute(request, body);
    }
}


