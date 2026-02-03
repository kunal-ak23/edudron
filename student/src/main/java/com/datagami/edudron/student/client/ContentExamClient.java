package com.datagami.edudron.student.client;

import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for fetching exam details from Content service. Results are cached by (clientId, examId)
 * to reduce load when many students load the same exam.
 */
@Component
public class ContentExamClient {

    private static final Logger logger = LoggerFactory.getLogger(ContentExamClient.class);

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate newTemplate = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new TenantContextRestTemplateInterceptor());
                    interceptors.add((request, body, execution) -> {
                        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest currentRequest = attributes.getRequest();
                            String authHeader = currentRequest.getHeader("Authorization");
                            if (authHeader != null && !authHeader.isBlank()) {
                                if (!request.getHeaders().containsKey("Authorization")) {
                                    request.getHeaders().add("Authorization", authHeader);
                                }
                            }
                        }
                        return execution.execute(request, body);
                    });
                    newTemplate.setInterceptors(interceptors);
                    restTemplate = newTemplate;
                }
            }
        }
        return restTemplate;
    }

    /**
     * Fetch exam by ID from Content service. Result is cached per (clientId, examId) with 5 min TTL.
     *
     * @param examId exam ID
     * @return exam as JsonNode, or null if not found or error
     */
    @Cacheable(value = "examFromContent", key = "T(com.datagami.edudron.common.TenantContext).getClientId() + '::' + #examId", unless = "#result == null")
    public JsonNode getExam(String examId) {
        String url = gatewayUrl + "/api/exams/" + examId;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(org.springframework.http.MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to fetch exam {} from Content: {}", examId, e.getMessage());
            return null;
        }
    }
}
