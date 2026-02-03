package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateInstructorAssignmentRequest;
import com.datagami.edudron.student.dto.InstructorAccessDTO;
import com.datagami.edudron.student.dto.InstructorAssignmentDTO;
import com.datagami.edudron.student.service.InstructorAssignmentService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/instructor-assignments")
@Tag(name = "Instructor Assignments", description = "Manage instructor assignments to classes, sections, and courses")
public class InstructorAssignmentController {

    private static final Logger log = LoggerFactory.getLogger(InstructorAssignmentController.class);

    @Autowired
    private InstructorAssignmentService assignmentService;

    @GetMapping
    @Operation(summary = "List all assignments", 
               description = "Get all instructor assignments for the current tenant (admin only)")
    public ResponseEntity<List<InstructorAssignmentDTO>> getAllAssignments() {
        List<InstructorAssignmentDTO> assignments = assignmentService.getAllAssignments();
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/me")
    @Operation(summary = "Get my assignments", 
               description = "Get all assignments for the current instructor")
    public ResponseEntity<List<InstructorAssignmentDTO>> getMyAssignments() {
        String userId = UserUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<InstructorAssignmentDTO> assignments = assignmentService.getAssignmentsForInstructor(userId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/me/access")
    @Operation(summary = "Get my access scope", 
               description = "Get the derived access scope for the current instructor")
    public ResponseEntity<InstructorAccessDTO> getMyAccess() {
        String userId = UserUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        InstructorAccessDTO access = assignmentService.getInstructorAccess(userId);
        return ResponseEntity.ok(access);
    }

    @GetMapping("/instructor/{instructorId}")
    @Operation(summary = "Get assignments for instructor", 
               description = "Get all assignments for a specific instructor (admin only)")
    public ResponseEntity<List<InstructorAssignmentDTO>> getAssignmentsForInstructor(
            @PathVariable String instructorId) {
        List<InstructorAssignmentDTO> assignments = assignmentService.getAssignmentsForInstructor(instructorId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/instructor/{instructorId}/access")
    @Operation(summary = "Get instructor access scope", 
               description = "Get the derived access scope for a specific instructor (admin only)")
    public ResponseEntity<InstructorAccessDTO> getInstructorAccess(
            @PathVariable String instructorId,
            HttpServletRequest request) {
        boolean hasXClientId = request.getHeader("X-Client-Id") != null && !request.getHeader("X-Client-Id").isBlank();
        boolean hasAuth = request.getHeader("Authorization") != null && !request.getHeader("Authorization").isBlank();
        log.info("Instructor access request: instructorId={}, hasX-Client-Id={}, hasAuthorization={}",
                instructorId, hasXClientId, hasAuth);

        InstructorAccessDTO access = assignmentService.getInstructorAccess(instructorId);

        int courseCount = access != null && access.getAllowedCourseIds() != null ? access.getAllowedCourseIds().size() : 0;
        int classCount = access != null && access.getAllowedClassIds() != null ? access.getAllowedClassIds().size() : 0;
        int sectionCount = access != null && access.getAllowedSectionIds() != null ? access.getAllowedSectionIds().size() : 0;
        log.info("Instructor access response: instructorUserId={}, allowedCourseIds={}, allowedClassIds={}, allowedSectionIds={}, sectionOnlyAccess={}",
                access != null ? access.getInstructorUserId() : null,
                courseCount, classCount, sectionCount,
                access != null ? access.isSectionOnlyAccess() : null);

        return ResponseEntity.ok(access);
    }

    @GetMapping("/instructor/{instructorId}/allowed-classes")
    @Operation(summary = "Get allowed class IDs", 
               description = "Get all class IDs the instructor can access")
    public ResponseEntity<Set<String>> getAllowedClassIds(
            @PathVariable String instructorId) {
        Set<String> classIds = assignmentService.getAllowedClassIds(instructorId);
        return ResponseEntity.ok(classIds);
    }

    @GetMapping("/instructor/{instructorId}/allowed-sections")
    @Operation(summary = "Get allowed section IDs", 
               description = "Get all section IDs the instructor can access")
    public ResponseEntity<Set<String>> getAllowedSectionIds(
            @PathVariable String instructorId) {
        Set<String> sectionIds = assignmentService.getAllowedSectionIds(instructorId);
        return ResponseEntity.ok(sectionIds);
    }

    @GetMapping("/instructor/{instructorId}/allowed-courses")
    @Operation(summary = "Get allowed course IDs", 
               description = "Get all course IDs the instructor can access")
    public ResponseEntity<Set<String>> getAllowedCourseIds(
            @PathVariable String instructorId) {
        Set<String> courseIds = assignmentService.getAllowedCourseIds(instructorId);
        return ResponseEntity.ok(courseIds);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get assignment by ID", 
               description = "Get a specific instructor assignment")
    public ResponseEntity<InstructorAssignmentDTO> getAssignment(@PathVariable String id) {
        InstructorAssignmentDTO assignment = assignmentService.getAssignment(id);
        return ResponseEntity.ok(assignment);
    }

    @PostMapping
    @Operation(summary = "Create assignment", 
               description = "Create a new instructor assignment (admin only)")
    public ResponseEntity<InstructorAssignmentDTO> createAssignment(
            @Valid @RequestBody CreateInstructorAssignmentRequest request) {
        InstructorAssignmentDTO assignment = assignmentService.createAssignment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PostMapping("/class")
    @Operation(summary = "Assign to class", 
               description = "Assign an instructor to a class (admin only)")
    public ResponseEntity<InstructorAssignmentDTO> assignToClass(
            @Valid @RequestBody CreateInstructorAssignmentRequest request) {
        InstructorAssignmentDTO assignment = assignmentService.assignToClass(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PostMapping("/section")
    @Operation(summary = "Assign to section", 
               description = "Assign an instructor to a section (admin only)")
    public ResponseEntity<InstructorAssignmentDTO> assignToSection(
            @Valid @RequestBody CreateInstructorAssignmentRequest request) {
        InstructorAssignmentDTO assignment = assignmentService.assignToSection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @PostMapping("/course")
    @Operation(summary = "Assign to course", 
               description = "Assign an instructor to a course with optional class/section scope (admin only)")
    public ResponseEntity<InstructorAssignmentDTO> assignToCourse(
            @Valid @RequestBody CreateInstructorAssignmentRequest request) {
        InstructorAssignmentDTO assignment = assignmentService.assignToCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete assignment", 
               description = "Remove an instructor assignment (admin only)")
    public ResponseEntity<Void> deleteAssignment(@PathVariable String id) {
        assignmentService.removeAssignment(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Access Check Endpoints ====================

    @GetMapping("/instructor/{instructorId}/can-access-class/{classId}")
    @Operation(summary = "Check class access", 
               description = "Check if instructor can access a specific class")
    public ResponseEntity<Boolean> canAccessClass(
            @PathVariable String instructorId,
            @PathVariable String classId) {
        boolean canAccess = assignmentService.canAccessClass(instructorId, classId);
        return ResponseEntity.ok(canAccess);
    }

    @GetMapping("/instructor/{instructorId}/can-access-section/{sectionId}")
    @Operation(summary = "Check section access", 
               description = "Check if instructor can access a specific section")
    public ResponseEntity<Boolean> canAccessSection(
            @PathVariable String instructorId,
            @PathVariable String sectionId) {
        boolean canAccess = assignmentService.canAccessSection(instructorId, sectionId);
        return ResponseEntity.ok(canAccess);
    }

    @GetMapping("/instructor/{instructorId}/can-access-course/{courseId}")
    @Operation(summary = "Check course access", 
               description = "Check if instructor can access a specific course")
    public ResponseEntity<Boolean> canAccessCourse(
            @PathVariable String instructorId,
            @PathVariable String courseId) {
        boolean canAccess = assignmentService.canAccessCourse(instructorId, courseId);
        return ResponseEntity.ok(canAccess);
    }

    @GetMapping("/instructor/{instructorId}/can-access-course/{courseId}/section/{sectionId}")
    @Operation(summary = "Check course access for section", 
               description = "Check if instructor can access a specific course for a specific section")
    public ResponseEntity<Boolean> canAccessCourseForSection(
            @PathVariable String instructorId,
            @PathVariable String courseId,
            @PathVariable String sectionId) {
        boolean canAccess = assignmentService.canAccessCourseForSection(instructorId, courseId, sectionId);
        return ResponseEntity.ok(canAccess);
    }
}
