package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.dto.SectionStudentDTO;
import com.datagami.edudron.student.dto.StudentClassSectionInfoDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import com.datagami.edudron.student.repo.SectionRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnrollmentService {
    
    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private InstituteRepository instituteRepository;
    
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
    
    /**
     * Get the current user's role from SecurityContext.
     */
    private String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String authorityStr = authority.getAuthority();
                if (authorityStr.startsWith("ROLE_")) {
                    return authorityStr.substring(5); // Remove "ROLE_" prefix
                }
            }
        }
        return null;
    }
    
    /**
     * Check if student self-enrollment is enabled for the current tenant.
     */
    private boolean isStudentSelfEnrollmentEnabled(UUID clientId) {
        try {
            String featureUrl = gatewayUrl + "/api/tenant/features/student-self-enrollment";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Boolean> response = getRestTemplate().exchange(
                featureUrl,
                HttpMethod.GET,
                entity,
                Boolean.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Boolean.TRUE.equals(response.getBody());
            }
            
            // Default to false if call fails
            log.warn("Failed to fetch student self-enrollment feature, defaulting to false");
            return false;
        } catch (Exception e) {
            log.error("Error checking student self-enrollment feature: {}", e.getMessage(), e);
            // Default to false on error
            return false;
        }
    }
    
    public EnrollmentDTO enrollStudent(String studentId, CreateEnrollmentRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Check if user is a student trying to self-enroll
        String userRole = getCurrentUserRole();
        boolean isStudent = "STUDENT".equals(userRole);
        
        if (isStudent) {
            // Check if student self-enrollment is enabled for this tenant
            boolean selfEnrollmentEnabled = isStudentSelfEnrollmentEnabled(clientId);
            if (!selfEnrollmentEnabled) {
                throw new org.springframework.security.access.AccessDeniedException(
                    "Student self-enrollment is disabled. Please contact your instructor to enroll you in this course."
                );
            }
        }
        // Admins, instructors, and other roles can always enroll students
        
        // Check if already enrolled
        if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, request.getCourseId())) {
            throw new IllegalArgumentException("Student is already enrolled in this course");
        }
        
        String instituteId = null;
        String classId = null;
        String sectionId = null;
        
        // Validate hierarchy if provided
        if (request.getBatchId() != null && !request.getBatchId().isBlank()) {
            // batchId now represents sectionId
            final String finalSectionId = request.getBatchId();
            Section section = sectionRepository.findByIdAndClientId(finalSectionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + finalSectionId));
            
            if (!section.getIsActive()) {
                throw new IllegalArgumentException("Section is not active");
            }
            
            // Get class from section
            final String finalClassId = section.getClassId();
            Class classEntity = classRepository.findByIdAndClientId(finalClassId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + finalClassId));
            
            if (!classEntity.getIsActive()) {
                throw new IllegalArgumentException("Class is not active");
            }
            
            // Get institute from class
            final String finalInstituteId = classEntity.getInstituteId();
            Institute institute = instituteRepository.findByIdAndClientId(finalInstituteId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + finalInstituteId));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
            
            // Check section capacity
            if (section.getMaxStudents() != null) {
                long currentCount = sectionRepository.countStudentsInSection(clientId, section.getId());
                if (currentCount >= section.getMaxStudents()) {
                    throw new IllegalArgumentException("Section is full");
                }
            }
            
            // Assign to outer variables after validation
            sectionId = finalSectionId;
            classId = finalClassId;
            instituteId = finalInstituteId;
        } else if (request.getClassId() != null && !request.getClassId().isBlank()) {
            // If classId is provided but no sectionId, validate class
            final String finalClassId = request.getClassId();
            Class classEntity = classRepository.findByIdAndClientId(finalClassId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + finalClassId));
            
            if (!classEntity.getIsActive()) {
                throw new IllegalArgumentException("Class is not active");
            }
            
            final String finalInstituteId = classEntity.getInstituteId();
            Institute institute = instituteRepository.findByIdAndClientId(finalInstituteId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + finalInstituteId));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
            
            // Assign to outer variables after validation
            classId = finalClassId;
            instituteId = finalInstituteId;
        } else if (request.getInstituteId() != null && !request.getInstituteId().isBlank()) {
            // If only instituteId is provided, validate it
            final String finalInstituteId = request.getInstituteId();
            Institute institute = instituteRepository.findByIdAndClientId(finalInstituteId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + finalInstituteId));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
            
            // Assign to outer variable after validation
            instituteId = finalInstituteId;
        }
        
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UlidGenerator.nextUlid());
        enrollment.setClientId(clientId);
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(request.getCourseId());
        enrollment.setBatchId(sectionId); // Keep batchId for backward compatibility
        enrollment.setInstituteId(instituteId);
        enrollment.setClassId(classId);
        
        Enrollment saved = enrollmentRepository.save(enrollment);
        return toDTO(saved);
    }
    
    public EnrollmentDTO getEnrollment(String enrollmentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        
        if (!enrollment.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Enrollment not found: " + enrollmentId);
        }
        
        return toDTO(enrollment);
    }
    
    public List<EnrollmentDTO> getStudentEnrollments(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId);
        return enrollments.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public Page<EnrollmentDTO> getStudentEnrollments(String studentId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId, pageable);
        return enrollments.map(this::toDTO);
    }
    
    public List<EnrollmentDTO> getCourseEnrollments(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndCourseId(clientId, courseId);
        return enrollments.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public boolean isEnrolled(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
    }
    
    public void unenroll(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("Enrollment not found");
        }
        Enrollment enrollment = enrollments.get(0); // Use first (most recent) enrollment if duplicates exist
        
        enrollmentRepository.delete(enrollment);
    }
    
    /**
     * Get student's current class and section information from their enrollments.
     * Returns the most recent enrollment that has both class and section associations.
     * Returns null if no class/section association is found.
     */
    @Transactional(readOnly = true)
    public StudentClassSectionInfoDTO getStudentClassSectionInfo(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        log.debug("Fetching class/section info for student {} in tenant {}", studentId, clientId);
        
        // Get all enrollments for the student
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId);
        log.debug("Found {} enrollments for student {}", enrollments.size(), studentId);
        
        // Find the most recent enrollment with both classId and sectionId
        Enrollment enrollmentWithSection = null;
        Enrollment enrollmentWithClass = null;
        Enrollment enrollmentWithOnlySection = null;
        
        for (Enrollment enrollment : enrollments) {
            // Prefer enrollment with both class and section
            if (enrollment.getClassId() != null && enrollment.getBatchId() != null) {
                enrollmentWithSection = enrollment;
                log.debug("Found enrollment with both class {} and section {}", 
                    enrollment.getClassId(), enrollment.getBatchId());
                break; // Found the best match
            }
            // Fallback to enrollment with just class
            if (enrollmentWithClass == null && enrollment.getClassId() != null) {
                enrollmentWithClass = enrollment;
                log.debug("Found enrollment with class {} (no section)", enrollment.getClassId());
            }
            // Also check for enrollment with just section (batchId)
            if (enrollmentWithOnlySection == null && enrollment.getBatchId() != null) {
                enrollmentWithOnlySection = enrollment;
                log.debug("Found enrollment with section {} (no class in enrollment)", enrollment.getBatchId());
            }
        }
        
        Enrollment targetEnrollment = enrollmentWithSection != null ? enrollmentWithSection : 
                                      (enrollmentWithClass != null ? enrollmentWithClass : enrollmentWithOnlySection);
        
        if (targetEnrollment == null) {
            log.debug("No enrollment with class/section found for student {}", studentId);
            return null; // No class/section association found
        }
        
        String classId = targetEnrollment.getClassId();
        String sectionId = targetEnrollment.getBatchId(); // batchId is sectionId
        
        // If we have a sectionId but no classId, get classId from the section
        if (sectionId != null && classId == null) {
            Section section = sectionRepository.findByIdAndClientId(sectionId, clientId).orElse(null);
            if (section != null) {
                classId = section.getClassId();
                log.debug("Retrieved classId {} from section {}", classId, sectionId);
            }
        }
        
        String className = null;
        String sectionName = null;
        
        // Fetch class name
        if (classId != null) {
            Class classEntity = classRepository.findByIdAndClientId(classId, clientId).orElse(null);
            if (classEntity != null) {
                className = classEntity.getName();
                log.debug("Found class name: {}", className);
            } else {
                log.warn("Class {} not found for student {}", classId, studentId);
            }
        }
        
        // Fetch section name
        if (sectionId != null) {
            Section section = sectionRepository.findByIdAndClientId(sectionId, clientId).orElse(null);
            if (section != null) {
                sectionName = section.getName();
                log.debug("Found section name: {}", sectionName);
            } else {
                log.warn("Section {} not found for student {}", sectionId, studentId);
            }
        }
        
        StudentClassSectionInfoDTO result = new StudentClassSectionInfoDTO(classId, className, sectionId, sectionName);
        log.debug("Returning class/section info for student {}: class={}, section={}", 
            studentId, className, sectionName);
        return result;
    }
    
    /**
     * Get all students enrolled in a section.
     * Returns a list of student information (id, name, email, phone) for students associated with the section.
     */
    @Transactional(readOnly = true)
    public List<SectionStudentDTO> getStudentsBySection(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate section exists
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        // Get all enrollments for this section (batchId is sectionId)
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
        
        // Extract unique student IDs
        List<String> studentIds = enrollments.stream()
            .map(Enrollment::getStudentId)
            .distinct()
            .collect(Collectors.toList());
        
        if (studentIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Fetch user details from identity service
        List<SectionStudentDTO> students = new ArrayList<>();
        for (String studentId : studentIds) {
            try {
                UserDTO user = getUserFromIdentityService(studentId);
                if (user != null) {
                    students.add(new SectionStudentDTO(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getPhone()
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch user details for student {}: {}", studentId, e.getMessage());
                // Continue with other students even if one fails
            }
        }
        
        return students;
    }
    
    /**
     * Inner class to deserialize user info from identity service
     */
    private static class UserDTO {
        private String id;
        private String name;
        private String email;
        private String phone;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }
    
    /**
     * Fetch user details from identity service
     */
    private UserDTO getUserFromIdentityService(String userId) {
        try {
            String url = gatewayUrl + "/idp/users/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<UserDTO> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                entity,
                UserDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not fetch user details for user {}: {}", userId, e.getMessage());
        }
        return null;
    }
    
    private EnrollmentDTO toDTO(Enrollment enrollment) {
        EnrollmentDTO dto = new EnrollmentDTO();
        dto.setId(enrollment.getId());
        dto.setClientId(enrollment.getClientId());
        dto.setStudentId(enrollment.getStudentId());
        dto.setCourseId(enrollment.getCourseId());
        dto.setBatchId(enrollment.getBatchId()); // Keep for backward compatibility
        dto.setInstituteId(enrollment.getInstituteId());
        dto.setClassId(enrollment.getClassId());
        dto.setEnrolledAt(enrollment.getEnrolledAt());
        return dto;
    }
}

