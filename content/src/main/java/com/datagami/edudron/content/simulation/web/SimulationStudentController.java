package com.datagami.edudron.content.simulation.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.content.simulation.dto.DecisionInputDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDTO;
import com.datagami.edudron.content.simulation.dto.SimulationPlayDTO;
import com.datagami.edudron.content.simulation.dto.SimulationStateDTO;
import com.datagami.edudron.content.simulation.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/content/api/simulations")
@Tag(name = "Simulations (Student)", description = "Student endpoints for playing simulations")
public class SimulationStudentController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationStudentController.class);

    @Autowired
    private SimulationService simulationService;

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();

    /**
     * Get a RestTemplate configured with tenant context and auth header forwarding.
     * Same pattern as PsychTestController.
     */
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate template = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new TenantContextRestTemplateInterceptor());
                    interceptors.add((request, body, execution) -> {
                        ServletRequestAttributes attributes =
                                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
                    template.setInterceptors(interceptors);
                    restTemplate = template;
                }
            }
        }
        return restTemplate;
    }

    /**
     * Resolve the current user's ULID via the identity service (/idp/users/me).
     * Same pattern as PsychTestController.requireUserId().
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Unauthorized");
        }

        try {
            String meUrl = gatewayUrl + "/idp/users/me";
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                    meUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object id = response.getBody().get("id");
                if (id != null && !id.toString().isBlank()) {
                    return id.toString();
                }
            }
        } catch (Exception ignored) {
            // Fall through to unauthorized
        }

        throw new IllegalStateException("Unauthorized");
    }

    /**
     * Resolve the current student's section ID via the enrollment service.
     * Returns null if the student has no section assignment.
     */
    private String getStudentSectionId(String studentId) {
        try {
            String url = gatewayUrl + "/api/students/" + studentId + "/class-section";
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object sectionId = response.getBody().get("sectionId");
                if (sectionId != null && !sectionId.toString().isBlank()) {
                    return sectionId.toString();
                }
            }
        } catch (Exception ignored) {
            // Student may not have an enrollment yet
        }
        return null;
    }

    /**
     * Check that the SIMULATION feature flag is enabled for the current tenant.
     * Calls the identity service via gateway to verify.
     */
    private void requireSimulationEnabled(String tenantId) {
        try {
            if (tenantId == null || "SYSTEM".equals(tenantId) || "PENDING_TENANT_SELECTION".equals(tenantId)) {
                logger.warn("No valid tenant ID for feature check, allowing access");
                return;
            }

            String url = gatewayUrl + "/api/tenant/features/SIMULATION/enabled";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Client-Id", tenantId);
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader("Authorization");
                if (authHeader != null) {
                    headers.set("Authorization", authHeader);
                }
            }

            ResponseEntity<Boolean> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Boolean.class
            );

            if (response.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(response.getBody())) {
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to check SIMULATION feature flag: {}. Allowing access as fallback.", e.getMessage());
            return;
        }
        throw new IllegalStateException("Simulation feature is not enabled for this tenant");
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "Get simulation details",
               description = "Get simulation metadata (concept, title, subject) for the student view")
    public ResponseEntity<SimulationDTO> getSimulationDetails(@PathVariable String id) {
        requireSimulationEnabled(TenantContext.getClientId());
        return ResponseEntity.ok(simulationService.getSimulationForStudent(id));
    }

    @GetMapping("/available")
    @Operation(summary = "Get available simulations",
               description = "List published simulations available to the current student")
    public ResponseEntity<List<SimulationDTO>> available() {
        requireSimulationEnabled(TenantContext.getClientId());
        String studentId = getCurrentUserId();
        String sectionId = getStudentSectionId(studentId);
        return ResponseEntity.ok(simulationService.getAvailableSimulations(studentId, sectionId));
    }

    @PostMapping("/{id}/play")
    @Operation(summary = "Start playing a simulation",
               description = "Create a new play session for a simulation")
    public ResponseEntity<SimulationPlayDTO> startPlay(@PathVariable String id) {
        requireSimulationEnabled(TenantContext.getClientId());
        String studentId = getCurrentUserId();
        String sectionId = getStudentSectionId(studentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulationService.startPlay(id, studentId, sectionId));
    }

    @GetMapping("/play/{playId}/state")
    @Operation(summary = "Get current play state",
               description = "Get the current state of a play session including phase, decision, or review")
    public ResponseEntity<SimulationStateDTO> getCurrentState(@PathVariable String playId) {
        String studentId = getCurrentUserId();
        return ResponseEntity.ok(simulationService.getCurrentState(playId, studentId));
    }

    @PostMapping("/play/{playId}/decide")
    @Operation(summary = "Submit a decision",
               description = "Submit a decision for the current play session")
    public ResponseEntity<SimulationStateDTO> submitDecision(
            @PathVariable String playId,
            @RequestBody DecisionInputDTO input) {
        String studentId = getCurrentUserId();
        return ResponseEntity.ok(simulationService.submitDecision(playId, studentId, input));
    }

    @PostMapping("/play/{playId}/advance-year")
    @Operation(summary = "Advance to next year",
               description = "Advance to the next year after viewing the year-end review")
    public ResponseEntity<SimulationStateDTO> advanceYear(@PathVariable String playId) {
        String studentId = getCurrentUserId();
        return ResponseEntity.ok(simulationService.advanceYear(playId, studentId));
    }

    @PostMapping("/play/{playId}/abandon")
    @Operation(summary = "Abandon play", description = "Abandon the current play attempt (for restarting)")
    public ResponseEntity<Void> abandonPlay(@PathVariable String playId) {
        String studentId = getCurrentUserId();
        simulationService.abandonPlay(playId, studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get play history for a simulation",
               description = "Get all play attempts for a specific simulation by the current student")
    public ResponseEntity<List<SimulationPlayDTO>> history(@PathVariable String id) {
        String studentId = getCurrentUserId();
        return ResponseEntity.ok(simulationService.getPlayHistory(id, studentId));
    }

    @GetMapping("/my-history")
    @Operation(summary = "Get all play history",
               description = "Get all simulation play history for the current student")
    public ResponseEntity<List<SimulationPlayDTO>> myHistory() {
        String studentId = getCurrentUserId();
        return ResponseEntity.ok(simulationService.getAllPlayHistory(studentId));
    }
}
