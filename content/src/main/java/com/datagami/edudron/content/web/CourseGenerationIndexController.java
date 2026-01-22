package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.CourseGenerationIndex;
import com.datagami.edudron.content.dto.CourseGenerationIndexDTO;
import com.datagami.edudron.content.service.CourseGenerationIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/content/course-generation-index")
@Tag(name = "Course Generation Index", description = "Manage reference content and writing formats for AI course generation")
public class CourseGenerationIndexController {
    
    @Autowired
    private CourseGenerationIndexService indexService;
    
    @PostMapping("/reference-content")
    @Operation(
        summary = "Upload reference content",
        description = "Upload a document (PDF, DOCX, etc.) to use as reference content for course generation. Text will be automatically extracted. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features."
    )
    public ResponseEntity<CourseGenerationIndexDTO> uploadReferenceContent(
            @RequestParam @NotBlank String title,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file) throws IOException {
        // Role check is done in service, but we check here for better error messages
        CourseGenerationIndexDTO index = indexService.uploadReferenceContent(title, description, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(index);
    }
    
    @PostMapping("/writing-format")
    @Operation(
        summary = "Create writing format",
        description = "Create a writing format template by providing text directly or uploading a document. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features."
    )
    public ResponseEntity<CourseGenerationIndexDTO> createWritingFormat(
            @RequestParam @NotBlank String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String writingFormat,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        // Role check is done in service, but we check here for better error messages
        CourseGenerationIndexDTO index;
        if (file != null && !file.isEmpty()) {
            index = indexService.uploadWritingFormat(title, description, file);
        } else if (writingFormat != null && !writingFormat.trim().isEmpty()) {
            index = indexService.createWritingFormat(title, description, writingFormat);
        } else {
            throw new IllegalArgumentException("Either writingFormat text or file must be provided");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(index);
    }
    
    @GetMapping
    @Operation(summary = "List all indexes", description = "Get all active reference content and writing formats")
    public ResponseEntity<List<CourseGenerationIndexDTO>> getAllIndexes() {
        List<CourseGenerationIndexDTO> indexes = indexService.getAllActiveIndexes();
        return ResponseEntity.ok(indexes);
    }
    
    @GetMapping("/type/{type}")
    @Operation(summary = "List indexes by type", description = "Get indexes filtered by type (REFERENCE_CONTENT or WRITING_FORMAT)")
    public ResponseEntity<List<CourseGenerationIndexDTO>> getIndexesByType(
            @PathVariable CourseGenerationIndex.IndexType type) {
        List<CourseGenerationIndexDTO> indexes = indexService.getIndexesByType(type);
        return ResponseEntity.ok(indexes);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get index", description = "Get index details by ID")
    public ResponseEntity<CourseGenerationIndexDTO> getIndex(@PathVariable String id) {
        CourseGenerationIndexDTO index = indexService.getIndexById(id);
        return ResponseEntity.ok(index);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete index", description = "Delete an index. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features.")
    public ResponseEntity<Void> deleteIndex(@PathVariable String id) {
        // Role check is done in service
        indexService.deleteIndex(id);
        return ResponseEntity.noContent().build();
    }
}

