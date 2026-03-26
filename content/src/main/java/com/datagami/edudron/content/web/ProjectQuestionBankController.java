package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.BulkProjectQuestionRequest;
import com.datagami.edudron.content.dto.CreateProjectQuestionRequest;
import com.datagami.edudron.content.dto.ProjectQuestionDTO;
import com.datagami.edudron.content.service.ProjectQuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project-questions")
@Tag(name = "Project Question Bank", description = "Project question bank management endpoints")
public class ProjectQuestionBankController {

    private static final Logger log = LoggerFactory.getLogger(ProjectQuestionBankController.class);

    @Autowired
    private ProjectQuestionBankService projectQuestionBankService;

    @PostMapping
    @Operation(summary = "Create project question")
    public ResponseEntity<ProjectQuestionDTO> create(@Valid @RequestBody CreateProjectQuestionRequest request) {
        ProjectQuestionDTO question = projectQuestionBankService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(question);
    }

    @GetMapping
    @Operation(summary = "List project questions", description = "List project questions with optional filters")
    public ResponseEntity<List<ProjectQuestionDTO>> list(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Boolean isActive) {
        List<ProjectQuestionDTO> questions = projectQuestionBankService.list(courseId, difficulty, isActive);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project question")
    public ResponseEntity<ProjectQuestionDTO> get(@PathVariable String id) {
        ProjectQuestionDTO question = projectQuestionBankService.get(id);
        return ResponseEntity.ok(question);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project question")
    public ResponseEntity<ProjectQuestionDTO> update(
            @PathVariable String id,
            @Valid @RequestBody CreateProjectQuestionRequest request) {
        ProjectQuestionDTO question = projectQuestionBankService.update(id, request);
        return ResponseEntity.ok(question);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project question", description = "Soft delete - sets isActive to false")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        projectQuestionBankService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-upload")
    @Operation(summary = "Bulk create project questions")
    public ResponseEntity<List<ProjectQuestionDTO>> bulkCreate(@Valid @RequestBody BulkProjectQuestionRequest request) {
        List<ProjectQuestionDTO> questions = projectQuestionBankService.bulkCreate(request.getQuestions());
        return ResponseEntity.status(HttpStatus.CREATED).body(questions);
    }
}
