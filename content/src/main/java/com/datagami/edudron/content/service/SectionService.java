package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.domain.Section;
import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SectionService {
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private CourseRepository courseRepository;
    
    public SectionDTO createSection(String courseId, String title, String description) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify course exists
        courseRepository.findByIdAndClientId(courseId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        
        // Get next sequence
        Integer maxSequence = sectionRepository.findMaxSequenceByCourseIdAndClientId(courseId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        Section section = new Section();
        section.setId(UlidGenerator.nextUlid());
        section.setClientId(clientId);
        section.setCourseId(courseId);
        section.setTitle(title);
        section.setDescription(description);
        section.setSequence(nextSequence);
        
        Section saved = sectionRepository.save(section);
        return toDTO(saved);
    }
    
    public List<SectionDTO> getSectionsByCourse(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Section> sections = sectionRepository.findByCourseIdAndClientIdOrderBySequenceAsc(courseId, clientId);
        return sections.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public SectionDTO getSectionById(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + id));
        
        return toDTO(section);
    }
    
    public SectionDTO updateSection(String id, String title, String description) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + id));
        
        if (title != null) {
            section.setTitle(title);
        }
        if (description != null) {
            section.setDescription(description);
        }
        
        Section saved = sectionRepository.save(section);
        return toDTO(saved);
    }
    
    public void deleteSection(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + id));
        
        sectionRepository.delete(section);
    }
    
    private SectionDTO toDTO(Section section) {
        SectionDTO dto = new SectionDTO();
        dto.setId(section.getId());
        dto.setClientId(section.getClientId());
        dto.setCourseId(section.getCourseId());
        dto.setTitle(section.getTitle());
        dto.setDescription(section.getDescription());
        dto.setSequence(section.getSequence());
        dto.setIsPublished(section.getIsPublished());
        dto.setCreatedAt(section.getCreatedAt());
        dto.setUpdatedAt(section.getUpdatedAt());
        return dto;
    }
}

