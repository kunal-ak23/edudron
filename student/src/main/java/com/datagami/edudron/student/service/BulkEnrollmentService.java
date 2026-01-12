package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.BatchEnrollmentRequest;
import com.datagami.edudron.student.dto.BulkEnrollmentResult;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
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
        
        // Get all students in the section (using batchId for backward compatibility)
        List<Enrollment> sectionEnrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
        Set<String> studentIds = sectionEnrollments.stream()
            .map(Enrollment::getStudentId)
            .collect(Collectors.toSet());
        
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
        
        for (String studentId : studentIds) {
            try {
                // Check if already enrolled
                if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId)) {
                    result.setSkippedStudents(result.getSkippedStudents() + 1);
                    continue;
                }
                
                // Create enrollment
                CreateEnrollmentRequest enrollmentRequest = new CreateEnrollmentRequest();
                enrollmentRequest.setCourseId(courseId);
                enrollmentRequest.setClassId(classId);
                enrollmentRequest.setBatchId(sectionId); // batchId is used for sectionId
                enrollmentRequest.setInstituteId(instituteId);
                
                enrollmentService.enrollStudent(studentId, enrollmentRequest);
                enrolledStudentIds.add(studentId);
                result.setEnrolledStudents(result.getEnrolledStudents() + 1);
                
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

