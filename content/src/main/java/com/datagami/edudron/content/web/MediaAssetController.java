package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.MediaAsset;
import com.datagami.edudron.content.service.MediaAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Media Assets", description = "Media asset upload and management endpoints")
public class MediaAssetController {

    @Autowired
    private MediaAssetService mediaAssetService;

    @PostMapping("/upload")
    @Operation(summary = "Upload media asset", description = "Upload a media file (video, PDF, image, audio, document)")
    public ResponseEntity<MediaAsset> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam("assetType") MediaAsset.AssetType assetType,
            Authentication authentication) {
        try {
            String uploadedBy = authentication != null ? authentication.getName() : null;
            MediaAsset asset = mediaAssetService.uploadAsset(file, assetType, uploadedBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(asset);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload asset: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset", description = "Get media asset details by ID")
    public ResponseEntity<MediaAsset> getAsset(@PathVariable String id) {
        // Implementation would fetch from repository
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete asset", description = "Delete a media asset")
    public ResponseEntity<Void> deleteAsset(@PathVariable String id) {
        mediaAssetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }
}


