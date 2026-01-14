package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.BatchEnrollmentRequest;
import com.datagami.edudron.student.dto.BulkEnrollmentResult;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BulkEnrollmentService {
    
    private static final Logger log = LoggerFactory.getLogger(BulkEnrollmentService.class);
    
    @Autowired
    private EnrollmentService enrollmentService;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
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
                            log.debug("Propagated Authorization header (JWT token) to content service: {}", request.getURI());
                        } else {
                            log.debug("Authorization header already present in request to {}", request.getURI());
                        }
                    } else {
                        log.warn("No Authorization header found in current request - content service call may fail with 403 Forbidden: {}", request.getURI());
                    }
                } else {
                    log.error("No request context available - cannot forward Authorization header to {}. This may cause 403 Forbidden errors.", request.getURI());
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
    public BulkEnrollmentResult enrollClassToCourse(String classId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate class
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        if (!classEntity.getIsActive()) {
            throw new IllegalArgumentException("Class is not active: " + classId);
        }
        
        // Validate course and check assignment
        validateCourseAssignment(courseId, classId, null, clientId);
        
        // Get all students in the class (across all sections)
        // Students are identified by having enrollments with this classId
        // This includes both real course enrollments and placeholder enrollments (used for association)
        List<Enrollment> classEnrollments = enrollmentRepository.findByClientIdAndClassId(clientId, classId);
        Set<String> studentIds = classEnrollments.stream()
            .map(Enrollment::getStudentId)
            .collect(Collectors.toSet());
        
        return enrollStudentsToCourse(studentIds, courseId, classId, null, classEntity.getInstituteId(), clientId);
    }
    
    public BulkEnrollmentResult enrollSectionToCourse(String sectionId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate section
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        if (!section.getIsActive()) {
            throw new IllegalArgumentException("Section is not active: " + sectionId);
        }
        
        String classId = section.getClassId();
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        // Validate course and check assignment
        validateCourseAssignment(courseId, classId, sectionId, clientId);
        
        // Get all students in the section using two methods:
        // 1. From enrollment records (batchId = sectionId) - for students with existing enrollments
        // 2. From identity service (sectionId field) - for all students assigned to the section
        Set<String> studentIds = new java.util.HashSet<>();
        
        // Method 1: Find students via enrollment records
        List<Enrollment> sectionEnrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
        Set<String> studentsFromEnrollments = sectionEnrollments.stream()
            .map(Enrollment::getStudentId)
            .collect(Collectors.toSet());
        studentIds.addAll(studentsFromEnrollments);
        
        log.info("Section {} enrollment: Found {} enrollment records with batchId={}, resulting in {} unique students from enrollments", 
            sectionId, sectionEnrollments.size(), sectionId, studentsFromEnrollments.size());
        
        // Method 2: Find students via identity service (users with sectionId field)
        try {
            Set<String> studentsFromIdentity = getStudentsFromIdentityServiceBySection(sectionId, clientId);
            log.info("Section {} enrollment: Found {} students from identity service with sectionId={}", 
                sectionId, studentsFromIdentity.size(), sectionId);
            studentIds.addAll(studentsFromIdentity);
            
            if (studentsFromIdentity.size() > studentsFromEnrollments.size()) {
                log.info("Section {} enrollment: Identity service found {} additional students not in enrollment records", 
                    sectionId, studentsFromIdentity.size() - studentsFromEnrollments.size());
            }
        } catch (Exception e) {
            log.warn("Section {} enrollment: Failed to fetch students from identity service: {}. Proceeding with enrollment-based students only.", 
                sectionId, e.getMessage());
        }
        
        log.info("Section {} enrollment: Total unique students to enroll: {}", sectionId, studentIds.size());
        
        if (studentIds.isEmpty()) {
            log.warn("Section {} enrollment: No students found in section via enrollments or identity service.", sectionId);
        } else {
            log.debug("Section {} enrollment: Student IDs to enroll: {}", sectionId, studentIds);
        }
        
        return enrollStudentsToCourse(studentIds, courseId, classId, sectionId, classEntity.getInstituteId(), clientId);
    }
    
    public List<BulkEnrollmentResult> enrollClassToCourses(String classId, BatchEnrollmentRequest request) {
        List<BulkEnrollmentResult> results = new ArrayList<>();
        for (String courseId : request.getCourseIds()) {
            try {
                BulkEnrollmentResult result = enrollClassToCourse(classId, courseId);
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to enroll class {} to course {}: {}", classId, courseId, e.getMessage());
                BulkEnrollmentResult errorResult = new BulkEnrollmentResult();
                errorResult.setTotalStudents(0L);
                errorResult.setEnrolledStudents(0L);
                errorResult.setFailedStudents(1L);
                List<String> errors = new ArrayList<>();
                errors.add("Course " + courseId + ": " + e.getMessage());
                errorResult.setErrorMessages(errors);
                results.add(errorResult);
            }
        }
        return results;
    }
    
    public List<BulkEnrollmentResult> enrollSectionToCourses(String sectionId, BatchEnrollmentRequest request) {
        List<BulkEnrollmentResult> results = new ArrayList<>();
        for (String courseId : request.getCourseIds()) {
            try {
                BulkEnrollmentResult result = enrollSectionToCourse(sectionId, courseId);
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to enroll section {} to course {}: {}", sectionId, courseId, e.getMessage());
                BulkEnrollmentResult errorResult = new BulkEnrollmentResult();
                errorResult.setTotalStudents(0L);
                errorResult.setEnrolledStudents(0L);
                errorResult.setFailedStudents(1L);
                List<String> errors = new ArrayList<>();
                errors.add("Course " + courseId + ": " + e.getMessage());
                errorResult.setErrorMessages(errors);
                results.add(errorResult);
            }
        }
        return results;
    }
    
    private void validateCourseAssignment(String courseId, String classId, String sectionId, UUID clientId) {
        // Get course from content service
        String courseUrl = gatewayUrl + "/content/courses/" + courseId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<CourseResponseDTO> response = getRestTemplate().exchange(
                courseUrl,
                HttpMethod.GET,
                entity,
                CourseResponseDTO.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Course not found: " + courseId);
            }
            
            CourseResponseDTO course = response.getBody();
            if (!course.getIsPublished()) {
                throw new IllegalArgumentException("Course is not published: " + courseId);
            }
            
            // Check if course is assigned to class or section
            boolean isAssigned = false;
            if (course.getAssignedToClassIds() != null && course.getAssignedToClassIds().contains(classId)) {
                isAssigned = true;
            }
            if (sectionId != null && course.getAssignedToSectionIds() != null && course.getAssignedToSectionIds().contains(sectionId)) {
                isAssigned = true;
            }
            
            if (!isAssigned) {
                throw new IllegalArgumentException("Course is not assigned to this class/section. Please assign the course first.");
            }
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Course not found: " + courseId);
            }
            throw new RuntimeException("Failed to validate course: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("Failed to validate course: " + e.getMessage(), e);
        }
    }
    
    private BulkEnrollmentResult enrollStudentsToCourse(Set<String> studentIds, String courseId, 
                                                        String classId, String sectionId, String instituteId, UUID clientId) {
        BulkEnrollmentResult result = new BulkEnrollmentResult();
        result.setTotalStudents((long) studentIds.size());
        result.setEnrolledStudents(0L);
        result.setSkippedStudents(0L);
        result.setFailedStudents(0L);
        List<String> enrolledStudentIds = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        
        log.info("Enrolling {} students to course {} (classId={}, sectionId={}, clientId={})", 
            studentIds.size(), courseId, classId, sectionId, clientId);
        log.debug("Student IDs to enroll: {}", studentIds);
        
        for (String studentId : studentIds) {
            try {
                // Check if already enrolled
                if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId)) {
                    result.setSkippedStudents(result.getSkippedStudents() + 1);
                    continue;
                }
                
                // Remove placeholder enrollment if it exists (student was associated but not enrolled in any course)
                List<Enrollment> placeholderEnrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId)
                    .stream()
                    .filter(e -> "__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()) && 
                                 (classId != null && classId.equals(e.getClassId())) &&
                                 (sectionId == null || sectionId.equals(e.getBatchId())))
                    .collect(Collectors.toList());
                for (Enrollment placeholder : placeholderEnrollments) {
                    enrollmentRepository.delete(placeholder);
                    log.debug("Removed placeholder enrollment for student {} when enrolling in course {}", studentId, courseId);
                }
                
                // Create enrollment
                CreateEnrollmentRequest enrollmentRequest = new CreateEnrollmentRequest();
                enrollmentRequest.setCourseId(courseId);
                enrollmentRequest.setClassId(classId);
                enrollmentRequest.setBatchId(sectionId); // batchId is used for sectionId
                enrollmentRequest.setInstituteId(instituteId);
                
                EnrollmentDTO enrollment = enrollmentService.enrollStudent(studentId, enrollmentRequest);
                enrolledStudentIds.add(studentId);
                result.setEnrolledStudents(result.getEnrolledStudents() + 1);
                log.info("Successfully enrolled student {} in course {} (classId={}, sectionId={}). Enrollment ID: {}, Course ID: {}", 
                    studentId, courseId, classId, sectionId, enrollment.getId(), enrollment.getCourseId());
                
                // Verify enrollment was created by querying it back
                boolean exists = enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
                if (exists) {
                    log.debug("Verified: Enrollment exists for student {} and course {}", studentId, courseId);
                } else {
                    log.error("ERROR: Enrollment was created but verification query returned false for student {} and course {}", 
                        studentId, courseId);
                }
                
            } catch (Exception e) {
                log.warn("Failed to enroll student {} to course {}: {}", studentId, courseId, e.getMessage());
                errorMessages.add("Student " + studentId + ": " + e.getMessage());
                result.setFailedStudents(result.getFailedStudents() + 1);
            }
        }
        
        result.setEnrolledStudentIds(enrolledStudentIds);
        result.setErrorMessages(errorMessages);
        
        return result;
    }
    
    /**
     * Get all students from identity service that belong to a specific section.
     * This finds students by their sectionId field in the user record.
     * 
     * @param sectionId The section ID
     * @param clientId The client/tenant ID
     * @return Set of student IDs
     */
    private Set<String> getStudentsFromIdentityServiceBySection(String sectionId, UUID clientId) {
        Set<String> studentIds = new java.util.HashSet<>();
        
        try {
            // Get all students (users with role STUDENT) from identity service
            String url = gatewayUrl + "/idp/users/role/STUDENT";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            log.debug("Fetching students from identity service for section {}", sectionId);
            ResponseEntity<UserResponseDTO[]> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                entity,
                UserResponseDTO[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Filter students by sectionId
                for (UserResponseDTO user : response.getBody()) {
                    if (sectionId.equals(user.getSectionId())) {
                        studentIds.add(user.getId());
                        log.debug("Found student {} in section {} via identity service", user.getId(), sectionId);
                    }
                }
                log.info("Identity service returned {} total students, {} belong to section {}", 
                    response.getBody().length, studentIds.size(), sectionId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch students from identity service for section {}: {}", sectionId, e.getMessage());
            throw new RuntimeException("Failed to fetch students from identity service: " + e.getMessage(), e);
        }
        
        return studentIds;
    }
    
    // Helper DTO for user response from identity service
    private static class UserResponseDTO {
        private String id;
        private String sectionId;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getSectionId() { return sectionId; }
        public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    }
    
    // Helper DTO for course response
    private static class CourseResponseDTO {
        private String id;
        private Boolean isPublished;
        private List<String> assignedToClassIds;
        private List<String> assignedToSectionIds;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public Boolean getIsPublished() { return isPublished; }
        public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
        
        public List<String> getAssignedToClassIds() { return assignedToClassIds; }
        public void setAssignedToClassIds(List<String> assignedToClassIds) { this.assignedToClassIds = assignedToClassIds; }
        
        public List<String> getAssignedToSectionIds() { return assignedToSectionIds; }
        public void setAssignedToSectionIds(List<String> assignedToSectionIds) { this.assignedToSectionIds = assignedToSectionIds; }
    }
}

