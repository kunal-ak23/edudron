package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.AIGenerationJobDTO;
import com.datagami.edudron.content.service.AIJobQueueService;
import com.datagami.edudron.content.service.AIJobWorker;
import com.datagami.edudron.content.service.ImageGenerationService;
import com.datagami.edudron.content.service.LectureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/content/api")
@Tag(name = "Image Generation", description = "AI-powered image generation endpoints")
public class ImageGenerationController {

    private static final Logger logger = LoggerFactory.getLogger(ImageGenerationController.class);

    @Autowired
    private ImageGenerationService imageGenerationService;

    @Autowired
    private LectureService lectureService;

    @Autowired
    private AIJobQueueService aiJobQueueService;

    @Autowired
    private AIJobWorker aiJobWorker;

    /**
     * Generate a single image synchronously from a prompt.
     */
    @PostMapping("/images/generate")
    @Operation(summary = "Generate image", description = "Generate a single image from a text prompt using FLUX.2-pro. Only SYSTEM_ADMIN and TENANT_ADMIN.")
    public ResponseEntity<?> generateImage(@RequestBody Map<String, Object> request) {
        String userRole = lectureService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only SYSTEM_ADMIN and TENANT_ADMIN can generate images"));
        }

        if (!imageGenerationService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Image generation service is not configured"));
        }

        String prompt = (String) request.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt is required"));
        }

        int width = request.get("width") != null ? ((Number) request.get("width")).intValue() : 1024;
        int height = request.get("height") != null ? ((Number) request.get("height")).intValue() : 768;

        try {
            String url = imageGenerationService.generateAndUploadImage(prompt.trim(), width, height);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            logger.error("Image generation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Image generation failed: " + e.getMessage()));
        }
    }

    /**
     * Generate images for a lecture asynchronously.
     */
    @PostMapping("/lectures/{lectureId}/generate-images")
    @Operation(summary = "Generate lecture images", description = "Generate images for a lecture using AI. Async via job queue.")
    public ResponseEntity<?> generateLectureImages(
            @PathVariable String lectureId,
            @RequestBody(required = false) Map<String, Object> request) {

        String userRole = lectureService.getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only SYSTEM_ADMIN and TENANT_ADMIN can generate images"));
        }

        if (!imageGenerationService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Image generation service is not configured"));
        }

        int count = 2; // default
        if (request != null && request.get("count") != null) {
            count = ((Number) request.get("count")).intValue();
            count = Math.max(1, Math.min(count, 5)); // clamp 1-5
        }

        Map<String, String> jobRequest = new java.util.HashMap<>();
        jobRequest.put("lectureId", lectureId);
        jobRequest.put("count", String.valueOf(count));

        AIGenerationJobDTO job = aiJobQueueService.submitImageGenerationJob(jobRequest, aiJobWorker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * Get image generation job status.
     */
    @GetMapping("/images/generate/jobs/{jobId}")
    @Operation(summary = "Get image generation job status")
    public ResponseEntity<AIGenerationJobDTO> getJobStatus(@PathVariable String jobId) {
        AIGenerationJobDTO job = aiJobQueueService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}
