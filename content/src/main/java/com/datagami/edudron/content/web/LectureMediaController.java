package com.datagami.edudron.content.web;

import com.datagami.edudron.content.dto.LectureContentDTO;
import com.datagami.edudron.content.service.LectureMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/content/api/lectures")
@Tag(name = "Lecture Media", description = "Lecture media upload and management endpoints")
public class LectureMediaController {

    private static final Logger logger = LoggerFactory.getLogger(LectureMediaController.class);

    @Autowired
    private LectureMediaService lectureMediaService;

    @PostMapping("/{lectureId}/media/video")
    @Operation(summary = "Upload video", description = "Upload a single video file for a lecture (replaces existing video)")
    public ResponseEntity<LectureContentDTO> uploadVideo(
            @PathVariable String lectureId,
            @RequestParam("file") MultipartFile file) {
        try {
            LectureContentDTO content = lectureMediaService.uploadVideoOrAudio(lectureId, file, true);
            return ResponseEntity.status(HttpStatus.CREATED).body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{lectureId}/media/audio")
    @Operation(summary = "Upload audio", description = "Upload a single audio file for a lecture (replaces existing audio)")
    public ResponseEntity<LectureContentDTO> uploadAudio(
            @PathVariable String lectureId,
            @RequestParam("file") MultipartFile file) {
        try {
            LectureContentDTO content = lectureMediaService.uploadVideoOrAudio(lectureId, file, false);
            return ResponseEntity.status(HttpStatus.CREATED).body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{lectureId}/media/document")
    @Operation(summary = "Upload document", description = "Upload a single document file for a lecture (replaces existing document)")
    public ResponseEntity<LectureContentDTO> uploadDocument(
            @PathVariable String lectureId,
            @RequestParam("file") MultipartFile file) {
        try {
            LectureContentDTO content = lectureMediaService.uploadDocument(lectureId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/{lectureId}/media/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Upload attachments", description = "Upload multiple attachment files for a lecture")
    public ResponseEntity<?> uploadAttachments(
            @PathVariable String lectureId,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            logger.info("Uploading attachments for lectureId: {}, file count: {}", lectureId, files != null ? files.size() : 0);
            if (files == null || files.isEmpty()) {
                logger.warn("No files provided for attachment upload");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "No files provided"));
            }
            List<LectureContentDTO> contents = lectureMediaService.uploadAttachments(lectureId, files);
            logger.info("Successfully uploaded {} attachments for lectureId: {}", contents.size(), lectureId);
            return ResponseEntity.status(HttpStatus.CREATED).body(contents);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument error uploading attachments for lectureId: {}", lectureId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.error("Illegal state error uploading attachments for lectureId: {}", lectureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            logger.error("IO error uploading attachments for lectureId: {}", lectureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "File upload failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error uploading attachments for lectureId: {}", lectureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Unexpected error: " + e.getMessage()));
        }
    }

    @GetMapping("/{lectureId}/media")
    @Operation(summary = "Get lecture media", description = "Get all media content for a lecture")
    public ResponseEntity<List<LectureContentDTO>> getLectureMedia(@PathVariable String lectureId) {
        List<LectureContentDTO> contents = lectureMediaService.getLectureMedia(lectureId);
        return ResponseEntity.ok(contents);
    }

    @DeleteMapping("/media/{contentId}")
    @Operation(summary = "Delete media", description = "Delete a media content item")
    public ResponseEntity<Void> deleteMedia(@PathVariable String contentId) {
        lectureMediaService.deleteMedia(contentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{lectureId}/media/text")
    @Operation(summary = "Create text content", description = "Create a new text content item for a lecture")
    public ResponseEntity<LectureContentDTO> createTextContent(
            @PathVariable String lectureId,
            @RequestBody Map<String, Object> request) {
        String textContent = (String) request.get("textContent");
        String title = (String) request.get("title");
        Integer sequence = request.get("sequence") != null ? (Integer) request.get("sequence") : null;
        LectureContentDTO content = lectureMediaService.createTextContent(lectureId, textContent, title, sequence);
        return ResponseEntity.status(HttpStatus.CREATED).body(content);
    }

    @PutMapping("/media/{contentId}/text")
    @Operation(summary = "Update text content", description = "Update an existing text content item")
    public ResponseEntity<LectureContentDTO> updateTextContent(
            @PathVariable String contentId,
            @RequestBody Map<String, Object> request) {
        String textContent = (String) request.get("textContent");
        String title = (String) request.get("title");
        LectureContentDTO content = lectureMediaService.updateTextContent(contentId, textContent, title);
        return ResponseEntity.ok(content);
    }

    @PostMapping("/{lectureId}/media/transcript")
    @Operation(summary = "Upload transcript", description = "Upload a transcript file for a video lecture")
    public ResponseEntity<LectureContentDTO> uploadTranscript(
            @PathVariable String lectureId,
            @RequestParam("file") MultipartFile file) {
        try {
            LectureContentDTO content = lectureMediaService.uploadTranscript(lectureId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

