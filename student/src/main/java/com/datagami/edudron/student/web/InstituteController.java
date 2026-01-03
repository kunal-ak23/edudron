package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateInstituteRequest;
import com.datagami.edudron.student.dto.InstituteDTO;
import com.datagami.edudron.student.service.InstituteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/institutes")
@Tag(name = "Institutes", description = "Institute management endpoints")
public class InstituteController {

    @Autowired
    private InstituteService instituteService;

    @PostMapping
    @Operation(summary = "Create institute", description = "Create a new institute")
    public ResponseEntity<InstituteDTO> createInstitute(@Valid @RequestBody CreateInstituteRequest request) {
        InstituteDTO institute = instituteService.createInstitute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(institute);
    }

    @GetMapping
    @Operation(summary = "List institutes", description = "Get all institutes for the current tenant")
    public ResponseEntity<List<InstituteDTO>> getAllInstitutes() {
        List<InstituteDTO> institutes = instituteService.getAllInstitutes();
        return ResponseEntity.ok(institutes);
    }

    @GetMapping("/active")
    @Operation(summary = "List active institutes", description = "Get all active institutes for the current tenant")
    public ResponseEntity<List<InstituteDTO>> getActiveInstitutes() {
        List<InstituteDTO> institutes = instituteService.getActiveInstitutes();
        return ResponseEntity.ok(institutes);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get institute", description = "Get institute details by ID")
    public ResponseEntity<InstituteDTO> getInstitute(@PathVariable String id) {
        InstituteDTO institute = instituteService.getInstitute(id);
        return ResponseEntity.ok(institute);
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

