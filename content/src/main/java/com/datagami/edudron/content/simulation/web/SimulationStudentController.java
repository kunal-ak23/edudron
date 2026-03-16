package com.datagami.edudron.content.simulation.web;

import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.content.simulation.dto.DecisionInputDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDTO;
import com.datagami.edudron.content.simulation.dto.SimulationPlayDTO;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// TODO: Add backend feature flag check (SIMULATION feature type) once inter-service feature
//  flag validation is available. Currently feature checks are frontend-only, consistent with
//  PsychTestController which also lacks a backend feature flag check.
@RestController
@RequestMapping("/content/api/simulations")
@Tag(name = "Simulations (Student)", description = "Student endpoints for playing simulations")
public class SimulationStudentController {

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

    @GetMapping("/available")
    @Operation(summary = "Get available simulations",
               description = "List published simulations available to the current student")
    public ResponseEntity<List<SimulationDTO>> available() {
        String studentId = getCurrentUserId();
        return ResponseEntity.ok(simulationService.getAvailableSimulations(studentId));
    }

    @PostMapping("/{id}/play")
    @Operation(summary = "Start playing a simulation",
               description = "Create a new play session for a simulation")
    public ResponseEntity<SimulationPlayDTO> startPlay(@PathVariable String id) {
        String studentId = getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulationService.startPlay(id, studentId));
    }

    // TODO: v2 play flow endpoints (getCurrentState, submitDecision, getDebrief) will be
    // implemented in a subsequent task to match the year-based career tenure model.

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
