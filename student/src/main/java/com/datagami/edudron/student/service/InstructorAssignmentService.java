package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.InstructorAssignment;
import com.datagami.edudron.student.domain.InstructorAssignment.AssignmentType;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.CreateInstructorAssignmentRequest;
import com.datagami.edudron.student.dto.InstructorAccessDTO;
import com.datagami.edudron.student.dto.InstructorAssignmentDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.InstructorAssignmentRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class InstructorAssignmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(InstructorAssignmentService.class);
    
    @Autowired
    private InstructorAssignmentRepository assignmentRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate getRestTemplate() {
        // Double-checked locking for thread safety
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate template = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new TenantContextRestTemplateInterceptor());
                    interceptors.add((request, body, execution) -> {
                        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest currentRequest = attributes.getRequest();
                            String authHeader = currentRequest.getHeader("Authorization");
                            if (authHeader != null && !authHeader.isBlank()) {
                                if (!request.getHeaders().containsKey("Authorization")) {
                                    request.getHeaders().add("Authorization", authHeader);
                                }
                            }
                        }
                        return execution.execute(request, body);
                    });
                    template.setInterceptors(interceptors);
                    restTemplate = template;
                }
            }
        }
        return restTemplate;
    }
    
    // ==================== CRUD Operations ====================
    
    public InstructorAssignmentDTO assignToClass(CreateInstructorAssignmentRequest request) {
        UUID clientId = getClientId();
        
        // Validate the class exists
        Class classEntity = classRepository.findByIdAndClientId(request.getClassId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + request.getClassId()));
        
        // Check if assignment already exists
        if (assignmentRepository.existsByClientIdAndInstructorUserIdAndClassId(
                clientId, request.getInstructorUserId(), request.getClassId())) {
            throw new IllegalArgumentException("Instructor is already assigned to this class");
        }
        
        InstructorAssignment assignment = new InstructorAssignment();
        assignment.setId(UlidGenerator.nextUlid());
        assignment.setClientId(clientId);
        assignment.setInstructorUserId(request.getInstructorUserId());
        assignment.setAssignmentType(AssignmentType.CLASS);
        assignment.setClassId(request.getClassId());
        
        InstructorAssignment saved = assignmentRepository.save(assignment);
        logger.info("Assigned instructor {} to class {}", request.getInstructorUserId(), request.getClassId());
        
        return toDTO(saved, classEntity.getName(), null, null);
    }
    
    public InstructorAssignmentDTO assignToSection(CreateInstructorAssignmentRequest request) {
        UUID clientId = getClientId();
        
        // Validate the section exists
        Section section = sectionRepository.findByIdAndClientId(request.getSectionId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + request.getSectionId()));
        
        // Check if assignment already exists
        if (assignmentRepository.existsByClientIdAndInstructorUserIdAndSectionId(
                clientId, request.getInstructorUserId(), request.getSectionId())) {
            throw new IllegalArgumentException("Instructor is already assigned to this section");
        }
        
        InstructorAssignment assignment = new InstructorAssignment();
        assignment.setId(UlidGenerator.nextUlid());
        assignment.setClientId(clientId);
        assignment.setInstructorUserId(request.getInstructorUserId());
        assignment.setAssignmentType(AssignmentType.SECTION);
        assignment.setSectionId(request.getSectionId());
        
        InstructorAssignment saved = assignmentRepository.save(assignment);
        logger.info("Assigned instructor {} to section {}", request.getInstructorUserId(), request.getSectionId());
        
        return toDTO(saved, null, section.getName(), null);
    }
    
    public InstructorAssignmentDTO assignToCourse(CreateInstructorAssignmentRequest request) {
        UUID clientId = getClientId();
        
        // Validate course exists via content service
        String courseName = validateCourseExists(request.getCourseId());
        
        // Check if assignment already exists (without scope - direct course assignment)
        if (assignmentRepository.existsByClientIdAndInstructorUserIdAndCourseId(
                clientId, request.getInstructorUserId(), request.getCourseId())) {
            throw new IllegalArgumentException("Instructor is already assigned to this course");
        }
        
        InstructorAssignment assignment = new InstructorAssignment();
        assignment.setId(UlidGenerator.nextUlid());
        assignment.setClientId(clientId);
        assignment.setInstructorUserId(request.getInstructorUserId());
        assignment.setAssignmentType(AssignmentType.COURSE);
        assignment.setCourseId(request.getCourseId());
        assignment.setScopedClassIds(request.getScopedClassIds());
        assignment.setScopedSectionIds(request.getScopedSectionIds());
        
        InstructorAssignment saved = assignmentRepository.save(assignment);
        logger.info("Assigned instructor {} to course {} with scope: classes={}, sections={}", 
            request.getInstructorUserId(), request.getCourseId(), 
            request.getScopedClassIds(), request.getScopedSectionIds());
        
        return toDTO(saved, null, null, courseName);
    }
    
    public InstructorAssignmentDTO createAssignment(CreateInstructorAssignmentRequest request) {
        if (request.getAssignmentType() == null) {
            throw new IllegalArgumentException("Assignment type is required");
        }
        
        switch (request.getAssignmentType()) {
            case CLASS:
                if (request.getClassId() == null || request.getClassId().isEmpty()) {
                    throw new IllegalArgumentException("Class ID is required for CLASS assignment");
                }
                return assignToClass(request);
            case SECTION:
                if (request.getSectionId() == null || request.getSectionId().isEmpty()) {
                    throw new IllegalArgumentException("Section ID is required for SECTION assignment");
                }
                return assignToSection(request);
            case COURSE:
                if (request.getCourseId() == null || request.getCourseId().isEmpty()) {
                    throw new IllegalArgumentException("Course ID is required for COURSE assignment");
                }
                return assignToCourse(request);
            default:
                throw new IllegalArgumentException("Invalid assignment type: " + request.getAssignmentType());
        }
    }
    
    public void removeAssignment(String assignmentId) {
        UUID clientId = getClientId();
        
        InstructorAssignment assignment = assignmentRepository.findByIdAndClientId(assignmentId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        
        assignmentRepository.delete(assignment);
        logger.info("Removed instructor assignment: {}", assignmentId);
    }
    
    @Transactional(readOnly = true)
    public List<InstructorAssignmentDTO> getAssignmentsForInstructor(String instructorUserId) {
        UUID clientId = getClientId();
        
        List<InstructorAssignment> assignments = assignmentRepository
            .findByClientIdAndInstructorUserId(clientId, instructorUserId);
        
        return assignments.stream()
            .map(this::toDTOWithNames)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<InstructorAssignmentDTO> getAllAssignments() {
        UUID clientId = getClientId();
        
        List<InstructorAssignment> assignments = assignmentRepository.findByClientId(clientId);
        
        return assignments.stream()
            .map(this::toDTOWithNames)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public InstructorAssignmentDTO getAssignment(String assignmentId) {
        UUID clientId = getClientId();
        
        InstructorAssignment assignment = assignmentRepository.findByIdAndClientId(assignmentId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        
        return toDTOWithNames(assignment);
    }
    
    // ==================== Access Checking ====================
    
    @Transactional(readOnly = true)
    public InstructorAccessDTO getInstructorAccess(String instructorUserId) {
        UUID clientId = getClientId();
        
        List<InstructorAssignment> assignments = assignmentRepository
            .findByClientIdAndInstructorUserId(clientId, instructorUserId);
        
        Set<String> allowedClassIds = new HashSet<>();
        Set<String> allowedSectionIds = new HashSet<>();
        Set<String> allowedCourseIds = new HashSet<>();
        Set<String> scopedCourseIds = new HashSet<>();
        
        for (InstructorAssignment assignment : assignments) {
            switch (assignment.getAssignmentType()) {
                case CLASS:
                    // Add the class and all its sections
                    allowedClassIds.add(assignment.getClassId());
                    List<Section> sectionsInClass = sectionRepository
                        .findByClientIdAndClassId(clientId, assignment.getClassId());
                    for (Section section : sectionsInClass) {
                        allowedSectionIds.add(section.getId());
                    }
                    break;
                    
                case SECTION:
                    // Add the section and its parent class for navigation
                    allowedSectionIds.add(assignment.getSectionId());
                    Section section = sectionRepository
                        .findByIdAndClientId(assignment.getSectionId(), clientId)
                        .orElse(null);
                    if (section != null && section.getClassId() != null) {
                        // Add parent class so instructor can navigate to it in tree view
                        allowedClassIds.add(section.getClassId());
                    }
                    break;
                    
                case COURSE:
                    allowedCourseIds.add(assignment.getCourseId());
                    // If course has scope restrictions, track it
                    if ((assignment.getScopedClassIds() != null && !assignment.getScopedClassIds().isEmpty()) ||
                        (assignment.getScopedSectionIds() != null && !assignment.getScopedSectionIds().isEmpty())) {
                        scopedCourseIds.add(assignment.getCourseId());
                        // Add scoped classes/sections
                        if (assignment.getScopedClassIds() != null) {
                            allowedClassIds.addAll(assignment.getScopedClassIds());
                        }
                        if (assignment.getScopedSectionIds() != null) {
                            allowedSectionIds.addAll(assignment.getScopedSectionIds());
                        }
                    }
                    break;
            }
        }
        
        // Derive course access from class/section assignments
        // Courses assigned to any of the instructor's classes or sections are accessible
        Set<String> derivedCourseIds = getCourseIdsByAssignments(allowedClassIds, allowedSectionIds);
        allowedCourseIds.addAll(derivedCourseIds);
        
        return new InstructorAccessDTO(
            instructorUserId,
            allowedClassIds,
            allowedSectionIds,
            allowedCourseIds,
            scopedCourseIds
        );
    }
    
    /**
     * Get course IDs that are assigned to any of the specified classes or sections.
     * Calls the content service to get this information.
     */
    private Set<String> getCourseIdsByAssignments(Set<String> classIds, Set<String> sectionIds) {
        logger.info("=== getCourseIdsByAssignments START === classIds: {}, sectionIds: {}", classIds, sectionIds);
        
        if ((classIds == null || classIds.isEmpty()) && (sectionIds == null || sectionIds.isEmpty())) {
            logger.info("No class or section IDs provided, returning empty set");
            return new HashSet<>();
        }
        
        try {
            StringBuilder urlBuilder = new StringBuilder(gatewayUrl + "/content/courses/by-assignments?");
            List<String> params = new ArrayList<>();
            
            if (classIds != null && !classIds.isEmpty()) {
                for (String classId : classIds) {
                    params.add("classIds=" + classId);
                }
            }
            if (sectionIds != null && !sectionIds.isEmpty()) {
                for (String sectionId : sectionIds) {
                    params.add("sectionIds=" + sectionId);
                }
            }
            
            String url = urlBuilder.toString() + String.join("&", params);
            String clientId = TenantContext.getClientId();
            
            logger.info("Calling content service: URL={}, X-Client-Id={}", url, clientId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Client-Id", clientId);
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            logger.info("Content service response status: {}, body size: {}", 
                response.getStatusCode(), 
                response.getBody() != null ? response.getBody().size() : "null");
            
            Set<String> courseIds = new HashSet<>();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                for (Map<String, Object> course : response.getBody()) {
                    Object id = course.get("id");
                    if (id != null) {
                        courseIds.add(id.toString());
                    }
                }
            }
            
            logger.info("=== getCourseIdsByAssignments END === Returning {} course IDs: {}", courseIds.size(), courseIds);
            return courseIds;
        } catch (Exception e) {
            logger.error("=== getCourseIdsByAssignments FAILED === Error: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }
    
    @Transactional(readOnly = true)
    public boolean canAccessClass(String instructorUserId, String classId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        return access.canAccessClass(classId);
    }
    
    @Transactional(readOnly = true)
    public boolean canAccessSection(String instructorUserId, String sectionId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        return access.canAccessSection(sectionId);
    }
    
    @Transactional(readOnly = true)
    public boolean canAccessCourse(String instructorUserId, String courseId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        return access.canAccessCourse(courseId);
    }
    
    /**
     * Check if instructor can access a course for a specific section.
     * This is more restrictive - checks both course access and section access if course is scoped.
     */
    @Transactional(readOnly = true)
    public boolean canAccessCourseForSection(String instructorUserId, String courseId, String sectionId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        
        // First check if instructor has course access
        if (!access.canAccessCourse(courseId)) {
            return false;
        }
        
        // If course is scoped, check section access
        if (access.isCourseScoped(courseId)) {
            return access.canAccessSection(sectionId);
        }
        
        // Course is not scoped, so full access to course
        return true;
    }
    
    @Transactional(readOnly = true)
    public Set<String> getAllowedClassIds(String instructorUserId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        return access.getAllowedClassIds();
    }
    
    @Transactional(readOnly = true)
    public Set<String> getAllowedSectionIds(String instructorUserId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        return access.getAllowedSectionIds();
    }
    
    @Transactional(readOnly = true)
    public Set<String> getAllowedCourseIds(String instructorUserId) {
        InstructorAccessDTO access = getInstructorAccess(instructorUserId);
        return access.getAllowedCourseIds();
    }
    
    // ==================== Helper Methods ====================
    
    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }
    
    private String validateCourseExists(String courseId) {
        try {
            String url = gatewayUrl + "/content/courses/" + courseId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Client-Id", TenantContext.getClientId());
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("title");
            }
            throw new IllegalArgumentException("Course not found: " + courseId);
        } catch (Exception e) {
            logger.error("Error validating course: {}", e.getMessage());
            throw new IllegalArgumentException("Course not found or inaccessible: " + courseId);
        }
    }
    
    private String getCourseName(String courseId) {
        try {
            return validateCourseExists(courseId);
        } catch (Exception e) {
            return null;
        }
    }
    
    private InstructorAssignmentDTO toDTO(InstructorAssignment assignment, 
                                          String className, String sectionName, String courseName) {
        InstructorAssignmentDTO dto = new InstructorAssignmentDTO();
        dto.setId(assignment.getId());
        dto.setClientId(assignment.getClientId());
        dto.setInstructorUserId(assignment.getInstructorUserId());
        dto.setAssignmentType(assignment.getAssignmentType());
        dto.setClassId(assignment.getClassId());
        dto.setClassName(className);
        dto.setSectionId(assignment.getSectionId());
        dto.setSectionName(sectionName);
        dto.setCourseId(assignment.getCourseId());
        dto.setCourseName(courseName);
        dto.setScopedClassIds(assignment.getScopedClassIds());
        dto.setScopedSectionIds(assignment.getScopedSectionIds());
        dto.setCreatedAt(assignment.getCreatedAt());
        dto.setUpdatedAt(assignment.getUpdatedAt());
        return dto;
    }
    
    private InstructorAssignmentDTO toDTOWithNames(InstructorAssignment assignment) {
        UUID clientId = getClientId();
        String className = null;
        String sectionName = null;
        String courseName = null;
        
        if (assignment.getClassId() != null) {
            classRepository.findByIdAndClientId(assignment.getClassId(), clientId)
                .ifPresent(c -> {});
            Class classEntity = classRepository.findByIdAndClientId(assignment.getClassId(), clientId).orElse(null);
            if (classEntity != null) {
                className = classEntity.getName();
            }
        }
        
        if (assignment.getSectionId() != null) {
            Section section = sectionRepository.findByIdAndClientId(assignment.getSectionId(), clientId).orElse(null);
            if (section != null) {
                sectionName = section.getName();
            }
        }
        
        if (assignment.getCourseId() != null) {
            courseName = getCourseName(assignment.getCourseId());
        }
        
        return toDTO(assignment, className, sectionName, courseName);
    }
}
