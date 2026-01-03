package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.CreateCourseRequest;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CourseService {
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private LectureRepository lectureRepository;
    
    public CourseDTO createCourse(CreateCourseRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Course course = new Course();
        course.setId(UlidGenerator.nextUlid());
        course.setClientId(clientId);
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setThumbnailUrl(request.getThumbnailUrl());
        course.setPreviewVideoUrl(request.getPreviewVideoUrl());
        course.setIsFree(request.getIsFree() != null ? request.getIsFree() : false);
        course.setPricePaise(request.getPricePaise());
        course.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        course.setCategoryId(request.getCategoryId());
        course.setTags(request.getTags());
        course.setDifficultyLevel(request.getDifficultyLevel());
        course.setLanguage(request.getLanguage() != null ? request.getLanguage() : "en");
        course.setCertificateEligible(request.getCertificateEligible() != null ? request.getCertificateEligible() : false);
        course.setMaxCompletionDays(request.getMaxCompletionDays());
        if (request.getAssignedToClassIds() != null) {
            course.setAssignedToClassIds(request.getAssignedToClassIds());
        }
        if (request.getAssignedToSectionIds() != null) {
            course.setAssignedToSectionIds(request.getAssignedToSectionIds());
        }
        
        Course saved = courseRepository.save(course);
        return toDTO(saved);
    }
    
    public CourseDTO getCourseById(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Course course = courseRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        
        return toDTO(course);
    }
    
    public Page<CourseDTO> getCourses(Boolean isPublished, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<Course> courses = isPublished != null
            ? courseRepository.findByClientIdAndIsPublished(clientId, isPublished, pageable)
            : courseRepository.findByClientId(clientId, pageable);
        
        return courses.map(this::toDTO);
    }
    
    public Page<CourseDTO> searchCourses(String categoryId, String difficultyLevel, 
                                        String language, Boolean isFree, 
                                        Boolean isPublished, String searchTerm, 
                                        Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<Course> courses = courseRepository.searchCourses(
            clientId, categoryId, difficultyLevel, language, 
            isFree, isPublished, searchTerm, pageable
        );
        
        return courses.map(this::toDTO);
    }
    
    public CourseDTO updateCourse(String id, CreateCourseRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Course course = courseRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setThumbnailUrl(request.getThumbnailUrl());
        course.setPreviewVideoUrl(request.getPreviewVideoUrl());
        course.setIsFree(request.getIsFree() != null ? request.getIsFree() : course.getIsFree());
        course.setPricePaise(request.getPricePaise());
        if (request.getCurrency() != null) {
            course.setCurrency(request.getCurrency());
        }
        course.setCategoryId(request.getCategoryId());
        course.setTags(request.getTags());
        course.setDifficultyLevel(request.getDifficultyLevel());
        if (request.getLanguage() != null) {
            course.setLanguage(request.getLanguage());
        }
        if (request.getCertificateEligible() != null) {
            course.setCertificateEligible(request.getCertificateEligible());
        }
        course.setMaxCompletionDays(request.getMaxCompletionDays());
        if (request.getAssignedToClassIds() != null) {
            course.setAssignedToClassIds(request.getAssignedToClassIds());
        }
        if (request.getAssignedToSectionIds() != null) {
            course.setAssignedToSectionIds(request.getAssignedToSectionIds());
        }
        
        Course saved = courseRepository.save(course);
        return toDTO(saved);
    }
    
    public void deleteCourse(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Course course = courseRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        
        courseRepository.delete(course);
    }
    
    public CourseDTO publishCourse(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Course course = courseRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        
        // Calculate statistics
        Integer totalDuration = lectureRepository.sumDurationByCourseIdAndClientId(id, clientId);
        Long totalLectures = lectureRepository.countByCourseIdAndClientId(id, clientId);
        
        course.setTotalDurationSeconds(totalDuration != null ? totalDuration : 0);
        course.setTotalLecturesCount(totalLectures != null ? totalLectures.intValue() : 0);
        course.setIsPublished(true);
        
        Course saved = courseRepository.save(course);
        return toDTO(saved);
    }
    
    private CourseDTO toDTO(Course course) {
        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setClientId(course.getClientId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());
        dto.setIsPublished(course.getIsPublished());
        dto.setThumbnailUrl(course.getThumbnailUrl());
        dto.setPreviewVideoUrl(course.getPreviewVideoUrl());
        dto.setIsFree(course.getIsFree());
        dto.setPricePaise(course.getPricePaise());
        dto.setCurrency(course.getCurrency());
        dto.setCategoryId(course.getCategoryId());
        dto.setTags(course.getTags());
        dto.setDifficultyLevel(course.getDifficultyLevel());
        dto.setLanguage(course.getLanguage());
        dto.setTotalDurationSeconds(course.getTotalDurationSeconds());
        dto.setTotalLecturesCount(course.getTotalLecturesCount());
        dto.setTotalStudentsCount(course.getTotalStudentsCount());
        dto.setCertificateEligible(course.getCertificateEligible());
        dto.setMaxCompletionDays(course.getMaxCompletionDays());
        dto.setCreatedAt(course.getCreatedAt());
        dto.setUpdatedAt(course.getUpdatedAt());
        dto.setPublishedAt(course.getPublishedAt());
        dto.setAssignedToClassIds(course.getAssignedToClassIds());
        dto.setAssignedToSectionIds(course.getAssignedToSectionIds());
        // Note: Sections, objectives, instructors, resources are loaded separately
        return dto;
    }
}

