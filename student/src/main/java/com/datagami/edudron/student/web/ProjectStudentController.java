package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.ProjectDTO;
import com.datagami.edudron.student.dto.ProjectGroupDTO;
import com.datagami.edudron.student.dto.SubmitProjectRequest;
import com.datagami.edudron.student.service.ProjectService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
        ProjectGroupDTO result = projectService.submitProject(id, myGroup.getId(), studentId, request.getSubmissionUrl());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/my-attendance")
    @Operation(summary = "Get my attendance", description = "Get attendance and grades for the current student in a project")
    public ResponseEntity<Map<String, Object>> getMyAttendance(@PathVariable String id) {
        String studentId = UserUtil.getCurrentUserId();
        Map<String, Object> result = projectService.getMyAttendance(id, studentId);
        return ResponseEntity.ok(result);
    }
}
