package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.CreateCourseRequest;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CourseService {
    
    private static final Logger log = LoggerFactory.getLogger(CourseService.class);
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
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
        
        try {
            return toDTO(course);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert course to DTO: " + e.getMessage(), e);
        }
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
        
        // Store old assignments to detect new ones
        List<String> oldClassIds = course.getAssignedToClassIds() != null 
            ? new ArrayList<>(course.getAssignedToClassIds()) 
            : new ArrayList<>();
        List<String> oldSectionIds = course.getAssignedToSectionIds() != null 
            ? new ArrayList<>(course.getAssignedToSectionIds()) 
            : new ArrayList<>();
        
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
        
        // Get new assignments
        List<String> newClassIds = request.getAssignedToClassIds() != null 
            ? request.getAssignedToClassIds() 
            : new ArrayList<>();
        List<String> newSectionIds = request.getAssignedToSectionIds() != null 
            ? request.getAssignedToSectionIds() 
            : new ArrayList<>();
        
        course.setAssignedToClassIds(newClassIds);
        course.setAssignedToSectionIds(newSectionIds);
        
        Course saved = courseRepository.save(course);
        
        // Automatically enroll students for newly assigned classes/sections
        // Only enroll if course is published
        if (Boolean.TRUE.equals(saved.getIsPublished())) {
            enrollNewlyAssignedClassesAndSections(id, oldClassIds, newClassIds, oldSectionIds, newSectionIds);
        } else {
            log.debug("Course {} is not published, skipping automatic enrollment", id);
        }
        
        return toDTO(saved);
    }
    
    /**
     * Enrolls students to the course for newly assigned classes and sections.
     * This method is called automatically when a course is updated with new class/section assignments.
     * 
     * @param courseId The course ID
     * @param oldClassIds Previously assigned class IDs
     * @param newClassIds Newly assigned class IDs
     * @param oldSectionIds Previously assigned section IDs
     * @param newSectionIds Newly assigned section IDs
     */
    private void enrollNewlyAssignedClassesAndSections(
            String courseId,
            List<String> oldClassIds,
            List<String> newClassIds,
            List<String> oldSectionIds,
            List<String> newSectionIds) {
        
        // Find newly added class IDs
        Set<String> newlyAddedClassIds = newClassIds.stream()
            .filter(classId -> !oldClassIds.contains(classId))
            .collect(Collectors.toSet());
        
        // Find newly added section IDs
        Set<String> newlyAddedSectionIds = newSectionIds.stream()
            .filter(sectionId -> !oldSectionIds.contains(sectionId))
            .collect(Collectors.toSet());
        
        // Enroll students for newly assigned classes
        for (String classId : newlyAddedClassIds) {
            try {
                enrollClassToCourse(classId, courseId);
                log.info("Automatically enrolled students from class {} to course {}", classId, courseId);
            } catch (Exception e) {
                // Log error but don't fail the course update
                log.warn("Failed to automatically enroll students from class {} to course {}: {}", 
                    classId, courseId, e.getMessage());
            }
        }
        
        // Enroll students for newly assigned sections
        for (String sectionId : newlyAddedSectionIds) {
            try {
                enrollSectionToCourse(sectionId, courseId);
                log.info("Automatically enrolled students from section {} to course {}", sectionId, courseId);
            } catch (Exception e) {
                // Log error but don't fail the course update
                log.warn("Failed to automatically enroll students from section {} to course {}: {}", 
                    sectionId, courseId, e.getMessage());
            }
        }
    }
    
    /**
     * Calls the student service to enroll all students in a class to a course.
     * 
     * @param classId The class ID
     * @param courseId The course ID
     */
    private void enrollClassToCourse(String classId, String courseId) {
        String enrollmentUrl = gatewayUrl + "/api/classes/" + classId + "/enroll/" + courseId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Object> response = getRestTemplate().exchange(
                enrollmentUrl,
                HttpMethod.POST,
                entity,
                Object.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Enrollment failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling enrollment API for class {} and course {}: {}", 
                classId, courseId, e.getMessage());
            throw new RuntimeException("Failed to enroll class to course: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calls the student service to enroll all students in a section to a course.
     * 
     * @param sectionId The section ID
     * @param courseId The course ID
     */
    private void enrollSectionToCourse(String sectionId, String courseId) {
        String enrollmentUrl = gatewayUrl + "/api/sections/" + sectionId + "/enroll/" + courseId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Object> response = getRestTemplate().exchange(
                enrollmentUrl,
                HttpMethod.POST,
                entity,
                Object.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Enrollment failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling enrollment API for section {} and course {}: {}", 
                sectionId, courseId, e.getMessage());
            throw new RuntimeException("Failed to enroll section to course: " + e.getMessage(), e);
        }
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
        
        // Store old published status to detect if this is a new publication
        boolean wasPublished = Boolean.TRUE.equals(course.getIsPublished());
        course.setIsPublished(true);
        
        Course saved = courseRepository.save(course);
        
        // If course was not previously published, enroll students for assigned classes/sections
        if (!wasPublished && 
            ((saved.getAssignedToClassIds() != null && !saved.getAssignedToClassIds().isEmpty()) ||
             (saved.getAssignedToSectionIds() != null && !saved.getAssignedToSectionIds().isEmpty()))) {
            List<String> emptyList = new ArrayList<>();
            enrollNewlyAssignedClassesAndSections(
                saved.getId(),
                emptyList,
                saved.getAssignedToClassIds() != null ? saved.getAssignedToClassIds() : emptyList,
                emptyList,
                saved.getAssignedToSectionIds() != null ? saved.getAssignedToSectionIds() : emptyList
            );
        }
        
        return toDTO(saved);
    }
    
    private CourseDTO toDTO(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("Course cannot be null");
        }
        
        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setClientId(course.getClientId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());
        dto.setIsPublished(course.getIsPublished() != null ? course.getIsPublished() : false);
        dto.setThumbnailUrl(course.getThumbnailUrl());
        dto.setPreviewVideoUrl(course.getPreviewVideoUrl());
        dto.setIsFree(course.getIsFree() != null ? course.getIsFree() : false);
        dto.setPricePaise(course.getPricePaise());
        dto.setCurrency(course.getCurrency() != null ? course.getCurrency() : "INR");
        dto.setCategoryId(course.getCategoryId());
        dto.setTags(course.getTags() != null ? course.getTags() : new java.util.ArrayList<>());
        dto.setDifficultyLevel(course.getDifficultyLevel());
        dto.setLanguage(course.getLanguage() != null ? course.getLanguage() : "en");
        dto.setTotalDurationSeconds(course.getTotalDurationSeconds() != null ? course.getTotalDurationSeconds() : 0);
        dto.setTotalLecturesCount(course.getTotalLecturesCount() != null ? course.getTotalLecturesCount() : 0);
        dto.setTotalStudentsCount(course.getTotalStudentsCount() != null ? course.getTotalStudentsCount() : 0);
        dto.setCertificateEligible(course.getCertificateEligible() != null ? course.getCertificateEligible() : false);
        dto.setMaxCompletionDays(course.getMaxCompletionDays());
        dto.setCreatedAt(course.getCreatedAt());
        dto.setUpdatedAt(course.getUpdatedAt());
        dto.setPublishedAt(course.getPublishedAt());
        dto.setAssignedToClassIds(course.getAssignedToClassIds() != null ? course.getAssignedToClassIds() : new java.util.ArrayList<>());
        dto.setAssignedToSectionIds(course.getAssignedToSectionIds() != null ? course.getAssignedToSectionIds() : new java.util.ArrayList<>());
        // Note: Sections, objectives, instructors, resources are loaded separately
        return dto;
    }
}

