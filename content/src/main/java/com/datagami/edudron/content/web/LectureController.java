package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.service.CourseGenerationService;
import com.datagami.edudron.content.service.LectureService;
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
@RequestMapping("/content/api")
@Tag(name = "Lectures", description = "Lecture (Lesson) management endpoints")
public class LectureController {
    
    private static final Logger logger = LoggerFactory.getLogger(LectureController.class);
    private final Tika tika = new Tika();

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

    // Multipart endpoint for PDF support
    @PostMapping(value = "/sections/{sectionId}/lectures/generate", consumes = "multipart/form-data")
    @Operation(summary = "Generate sub-lecture with AI (with PDF)", description = "Submit a sub-lecture generation job to the queue with optional PDF file. Returns a job ID that can be used to check status. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features.")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateSubLectureWithAIMultipart(
            @PathVariable String sectionId,
            @RequestPart(required = false) String prompt,
            @RequestPart(required = false) String courseId,
            @RequestPart(required = false) MultipartFile pdfFile) {
        
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = lectureService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
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
        
        if (finalPrompt == null || finalPrompt.trim().isEmpty() || courseId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, String> jobRequest = new java.util.HashMap<>();
        jobRequest.put("courseId", courseId);
        jobRequest.put("sectionId", sectionId);
        jobRequest.put("prompt", finalPrompt);
        
        com.datagami.edudron.content.dto.AIGenerationJobDTO job = aiJobQueueService.submitSubLectureGenerationJob(jobRequest, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }
    
    // JSON endpoint for backward compatibility
    @PostMapping(value = "/sections/{sectionId}/lectures/generate", consumes = "application/json")
    @Operation(summary = "Generate sub-lecture with AI (JSON)", description = "Submit a sub-lecture generation job to the queue. Returns a job ID that can be used to check status. Only SYSTEM_ADMIN and TENANT_ADMIN can use AI generation features.")
    public ResponseEntity<com.datagami.edudron.content.dto.AIGenerationJobDTO> generateSubLectureWithAI(
            @PathVariable String sectionId,
            @RequestBody Map<String, String> request) {
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = lectureService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
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
            return "Generate a sub-lecture by following the structure from the reference document below. " +
                   "IMPORTANT: Use the EXACT titles and structure specified in the document if available.\n\n" +
                   "Reference Document:\n" + pdfText;
        } else {
            // Combine user prompt with PDF content
            return "Reference Document:\n" + pdfText + 
                   "\n\nUser Instructions:\n" + userPrompt;
        }
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


