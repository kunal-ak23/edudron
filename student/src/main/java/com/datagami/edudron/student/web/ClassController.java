package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.ClassDTO;
import com.datagami.edudron.student.dto.CreateClassRequest;
import com.datagami.edudron.student.service.ClassService;
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
@Tag(name = "Classes", description = "Class management endpoints")
public class ClassController {

    @Autowired
    private ClassService classService;

    @PostMapping("/institutes/{instituteId}/classes")
    @Operation(summary = "Create class", description = "Create a new class for an institute")
    public ResponseEntity<ClassDTO> createClass(
            @PathVariable String instituteId,
            @Valid @RequestBody CreateClassRequest request) {
        request.setInstituteId(instituteId);
        ClassDTO classDTO = classService.createClass(instituteId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(classDTO);
    }

    @GetMapping("/institutes/{instituteId}/classes")
    @Operation(summary = "List classes by institute", description = "Get all classes for an institute")
    public ResponseEntity<List<ClassDTO>> getClassesByInstitute(@PathVariable String instituteId) {
        List<ClassDTO> classes = classService.getClassesByInstitute(instituteId);
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/institutes/{instituteId}/classes/active")
    @Operation(summary = "List active classes by institute", description = "Get active classes for an institute")
    public ResponseEntity<List<ClassDTO>> getActiveClassesByInstitute(@PathVariable String instituteId) {
        List<ClassDTO> classes = classService.getActiveClassesByInstitute(instituteId);
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/classes/{id}")
    @Operation(summary = "Get class", description = "Get class details by ID")
    public ResponseEntity<ClassDTO> getClass(@PathVariable String id) {
        ClassDTO classDTO = classService.getClass(id);
        return ResponseEntity.ok(classDTO);
    }

    @PutMapping("/classes/{id}")
    @Operation(summary = "Update class", description = "Update an existing class")
    public ResponseEntity<ClassDTO> updateClass(
            @PathVariable String id,
            @Valid @RequestBody CreateClassRequest request) {
        ClassDTO classDTO = classService.updateClass(id, request);
        return ResponseEntity.ok(classDTO);
    }

    @DeleteMapping("/classes/{id}")
    @Operation(summary = "Deactivate class", description = "Deactivate a class")
    public ResponseEntity<Void> deactivateClass(@PathVariable String id) {
        classService.deactivateClass(id);
        return ResponseEntity.noContent().build();
    }
}


