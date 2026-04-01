package com.datagami.edudron.student.client;

import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client for fetching assessment and course data from Content service.
 * Used by ResultsExportService to build Excel exports with assessment metadata.
 */
@Component
public class ContentAssessmentClient {

    private static final Logger logger = LoggerFactory.getLogger(ContentAssessmentClient.class);

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
     * Fetch all assessments (exams) for a given course from Content service.
     * Parses the Spring Data Page response to extract the content array.
     *
     * @param courseId course ID
     * @return list of assessment JsonNodes, each with id, title, assessmentType, maxScore, etc.
     */
    public List<JsonNode> getAssessmentsForCourse(String courseId) {
        String url = gatewayUrl + "/api/exams?courseId=" + courseId + "&size=200";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                // Handle Spring Data Page response format
                JsonNode content = body.has("content") ? body.get("content") : body;
                if (content.isArray()) {
                    List<JsonNode> assessments = new ArrayList<>();
                    for (JsonNode node : content) {
                        assessments.add(node);
                    }
                    logger.debug("Fetched {} assessments for course {}", assessments.size(), courseId);
                    return assessments;
                }
                return Collections.emptyList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Failed to fetch assessments for course {} from Content: {}", courseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch course details from Content service.
     *
     * @param courseId course ID
     * @return course as JsonNode (with title, etc.), or null if not found
     */
    public JsonNode getCourse(String courseId) {
        String url = gatewayUrl + "/content/courses/" + courseId;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

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
            logger.warn("Failed to fetch course {} from Content: {}", courseId, e.getMessage());
            return null;
        }
    }
}
