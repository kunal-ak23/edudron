package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.domain.Section;
import com.datagami.edudron.content.dto.LectureContentDTO;
import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.repo.LectureContentRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import com.datagami.edudron.content.repo.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LectureService {
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    public LectureDTO createLecture(String sectionId, String title, String description, 
                                   Lecture.ContentType contentType) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify section exists
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        // Get next sequence
        Integer maxSequence = lectureRepository.findMaxSequenceBySectionIdAndClientId(sectionId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        Lecture lecture = new Lecture();
        lecture.setId(UlidGenerator.nextUlid());
        lecture.setClientId(clientId);
        lecture.setSectionId(sectionId);
        lecture.setCourseId(section.getCourseId());
        lecture.setTitle(title);
        lecture.setDescription(description);
        lecture.setContentType(contentType);
        lecture.setSequence(nextSequence);
        
        Lecture saved = lectureRepository.save(lecture);
        return toDTO(saved);
    }
    
    public List<LectureDTO> getLecturesBySection(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Lecture> lectures = lectureRepository.findBySectionIdAndClientIdOrderBySequenceAsc(sectionId, clientId);
        return lectures.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public LectureDTO getLectureById(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Lecture lecture = lectureRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + id));
        
        return toDTO(lecture);
    }
    
    public LectureDTO updateLecture(String id, String title, String description, 
                                   Integer durationSeconds, Boolean isPreview) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Lecture lecture = lectureRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + id));
        
        if (title != null) {
            lecture.setTitle(title);
        }
        if (description != null) {
            lecture.setDescription(description);
        }
        if (durationSeconds != null) {
            lecture.setDurationSeconds(durationSeconds);
        }
        if (isPreview != null) {
            lecture.setIsPreview(isPreview);
        }
        
        Lecture saved = lectureRepository.save(lecture);
        return toDTO(saved);
    }
    
    public void deleteLecture(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Lecture lecture = lectureRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + id));
        
        lectureRepository.delete(lecture);
    }
    
    private LectureDTO toDTO(Lecture lecture) {
        LectureDTO dto = new LectureDTO();
        dto.setId(lecture.getId());
        dto.setClientId(lecture.getClientId());
        dto.setSectionId(lecture.getSectionId());
        dto.setCourseId(lecture.getCourseId());
        dto.setTitle(lecture.getTitle());
        dto.setDescription(lecture.getDescription());
        dto.setContentType(lecture.getContentType());
        dto.setSequence(lecture.getSequence());
        dto.setDurationSeconds(lecture.getDurationSeconds());
        dto.setIsPreview(lecture.getIsPreview());
        dto.setIsPublished(lecture.getIsPublished());
        dto.setCreatedAt(lecture.getCreatedAt());
        dto.setUpdatedAt(lecture.getUpdatedAt());
        
        // Load and include lecture contents
        List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
            lecture.getId(), lecture.getClientId());
        List<LectureContentDTO> contentDTOs = contents.stream()
            .map(this::toContentDTO)
            .collect(Collectors.toList());
        dto.setContents(contentDTOs);
        
        return dto;
    }
    
    private LectureContentDTO toContentDTO(LectureContent content) {
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

