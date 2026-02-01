package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.CreateCourseRequest;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import com.datagami.edudron.content.service.CommonEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    
    @Autowired
    private CommonEventService eventService;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            log.debug("Initializing RestTemplate for student service calls. Gateway URL: {}", gatewayUrl);
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            // Add interceptor to forward JWT token (Authorization header)
            interceptors.add((request, body, execution) -> {
                // Get current request to extract Authorization header
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        // Only add if not already present
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().add("Authorization", authHeader);
                            log.debug("Propagated Authorization header (JWT token) to student service: {}", request.getURI());
                        } else {
                            log.debug("Authorization header already present in request to {}", request.getURI());
                        }
                    } else {
                        log.warn("No Authorization header found in current request - student service call may fail with 403 Forbidden: {}", request.getURI());
                    }
                } else {
                    log.error("No request context available - cannot forward Authorization header to {}. This may cause 403 Forbidden errors.", request.getURI());
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
            log.debug("RestTemplate initialized with TenantContextRestTemplateInterceptor and JWT token forwarding");
        }
        return restTemplate;
    }
    
    public CourseDTO createCourse(CreateCourseRequest request) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot create courses
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot create courses");
        }
        
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
        
        // Log course creation event
        String userId = getCurrentUserId();
        String userEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "courseId", saved.getId(),
            "courseTitle", saved.getTitle() != null ? saved.getTitle() : "",
            "isPublished", saved.getIsPublished() != null ? saved.getIsPublished() : false,
            "isFree", saved.getIsFree() != null ? saved.getIsFree() : false
        );
        eventService.logUserAction("COURSE_CREATED", userId, userEmail, "/api/content/courses", eventData);
        
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
        
        // For students, check if course is published - throw error if not
        String userRole = getCurrentUserRole();
        if ("STUDENT".equals(userRole) && !course.getIsPublished()) {
            log.warn("Student attempted to access unpublished course: {}", id);
            throw new IllegalArgumentException("Course not found: " + id);
        }
        
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
        
        // For students, always filter to only published courses
        String userRole = getCurrentUserRole();
        if ("STUDENT".equals(userRole)) {
            isPublished = true; // Force published=true for students
            log.debug("Student user detected - filtering to only published courses");
        }
        
        Page<Course> courses = isPublished != null
            ? courseRepository.findByClientIdAndIsPublished(clientId, isPublished, pageable)
            : courseRepository.findByClientId(clientId, pageable);
        
        // For instructors, filter courses based on their InstructorAssignment records
        if ("INSTRUCTOR".equals(userRole)) {
            String userId = getCurrentUserId();
            if (userId != null) {
                Map<String, Object> instructorAccess = getInstructorAccess(userId);
                if (instructorAccess != null) {
                    Set<String> allowedCourseIds = getSetFromAccess(instructorAccess, "allowedCourseIds");
                    Set<String> allowedClassIds = getSetFromAccess(instructorAccess, "allowedClassIds");
                    Set<String> allowedSectionIds = getSetFromAccess(instructorAccess, "allowedSectionIds");
                    
                    // Filter courses based on instructor assignments
                    List<Course> filteredCourses = courses.getContent().stream()
                        .filter(course -> {
                            // Course is directly assigned to instructor
                            if (allowedCourseIds.contains(course.getId())) {
                                return true;
                            }
                            // Course is assigned to a class the instructor has access to
                            boolean hasAllowedClass = course.getAssignedToClassIds() != null && 
                                course.getAssignedToClassIds().stream().anyMatch(allowedClassIds::contains);
                            // Course is assigned to a section the instructor has access to
                            boolean hasAllowedSection = course.getAssignedToSectionIds() != null && 
                                course.getAssignedToSectionIds().stream().anyMatch(allowedSectionIds::contains);
                            return hasAllowedClass || hasAllowedSection;
                        })
                        .collect(Collectors.toList());
                    
                    log.debug("Filtered courses for INSTRUCTOR user {} - showing {} out of {} total courses", 
                        userId, filteredCourses.size(), courses.getTotalElements());
                    
                    return new org.springframework.data.domain.PageImpl<>(
                        filteredCourses.stream().map(this::toDTO).collect(Collectors.toList()),
                        pageable,
                        filteredCourses.size()
                    );
                }
            }
            log.warn("INSTRUCTOR user has no assignments - returning empty course list");
            return new org.springframework.data.domain.PageImpl<>(
                new ArrayList<>(),
                pageable,
                0
            );
        }
        
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
        
        // For students, always filter to only published courses
        String userRole = getCurrentUserRole();
        if ("STUDENT".equals(userRole)) {
            isPublished = true; // Force published=true for students
            log.debug("Student user detected in search - filtering to only published courses");
        }
        
        Page<Course> courses = courseRepository.searchCourses(
            clientId, categoryId, difficultyLevel, language, 
            isFree, isPublished, searchTerm, pageable
        );
        
        // For instructors, filter courses based on their InstructorAssignment records
        if ("INSTRUCTOR".equals(userRole)) {
            String userId = getCurrentUserId();
            if (userId != null) {
                Map<String, Object> instructorAccess = getInstructorAccess(userId);
                if (instructorAccess != null) {
                    Set<String> allowedCourseIds = getSetFromAccess(instructorAccess, "allowedCourseIds");
                    Set<String> allowedClassIds = getSetFromAccess(instructorAccess, "allowedClassIds");
                    Set<String> allowedSectionIds = getSetFromAccess(instructorAccess, "allowedSectionIds");
                    
                    // Filter courses based on instructor assignments
                    List<Course> filteredCourses = courses.getContent().stream()
                        .filter(course -> {
                            // Course is directly assigned to instructor
                            if (allowedCourseIds.contains(course.getId())) {
                                return true;
                            }
                            // Course is assigned to a class the instructor has access to
                            boolean hasAllowedClass = course.getAssignedToClassIds() != null && 
                                course.getAssignedToClassIds().stream().anyMatch(allowedClassIds::contains);
                            // Course is assigned to a section the instructor has access to
                            boolean hasAllowedSection = course.getAssignedToSectionIds() != null && 
                                course.getAssignedToSectionIds().stream().anyMatch(allowedSectionIds::contains);
                            return hasAllowedClass || hasAllowedSection;
                        })
                        .collect(Collectors.toList());
                    
                    log.debug("Filtered courses in search for INSTRUCTOR user {} - showing {} out of {} total courses", 
                        userId, filteredCourses.size(), courses.getTotalElements());
                    
                    return new org.springframework.data.domain.PageImpl<>(
                        filteredCourses.stream().map(this::toDTO).collect(Collectors.toList()),
                        pageable,
                        filteredCourses.size()
                    );
                }
            }
            log.warn("INSTRUCTOR user has no assignments - returning empty course list");
            return new org.springframework.data.domain.PageImpl<>(
                new ArrayList<>(),
                pageable,
                0
            );
        }
        
        Page<CourseDTO> result = courses.map(this::toDTO);
        
        // Log search query event
        String userId = getCurrentUserId();
        Map<String, Object> searchData = new java.util.HashMap<>();
        searchData.put("categoryId", categoryId != null ? categoryId : "");
        searchData.put("difficultyLevel", difficultyLevel != null ? difficultyLevel : "");
        searchData.put("language", language != null ? language : "");
        searchData.put("isFree", isFree != null ? isFree : false);
        searchData.put("isPublished", isPublished != null ? isPublished : false);
        searchData.put("searchTerm", searchTerm != null ? searchTerm : "");
        searchData.put("resultCount", result.getNumberOfElements());
        searchData.put("totalResults", result.getTotalElements());
        eventService.logSearchQuery(userId, searchTerm != null ? searchTerm : "", "COURSE", 
            result.getNumberOfElements(), null, searchData);
        
        return result;
    }
    
    public CourseDTO updateCourse(String id, CreateCourseRequest request) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot update courses
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot update courses");
        }
        
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
        
        // Log course update event
        String userId = getCurrentUserId();
        String userEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "courseId", saved.getId(),
            "courseTitle", saved.getTitle() != null ? saved.getTitle() : "",
            "isPublished", saved.getIsPublished() != null ? saved.getIsPublished() : false,
            "hasClassAssignments", saved.getAssignedToClassIds() != null && !saved.getAssignedToClassIds().isEmpty(),
            "hasSectionAssignments", saved.getAssignedToSectionIds() != null && !saved.getAssignedToSectionIds().isEmpty()
        );
        eventService.logUserAction("COURSE_EDITED", userId, userEmail, "/api/content/courses/" + id, eventData);
        
        // Automatically enroll students for newly assigned classes/sections
        // Only enroll if course is published
        // IMPORTANT: Enrollment must happen AFTER transaction commits to avoid race condition
        // where student service validates course before the assignment is visible in the database
        if (Boolean.TRUE.equals(saved.getIsPublished())) {
            log.info("Course {} updated and is published. Checking for new class/section assignments to trigger automatic enrollment.", id);
            log.debug("Course {} - Previous class assignments: {}, New class assignments: {}", 
                id, oldClassIds, newClassIds);
            log.debug("Course {} - Previous section assignments: {}, New section assignments: {}", 
                id, oldSectionIds, newSectionIds);
            
            // Schedule enrollment to happen after transaction commits
            final String courseId = id;
            final List<String> finalOldClassIds = new ArrayList<>(oldClassIds);
            final List<String> finalNewClassIds = new ArrayList<>(newClassIds);
            final List<String> finalOldSectionIds = new ArrayList<>(oldSectionIds);
            final List<String> finalNewSectionIds = new ArrayList<>(newSectionIds);
            
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("Course {} - Transaction committed. Starting automatic enrollment for new assignments.", courseId);
                        enrollNewlyAssignedClassesAndSections(courseId, finalOldClassIds, finalNewClassIds, finalOldSectionIds, finalNewSectionIds);
                    }
                });
                log.debug("Course {} - Enrollment scheduled to run after transaction commits", id);
            } else {
                // If no transaction, enroll immediately (shouldn't happen in normal flow)
                log.warn("Course {} - No active transaction, enrolling immediately (this may cause race conditions)", id);
                enrollNewlyAssignedClassesAndSections(id, oldClassIds, newClassIds, oldSectionIds, newSectionIds);
            }
        } else {
            log.debug("Course {} is not published (isPublished={}), skipping automatic enrollment", 
                id, saved.getIsPublished());
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
        
        // Log summary of what will be enrolled
        if (newlyAddedClassIds.isEmpty() && newlyAddedSectionIds.isEmpty()) {
            log.info("Course {} - No new class or section assignments detected. All assignments already existed. Skipping enrollment.", courseId);
            return;
        }
        
        log.info("Course {} - Starting automatic enrollment for {} new class(es) and {} new section(s)", 
            courseId, newlyAddedClassIds.size(), newlyAddedSectionIds.size());
        
        if (!newlyAddedClassIds.isEmpty()) {
            log.info("Course {} - Newly assigned classes to enroll: {}", courseId, newlyAddedClassIds);
        }
        if (!newlyAddedSectionIds.isEmpty()) {
            log.info("Course {} - Newly assigned sections to enroll: {}", courseId, newlyAddedSectionIds);
        }
        
        int successfulClassEnrollments = 0;
        int failedClassEnrollments = 0;
        int successfulSectionEnrollments = 0;
        int failedSectionEnrollments = 0;
        
        // Enroll students for newly assigned classes
        for (String classId : newlyAddedClassIds) {
            try {
                log.debug("Course {} - Attempting to enroll students from class {} via student service", courseId, classId);
                enrollClassToCourse(classId, courseId);
                successfulClassEnrollments++;
                log.info("Course {} - Successfully enrolled students from class {} to course {}", courseId, classId, courseId);
            } catch (Exception e) {
                failedClassEnrollments++;
                // Log error but don't fail the course update
                log.warn("Course {} - Failed to automatically enroll students from class {} to course {}: {}", 
                    courseId, classId, courseId, e.getMessage(), e);
            }
        }
        
        // Enroll students for newly assigned sections
        for (String sectionId : newlyAddedSectionIds) {
            try {
                log.debug("Course {} - Attempting to enroll students from section {} via student service", courseId, sectionId);
                enrollSectionToCourse(sectionId, courseId);
                successfulSectionEnrollments++;
                log.info("Course {} - Successfully enrolled students from section {} to course {}", courseId, sectionId, courseId);
            } catch (Exception e) {
                failedSectionEnrollments++;
                // Log error but don't fail the course update
                log.warn("Course {} - Failed to automatically enroll students from section {} to course {}: {}", 
                    courseId, sectionId, courseId, e.getMessage(), e);
            }
        }
        
        // Log summary
        log.info("Course {} - Automatic enrollment completed. Classes: {}/{} successful, {}/{} failed. Sections: {}/{} successful, {}/{} failed.", 
            courseId,
            successfulClassEnrollments, newlyAddedClassIds.size(), failedClassEnrollments, newlyAddedClassIds.size(),
            successfulSectionEnrollments, newlyAddedSectionIds.size(), failedSectionEnrollments, newlyAddedSectionIds.size());
    }
    
    /**
     * Calls the student service to enroll all students in a class to a course.
     * 
     * @param classId The class ID
     * @param courseId The course ID
     */
    private void enrollClassToCourse(String classId, String courseId) {
        String enrollmentUrl = gatewayUrl + "/api/classes/" + classId + "/enroll/" + courseId;
        log.debug("Course {} - Calling student service enrollment API: POST {}", courseId, enrollmentUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Object> response = getRestTemplate().exchange(
                enrollmentUrl,
                HttpMethod.POST,
                entity,
                Object.class
            );
            long duration = System.currentTimeMillis() - startTime;
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Course {} - Enrollment API returned non-2xx status for class {}: {}", 
                    courseId, classId, response.getStatusCode());
                throw new RuntimeException("Enrollment failed with status: " + response.getStatusCode());
            }
            
            log.debug("Course {} - Enrollment API call for class {} completed successfully in {}ms. Response status: {}", 
                courseId, classId, duration, response.getStatusCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Course {} - HTTP error calling enrollment API for class {}: Status={}, Response={}", 
                courseId, classId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Failed to enroll class to course: " + e.getMessage(), e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Course {} - Network error calling enrollment API for class {}: {}", 
                courseId, classId, e.getMessage(), e);
            throw new RuntimeException("Failed to enroll class to course: Network error - " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Course {} - Unexpected error calling enrollment API for class {} and course {}: {}", 
                courseId, classId, courseId, e.getMessage(), e);
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
        log.debug("Course {} - Calling student service enrollment API: POST {}", courseId, enrollmentUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<Object> response = getRestTemplate().exchange(
                enrollmentUrl,
                HttpMethod.POST,
                entity,
                Object.class
            );
            long duration = System.currentTimeMillis() - startTime;
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Course {} - Enrollment API returned non-2xx status for section {}: {}", 
                    courseId, sectionId, response.getStatusCode());
                throw new RuntimeException("Enrollment failed with status: " + response.getStatusCode());
            }
            
            log.debug("Course {} - Enrollment API call for section {} completed successfully in {}ms. Response status: {}", 
                courseId, sectionId, duration, response.getStatusCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Course {} - HTTP error calling enrollment API for section {}: Status={}, Response={}", 
                courseId, sectionId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Failed to enroll section to course: " + e.getMessage(), e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Course {} - Network error calling enrollment API for section {}: {}", 
                courseId, sectionId, e.getMessage(), e);
            throw new RuntimeException("Failed to enroll section to course: Network error - " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Course {} - Unexpected error calling enrollment API for section {} and course {}: {}", 
                courseId, sectionId, courseId, e.getMessage(), e);
            throw new RuntimeException("Failed to enroll section to course: " + e.getMessage(), e);
        }
    }
    
    public void deleteCourse(String id) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot delete courses
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot delete courses");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Course course = courseRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        
        // Log course deletion event before deleting
        String userId = getCurrentUserId();
        String userEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "courseId", course.getId(),
            "courseTitle", course.getTitle() != null ? course.getTitle() : "",
            "wasPublished", course.getIsPublished() != null ? course.getIsPublished() : false
        );
        eventService.logUserAction("COURSE_DELETED", userId, userEmail, "/api/content/courses/" + id, eventData);
        
        courseRepository.delete(course);
    }
    
    public CourseDTO publishCourse(String id) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot publish courses
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot publish courses");
        }
        
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
        
        log.info("Course {} published. Previous published status: {}, Current published status: {}", 
            id, wasPublished, saved.getIsPublished());
        
        // Log course publication event
        String userId = getCurrentUserId();
        String userEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "courseId", saved.getId(),
            "courseTitle", saved.getTitle() != null ? saved.getTitle() : "",
            "wasPublished", wasPublished,
            "totalLectures", saved.getTotalLecturesCount() != null ? saved.getTotalLecturesCount() : 0,
            "totalDurationSeconds", saved.getTotalDurationSeconds() != null ? saved.getTotalDurationSeconds() : 0
        );
        eventService.logUserAction("COURSE_PUBLISHED", userId, userEmail, "/api/content/courses/" + id + "/publish", eventData);
        
        // If course was not previously published, enroll students for assigned classes/sections
        // IMPORTANT: Enrollment must happen AFTER transaction commits to avoid race condition
        if (!wasPublished && 
            ((saved.getAssignedToClassIds() != null && !saved.getAssignedToClassIds().isEmpty()) ||
             (saved.getAssignedToSectionIds() != null && !saved.getAssignedToSectionIds().isEmpty()))) {
            log.info("Course {} - First time publication detected with assigned classes/sections. Triggering automatic enrollment.", id);
            log.debug("Course {} - Assigned classes: {}, Assigned sections: {}", 
                id, saved.getAssignedToClassIds(), saved.getAssignedToSectionIds());
            
            // Schedule enrollment to happen after transaction commits
            final String courseId = saved.getId();
            final List<String> emptyList = new ArrayList<>();
            final List<String> finalClassIds = saved.getAssignedToClassIds() != null ? new ArrayList<>(saved.getAssignedToClassIds()) : emptyList;
            final List<String> finalSectionIds = saved.getAssignedToSectionIds() != null ? new ArrayList<>(saved.getAssignedToSectionIds()) : emptyList;
            
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("Course {} - Transaction committed. Starting automatic enrollment for first-time publication.", courseId);
                        enrollNewlyAssignedClassesAndSections(courseId, emptyList, finalClassIds, emptyList, finalSectionIds);
                    }
                });
                log.debug("Course {} - Enrollment scheduled to run after transaction commits", id);
            } else {
                // If no transaction, enroll immediately (shouldn't happen in normal flow)
                log.warn("Course {} - No active transaction, enrolling immediately (this may cause race conditions)", id);
                enrollNewlyAssignedClassesAndSections(saved.getId(), emptyList, finalClassIds, emptyList, finalSectionIds);
            }
        } else if (!wasPublished) {
            log.debug("Course {} - First time publication but no classes or sections assigned. Skipping enrollment.", id);
        } else {
            log.debug("Course {} - Already published. Enrollment on publish skipped (enrollment happens on assignment updates).", id);
        }
        
        return toDTO(saved);
    }

    public CourseDTO unpublishCourse(String id) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot unpublish courses
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot unpublish courses");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        Course course = courseRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));

        course.setIsPublished(false);
        // Clear publishedAt so UI can treat it as draft again
        course.setPublishedAt(null);

        Course saved = courseRepository.save(course);
        log.info("Course {} unpublished. Current published status: {}", id, saved.getIsPublished());

        // Log course unpublish event
        String userId = getCurrentUserId();
        String userEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "courseId", saved.getId(),
            "courseTitle", saved.getTitle() != null ? saved.getTitle() : "",
            "wasPublished", true
        );
        eventService.logUserAction("COURSE_UNPUBLISHED", userId, userEmail, "/api/content/courses/" + id + "/unpublish", eventData);

        return toDTO(saved);
    }
    
    /**
     * Get all published courses assigned to a specific section.
     * This method is used for automatic enrollment when students are added to sections.
     * 
     * @param sectionId The section ID
     * @return List of published courses assigned to the section
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getPublishedCoursesBySectionId(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        log.debug("Fetching published courses assigned to section {}", sectionId);
        List<Course> courses = courseRepository.findPublishedCoursesBySectionId(clientId, sectionId);
        log.info("Found {} published courses assigned to section {}", courses.size(), sectionId);
        
        return courses.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all published courses assigned to a specific class.
     * This method is used for automatic enrollment when students are added to classes.
     * 
     * @param classId The class ID
     * @return List of published courses assigned to the class
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getPublishedCoursesByClassId(String classId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        log.debug("Fetching published courses assigned to class {}", classId);
        List<Course> courses = courseRepository.findPublishedCoursesByClassId(clientId, classId);
        log.info("Found {} published courses assigned to class {}", courses.size(), classId);
        
        return courses.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the current user's ID from the security context
     * Returns null if unable to determine user ID
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getName() != null && 
                !"anonymousUser".equals(authentication.getName())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Could not determine user ID: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get the current user's email from the identity service
     * Returns null if unable to determine email
     */
    private String getCurrentUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return null;
            }
            
            // Get user info from identity service
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object email = response.getBody().get("email");
                return email != null ? email.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not determine user email: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get the current user's role from the identity service
     * Returns null if unable to determine role (e.g., anonymous user)
     */
    public String getCurrentUserRole() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return null;
            }
            
            // Get user info from identity service
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object role = response.getBody().get("role");
                return role != null ? role.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not determine user role: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get instructor access (allowed classes, sections, courses) from student service
     * Returns null if unable to determine access
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getInstructorAccess(String instructorUserId) {
        try {
            String accessUrl = gatewayUrl + "/api/instructor-assignments/instructor/" + instructorUserId + "/access";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                accessUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not get instructor access for user {}: {}", instructorUserId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Helper method to extract a Set of strings from instructor access response
     */
    @SuppressWarnings("unchecked")
    private Set<String> getSetFromAccess(Map<String, Object> access, String key) {
        if (access == null) {
            return new java.util.HashSet<>();
        }
        Object value = access.get(key);
        if (value instanceof List) {
            return new java.util.HashSet<>((List<String>) value);
        } else if (value instanceof Set) {
            return (Set<String>) value;
        }
        return new java.util.HashSet<>();
    }
    
    /**
     * Check if instructor can access a specific course
     */
    public boolean canInstructorAccessCourse(String instructorUserId, String courseId) {
        try {
            String url = gatewayUrl + "/api/instructor-assignments/instructor/" + instructorUserId + "/can-access-course/" + courseId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Boolean> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                entity,
                Boolean.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not check course access for instructor {}: {}", instructorUserId, e.getMessage());
        }
        return false;
    }
    
    /**
     * Get the current user's assigned institute IDs from the identity service
     * Returns empty list if unable to determine or user has no assigned institutes
     * @deprecated Use getInstructorAccess instead for instructor access control
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    private List<String> getCurrentUserInstituteIds() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return new ArrayList<>();
            }
            
            // Get user info from identity service
            String meUrl = gatewayUrl + "/idp/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object instituteIds = response.getBody().get("instituteIds");
                if (instituteIds instanceof List) {
                    return (List<String>) instituteIds;
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine user institute IDs: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
    
    /**
     * Get all class IDs for the given institute IDs by calling the student service
     * Returns empty list if unable to determine or no classes found
     */
    private List<String> getClassIdsByInstituteIds(List<String> instituteIds) {
        if (instituteIds == null || instituteIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> allClassIds = new ArrayList<>();
        try {
            for (String instituteId : instituteIds) {
                String classesUrl = gatewayUrl + "/api/institutes/" + instituteId + "/classes";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                    classesUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> classes = response.getBody();
                    for (Map<String, Object> classObj : classes) {
                        Object id = classObj.get("id");
                        if (id != null) {
                            allClassIds.add(id.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not get class IDs for institutes: {}", e.getMessage());
        }
        return allClassIds;
    }
    
    /**
     * Get all section IDs for the given class IDs by calling the student service
     * Returns empty list if unable to determine or no sections found
     */
    private List<String> getSectionIdsByClassIds(List<String> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> allSectionIds = new ArrayList<>();
        try {
            for (String classId : classIds) {
                String sectionsUrl = gatewayUrl + "/api/classes/" + classId + "/sections";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                    sectionsUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> sections = response.getBody();
                    for (Map<String, Object> sectionObj : sections) {
                        Object id = sectionObj.get("id");
                        if (id != null) {
                            allSectionIds.add(id.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not get section IDs for classes: {}", e.getMessage());
        }
        return allSectionIds;
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

