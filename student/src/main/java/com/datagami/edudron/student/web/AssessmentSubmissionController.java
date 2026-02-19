package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.AssessmentSubmissionDTO;
import com.datagami.edudron.student.dto.BulkGradeRequest;
import com.datagami.edudron.student.dto.BulkGradeResponse;
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
import java.util.Map;

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

    @GetMapping("/assessments/{assessmentId}/submissions")
    @Operation(summary = "Get submissions by assessment", description = "Get all submissions for an assessment (instructor/admin only)")
    public ResponseEntity<List<AssessmentSubmissionDTO>> getSubmissionsByAssessment(@PathVariable String assessmentId) {
        List<AssessmentSubmissionDTO> submissions = submissionService.getSubmissionsByAssessmentId(assessmentId);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/students/{studentId}/submissions")
    @Operation(summary = "Get submissions by student", description = "Get all assessment/exam submissions for a student (instructor/admin only)")
    public ResponseEntity<List<AssessmentSubmissionDTO>> getSubmissionsByStudent(@PathVariable String studentId) {
        List<AssessmentSubmissionDTO> submissions = submissionService.getSubmissionsByStudentId(studentId);
        return ResponseEntity.ok(submissions);
    }
    
    @PostMapping("/assessments/{assessmentId}/submissions/bulk-grade")
    @Operation(summary = "Bulk grade submissions", description = "Grade multiple submissions for an assessment (instructor/admin only)")
    public ResponseEntity<BulkGradeResponse> bulkGrade(
            @PathVariable String assessmentId,
            @RequestBody BulkGradeRequest request) {
        if (request == null || request.getGrades() == null) {
            return ResponseEntity.badRequest().body(new BulkGradeResponse(0, java.util.Collections.emptyList()));
        }
        BulkGradeResponse response = submissionService.bulkGradeSubmissions(assessmentId, request.getGrades());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/assessments/submissions/{submissionId}/grade")
    @Operation(summary = "Grade submission", description = "Grade an assessment submission (instructor/admin only)")
    public ResponseEntity<AssessmentSubmissionDTO> gradeSubmission(
            @PathVariable String submissionId,
            @RequestParam(required = false) BigDecimal score,
            @RequestParam(required = false) BigDecimal maxScore,
            @RequestBody(required = false) java.util.Map<String, Object> requestBody) {
        
        // Support both old format (query params) and new format (JSON body)
        if (requestBody != null && !requestBody.isEmpty()) {
            BigDecimal reqScore = requestBody.get("score") != null ? 
                new BigDecimal(requestBody.get("score").toString()) : null;
            BigDecimal reqMaxScore = requestBody.get("maxScore") != null ? 
                new BigDecimal(requestBody.get("maxScore").toString()) : null;
            BigDecimal reqPercentage = requestBody.get("percentage") != null ? 
                new BigDecimal(requestBody.get("percentage").toString()) : null;
            Boolean reqIsPassed = requestBody.get("isPassed") != null ? 
                (Boolean) requestBody.get("isPassed") : null;
            Object reqFeedback = requestBody.get("aiReviewFeedback");
            String reqReviewStatus = requestBody.get("reviewStatus") != null ? 
                requestBody.get("reviewStatus").toString() : null;
            
            AssessmentSubmissionDTO submission = submissionService.gradeSubmissionWithDetails(
                submissionId, reqScore, reqMaxScore, reqPercentage, reqIsPassed, reqFeedback, reqReviewStatus);
            return ResponseEntity.ok(submission);
        } else {
            // Legacy format with query params
            AssessmentSubmissionDTO submission = submissionService.gradeSubmission(submissionId, score, maxScore);
            return ResponseEntity.ok(submission);
        }
    }

    @DeleteMapping("/assessments/{assessmentId}/submissions/{submissionId}")
    @Operation(summary = "Reset test for student", description = "Delete this submission so the student can take the test again. Available for all students who have taken the test (instructor/admin only).")
    public ResponseEntity<Void> discardInProgressSubmission(
            @PathVariable String assessmentId,
            @PathVariable String submissionId) {
        submissionService.discardInProgressSubmission(assessmentId, submissionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assessments/{assessmentId}/submissions/reset-bulk")
    @Operation(summary = "Bulk reset test for students", description = "Delete multiple submissions so those students can take the test again. Max 500 per request (instructor/admin only).")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> resetSubmissionsBulk(
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> request) {
        List<String> submissionIds = request != null ? (List<String>) request.get("submissionIds") : null;
        if (submissionIds == null || submissionIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "submissionIds must not be null or empty"));
        }
        try {
            return ResponseEntity.ok(submissionService.resetSubmissionsBulk(assessmentId, submissionIds));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/assessments/submissions/{submissionId}/mark-cheating")
    @Operation(summary = "Mark submission as cheating", description = "Set or clear the marked-as-cheating flag (instructor/admin only)")
    public ResponseEntity<AssessmentSubmissionDTO> markAsCheating(
            @PathVariable String submissionId,
            @RequestBody java.util.Map<String, Object> requestBody) {
        if (requestBody == null || !requestBody.containsKey("markedAsCheating")) {
            return ResponseEntity.badRequest().build();
        }
        Object val = requestBody.get("markedAsCheating");
        Boolean markedAsCheating = val instanceof Boolean ? (Boolean) val
            : val instanceof String ? Boolean.parseBoolean((String) val) : null;
        if (markedAsCheating == null) {
            return ResponseEntity.badRequest().build();
        }
        AssessmentSubmissionDTO submission = submissionService.markSubmissionAsCheating(submissionId, markedAsCheating);
        return ResponseEntity.ok(submission);
    }
}


