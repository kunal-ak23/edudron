package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.AssessmentSubmissionDTO;
import com.datagami.edudron.student.dto.SubmitAssessmentRequest;
import com.datagami.edudron.student.service.AssessmentSubmissionService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Assessment Submissions", description = "Assessment submission and grading endpoints")
public class AssessmentSubmissionController {

    @Autowired
    private AssessmentSubmissionService submissionService;

    @PostMapping("/courses/{courseId}/assessments/submit")
    @Operation(summary = "Submit assessment", description = "Submit answers for an assessment")
    public ResponseEntity<AssessmentSubmissionDTO> submitAssessment(
            @PathVariable String courseId,
            @Valid @RequestBody SubmitAssessmentRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        AssessmentSubmissionDTO submission = submissionService.submitAssessment(studentId, courseId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(submission);
    }

    @GetMapping("/courses/{courseId}/assessments/submissions")
    @Operation(summary = "List submissions", description = "Get all assessment submissions for a course")
    public ResponseEntity<List<AssessmentSubmissionDTO>> getSubmissions(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        List<AssessmentSubmissionDTO> submissions = submissionService.getStudentSubmissions(studentId, courseId);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/courses/{courseId}/assessments/submissions/paged")
    @Operation(summary = "List submissions (paginated)", description = "Get paginated assessment submissions for a course")
    public ResponseEntity<Page<AssessmentSubmissionDTO>> getSubmissions(
            @PathVariable String courseId,
            Pageable pageable) {
        String studentId = UserUtil.getCurrentUserId();
        Page<AssessmentSubmissionDTO> submissions = submissionService.getStudentSubmissions(studentId, courseId, pageable);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/assessments/{assessmentId}/submissions/latest")
    @Operation(summary = "Get latest submission", description = "Get the latest submission for an assessment")
    public ResponseEntity<AssessmentSubmissionDTO> getLatestSubmission(@PathVariable String assessmentId) {
        String studentId = UserUtil.getCurrentUserId();
        AssessmentSubmissionDTO submission = submissionService.getLatestSubmission(studentId, assessmentId);
        return ResponseEntity.ok(submission);
    }

    @PostMapping("/assessments/submissions/{submissionId}/grade")
    @Operation(summary = "Grade submission", description = "Grade an assessment submission (instructor/admin only)")
    public ResponseEntity<AssessmentSubmissionDTO> gradeSubmission(
            @PathVariable String submissionId,
            @RequestParam BigDecimal score,
            @RequestParam BigDecimal maxScore) {
        AssessmentSubmissionDTO submission = submissionService.gradeSubmission(submissionId, score, maxScore);
        return ResponseEntity.ok(submission);
    }
}

