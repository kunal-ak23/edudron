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

@RestController
@RequestMapping("/content/api/lectures")
@Tag(name = "Lecture Media", description = "Lecture media upload and management endpoints")
public class LectureMediaController {

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

    @PostMapping("/{lectureId}/media/attachments")
    @Operation(summary = "Upload attachments", description = "Upload multiple attachment files for a lecture")
    public ResponseEntity<List<LectureContentDTO>> uploadAttachments(
            @PathVariable String lectureId,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            List<LectureContentDTO> contents = lectureMediaService.uploadAttachments(lectureId, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(contents);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
}

