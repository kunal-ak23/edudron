package com.datagami.edudron.content.simulation.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.datagami.edudron.content.service.AIJobQueueService;
import com.datagami.edudron.content.service.AIJobWorker;
import com.datagami.edudron.content.service.LectureService;
import com.datagami.edudron.content.simulation.domain.Simulation;
import com.datagami.edudron.content.simulation.dto.GenerateSimulationRequest;
import com.datagami.edudron.content.simulation.dto.SimulationDTO;
import com.datagami.edudron.content.simulation.dto.SimulationExportDTO;
import com.datagami.edudron.content.simulation.dto.SimulationSuggestionRequest;
import com.datagami.edudron.content.simulation.dto.SimulationSuggestionResponse;
import com.datagami.edudron.content.simulation.repo.SimulationRepository;
import com.datagami.edudron.content.simulation.service.SimulationGenerationService;
import com.datagami.edudron.content.simulation.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/content/api/simulations")
@Tag(name = "Simulations (Admin)", description = "Admin endpoints for simulation management")
public class SimulationAdminController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationAdminController.class);

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private SimulationGenerationService simulationGenerationService;

    @Autowired
    private AIJobQueueService aiJobQueueService;

    @Autowired
    private AIJobWorker aiJobWorker;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private LectureService lectureService;

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();

    /**
     * Check that current user is SYSTEM_ADMIN or TENANT_ADMIN.
     * Uses the same pattern as CourseController and ImageGenerationController.
     */
    private void requireAdmin() {
        String userRole = lectureService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("Only SYSTEM_ADMIN and TENANT_ADMIN can manage simulations");
        }
    }

    /**
     * Get a RestTemplate configured with tenant context and auth header forwarding.
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
            logger.debug("Checking SIMULATION feature flag at {} for tenant {}", url, tenantId);

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

            logger.info("SIMULATION feature check: status={}, enabled={}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(response.getBody())) {
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to check SIMULATION feature flag for tenant {}: {}", tenantId, e.getMessage(), e);
            return; // Allow access if inter-service call fails — requireAdmin() already verified the user
        }
        throw new IllegalStateException("Simulation feature is not enabled for this tenant");
    }

    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception e) {
            logger.debug("Could not determine user ID: {}", e.getMessage());
        }
        return null;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate simulation",
               description = "Submit AI simulation generation job. Only SYSTEM_ADMIN and TENANT_ADMIN.")
    public ResponseEntity<?> generateSimulation(@Valid @RequestBody GenerateSimulationRequest request) {
        requireAdmin();
        requireSimulationEnabled(TenantContext.getClientId());

        UUID clientId = UUID.fromString(TenantContext.getClientId());

        // Create simulation entity in GENERATING status
        Simulation sim = new Simulation();
        sim.setClientId(clientId);
        sim.setTitle(request.getConcept() + " Simulation");
        sim.setConcept(request.getConcept());
        sim.setSubject(request.getSubject());
        sim.setAudience(request.getAudience());
        sim.setDescription(request.getDescription());
        sim.setCourseId(request.getCourseId());
        sim.setLectureId(request.getLectureId());
        sim.setTargetYears(request.getTargetYears() != null ? request.getTargetYears() : 5);
        sim.setDecisionsPerYear(request.getDecisionsPerYear() != null ? request.getDecisionsPerYear() : 6);
        sim.setStatus(Simulation.SimulationStatus.GENERATING);
        sim.setCreatedBy(getCurrentUserId());
        simulationRepository.save(sim);

        // Submit async job
        Map<String, String> jobRequest = new HashMap<>();
        jobRequest.put("simulationId", sim.getId());
        jobRequest.put("concept", request.getConcept());
        jobRequest.put("subject", request.getSubject());
        jobRequest.put("audience", request.getAudience());
        if (request.getDescription() != null) {
            jobRequest.put("description", request.getDescription());
        }
        if (request.getTargetYears() != null) {
            jobRequest.put("targetYears", String.valueOf(request.getTargetYears()));
        }
        if (request.getDecisionsPerYear() != null) {
            jobRequest.put("decisionsPerYear", String.valueOf(request.getDecisionsPerYear()));
        }

        AIGenerationJobDTO job = aiJobQueueService.submitSimulationGenerationJob(jobRequest, aiJobWorker);
        logger.info("Simulation generation job {} submitted for simulation {}", job.getJobId(), sim.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.getJobId(),
                "simulationId", sim.getId()
        ));
    }

    @PostMapping("/suggest-from-course")
    @Operation(summary = "Smart suggest simulation parameters from course content")
    public ResponseEntity<SimulationSuggestionResponse> suggestFromCourse(
            @Valid @RequestBody SimulationSuggestionRequest request) {
        requireAdmin();
        SimulationSuggestionResponse response = simulationService.suggestFromCourse(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/generate/jobs/{jobId}")
    @Operation(summary = "Get simulation generation job status")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        requireAdmin();
        AIGenerationJobDTO job = aiJobQueueService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    @GetMapping
    @Operation(summary = "List simulations", description = "Get paginated list of simulations with optional status filter")
    public ResponseEntity<Page<SimulationDTO>> list(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.listSimulations(pageable, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get simulation", description = "Get simulation details by ID")
    public ResponseEntity<SimulationDTO> get(@PathVariable String id) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.getSimulation(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update simulation", description = "Update simulation metadata")
    public ResponseEntity<SimulationDTO> update(@PathVariable String id, @RequestBody SimulationDTO updates) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.updateSimulation(id, updates));
    }

    @PutMapping({"/{id}/data", "/{id}/simulation-data"})
    @Operation(summary = "Update simulation data", description = "Update the simulation year-based structure data")
    public ResponseEntity<SimulationDTO> updateSimulationData(@PathVariable String id,
                                                     @RequestBody Map<String, Object> simulationData) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.updateSimulationData(id, simulationData));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish simulation", description = "Publish a simulation (must be in REVIEW status)")
    public ResponseEntity<SimulationDTO> publish(@PathVariable String id) {
        requireAdmin();
        requireSimulationEnabled(TenantContext.getClientId());
        return ResponseEntity.ok(simulationService.publish(id));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive simulation", description = "Archive a simulation")
    public ResponseEntity<SimulationDTO> archive(@PathVariable String id) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.archive(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete simulation", description = "Permanently delete a simulation and all its play data")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        requireAdmin();
        simulationService.deleteSimulation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/move-to-draft")
    @Operation(summary = "Move to draft", description = "Move simulation back to review/draft status for editing")
    public ResponseEntity<SimulationDTO> moveToDraft(@PathVariable String id) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.moveToDraft(id));
    }

    @PostMapping("/{id}/move-to-published")
    @Operation(summary = "Move to published", description = "Re-publish a simulation (from any non-generating status)")
    public ResponseEntity<SimulationDTO> moveToPublished(@PathVariable String id) {
        requireAdmin();
        requireSimulationEnabled(TenantContext.getClientId());
        return ResponseEntity.ok(simulationService.moveToPublished(id));
    }

    @PostMapping("/{id}/regenerate-mentor-guidance")
    @Operation(summary = "Regenerate mentor guidance", description = "Re-run mentor enrichment (Phase 5.5) on an existing simulation without changing decisions")
    public ResponseEntity<Map<String, String>> regenerateMentorGuidance(@PathVariable String id) {
        requireAdmin();
        simulationGenerationService.regenerateMentorGuidance(id);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Mentor guidance regenerated"));
    }

    @PostMapping("/{id}/export")
    @Operation(summary = "Export simulation", description = "Export simulation as portable JSON")
    public ResponseEntity<SimulationExportDTO> exportSimulation(@PathVariable String id) {
        requireAdmin();
        return ResponseEntity.ok(simulationService.exportSimulation(id));
    }

    @PostMapping("/import")
    @Operation(summary = "Import simulation", description = "Import a simulation from exported JSON")
    public ResponseEntity<SimulationDTO> importSimulation(@RequestBody SimulationExportDTO data) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulationService.importSimulation(data, getCurrentUserId()));
    }
}
