package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.CreateCourseRequest;
import com.datagami.edudron.content.dto.GenerateCourseRequest;
import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.service.CourseGenerationService;
import com.datagami.edudron.content.service.CourseService;
import com.datagami.edudron.content.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/content/courses")
@Tag(name = "Courses", description = "Course management endpoints")
public class CourseController {

    @Autowired
    private CourseService courseService;
    
    @Autowired
    private CourseGenerationService courseGenerationService;
    
    @Autowired
    private SectionService sectionService;

    @GetMapping
    @Operation(summary = "List courses", description = "Get paginated list of courses with optional filters")
    public ResponseEntity<Page<CourseDTO>> getCourses(
            @RequestParam(required = false) Boolean isPublished,
            Pageable pageable) {
        Page<CourseDTO> courses = courseService.getCourses(isPublished, pageable);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/search")
    @Operation(summary = "Search courses", description = "Search courses with filters")
    public ResponseEntity<Page<CourseDTO>> searchCourses(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String difficultyLevel,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Boolean isFree,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String searchTerm,
            Pageable pageable) {
        Page<CourseDTO> courses = courseService.searchCourses(
            categoryId, difficultyLevel, language, isFree, isPublished, searchTerm, pageable
        );
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get course", description = "Get course details by ID")
    public ResponseEntity<CourseDTO> getCourse(@PathVariable String id) {
        CourseDTO course = courseService.getCourseById(id);
        return ResponseEntity.ok(course);
    }

    @PostMapping
    @Operation(summary = "Create course", description = "Create a new course")
    public ResponseEntity<CourseDTO> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        CourseDTO course = courseService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(course);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update course", description = "Update an existing course")
    public ResponseEntity<CourseDTO> updateCourse(
            @PathVariable String id,
            @Valid @RequestBody CreateCourseRequest request) {
        CourseDTO course = courseService.updateCourse(id, request);
        return ResponseEntity.ok(course);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete course", description = "Delete a course")
    public ResponseEntity<Void> deleteCourse(@PathVariable String id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish course", description = "Publish a course and calculate statistics")
    public ResponseEntity<CourseDTO> publishCourse(@PathVariable String id) {
        CourseDTO course = courseService.publishCourse(id);
        return ResponseEntity.ok(course);
    }

    @Autowired
    private com.datagami.edudron.content.service.AIJobQueueService aiJobQueueService;
    
    @Autowired
    private com.datagami.edudron.content.service.AIJobWorker aiJobWorker;
    
    @PostMapping("/generate")
    @Operation(
        summary = "Generate course from prompt",
        description = "Submit a course generation job to the queue. Returns a job ID that can be used to check status."
    )
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateCourse(@Valid @RequestBody GenerateCourseRequest request) {
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitCourseGenerationJob(request, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    @GetMapping("/generate/jobs/{jobId}")
    @Operation(
        summary = "Get course generation job status",
        description = "Get the status of a course generation job"
    )
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> getCourseGenerationJobStatus(@PathVariable String jobId) {
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }

    // Backward compatibility: redirect /sections to /lectures
    @GetMapping("/{id}/sections")
    @Operation(summary = "Get course sections (deprecated)", description = "Deprecated: Use /lectures instead. Get all sections (modules) for a course")
    public ResponseEntity<List<SectionDTO>> getCourseSections(@PathVariable String id) {
        List<SectionDTO> sections = sectionService.getSectionsByCourse(id);
        return ResponseEntity.ok(sections);
    }
}

