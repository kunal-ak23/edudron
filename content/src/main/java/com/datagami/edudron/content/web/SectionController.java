package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Sections", description = "Section (Chapter) management endpoints")
public class SectionController {

    @Autowired
    private SectionService sectionService;

    @GetMapping("/courses/{courseId}/sections")
    @Operation(summary = "List sections", description = "Get all sections for a course")
    public ResponseEntity<List<SectionDTO>> getSections(@PathVariable String courseId) {
        List<SectionDTO> sections = sectionService.getSectionsByCourse(courseId);
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/sections/{id}")
    @Operation(summary = "Get section", description = "Get section details by ID")
    public ResponseEntity<SectionDTO> getSection(@PathVariable String id) {
        SectionDTO section = sectionService.getSectionById(id);
        return ResponseEntity.ok(section);
    }

    @PostMapping("/courses/{courseId}/sections")
    @Operation(summary = "Create section", description = "Create a new section for a course")
    public ResponseEntity<SectionDTO> createSection(
            @PathVariable String courseId,
            @RequestBody Map<String, String> request) {
        SectionDTO section = sectionService.createSection(
            courseId,
            request.get("title"),
            request.get("description")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(section);
    }

    @PutMapping("/sections/{id}")
    @Operation(summary = "Update section", description = "Update an existing section")
    public ResponseEntity<SectionDTO> updateSection(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        SectionDTO section = sectionService.updateSection(
            id,
            request.get("title"),
            request.get("description")
        );
        return ResponseEntity.ok(section);
    }

    @DeleteMapping("/sections/{id}")
    @Operation(summary = "Delete section", description = "Delete a section")
    public ResponseEntity<Void> deleteSection(@PathVariable String id) {
        sectionService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }
}

