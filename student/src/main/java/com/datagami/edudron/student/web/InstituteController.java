package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateInstituteRequest;
import com.datagami.edudron.student.dto.InstituteDTO;
import com.datagami.edudron.student.service.InstituteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/institutes")
@Tag(name = "Institutes", description = "Institute management endpoints")
public class InstituteController {

    private static final Logger logger = LoggerFactory.getLogger(InstituteController.class);

    @Autowired
    private InstituteService instituteService;

    @PostMapping
    @Operation(summary = "Create institute", description = "Create a new institute")
    public ResponseEntity<InstituteDTO> createInstitute(@Valid @RequestBody CreateInstituteRequest request, HttpServletRequest httpRequest) {
        String requestId = httpRequest.getHeader("X-Request-Id");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        
        logger.info("POST /api/institutes - Creating institute: username={}, requestName={}, X-Request-Id={}", 
                username, request.getName(), requestId);
        
        try {
            InstituteDTO institute = instituteService.createInstitute(request);
            logger.info("POST /api/institutes - Institute created successfully: id={}, name={}, username={}, X-Request-Id={}", 
                    institute.getId(), institute.getName(), username, requestId);
            return ResponseEntity.status(HttpStatus.CREATED).body(institute);
        } catch (Exception e) {
            logger.error("POST /api/institutes - Failed to create institute: username={}, error={}, X-Request-Id={}", 
                    username, e.getMessage(), requestId, e);
            throw e;
        }
    }

    @GetMapping
    @Operation(summary = "List institutes", description = "Get all institutes for the current tenant")
    public ResponseEntity<List<InstituteDTO>> getAllInstitutes(HttpServletRequest httpRequest) {
        String requestId = httpRequest.getHeader("X-Request-Id");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        
        logger.info("GET /api/institutes - Listing all institutes: username={}, X-Request-Id={}", username, requestId);
        
        try {
            List<InstituteDTO> institutes = instituteService.getAllInstitutes();
            logger.info("GET /api/institutes - Successfully retrieved {} institutes: username={}, X-Request-Id={}", 
                    institutes.size(), username, requestId);
            return ResponseEntity.ok(institutes);
        } catch (Exception e) {
            logger.error("GET /api/institutes - Failed to list institutes: username={}, error={}, X-Request-Id={}", 
                    username, e.getMessage(), requestId, e);
            throw e;
        }
    }

    @GetMapping("/active")
    @Operation(summary = "List active institutes", description = "Get all active institutes for the current tenant")
    public ResponseEntity<List<InstituteDTO>> getActiveInstitutes(HttpServletRequest httpRequest) {
        String requestId = httpRequest.getHeader("X-Request-Id");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        
        logger.info("GET /api/institutes/active - Listing active institutes: username={}, X-Request-Id={}", username, requestId);
        
        try {
            List<InstituteDTO> institutes = instituteService.getActiveInstitutes();
            logger.info("GET /api/institutes/active - Successfully retrieved {} active institutes: username={}, X-Request-Id={}", 
                    institutes.size(), username, requestId);
            return ResponseEntity.ok(institutes);
        } catch (Exception e) {
            logger.error("GET /api/institutes/active - Failed to list active institutes: username={}, error={}, X-Request-Id={}", 
                    username, e.getMessage(), requestId, e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get institute", description = "Get institute details by ID")
    public ResponseEntity<InstituteDTO> getInstitute(@PathVariable String id, HttpServletRequest httpRequest) {
        String requestId = httpRequest.getHeader("X-Request-Id");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        
        logger.info("GET /api/institutes/{} - Getting institute: username={}, X-Request-Id={}", id, username, requestId);
        
        try {
            InstituteDTO institute = instituteService.getInstitute(id);
            logger.info("GET /api/institutes/{} - Successfully retrieved institute: name={}, username={}, X-Request-Id={}", 
                    id, institute.getName(), username, requestId);
            return ResponseEntity.ok(institute);
        } catch (Exception e) {
            logger.error("GET /api/institutes/{} - Failed to get institute: username={}, error={}, X-Request-Id={}", 
                    id, username, e.getMessage(), requestId, e);
            throw e;
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update institute", description = "Update an existing institute")
    public ResponseEntity<InstituteDTO> updateInstitute(
            @PathVariable String id,
            @Valid @RequestBody CreateInstituteRequest request) {
        InstituteDTO institute = instituteService.updateInstitute(id, request);
        return ResponseEntity.ok(institute);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate institute", description = "Deactivate an institute")
    public ResponseEntity<Void> deactivateInstitute(@PathVariable String id) {
        instituteService.deactivateInstitute(id);
        return ResponseEntity.noContent().build();
    }
}


