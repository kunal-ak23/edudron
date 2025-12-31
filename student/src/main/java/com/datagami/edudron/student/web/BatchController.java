package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.BatchDTO;
import com.datagami.edudron.student.dto.BatchProgressDTO;
import com.datagami.edudron.student.dto.CreateBatchRequest;
import com.datagami.edudron.student.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
@Tag(name = "Batches", description = "Batch management endpoints")
public class BatchController {

    @Autowired
    private BatchService batchService;

    @PostMapping
    @Operation(summary = "Create batch", description = "Create a new batch for a course")
    public ResponseEntity<BatchDTO> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        BatchDTO batch = batchService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(batch);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get batch", description = "Get batch details by ID")
    public ResponseEntity<BatchDTO> getBatch(@PathVariable String id) {
        BatchDTO batch = batchService.getBatch(id);
        return ResponseEntity.ok(batch);
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "List batches by course", description = "Get all batches for a course")
    public ResponseEntity<List<BatchDTO>> getBatchesByCourse(@PathVariable String courseId) {
        List<BatchDTO> batches = batchService.getBatchesByCourse(courseId);
        return ResponseEntity.ok(batches);
    }

    @GetMapping("/courses/{courseId}/active")
    @Operation(summary = "List active batches by course", description = "Get active batches for a course")
    public ResponseEntity<List<BatchDTO>> getActiveBatchesByCourse(@PathVariable String courseId) {
        List<BatchDTO> batches = batchService.getActiveBatchesByCourse(courseId);
        return ResponseEntity.ok(batches);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update batch", description = "Update an existing batch")
    public ResponseEntity<BatchDTO> updateBatch(
            @PathVariable String id,
            @Valid @RequestBody CreateBatchRequest request) {
        BatchDTO batch = batchService.updateBatch(id, request);
        return ResponseEntity.ok(batch);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate batch", description = "Deactivate a batch")
    public ResponseEntity<Void> deactivateBatch(@PathVariable String id) {
        batchService.deactivateBatch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/progress")
    @Operation(summary = "Get batch progress", description = "Get progress statistics for a batch")
    public ResponseEntity<BatchProgressDTO> getBatchProgress(@PathVariable String id) {
        BatchProgressDTO progress = batchService.getBatchProgress(id);
        return ResponseEntity.ok(progress);
    }
}

