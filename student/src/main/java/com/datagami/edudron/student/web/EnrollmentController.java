package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.dto.SectionStudentDTO;
import com.datagami.edudron.student.dto.StudentClassSectionInfoDTO;
import com.datagami.edudron.student.service.EnrollmentService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Enrollments", description = "Student enrollment management endpoints")
public class EnrollmentController {

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
        List<EnrollmentDTO> enrollments = enrollmentService.getStudentEnrollments(studentId);
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

    @GetMapping("/courses/{courseId}/enrolled")
    @Operation(summary = "Check enrollment", description = "Check if current student is enrolled in a course")
    public ResponseEntity<Boolean> isEnrolled(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        boolean enrolled = enrollmentService.isEnrolled(studentId, courseId);
        return ResponseEntity.ok(enrolled);
    }

    @DeleteMapping("/courses/{courseId}/enroll")
    @Operation(summary = "Unenroll from course", description = "Unenroll the current student from a course")
    public ResponseEntity<Void> unenroll(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        enrollmentService.unenroll(studentId, courseId);
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


