package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.service.ProjectService;
import com.datagami.edudron.student.util.UserUtil;
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
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects (Student)", description = "Student-facing project endpoints")
public class ProjectStudentController {

    private static final Logger log = LoggerFactory.getLogger(ProjectStudentController.class);

    @Autowired
    private ProjectService projectService;

    @GetMapping("/my-projects")
    @Operation(summary = "Get my projects", description = "Get all projects where the current student is a group member")
    public ResponseEntity<List<ProjectDTO>> getMyProjects() {
        String studentId = UserUtil.getCurrentUserId();
        log.info("GET /api/projects/my-projects - student: {}", studentId);
        List<ProjectDTO> projects = projectService.getMyProjects(studentId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}/my-group")
    @Operation(summary = "Get my group", description = "Get the current student's group for a specific project")
    public ResponseEntity<ProjectGroupDTO> getMyGroup(@PathVariable String id) {
        String studentId = UserUtil.getCurrentUserId();
        ProjectGroupDTO group = projectService.getMyGroup(id, studentId);
        return ResponseEntity.ok(group);
    }

    @PostMapping("/{id}/my-group/submit")
    @Operation(summary = "Submit project", description = "Submit the project for the current student's group")
    public ResponseEntity<ProjectGroupDTO> submitProject(
            @PathVariable String id,
            @Valid @RequestBody SubmitProjectRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        // First get the student's group
        ProjectGroupDTO myGroup = projectService.getMyGroup(id, studentId);
        ProjectGroupDTO result = projectService.submitProject(id, myGroup.getId(), studentId,
                request.getSubmissionUrl(), request.getAttachments());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/my-group/history")
    @Operation(summary = "Get submission history", description = "Get submission history for the current student's group")
    public ResponseEntity<List<Map<String, Object>>> getSubmissionHistory(@PathVariable String id) {
        String studentId = UserUtil.getCurrentUserId();
        ProjectGroupDTO myGroup = projectService.getMyGroup(id, studentId);
        List<Map<String, Object>> history = projectService.getSubmissionHistory(id, myGroup.getId());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/my-attendance")
    @Operation(summary = "Get my attendance", description = "Get attendance and grades for the current student in a project")
    public ResponseEntity<Map<String, Object>> getMyAttendance(@PathVariable String id) {
        String studentId = UserUtil.getCurrentUserId();
        Map<String, Object> result = projectService.getMyAttendance(id, studentId);
        return ResponseEntity.ok(result);
    }

    // ======================== Event Submissions (Student) ========================

    @PostMapping("/{id}/events/{eventId}/my-submission")
    @Operation(summary = "Submit to event", description = "Submit to a project event for the current student's group")
    public ResponseEntity<ProjectEventSubmissionDTO> submitToEvent(
            @PathVariable String id, @PathVariable String eventId,
            @RequestBody SubmitEventRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        ProjectGroupDTO myGroup = projectService.getMyGroup(id, studentId);
        ProjectEventSubmissionDTO result = projectService.submitToEvent(id, eventId, myGroup.getId(), studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/events/{eventId}/my-submission")
    @Operation(summary = "Get my event submission", description = "Get latest submission for current student's group")
    public ResponseEntity<ProjectEventSubmissionDTO> getMyEventSubmission(
            @PathVariable String id, @PathVariable String eventId) {
        String studentId = UserUtil.getCurrentUserId();
        ProjectGroupDTO myGroup = projectService.getMyGroup(id, studentId);
        ProjectEventSubmissionDTO result = projectService.getLatestEventSubmission(id, eventId, myGroup.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/events/{eventId}/my-submission/history")
    @Operation(summary = "Get my submission history", description = "Get submission version history")
    public ResponseEntity<List<ProjectEventSubmissionDTO>> getMyEventSubmissionHistory(
            @PathVariable String id, @PathVariable String eventId) {
        String studentId = UserUtil.getCurrentUserId();
        ProjectGroupDTO myGroup = projectService.getMyGroup(id, studentId);
        return ResponseEntity.ok(projectService.getEventSubmissionHistory(id, eventId, myGroup.getId()));
    }

    @GetMapping("/{id}/events/{eventId}/my-submission/feedback")
    @Operation(summary = "Get my submission feedback", description = "Get feedback for current student's group submission")
    public ResponseEntity<List<ProjectEventFeedbackDTO>> getMyEventFeedback(
            @PathVariable String id, @PathVariable String eventId) {
        String studentId = UserUtil.getCurrentUserId();
        ProjectGroupDTO myGroup = projectService.getMyGroup(id, studentId);
        return ResponseEntity.ok(projectService.getEventGroupFeedback(eventId, myGroup.getId()));
    }
}
