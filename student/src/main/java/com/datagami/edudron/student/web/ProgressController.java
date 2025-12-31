package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CourseProgressDTO;
import com.datagami.edudron.student.dto.ProgressDTO;
import com.datagami.edudron.student.dto.UpdateProgressRequest;
import com.datagami.edudron.student.service.ProgressService;
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
@Tag(name = "Progress", description = "Student progress tracking endpoints")
public class ProgressController {

    @Autowired
    private ProgressService progressService;

    @PutMapping("/courses/{courseId}/progress")
    @Operation(summary = "Update progress", description = "Update student progress for a lecture or section")
    public ResponseEntity<ProgressDTO> updateProgress(
            @PathVariable String courseId,
            @Valid @RequestBody UpdateProgressRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        ProgressDTO progress = progressService.updateProgress(studentId, courseId, request);
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/courses/{courseId}/progress")
    @Operation(summary = "Get course progress", description = "Get overall progress for a course")
    public ResponseEntity<CourseProgressDTO> getCourseProgress(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        CourseProgressDTO progress = progressService.getCourseProgress(studentId, courseId);
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/courses/{courseId}/lectures/progress")
    @Operation(summary = "Get lecture progress", description = "Get progress for all lectures in a course")
    public ResponseEntity<List<ProgressDTO>> getLectureProgress(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        List<ProgressDTO> progress = progressService.getLectureProgress(studentId, courseId);
        return ResponseEntity.ok(progress);
    }
}

