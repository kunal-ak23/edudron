package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.BatchEnrollmentRequest;
import com.datagami.edudron.student.dto.BulkEnrollmentResult;
import com.datagami.edudron.student.service.BulkEnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Bulk Enrollment", description = "Bulk enrollment endpoints")
public class BulkEnrollmentController {

    @Autowired
    private BulkEnrollmentService bulkEnrollmentService;

    @PostMapping("/classes/{classId}/enroll/{courseId}")
    @Operation(summary = "Enroll class to course", description = "Enroll all students in a class to a course")
    public ResponseEntity<BulkEnrollmentResult> enrollClassToCourse(
            @PathVariable String classId,
            @PathVariable String courseId) {
        BulkEnrollmentResult result = bulkEnrollmentService.enrollClassToCourse(classId, courseId);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping("/sections/{sectionId}/enroll/{courseId}")
    @Operation(summary = "Enroll section to course", description = "Enroll all students in a section to a course")
    public ResponseEntity<BulkEnrollmentResult> enrollSectionToCourse(
            @PathVariable String sectionId,
            @PathVariable String courseId) {
        BulkEnrollmentResult result = bulkEnrollmentService.enrollSectionToCourse(sectionId, courseId);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping("/classes/{classId}/enroll-batch")
    @Operation(summary = "Enroll class to multiple courses", description = "Enroll all students in a class to multiple courses")
    public ResponseEntity<List<BulkEnrollmentResult>> enrollClassToCourses(
            @PathVariable String classId,
            @RequestBody BatchEnrollmentRequest request) {
        List<BulkEnrollmentResult> results = bulkEnrollmentService.enrollClassToCourses(classId, request);
        return ResponseEntity.status(HttpStatus.OK).body(results);
    }

    @PostMapping("/sections/{sectionId}/enroll-batch")
    @Operation(summary = "Enroll section to multiple courses", description = "Enroll all students in a section to multiple courses")
    public ResponseEntity<List<BulkEnrollmentResult>> enrollSectionToCourses(
            @PathVariable String sectionId,
            @RequestBody BatchEnrollmentRequest request) {
        List<BulkEnrollmentResult> results = bulkEnrollmentService.enrollSectionToCourses(sectionId, request);
        return ResponseEntity.status(HttpStatus.OK).body(results);
    }
}

