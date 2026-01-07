package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.dto.LectureContentDTO;
import com.datagami.edudron.content.repo.LectureContentRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LectureMediaService {
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    @Autowired
    private MediaUploadService mediaUploadService;
    
    /**
     * Upload a single video or audio file for a lecture
     * Replaces any existing video/audio content
     */
    public LectureContentDTO uploadVideoOrAudio(String lectureId, MultipartFile file, boolean isVideo) throws IOException {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify lecture exists
        Lecture lecture = lectureRepository.findByIdAndClientId(lectureId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));
        
        // Delete existing video/audio content for this lecture
        List<LectureContent> existingContent = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(lectureId, clientId);
        existingContent.stream()
            .filter(content -> (isVideo && content.getContentType() == LectureContent.ContentType.VIDEO) ||
                              (!isVideo && content.getContentType() == LectureContent.ContentType.AUDIO))
            .forEach(content -> {
                if (content.getVideoUrl() != null) {
                    mediaUploadService.deleteMedia(content.getVideoUrl());
                }
                if (content.getFileUrl() != null) {
                    mediaUploadService.deleteMedia(content.getFileUrl());
                }
                lectureContentRepository.delete(content);
            });
        
        // Upload file to Azure Storage
        String folder = isVideo ? "lectures/videos" : "lectures/audio";
        String fileUrl;
        if (isVideo) {
            fileUrl = mediaUploadService.uploadVideo(file, folder);
        } else {
            // For audio, use generic upload method
            fileUrl = uploadGenericFile(file, folder);
        }
        
        // Create new LectureContent
        LectureContent content = new LectureContent();
        content.setId(UlidGenerator.nextUlid());
        content.setClientId(clientId);
        content.setLectureId(lectureId);
        content.setContentType(isVideo ? LectureContent.ContentType.VIDEO : LectureContent.ContentType.AUDIO);
        content.setTitle(lecture.getTitle());
        content.setDescription(lecture.getDescription());
        
        if (isVideo) {
            content.setVideoUrl(fileUrl);
        } else {
            content.setFileUrl(fileUrl);
        }
        
        content.setFileSizeBytes(file.getSize());
        content.setMimeType(file.getContentType());
        content.setSequence(0);
        
        LectureContent saved = lectureContentRepository.save(content);
        return toDTO(saved);
    }
    
    /**
     * Upload multiple attachment files for a lecture
     */
    public List<LectureContentDTO> uploadAttachments(String lectureId, List<MultipartFile> files) throws IOException {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify lecture exists
        lectureRepository.findByIdAndClientId(lectureId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));
        
        // Get next sequence number
        Integer maxSequence = lectureContentRepository.findMaxSequenceByLectureIdAndClientId(lectureId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        List<LectureContentDTO> uploadedContents = new java.util.ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            
            // Determine content type based on file
            LectureContent.ContentType contentType = determineContentType(file);
            
            // Upload file to Azure Storage
            String folder = "lectures/attachments";
            String fileUrl = uploadGenericFile(file, folder);
            
            // Create LectureContent
            LectureContent content = new LectureContent();
            content.setId(UlidGenerator.nextUlid());
            content.setClientId(clientId);
            content.setLectureId(lectureId);
            content.setContentType(contentType);
            content.setTitle(file.getOriginalFilename());
            content.setFileUrl(fileUrl);
            content.setFileSizeBytes(file.getSize());
            content.setMimeType(file.getContentType());
            content.setSequence(nextSequence++);
            
            LectureContent saved = lectureContentRepository.save(content);
            uploadedContents.add(toDTO(saved));
        }
        
        return uploadedContents;
    }
    
    /**
     * Get all media content for a lecture
     */
    public List<LectureContentDTO> getLectureMedia(String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(lectureId, clientId);
        return contents.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Delete a media content item
     */
    public void deleteMedia(String contentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        LectureContent content = lectureContentRepository.findByIdAndClientId(contentId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Content not found: " + contentId));
        
        // Delete from Azure Storage
        if (content.getVideoUrl() != null) {
            mediaUploadService.deleteMedia(content.getVideoUrl());
        }
        if (content.getFileUrl() != null) {
            mediaUploadService.deleteMedia(content.getFileUrl());
        }
        
        // Delete from database
        lectureContentRepository.delete(content);
    }
    
    private LectureContent.ContentType determineContentType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                return LectureContent.ContentType.IMAGE;
            } else if (contentType.equals("application/pdf")) {
                return LectureContent.ContentType.PDF;
            } else if (contentType.startsWith("video/")) {
                return LectureContent.ContentType.VIDEO;
            } else if (contentType.startsWith("audio/")) {
                return LectureContent.ContentType.AUDIO;
            }
        }
        
        // Fallback to filename extension
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".pdf")) {
                return LectureContent.ContentType.PDF;
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")) {
                return LectureContent.ContentType.IMAGE;
            } else if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")) {
                return LectureContent.ContentType.VIDEO;
            } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) {
                return LectureContent.ContentType.AUDIO;
            }
        }
        
        // Default to PDF for unknown types
        return LectureContent.ContentType.PDF;
    }
    
    /**
     * Generic file upload method that handles any file type
     */
    private String uploadGenericFile(MultipartFile file, String folder) throws IOException {
        return mediaUploadService.uploadFile(file, folder);
    }
    
    private LectureContentDTO toDTO(LectureContent content) {
        LectureContentDTO dto = new LectureContentDTO();
        dto.setId(content.getId());
        dto.setClientId(content.getClientId());
        dto.setLectureId(content.getLectureId());
        dto.setContentType(content.getContentType());
        dto.setTitle(content.getTitle());
        dto.setDescription(content.getDescription());
        dto.setFileUrl(content.getFileUrl());
        dto.setFileSizeBytes(content.getFileSizeBytes());
        dto.setMimeType(content.getMimeType());
        dto.setVideoUrl(content.getVideoUrl());
        dto.setTranscriptUrl(content.getTranscriptUrl());
        dto.setSubtitleUrls(content.getSubtitleUrls());
        dto.setThumbnailUrl(content.getThumbnailUrl());
        dto.setTextContent(content.getTextContent());
        dto.setExternalUrl(content.getExternalUrl());
        dto.setEmbeddedCode(content.getEmbeddedCode());
        dto.setSequence(content.getSequence());
        dto.setCreatedAt(content.getCreatedAt());
        dto.setUpdatedAt(content.getUpdatedAt());
        return dto;
    }
}

