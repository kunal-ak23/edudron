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

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects (Admin)", description = "Project management endpoints for admin/instructors")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    private ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create project")
    public ResponseEntity<ProjectDTO> createProject(@Valid @RequestBody CreateProjectRequest request) {
        String createdBy = UserUtil.getCurrentUserEmail();
        log.info("POST /api/projects - Creating project '{}' by {}", request.getTitle(), createdBy);
        ProjectDTO project = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @PostMapping("/bulk-setup")
    @Operation(summary = "Bulk project setup",
               description = "Create project, generate groups from multiple sections, assign problem statements")
    public ResponseEntity<ProjectDTO> bulkSetup(@Valid @RequestBody BulkProjectSetupRequest request) {
        log.info("POST /api/projects/bulk-setup - Bulk setup '{}' for course {} with {} sections",
                request.getTitle(), request.getCourseId(), request.getSectionIds().size());
        ProjectDTO project = projectService.bulkSetup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping("/sections-by-course/{courseId}")
    @Operation(summary = "Get sections with enrollments for a course")
    public ResponseEntity<List<String>> getSectionsByCourse(@PathVariable String courseId) {
        List<String> sectionIds = projectService.getSectionIdsByCourse(courseId);
        return ResponseEntity.ok(sectionIds);
    }

    @PostMapping("/{id}/add-sections")
    @Operation(summary = "Add sections to existing project",
               description = "Add new sections, generate groups for new students, assign problem statements")
    public ResponseEntity<ProjectDTO> addSections(
            @PathVariable String id,
            @Valid @RequestBody AddSectionsRequest request) {
        log.info("POST /api/projects/{}/add-sections - Adding {} sections", id, request.getSectionIds().size());
        ProjectDTO project = projectService.addSections(id, request);
        return ResponseEntity.ok(project);
    }

    @GetMapping
    @Operation(summary = "List projects")
    public ResponseEntity<List<ProjectDTO>> listProjects(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String status) {
        List<ProjectDTO> projects = projectService.listProjects(courseId, sectionId, status);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable String id) {
        ProjectDTO project = projectService.getProject(id);
        return ResponseEntity.ok(project);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project")
    public ResponseEntity<ProjectDTO> updateProject(
            @PathVariable String id,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectDTO project = projectService.updateProject(id, request);
        return ResponseEntity.ok(project);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project", description = "Delete project and all related data")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate project")
    public ResponseEntity<ProjectDTO> activateProject(@PathVariable String id) {
        ProjectDTO project = projectService.activateProject(id);
        return ResponseEntity.ok(project);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete project")
    public ResponseEntity<ProjectDTO> completeProject(@PathVariable String id) {
        ProjectDTO project = projectService.completeProject(id);
        return ResponseEntity.ok(project);
    }

    @PostMapping("/{id}/generate-groups")
    @Operation(summary = "Generate groups", description = "Auto-generate student groups for the project")
    public ResponseEntity<List<ProjectGroupDTO>> generateGroups(
            @PathVariable String id,
            @Valid @RequestBody GenerateGroupsRequest request) {
        List<ProjectGroupDTO> groups = projectService.generateGroups(id, request.getGroupSize());
        return ResponseEntity.status(HttpStatus.CREATED).body(groups);
    }

    @GetMapping("/{id}/groups")
    @Operation(summary = "Get groups")
    public ResponseEntity<List<ProjectGroupDTO>> getGroups(@PathVariable String id) {
        List<ProjectGroupDTO> groups = projectService.getGroups(id);
        return ResponseEntity.ok(groups);
    }

    @PutMapping("/{id}/groups/{groupId}")
    @Operation(summary = "Update group")
    public ResponseEntity<ProjectGroupDTO> updateGroup(
            @PathVariable String id,
            @PathVariable String groupId,
            @RequestBody ProjectGroupDTO updateData) {
        ProjectGroupDTO group = projectService.updateGroup(id, groupId, updateData);
        return ResponseEntity.ok(group);
    }

    @PostMapping("/{id}/groups/{groupId}/submit")
    @Operation(summary = "Submit project for group", description = "Admin submits project on behalf of a group")
    public ResponseEntity<ProjectGroupDTO> submitProjectForGroup(
            @PathVariable String id,
            @PathVariable String groupId,
            @Valid @RequestBody SubmitProjectRequest request) {
        String submittedBy = UserUtil.getCurrentUserId();
        ProjectGroupDTO result = projectService.submitProject(id, groupId, submittedBy, request.getSubmissionUrl(), request.getAttachments());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/assign-statements")
    @Operation(summary = "Assign problem statements", description = "Auto-assign problem statements from question bank to groups")
    public ResponseEntity<Void> assignStatements(@PathVariable String id) {
        projectService.assignStatements(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Get events", description = "Get all events for a project")
    public ResponseEntity<List<ProjectEventDTO>> getEvents(@PathVariable String id) {
        List<ProjectEventDTO> events = projectService.getEvents(id);
        return ResponseEntity.ok(events);
    }

    @PostMapping("/{id}/events")
    @Operation(summary = "Add event")
    public ResponseEntity<ProjectEventDTO> addEvent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String dateTimeStr = (String) body.get("dateTime");
        OffsetDateTime dateTime = dateTimeStr != null ? OffsetDateTime.parse(dateTimeStr) : null;
        String zoomLink = (String) body.get("zoomLink");
        Boolean hasMarks = (Boolean) body.get("hasMarks");
        Integer maxMarks = body.get("maxMarks") != null ? ((Number) body.get("maxMarks")).intValue() : null;
        Integer sequence = body.get("sequence") != null ? ((Number) body.get("sequence")).intValue() : null;
        String sectionId = (String) body.get("sectionId");
        Boolean hasSubmission = (Boolean) body.get("hasSubmission");

        ProjectEventDTO event = projectService.addEvent(id, name, dateTime, zoomLink, hasMarks, maxMarks, sequence, sectionId, hasSubmission);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @PutMapping("/{id}/events/{eventId}")
    @Operation(summary = "Update event")
    public ResponseEntity<ProjectEventDTO> updateEvent(
            @PathVariable String id,
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String dateTimeStr = (String) body.get("dateTime");
        OffsetDateTime dateTime = dateTimeStr != null ? OffsetDateTime.parse(dateTimeStr) : null;
        String zoomLink = (String) body.get("zoomLink");
        Boolean hasMarks = (Boolean) body.get("hasMarks");
        Integer maxMarks = body.get("maxMarks") != null ? ((Number) body.get("maxMarks")).intValue() : null;
        Integer sequence = body.get("sequence") != null ? ((Number) body.get("sequence")).intValue() : null;

        ProjectEventDTO event = projectService.updateEvent(id, eventId, name, dateTime, zoomLink, hasMarks, maxMarks, sequence);
        return ResponseEntity.ok(event);
    }

    @DeleteMapping("/{id}/events/{eventId}")
    @Operation(summary = "Delete event")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable String id,
            @PathVariable String eventId) {
        projectService.deleteEvent(id, eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/events/{eventId}/attendance")
    @Operation(summary = "Get attendance", description = "Get existing attendance records for a project event")
    public ResponseEntity<List<Map<String, Object>>> getAttendance(
            @PathVariable String id, @PathVariable String eventId) {
        return ResponseEntity.ok(projectService.getEventAttendance(id, eventId));
    }

    @GetMapping("/{id}/events/{eventId}/grades")
    @Operation(summary = "Get grades", description = "Get existing grade records for a project event")
    public ResponseEntity<List<Map<String, Object>>> getGrades(
            @PathVariable String id, @PathVariable String eventId) {
        return ResponseEntity.ok(projectService.getEventGrades(id, eventId));
    }

    @PostMapping("/{id}/events/{eventId}/attendance")
    @Operation(summary = "Save attendance", description = "Bulk save attendance for a project event")
    public ResponseEntity<Void> saveAttendance(
            @PathVariable String id,
            @PathVariable String eventId,
            @Valid @RequestBody ProjectBulkAttendanceRequest request) {
        projectService.saveAttendance(id, eventId, request.getEntries());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/events/{eventId}/grades")
    @Operation(summary = "Save grades", description = "Bulk save grades for a project event")
    public ResponseEntity<Void> saveGrades(
            @PathVariable String id,
            @PathVariable String eventId,
            @Valid @RequestBody ProjectBulkGradeRequest request) {
        projectService.saveGrades(id, eventId, request.getEntries());
        return ResponseEntity.ok().build();
    }

    // ======================== Event Submissions (Admin) ========================

    @GetMapping("/{id}/events/{eventId}/submissions")
    @Operation(summary = "Get all event submissions", description = "Get submissions for all groups for an event")
    public ResponseEntity<List<Map<String, Object>>> getEventSubmissions(
            @PathVariable String id, @PathVariable String eventId) {
        return ResponseEntity.ok(projectService.getAllEventSubmissions(id, eventId));
    }

    @GetMapping("/{id}/events/{eventId}/submissions/{groupId}")
    @Operation(summary = "Get group submission", description = "Get latest submission and history for a group")
    public ResponseEntity<Map<String, Object>> getGroupSubmission(
            @PathVariable String id, @PathVariable String eventId, @PathVariable String groupId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latest", projectService.getLatestEventSubmission(id, eventId, groupId));
        result.put("history", projectService.getEventSubmissionHistory(id, eventId, groupId));
        result.put("feedback", projectService.getEventGroupFeedback(eventId, groupId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/events/{eventId}/submissions/{groupId}/feedback")
    @Operation(summary = "Give feedback", description = "Give feedback on a group's submission")
    public ResponseEntity<ProjectEventFeedbackDTO> giveFeedback(
            @PathVariable String id, @PathVariable String eventId, @PathVariable String groupId,
            @Valid @RequestBody EventFeedbackRequest request) {
        // Use the latest submission
        ProjectEventSubmissionDTO latest = projectService.getLatestEventSubmission(id, eventId, groupId);
        if (latest == null) {
            throw new IllegalStateException("No submission found for this group");
        }
        ProjectEventFeedbackDTO feedback = projectService.giveFeedback(id, eventId, groupId, latest.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback);
    }

    @PostMapping("/{id}/advance-phase")
    @Operation(summary = "Advance phase", description = "Advance project to next event phase")
    public ResponseEntity<ProjectDTO> advancePhase(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        String nextEventId = body.get("nextEventId");
        ProjectDTO project = projectService.advancePhase(id, nextEventId);
        return ResponseEntity.ok(project);
    }

    // ======================== Attachments ========================

    @GetMapping("/{id}/attachments")
    @Operation(summary = "Get attachments", description = "Get attachments for a project, optionally filtered by context (STATEMENT or SUBMISSION)")
    public ResponseEntity<List<ProjectAttachmentDTO>> getAttachments(
            @PathVariable String id,
            @RequestParam(required = false) String context) {
        List<ProjectAttachmentDTO> attachments = projectService.getAttachments(id, context);
        return ResponseEntity.ok(attachments);
    }

    @PostMapping("/{id}/attachments")
    @Operation(summary = "Add attachment", description = "Add an attachment to a project (statement) or group (submission)")
    public ResponseEntity<ProjectAttachmentDTO> addAttachment(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String groupId = (String) body.get("groupId");
        String context = (String) body.getOrDefault("context", "STATEMENT");

        SubmitProjectRequest.AttachmentInfo info = new SubmitProjectRequest.AttachmentInfo();
        info.setFileUrl((String) body.get("fileUrl"));
        info.setFileName((String) body.get("fileName"));
        if (body.get("fileSizeBytes") != null) {
            info.setFileSizeBytes(((Number) body.get("fileSizeBytes")).longValue());
        }
        info.setMimeType((String) body.get("mimeType"));

        ProjectAttachmentDTO attachment = projectService.addAttachment(id, groupId, context, info);
        return ResponseEntity.status(HttpStatus.CREATED).body(attachment);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Delete attachment")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable String id,
            @PathVariable String attachmentId) {
        projectService.deleteAttachment(id, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
