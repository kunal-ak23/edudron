package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.dto.SectionStudentDTO;
import com.datagami.edudron.student.dto.StudentClassSectionInfoDTO;
import com.datagami.edudron.student.service.EnrollmentService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Enrollments", description = "Student enrollment management endpoints")
public class EnrollmentController {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentController.class);

    @Autowired
    private EnrollmentService enrollmentService;

    @PostMapping("/courses/{courseId}/enroll")
    @Operation(summary = "Enroll in course", description = "Enroll the current student in a course")
    public ResponseEntity<EnrollmentDTO> enroll(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        CreateEnrollmentRequest request = new CreateEnrollmentRequest();
        request.setCourseId(courseId);
        EnrollmentDTO enrollment = enrollmentService.enrollStudent(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollment);
    }

    @GetMapping("/enrollments")
    @Operation(summary = "List my enrollments", description = "Get all enrollments for the current student")
    public ResponseEntity<List<EnrollmentDTO>> getMyEnrollments() {
        String studentId = UserUtil.getCurrentUserId();
        log.info("GET /api/enrollments - Fetching enrollments for student: {}", studentId);
        List<EnrollmentDTO> enrollments = enrollmentService.getStudentEnrollments(studentId);
        log.info("GET /api/enrollments - Returning {} enrollments for student {}", enrollments.size(), studentId);
        if (!enrollments.isEmpty()) {
            log.debug("GET /api/enrollments - Enrollment courseIds: {}", 
                enrollments.stream().map(EnrollmentDTO::getCourseId).collect(java.util.stream.Collectors.toList()));
        } else {
            log.warn("GET /api/enrollments - No enrollments found for student {}", studentId);
        }
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/enrollments/paged")
    @Operation(summary = "List my enrollments (paginated)", description = "Get paginated enrollments for the current student")
    public ResponseEntity<Page<EnrollmentDTO>> getMyEnrollments(Pageable pageable) {
        String studentId = UserUtil.getCurrentUserId();
        Page<EnrollmentDTO> enrollments = enrollmentService.getStudentEnrollments(studentId, pageable);
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/enrollments/{id}")
    @Operation(summary = "Get enrollment", description = "Get enrollment details by ID")
    public ResponseEntity<EnrollmentDTO> getEnrollment(@PathVariable String id) {
        EnrollmentDTO enrollment = enrollmentService.getEnrollment(id);
        return ResponseEntity.ok(enrollment);
    }

    @GetMapping("/courses/{courseId}/enrollments")
    @Operation(summary = "List course enrollments", description = "Get all enrollments for a course (admin/instructor only)")
    public ResponseEntity<List<EnrollmentDTO>> getCourseEnrollments(@PathVariable String courseId) {
        List<EnrollmentDTO> enrollments = enrollmentService.getCourseEnrollments(courseId);
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/enrollments/all")
    @Operation(summary = "List all enrollments (admin)", description = "Get all enrollments in the tenant (admin/instructor only)")
    public ResponseEntity<List<EnrollmentDTO>> getAllEnrollments() {
        List<EnrollmentDTO> enrollments = enrollmentService.getAllEnrollments();
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/enrollments/all/paged")
    @Operation(summary = "List all enrollments with pagination (admin)", description = "Get paginated enrollments in the tenant (admin/instructor only)")
    public ResponseEntity<Page<EnrollmentDTO>> getAllEnrollments(
            @PageableDefault(size = 20, sort = "enrolledAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<EnrollmentDTO> enrollments = enrollmentService.getAllEnrollments(pageable);
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/courses/{courseId}/enrolled")
    @Operation(summary = "Check enrollment", description = "Check if current student is enrolled in a course")
    public ResponseEntity<java.util.Map<String, Boolean>> isEnrolled(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        log.info("GET /api/courses/{}/enrolled - Checking enrollment for student: {}", courseId, studentId);
        boolean enrolled = enrollmentService.isEnrolled(studentId, courseId);
        log.info("GET /api/courses/{}/enrolled - Student {} enrollment status: {}", courseId, studentId, enrolled);
        java.util.Map<String, Boolean> response = java.util.Map.of("enrolled", enrolled);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/courses/{courseId}/enroll")
    @Operation(summary = "Unenroll from course", description = "Unenroll the current student from a course")
    public ResponseEntity<Void> unenroll(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        enrollmentService.unenroll(studentId, courseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    @Operation(summary = "Delete enrollment (admin)", description = "Delete an enrollment by ID (admin/instructor only)")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable String enrollmentId) {
        enrollmentService.deleteEnrollment(enrollmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/{studentId}/class-section")
    @Operation(summary = "Get student class and section info", description = "Get the current class and section information for a student")
    public ResponseEntity<StudentClassSectionInfoDTO> getStudentClassSectionInfo(@PathVariable String studentId) {
        StudentClassSectionInfoDTO info = enrollmentService.getStudentClassSectionInfo(studentId);
        if (info == null) {
            // Return empty DTO instead of 204 to avoid issues with RestTemplate
            return ResponseEntity.ok(new StudentClassSectionInfoDTO(null, null, null, null));
        }
        return ResponseEntity.ok(info);
    }

    @GetMapping("/sections/{sectionId}/students")
    @Operation(summary = "Get students by section", description = "Get all students enrolled in a section")
    public ResponseEntity<List<SectionStudentDTO>> getStudentsBySection(@PathVariable String sectionId) {
        List<SectionStudentDTO> students = enrollmentService.getStudentsBySection(sectionId);
        return ResponseEntity.ok(students);
    }
}


