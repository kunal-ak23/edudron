package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Sections", description = "Section management endpoints")
public class SectionController {

    @Autowired
    private SectionService sectionService;

    @PostMapping("/classes/{classId}/sections")
    @Operation(summary = "Create section", description = "Create a new section for a class")
    public ResponseEntity<SectionDTO> createSection(
            @PathVariable String classId,
            @Valid @RequestBody CreateSectionRequest request) {
        request.setClassId(classId);
        SectionDTO section = sectionService.createSection(classId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(section);
    }

    @PostMapping("/classes/{classId}/sections/batch")
    @Operation(summary = "Batch create sections", 
               description = "Create multiple sections at once for a class (max 50)")
    public ResponseEntity<BatchCreateSectionsResponse> batchCreateSections(
            @PathVariable String classId,
            @Valid @RequestBody BatchCreateSectionsRequest request) {
        // Set class ID for all sections
        request.getSections().forEach(sectionRequest -> sectionRequest.setClassId(classId));
        BatchCreateSectionsResponse response = sectionService.batchCreateSections(classId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/classes/{classId}/sections")
    @Operation(summary = "List sections by class", description = "Get all sections for a class")
    public ResponseEntity<List<SectionDTO>> getSectionsByClass(@PathVariable String classId) {
        List<SectionDTO> sections = sectionService.getSectionsByClass(classId);
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/classes/{classId}/sections/active")
    @Operation(summary = "List active sections by class", description = "Get active sections for a class")
    public ResponseEntity<List<SectionDTO>> getActiveSectionsByClass(@PathVariable String classId) {
        List<SectionDTO> sections = sectionService.getActiveSectionsByClass(classId);
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/sections/{id}")
    @Operation(summary = "Get section", description = "Get section details by ID")
    public ResponseEntity<SectionDTO> getSection(@PathVariable String id) {
        SectionDTO section = sectionService.getSection(id);
        return ResponseEntity.ok(section);
    }

    @GetMapping("/sections/count")
    @Operation(summary = "Count sections", description = "Get section count for current tenant. Defaults to counting only active sections.")
    public ResponseEntity<Long> countSections(
            @RequestParam(name = "active", defaultValue = "true") boolean activeOnly
    ) {
        long count = sectionService.countSections(activeOnly);
        return ResponseEntity.ok(count);
    }

    @PutMapping("/sections/{id}")
    @Operation(summary = "Update section", description = "Update an existing section")
    public ResponseEntity<SectionDTO> updateSection(
            @PathVariable String id,
            @Valid @RequestBody CreateSectionRequest request) {
        SectionDTO section = sectionService.updateSection(id, request);
        return ResponseEntity.ok(section);
    }

    @DeleteMapping("/sections/{id}")
    @Operation(summary = "Deactivate section", description = "Deactivate a section")
    public ResponseEntity<Void> deactivateSection(@PathVariable String id) {
        sectionService.deactivateSection(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sections/{id}/progress")
    @Operation(summary = "Get section progress", description = "Get progress statistics for a section")
    public ResponseEntity<SectionProgressDTO> getSectionProgress(@PathVariable String id) {
        SectionProgressDTO progress = sectionService.getSectionProgress(id);
        return ResponseEntity.ok(progress);
    }
}


