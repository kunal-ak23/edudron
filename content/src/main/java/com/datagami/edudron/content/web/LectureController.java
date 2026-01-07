package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.service.CourseGenerationService;
import com.datagami.edudron.content.service.LectureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/content/api")
@Tag(name = "Lectures", description = "Lecture (Lesson) management endpoints")
public class LectureController {

    @Autowired
    private LectureService lectureService;
    
    @Autowired
    private CourseGenerationService courseGenerationService;
    
    @Autowired
    private com.datagami.edudron.content.service.AIJobQueueService aiJobQueueService;
    
    @Autowired
    private com.datagami.edudron.content.service.AIJobWorker aiJobWorker;

    @GetMapping("/sections/{sectionId}/lectures")
    @Operation(summary = "List lectures", description = "Get all lectures for a section")
    public ResponseEntity<List<LectureDTO>> getLectures(@PathVariable String sectionId) {
        List<LectureDTO> lectures = lectureService.getLecturesBySection(sectionId);
        return ResponseEntity.ok(lectures);
    }

    @GetMapping("/lectures/{id}")
    @Operation(summary = "Get lecture", description = "Get lecture details by ID")
    public ResponseEntity<LectureDTO> getLecture(@PathVariable String id) {
        LectureDTO lecture = lectureService.getLectureById(id);
        return ResponseEntity.ok(lecture);
    }

    @PostMapping("/sections/{sectionId}/lectures")
    @Operation(summary = "Create lecture", description = "Create a new lecture for a section")
    public ResponseEntity<LectureDTO> createLecture(
            @PathVariable String sectionId,
            @RequestBody Map<String, Object> request) {
        LectureDTO lecture = lectureService.createLecture(
            sectionId,
            (String) request.get("title"),
            (String) request.get("description"),
            Lecture.ContentType.valueOf((String) request.get("contentType"))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(lecture);
    }

    @PutMapping("/lectures/{id}")
    @Operation(summary = "Update lecture", description = "Update an existing lecture")
    public ResponseEntity<LectureDTO> updateLecture(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        Lecture.ContentType contentType = null;
        if (request.get("contentType") != null) {
            try {
                contentType = Lecture.ContentType.valueOf((String) request.get("contentType"));
            } catch (IllegalArgumentException e) {
                // Invalid contentType, will be ignored
            }
        }
        
        LectureDTO lecture = lectureService.updateLecture(
            id,
            (String) request.get("title"),
            (String) request.get("description"),
            request.get("durationSeconds") != null ? (Integer) request.get("durationSeconds") : null,
            request.get("isPreview") != null ? (Boolean) request.get("isPreview") : null,
            contentType,
            request.get("isPublished") != null ? (Boolean) request.get("isPublished") : null
        );
        return ResponseEntity.ok(lecture);
    }

    @DeleteMapping("/lectures/{id}")
    @Operation(summary = "Delete lecture", description = "Delete a lecture")
    public ResponseEntity<Void> deleteLecture(@PathVariable String id) {
        lectureService.deleteLecture(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{sectionId}/lectures/generate")
    @Operation(summary = "Generate sub-lecture with AI", description = "Submit a sub-lecture generation job to the queue. Returns a job ID that can be used to check status.")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateSubLectureWithAI(
            @PathVariable String sectionId,
            @RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        String courseId = request.get("courseId");
        if (prompt == null || prompt.trim().isEmpty() || courseId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, String> jobRequest = new java.util.HashMap<>();
        jobRequest.put("courseId", courseId);
        jobRequest.put("sectionId", sectionId);
        jobRequest.put("prompt", prompt);
        
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitSubLectureGenerationJob(jobRequest, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    @GetMapping("/sections/{sectionId}/lectures/generate/jobs/{jobId}")
    @Operation(summary = "Get sub-lecture generation job status", description = "Get the status of a sub-lecture generation job")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> getSubLectureGenerationJobStatus(
            @PathVariable String sectionId,
            @PathVariable String jobId) {
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}


