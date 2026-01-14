package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.service.CourseGenerationService;
import com.datagami.edudron.content.service.LectureService;
import com.datagami.edudron.content.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/content/courses")
@Tag(name = "Lectures", description = "Course lecture (module) management endpoints")
public class LecturesController {
    
    private static final Logger logger = LoggerFactory.getLogger(LecturesController.class);
    private final Tika tika = new Tika();

    @Autowired
    private SectionService sectionService;
    
    @Autowired
    private LectureService lectureService;
    
    @Autowired
    private CourseGenerationService courseGenerationService;
    
    @Autowired
    private com.datagami.edudron.content.service.AIJobQueueService aiJobQueueService;
    
    @Autowired
    private com.datagami.edudron.content.service.AIJobWorker aiJobWorker;

    @GetMapping("/{courseId}/lectures")
    @Operation(summary = "List lectures", description = "Get all lectures (modules) for a course")
    public ResponseEntity<List<SectionDTO>> getLectures(@PathVariable String courseId) {
        List<SectionDTO> sections = sectionService.getSectionsByCourse(courseId);
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/{courseId}/lectures/{id}")
    @Operation(summary = "Get lecture", description = "Get lecture (module or sub-lecture) details by ID")
    public ResponseEntity<?> getLecture(@PathVariable String courseId, @PathVariable String id) {
        // First try to get it as a section (main lecture/module)
        try {
            SectionDTO section = sectionService.getSectionById(id);
            // Verify it belongs to this course
            if (section.getCourseId() != null && section.getCourseId().equals(courseId)) {
                return ResponseEntity.ok(section);
            } else {
                // Section exists but doesn't belong to this course
                throw new IllegalArgumentException("Section not found in course: " + id);
            }
        } catch (IllegalArgumentException e) {
            // Not a section or doesn't belong to course, try as a sub-lecture
            try {
                LectureDTO lecture = lectureService.getLectureById(id);
                // Verify it belongs to this course
                if (lecture.getCourseId() != null && lecture.getCourseId().equals(courseId)) {
                    return ResponseEntity.ok(lecture);
                } else {
                    throw new IllegalArgumentException("Lecture not found in course: " + id);
                }
            } catch (IllegalArgumentException le) {
                // Neither section nor lecture found, or doesn't belong to course
                throw new IllegalArgumentException("Lecture not found: " + id);
            }
        }
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

    // Multipart endpoint for PDF support
    @PostMapping(value = "/{courseId}/lectures/generate", consumes = "multipart/form-data")
    @Operation(summary = "Generate lecture with AI (with PDF)", description = "Submit a lecture generation job to the queue with optional PDF file. Returns a job ID that can be used to check status.")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateLectureWithAIMultipart(
            @PathVariable String courseId,
            @RequestPart(required = false) String prompt,
            @RequestPart(required = false) MultipartFile pdfFile) {
        
        String finalPrompt = prompt;
        
        // Extract text from PDF file if provided
        if (pdfFile != null && !pdfFile.isEmpty()) {
            try {
                String pdfText = extractTextFromFile(pdfFile);
                if (pdfText != null && !pdfText.trim().isEmpty()) {
                    // Combine PDF content with prompt
                    finalPrompt = buildPromptWithPdf(finalPrompt != null ? finalPrompt : "", pdfText);
                    logger.info("Extracted {} characters from PDF and combined with prompt", pdfText.length());
                }
            } catch (Exception e) {
                logger.warn("Failed to extract text from PDF file: {}", e.getMessage());
                // Continue with original prompt if PDF extraction fails
            }
        }
        
        if (finalPrompt == null || finalPrompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, String> jobRequest = new java.util.HashMap<>();
        jobRequest.put("courseId", courseId);
        jobRequest.put("prompt", finalPrompt);
        
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitLectureGenerationJob(jobRequest, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    // JSON endpoint for backward compatibility
    @PostMapping(value = "/{courseId}/lectures/generate", consumes = "application/json")
    @Operation(summary = "Generate lecture with AI (JSON)", description = "Submit a lecture generation job to the queue. Returns a job ID that can be used to check status.")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateLectureWithAI(
            @PathVariable String courseId,
            @RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, String> jobRequest = new java.util.HashMap<>();
        jobRequest.put("courseId", courseId);
        jobRequest.put("prompt", prompt);
        
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitLectureGenerationJob(jobRequest, aiJobWorker);
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
            return "Generate a lecture (module) with sub-lectures by following the structure from the reference document below. " +
                   "IMPORTANT: Use the EXACT titles and structure specified in the document if available.\n\n" +
                   "Reference Document:\n" + pdfText;
        } else {
            // Combine user prompt with PDF content
            return "Reference Document:\n" + pdfText + 
                   "\n\nUser Instructions:\n" + userPrompt;
        }
    }
    
    @GetMapping("/{courseId}/lectures/generate/jobs/{jobId}")
    @Operation(summary = "Get lecture generation job status", description = "Get the status of a lecture generation job")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> getLectureGenerationJobStatus(
            @PathVariable String courseId,
            @PathVariable String jobId) {
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}

