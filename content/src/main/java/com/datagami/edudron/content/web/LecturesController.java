package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.service.LectureService;
import com.datagami.edudron.content.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/content/courses")
@Tag(name = "Lectures", description = "Course lecture (module) management endpoints")
public class LecturesController {

    @Autowired
    private SectionService sectionService;
    
    @Autowired
    private LectureService lectureService;

    @GetMapping("/{courseId}/lectures")
    @Operation(summary = "List lectures", description = "Get all lectures (modules) for a course")
    public ResponseEntity<List<SectionDTO>> getLectures(@PathVariable String courseId) {
        List<SectionDTO> sections = sectionService.getSectionsByCourse(courseId);
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/{courseId}/lectures/{id}")
    @Operation(summary = "Get lecture", description = "Get lecture (module) details by ID")
    public ResponseEntity<SectionDTO> getLecture(@PathVariable String courseId, @PathVariable String id) {
        SectionDTO section = sectionService.getSectionById(id);
        return ResponseEntity.ok(section);
    }

    @PostMapping("/{courseId}/lectures")
    @Operation(summary = "Create lecture", description = "Create a new lecture (module) for a course")
    public ResponseEntity<SectionDTO> createLecture(
            @PathVariable String courseId,
            @RequestBody Map<String, String> request) {
        SectionDTO section = sectionService.createSection(
            courseId,
            request.get("title"),
            request.get("description")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(section);
    }

    @PutMapping("/{courseId}/lectures/{id}")
    @Operation(summary = "Update lecture", description = "Update an existing lecture (module)")
    public ResponseEntity<SectionDTO> updateLecture(
            @PathVariable String courseId,
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        SectionDTO section = sectionService.updateSection(
            id,
            request.get("title"),
            request.get("description")
        );
        return ResponseEntity.ok(section);
    }

    @DeleteMapping("/{courseId}/lectures/{id}")
    @Operation(summary = "Delete lecture", description = "Delete a lecture (module)")
    public ResponseEntity<Void> deleteLecture(@PathVariable String courseId, @PathVariable String id) {
        sectionService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }

    // Sub-lectures (lessons within a lecture/module)
    @GetMapping("/{courseId}/lectures/{lectureId}/sub-lectures")
    @Operation(summary = "List sub-lectures", description = "Get all sub-lectures (lessons) for a lecture (module)")
    public ResponseEntity<List<LectureDTO>> getSubLectures(
            @PathVariable String courseId,
            @PathVariable String lectureId) {
        List<LectureDTO> lectures = lectureService.getLecturesBySection(lectureId);
        return ResponseEntity.ok(lectures);
    }
}

