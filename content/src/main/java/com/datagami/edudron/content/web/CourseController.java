package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.CreateCourseRequest;
import com.datagami.edudron.content.dto.GenerateCourseRequest;
import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.dto.CourseCopyRequest;
import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.datagami.edudron.content.service.CourseService;
import com.datagami.edudron.content.service.SectionService;
import com.datagami.edudron.content.service.CourseCopyWorker;
import com.datagami.edudron.content.service.AIJobQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/content/courses")
@Tag(name = "Courses", description = "Course management endpoints")
public class CourseController {
    
    private static final Logger logger = LoggerFactory.getLogger(CourseController.class);
    private final Tika tika = new Tika();

    @Autowired
    private CourseService courseService;
    
    @Autowired
    private SectionService sectionService;
    
    @Autowired
    private CourseCopyWorker courseCopyWorker;
    
    @Autowired
    private AIJobQueueService queueService;

    @GetMapping
    @Operation(summary = "List courses", description = "Get paginated list of courses with optional filters")
    public ResponseEntity<Page<CourseDTO>> getCourses(
            @RequestParam(required = false) Boolean isPublished,
            Pageable pageable) {
        Page<CourseDTO> courses = courseService.getCourses(isPublished, pageable);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/by-assignments")
    @Operation(summary = "Get courses by class/section assignments", 
               description = "Get courses assigned to any of the specified classes or sections")
    public ResponseEntity<List<CourseDTO>> getCoursesByAssignments(
            @RequestParam(required = false) List<String> classIds,
            @RequestParam(required = false) List<String> sectionIds) {
        List<CourseDTO> courses = courseService.getCoursesByAssignments(classIds, sectionIds);
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

    @PostMapping("/{id}/unpublish")
    @Operation(summary = "Unpublish course", description = "Unpublish a course (set isPublished=false)")
    public ResponseEntity<CourseDTO> unpublishCourse(@PathVariable String id) {
        CourseDTO course = courseService.unpublishCourse(id);
        return ResponseEntity.ok(course);
    }

    @Autowired
    private com.datagami.edudron.content.service.AIJobQueueService aiJobQueueService;
    
    @Autowired
    private com.datagami.edudron.content.service.AIJobWorker aiJobWorker;
    
    @PostMapping(value = "/generate", consumes = {"application/json"})
    @Operation(
        summary = "Generate course from prompt (JSON)",
        description = "Submit a course generation job to the queue using JSON. Returns a job ID that can be used to check status. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features."
    )
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateCourseJson(@Valid @RequestBody GenerateCourseRequest request) {
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = courseService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitCourseGenerationJob(request, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    @PostMapping(value = "/generate", consumes = {"multipart/form-data"})
    @Operation(
        summary = "Generate course from prompt with PDF (Multipart)",
        description = "Submit a course generation job to the queue with optional PDF file. Returns a job ID that can be used to check status. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features."
    )
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateCourseMultipart(
            @RequestPart(required = false) String prompt,
            @RequestPart(required = false) String categoryId,
            @RequestPart(required = false) String difficultyLevel,
            @RequestPart(required = false) String language,
            @RequestPart(required = false) String tags,
            @RequestPart(required = false) String certificateEligible,
            @RequestPart(required = false) String maxCompletionDays,
            @RequestPart(required = false) String referenceIndexIds,
            @RequestPart(required = false) String writingFormatId,
            @RequestPart(required = false) String writingFormat,
            @RequestPart(required = false) MultipartFile pdfFile) {
        
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = courseService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        GenerateCourseRequest request = new GenerateCourseRequest();
        request.setPrompt(prompt != null ? prompt : "");
        
        if (categoryId != null && !categoryId.isEmpty()) {
            request.setCategoryId(categoryId);
        }
        if (difficultyLevel != null && !difficultyLevel.isEmpty()) {
            request.setDifficultyLevel(difficultyLevel);
        }
        if (language != null && !language.isEmpty()) {
            request.setLanguage(language);
        }
        if (tags != null && !tags.isEmpty()) {
            request.setTags(java.util.Arrays.asList(tags.split(",")));
        }
        if (certificateEligible != null && !certificateEligible.isEmpty()) {
            request.setCertificateEligible(Boolean.parseBoolean(certificateEligible));
        }
        if (maxCompletionDays != null && !maxCompletionDays.isEmpty()) {
            try {
                request.setMaxCompletionDays(Integer.parseInt(maxCompletionDays));
            } catch (NumberFormatException e) {
                // Ignore invalid number
            }
        }
        if (referenceIndexIds != null && !referenceIndexIds.isEmpty()) {
            request.setReferenceIndexIds(java.util.Arrays.asList(referenceIndexIds.split(",")));
        }
        if (writingFormatId != null && !writingFormatId.isEmpty()) {
            request.setWritingFormatId(writingFormatId);
        }
        if (writingFormat != null && !writingFormat.isEmpty()) {
            request.setWritingFormat(writingFormat);
        }
        
        // Extract text from PDF file if provided
        if (pdfFile != null && !pdfFile.isEmpty()) {
            try {
                String pdfText = extractTextFromFile(pdfFile);
                if (pdfText != null && !pdfText.trim().isEmpty()) {
                    // Combine PDF content with prompt
                    String combinedPrompt = buildPromptWithPdf(prompt != null ? prompt : "", pdfText);
                    request.setPrompt(combinedPrompt);
                    logger.info("Extracted {} characters from PDF and combined with prompt", pdfText.length());
                }
            } catch (Exception e) {
                logger.warn("Failed to extract text from PDF file: {}", e.getMessage());
                // Continue with original prompt if PDF extraction fails
            }
        }
        
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitCourseGenerationJob(request, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    /**
     * Extract text from a file using Apache Tika
     */
    private String extractTextFromFile(MultipartFile file) throws IOException {
        try {
            String extractedText = tika.parseToString(file.getInputStream());
            // Limit extracted text to reasonable size (100K characters)
            if (extractedText.length() > 100000) {
                extractedText = extractedText.substring(0, 100000) + "... [truncated]";
            }
            return extractedText;
        } catch (TikaException e) {
            logger.warn("Failed to extract text from file: {}", file.getOriginalFilename(), e);
            throw new IOException("Failed to extract text from file", e);
        }
    }
    
    /**
     * Build a combined prompt that includes PDF content
     */
    private String buildPromptWithPdf(String userPrompt, String pdfText) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            // If no user prompt, use PDF content as the main prompt with clear instructions
            return "Generate a course by following the EXACT structure from the course structure document below. " +
                   "IMPORTANT: Use the EXACT module titles, lesson titles, and time durations specified in the document. " +
                   "Convert all time durations (hours) to seconds (1 hour = 3600 seconds). " +
                   "Ensure every lesson has a time duration attached.\n\n" +
                   "Course Structure Document:\n" + pdfText;
        } else {
            // Combine user prompt with PDF content, emphasizing structure following
            return "Course Structure Document (FOLLOW THIS EXACT STRUCTURE):\n" + pdfText + 
                   "\n\nIMPORTANT INSTRUCTIONS:\n" +
                   "- Use the EXACT module titles and lesson titles from the document above\n" +
                   "- Extract time durations (in hours) from the document and convert to seconds (1 hour = 3600 seconds)\n" +
                   "- Ensure EVERY lesson has a time duration attached\n" +
                   "- Follow the structure and organization exactly as shown\n\n" +
                   "User Instructions:\n" + userPrompt;
        }
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
    
    @GetMapping("/section/{sectionId}")
    @Operation(summary = "Get published courses by section", description = "Get all published courses assigned to a specific section. Used for automatic enrollment.")
    public ResponseEntity<List<CourseDTO>> getPublishedCoursesBySection(@PathVariable String sectionId) {
        List<CourseDTO> courses = courseService.getPublishedCoursesBySectionId(sectionId);
        return ResponseEntity.ok(courses);
    }
    
    @GetMapping("/class/{classId}")
    @Operation(summary = "Get published courses by class", description = "Get all published courses assigned to a specific class. Used for automatic enrollment.")
    public ResponseEntity<List<CourseDTO>> getPublishedCoursesByClass(@PathVariable String classId) {
        List<CourseDTO> courses = courseService.getPublishedCoursesByClassId(classId);
        return ResponseEntity.ok(courses);
    }
    
    /**
     * Submit course copy job (SYSTEM_ADMIN only)
     * Returns immediately with job ID for async processing
     */
    @PostMapping("/{courseId}/copy-to-tenant")
    @Operation(summary = "Copy course to another tenant (SYSTEM_ADMIN only)", 
               description = "Submit an async job to copy a course to another tenant. Returns immediately with a job ID. " +
                            "Only SYSTEM_ADMIN users can perform this operation. The copy includes all course structure, " +
                            "content, assessments, and media assets.")
    public ResponseEntity<AIGenerationJobDTO> copyCourseToTenant(
            @PathVariable String courseId,
            @Valid @RequestBody CourseCopyRequest request) {
        
        logger.info("Received course copy request for course {} to tenant {}", courseId, request.getTargetClientId());
        AIGenerationJobDTO job = courseCopyWorker.submitCourseCopyJob(courseId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    /**
     * Get course copy job status (polling endpoint)
     */
    @GetMapping("/copy-jobs/{jobId}")
    @Operation(summary = "Get course copy job status", 
               description = "Poll this endpoint to track the progress of a course copy operation. " +
                            "The job status includes progress percentage (0-100) and current step description.")
    public ResponseEntity<AIGenerationJobDTO> getCourseCopyJobStatus(@PathVariable String jobId) {
        AIGenerationJobDTO job = queueService.getJob(jobId);
        if (job == null) {
            logger.warn("Course copy job {} not found", jobId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}

