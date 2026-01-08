package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateIssueReportRequest;
import com.datagami.edudron.student.dto.IssueReportDTO;
import com.datagami.edudron.student.service.IssueReportService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Issue Reports", description = "Issue reporting endpoints for lectures")
public class IssueReportController {

    @Autowired
    private IssueReportService issueReportService;

    @PostMapping("/lectures/{lectureId}/issues")
    @Operation(summary = "Report issue", description = "Report an issue with a lecture")
    public ResponseEntity<IssueReportDTO> reportIssue(
            @PathVariable String lectureId,
            @Valid @RequestBody CreateIssueReportRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        request.setLectureId(lectureId);
        IssueReportDTO report = issueReportService.createIssueReport(studentId, request);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/lectures/{lectureId}/issues")
    @Operation(summary = "Get issues by lecture", description = "Get all issue reports for a specific lecture")
    public ResponseEntity<List<IssueReportDTO>> getIssuesByLecture(@PathVariable String lectureId) {
        String studentId = UserUtil.getCurrentUserId();
        List<IssueReportDTO> reports = issueReportService.getIssueReportsByLecture(studentId, lectureId);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/courses/{courseId}/issues")
    @Operation(summary = "Get issues by course", description = "Get all issue reports for a course")
    public ResponseEntity<List<IssueReportDTO>> getIssuesByCourse(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        List<IssueReportDTO> reports = issueReportService.getIssueReportsByCourse(studentId, courseId);
        return ResponseEntity.ok(reports);
    }
}

