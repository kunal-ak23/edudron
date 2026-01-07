package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.service.AssessmentService;
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
@Tag(name = "Assessments", description = "Assessment (Quiz, Assignment) management endpoints")
public class AssessmentController {

    @Autowired
    private AssessmentService assessmentService;

    @GetMapping("/courses/{courseId}/assessments")
    @Operation(summary = "List assessments", description = "Get all assessments for a course")
    public ResponseEntity<List<Assessment>> getAssessmentsByCourse(@PathVariable String courseId) {
        List<Assessment> assessments = assessmentService.getAssessmentsByCourse(courseId);
        return ResponseEntity.ok(assessments);
    }

    @GetMapping("/lectures/{lectureId}/assessments")
    @Operation(summary = "List assessments", description = "Get all assessments for a lecture")
    public ResponseEntity<List<Assessment>> getAssessmentsByLecture(@PathVariable String lectureId) {
        List<Assessment> assessments = assessmentService.getAssessmentsByLecture(lectureId);
        return ResponseEntity.ok(assessments);
    }

    @GetMapping("/assessments/{id}")
    @Operation(summary = "Get assessment", description = "Get assessment details by ID")
    public ResponseEntity<Assessment> getAssessment(@PathVariable String id) {
        Assessment assessment = assessmentService.getAssessmentById(id);
        return ResponseEntity.ok(assessment);
    }

    @PostMapping("/lectures/{lectureId}/assessments")
    @Operation(summary = "Create assessment", description = "Create a new assessment for a lecture")
    public ResponseEntity<Assessment> createAssessment(
            @PathVariable String lectureId,
            @RequestBody Map<String, Object> request) {
        Assessment assessment = assessmentService.createAssessment(
            (String) request.get("courseId"),
            lectureId,
            Assessment.AssessmentType.valueOf((String) request.get("assessmentType")),
            (String) request.get("title"),
            (String) request.get("description"),
            (String) request.get("instructions")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(assessment);
    }

    @DeleteMapping("/assessments/{id}")
    @Operation(summary = "Delete assessment", description = "Delete an assessment")
    public ResponseEntity<Void> deleteAssessment(@PathVariable String id) {
        assessmentService.deleteAssessment(id);
        return ResponseEntity.noContent().build();
    }
}


