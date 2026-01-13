package com.datagami.edudron.content.web;

import com.datagami.edudron.content.constants.MediaFolderConstants;
import com.datagami.edudron.content.service.MediaUploadService;
import com.datagami.edudron.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/content/media")
@Tag(name = "Media Upload", description = "Media upload operations for courses")
public class MediaUploadController {

    @Autowired
    private MediaUploadService mediaUploadService;

    @PostMapping("/upload/image")
    @Operation(summary = "Upload image", description = "Upload an image file (thumbnail, course image, etc.) and return the public URL")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = MediaFolderConstants.THUMBNAILS) String folder) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "B,C", "location", "MediaUploadController.java:27", "message", "Upload image endpoint called", "data", java.util.Map.of("folder", folder, "fileName", file.getOriginalFilename(), "fileSize", file.getSize()), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        try {
            // Get tenant ID from context
            String tenantId = TenantContext.getClientId();
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "B", "location", "MediaUploadController.java:34", "message", "Tenant context retrieved", "data", java.util.Map.of("tenantId", tenantId != null ? tenantId : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            String imageUrl = mediaUploadService.uploadImage(file, folder, tenantId);
            
            Map<String, String> response = new HashMap<>();
            response.put("url", imageUrl);
            response.put("message", "Image uploaded successfully");
            response.put("tenantId", tenantId);
            response.put("folder", folder);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "B,C", "location", "MediaUploadController.java:43", "message", "Upload failed - IllegalArgumentException", "data", java.util.Map.of("error", e.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (IOException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "B,C", "location", "MediaUploadController.java:48", "message", "Upload failed - IOException", "data", java.util.Map.of("error", e.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload image: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        } catch (IllegalStateException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "D", "location", "MediaUploadController.java:53", "message", "Upload failed - IllegalStateException", "data", java.util.Map.of("error", e.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(503).body(errorResponse); // Service Unavailable
        }
    }

    @PostMapping("/upload/video")
    @Operation(summary = "Upload video", description = "Upload a video file (preview video, lecture video, etc.) and return the public URL")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = MediaFolderConstants.PREVIEW_VIDEOS) String folder) {
        
        try {
            // Get tenant ID from context
            String tenantId = TenantContext.getClientId();
            String videoUrl = mediaUploadService.uploadVideo(file, folder, tenantId);
            
            Map<String, String> response = new HashMap<>();
            response.put("url", videoUrl);
            response.put("message", "Video uploaded successfully");
            response.put("tenantId", tenantId);
            response.put("folder", folder);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (IOException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload video: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        } catch (IllegalStateException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(503).body(errorResponse); // Service Unavailable
        }
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete media", description = "Delete a media file by URL")
    public ResponseEntity<Map<String, String>> deleteMedia(@RequestParam("url") String mediaUrl) {
        try {
            mediaUploadService.deleteMedia(mediaUrl);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Media deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to delete media: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}

