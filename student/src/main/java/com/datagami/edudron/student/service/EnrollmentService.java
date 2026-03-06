package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.BulkTransferEnrollmentRequest;
import com.datagami.edudron.student.dto.BulkTransferEnrollmentResponse;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.dto.SectionStudentDTO;
import com.datagami.edudron.student.dto.ClassStudentDTO;
import com.datagami.edudron.student.dto.StudentClassSectionInfoDTO;
import com.datagami.edudron.student.dto.TransferEnrollmentError;
import com.datagami.edudron.student.dto.TransferEnrollmentRequest;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    
    @Autowired
    private CommonEventService eventService;

    @Autowired
    private StudentAuditService auditService;

    @Autowired
    private LectureViewSessionService sessionService;

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();
    
    private RestTemplate getRestTemplate() {
        // Double-checked locking for thread safety
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate template = new RestTemplate();
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
                    template.setInterceptors(interceptors);
                    restTemplate = template;
                }
            }
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
     * Get the current user's ID from the security context.
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
     * Get the current user's email from the identity service.
     */
    private String getCurrentUserEmail() {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return null;
            }
            
            UserDTO user = getUserFromIdentityService(userId);
            return user != null ? user.getEmail() : null;
        } catch (Exception e) {
            log.debug("Could not determine user email: {}", e.getMessage());
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
        // For placeholder enrollments, allow multiple (one per section)
        // For real courses, prevent duplicates
        if (request.getCourseId().equals("__PLACEHOLDER_ASSOCIATION__")) {
            // Check if placeholder already exists for this specific section
            if (request.getBatchId() != null) {
                List<Enrollment> existingPlaceholders = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, request.getCourseId());
                boolean sectionPlaceholderExists = existingPlaceholders.stream()
                    .anyMatch(e -> request.getBatchId().equals(e.getBatchId()));
                if (sectionPlaceholderExists) {
                    throw new IllegalArgumentException("Student is already associated with this section");
                }
            }
        } else {
            // For real courses, check for any existing enrollment
            if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, request.getCourseId())) {
                throw new IllegalArgumentException("Student is already enrolled in this course");
            }
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
        log.info("Created enrollment for student {} in course {} (classId={}, sectionId={}, enrollmentId={})", 
            studentId, request.getCourseId(), classId, sectionId, saved.getId());
        
        // Log enrollment event
        String currentUserId = getCurrentUserId();
        String currentUserEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "enrollmentId", saved.getId(),
            "studentId", studentId,
            "courseId", request.getCourseId(),
            "instituteId", instituteId != null ? instituteId : "",
            "classId", classId != null ? classId : "",
            "sectionId", sectionId != null ? sectionId : ""
        );
        eventService.logUserAction("COURSE_ENROLLED", currentUserId, currentUserEmail, "/api/courses/" + request.getCourseId() + "/enroll", eventData);
        auditService.logCrud(clientId, "CREATE", "Enrollment", saved.getId(), currentUserId, currentUserEmail, eventData);

        // Automatically enroll student in all published courses assigned to this section/class
        // This happens after the main enrollment is created to ensure consistency
        try {
            autoEnrollStudentInAssignedCourses(studentId, sectionId, classId, instituteId, clientId);
        } catch (Exception e) {
            // Log error but don't fail the main enrollment
            log.warn("Failed to auto-enroll student {} in assigned courses for section {} / class {}: {}", 
                studentId, sectionId, classId, e.getMessage(), e);
        }
        
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
        
        log.info("GET /api/enrollments - Fetching enrollments for student: {}, clientId: {}", studentId, clientId);
        
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId);
        
        log.info("GET /api/enrollments - Found {} total enrollments for student {} (before filtering)", 
            enrollments.size(), studentId);
        
        if (!enrollments.isEmpty()) {
            log.debug("GET /api/enrollments - Enrollment details for student {}: {}", studentId,
                enrollments.stream()
                    .map(e -> String.format("id=%s, courseId=%s, batchId=%s, classId=%s", 
                        e.getId(), e.getCourseId(), e.getBatchId(), e.getClassId()))
                    .collect(Collectors.joining(", ")));
        }
        
        // Filter out placeholder enrollments (they're not real course enrollments)
        List<Enrollment> realEnrollments = enrollments.stream()
            .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()))
            .collect(Collectors.toList());
        
        log.info("GET /api/enrollments - Student {} enrollments: Found {} total enrollments, {} real course enrollments (excluding placeholders)", 
            studentId, enrollments.size(), realEnrollments.size());
        
        if (realEnrollments.size() != enrollments.size()) {
            log.debug("GET /api/enrollments - Student {} enrollments: Filtered out {} placeholder enrollments", 
                studentId, enrollments.size() - realEnrollments.size());
        }
        
        if (!realEnrollments.isEmpty()) {
            log.info("GET /api/enrollments - Returning {} enrollments for student {} with courseIds: {}", 
                realEnrollments.size(), studentId,
                realEnrollments.stream().map(Enrollment::getCourseId).collect(Collectors.toList()));
        } else {
            log.warn("GET /api/enrollments - No real enrollments found for student {}. Total enrollments: {}", 
                studentId, enrollments.size());
        }
        
        return realEnrollments.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public Page<EnrollmentDTO> getStudentEnrollments(String studentId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Get all enrollments (not paginated) to count real enrollments
        List<Enrollment> allEnrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId);
        long totalRealEnrollments = allEnrollments.stream()
            .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()))
            .count();
        
        // Get paginated enrollments
        Page<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId, pageable);
        
        // Filter out placeholder enrollments from current page
        List<Enrollment> realEnrollmentsList = enrollments.getContent().stream()
            .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()))
            .collect(Collectors.toList());
        
        // Create a new Page with filtered content
        Page<Enrollment> realEnrollments = new PageImpl<>(
            realEnrollmentsList, 
            pageable, 
            totalRealEnrollments
        );
        
        log.debug("Student {} enrollments (paginated): Found {} total enrollments, {} real course enrollments (excluding placeholders)", 
            studentId, enrollments.getTotalElements(), totalRealEnrollments);
        
        return realEnrollments.map(this::toDTO);
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

    /**
     * Get all enrollments for the current tenant (admin operation)
     * Filters out placeholder enrollments
     */
    public List<EnrollmentDTO> getAllEnrollments() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Enrollment> allEnrollments = enrollmentRepository.findByClientId(clientId);
        
        // Filter out placeholder enrollments
        List<Enrollment> realEnrollments = allEnrollments.stream()
            .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()))
            .collect(Collectors.toList());
        
        log.info("Getting all enrollments for tenant {}: Found {} total, {} real enrollments (excluding placeholders)", 
            clientId, allEnrollments.size(), realEnrollments.size());
        
        // Get unique student IDs
        Set<String> uniqueStudentIds = realEnrollments.stream()
            .map(Enrollment::getStudentId)
            .collect(Collectors.toSet());
        
        // Batch fetch student emails
        Map<String, String> studentEmailMap = fetchStudentEmails(uniqueStudentIds);
        
        // Convert to DTOs with emails
        return realEnrollments.stream()
            .map(enrollment -> {
                EnrollmentDTO dto = toDTO(enrollment);
                dto.setStudentEmail(studentEmailMap.get(enrollment.getStudentId()));
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Get all enrollments with pagination (admin operation)
     * Filters out placeholder enrollments and includes student emails
     */
    public Page<EnrollmentDTO> getAllEnrollments(Pageable pageable) {
        return getAllEnrollments(pageable, null, null, null, null, null);
    }

    /**
     * Get all enrollments with pagination and filters (admin operation)
     * Filters out placeholder enrollments and includes student emails
     * 
     * @param pageable Pagination parameters
     * @param courseId Optional course filter
     * @param instituteId Optional institute filter
     * @param classId Optional class filter
     * @param batchId Optional section/batch filter
     * @param studentIds Optional list of student IDs to filter by (for email search)
     * @param emailSearch Optional email search term (used for fallback filtering if studentIds is empty)
     */
    public Page<EnrollmentDTO> getAllEnrollments(Pageable pageable, String courseId, String instituteId, 
                                                  String classId, String batchId, List<String> studentIds, String emailSearch) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        log.info("Getting enrollments with filters - courseId: {}, instituteId: {}, classId: {}, sectionId (batchId): {}, studentIds: {} (count: {}), emailSearch: {}", 
            courseId, instituteId, classId, batchId, studentIds, studentIds != null ? studentIds.size() : 0, emailSearch);
        
        // If studentIds is empty but emailSearch is provided, we'll filter after fetching emails
        boolean needsEmailFiltering = (studentIds != null && studentIds.isEmpty() && emailSearch != null && !emailSearch.trim().isEmpty());
        
        // Build specification for filtering (don't use studentIds if we need email filtering)
        Specification<Enrollment> spec = buildEnrollmentSpecification(
            clientId, courseId, instituteId, classId, batchId, 
            needsEmailFiltering ? null : studentIds);
        
        // Get paginated enrollments with filters
        // If we need email filtering, we might need to fetch more to filter properly
        Page<Enrollment> enrollmentsPage = enrollmentRepository.findAll(spec, pageable);
        
        log.debug("Repository returned {} enrollments (total: {}, page: {}/{})", 
            enrollmentsPage.getNumberOfElements(), enrollmentsPage.getTotalElements(), 
            enrollmentsPage.getNumber(), enrollmentsPage.getTotalPages());
        
        // Get unique student IDs from current page
        Set<String> uniqueStudentIds = enrollmentsPage.getContent().stream()
            .map(Enrollment::getStudentId)
            .collect(Collectors.toSet());
        
        log.debug("Fetching emails for {} unique student IDs", uniqueStudentIds.size());
        
        // Batch fetch student emails
        Map<String, String> studentEmailMap = fetchStudentEmails(uniqueStudentIds);
        
        // Convert to DTOs with emails
        List<EnrollmentDTO> dtoList = enrollmentsPage.getContent().stream()
            .map(enrollment -> {
                EnrollmentDTO dto = toDTO(enrollment);
                dto.setStudentEmail(studentEmailMap.get(enrollment.getStudentId()));
                return dto;
            })
            .collect(Collectors.toList());
        
        // If we need email filtering (identity service didn't find matches), filter by email now
        if (needsEmailFiltering) {
            String emailLower = emailSearch.trim().toLowerCase();
            log.info("Filtering {} enrollments by email contains '{}' (fallback after identity service returned 0 matches)", 
                dtoList.size(), emailSearch);
            
            List<EnrollmentDTO> filtered = dtoList.stream()
                .filter(dto -> dto.getStudentEmail() != null && 
                        dto.getStudentEmail().toLowerCase().contains(emailLower))
                .collect(Collectors.toList());
            
            log.info("After email filtering: {} enrollments match '{}'", filtered.size(), emailSearch);
            dtoList = filtered;
            
            // Note: totalElements will be approximate since we filtered after pagination
            // For accurate pagination, we'd need to fetch all and paginate, but that's expensive
        }
        
        log.debug("Returning {} enrollment DTOs", dtoList.size());
        
        // Create new Page with filtered content
        // If we did email filtering, adjust totalElements (approximate)
        long totalElements = needsEmailFiltering && dtoList.size() < enrollmentsPage.getNumberOfElements() 
            ? dtoList.size() // Approximate - we only filtered current page
            : enrollmentsPage.getTotalElements();
        
        return new PageImpl<>(dtoList, pageable, totalElements);
    }
    
    /**
     * Get all enrollments with pagination and filters (admin operation)
     * Filters out placeholder enrollments and includes student emails
     * 
     * @param pageable Pagination parameters
     * @param courseId Optional course filter
     * @param instituteId Optional institute filter
     * @param classId Optional class filter
     * @param batchId Optional section/batch filter
     * @param studentIds Optional list of student IDs to filter by (for email search)
     */
    public Page<EnrollmentDTO> getAllEnrollments(Pageable pageable, String courseId, String instituteId, 
                                                  String classId, String batchId, List<String> studentIds) {
        return getAllEnrollments(pageable, courseId, instituteId, classId, batchId, studentIds, null);
    }

    /**
     * Build JPA Specification for enrollment filtering
     */
    private Specification<Enrollment> buildEnrollmentSpecification(UUID clientId, String courseId, 
                                                                     String instituteId, String classId, 
                                                                     String batchId, List<String> studentIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Always filter by clientId
            predicates.add(cb.equal(root.get("clientId"), clientId));
            
            // Exclude placeholder enrollments
            predicates.add(cb.notEqual(root.get("courseId"), "__PLACEHOLDER_ASSOCIATION__"));
            
            // Apply optional filters
            if (courseId != null && !courseId.isEmpty()) {
                log.info("Adding courseId backend filter: {}", courseId);
                predicates.add(cb.equal(root.get("courseId"), courseId));
            }
            
            if (instituteId != null && !instituteId.isEmpty()) {
                log.info("Adding instituteId backend filter: {}", instituteId);
                predicates.add(cb.equal(root.get("instituteId"), instituteId));
            }
            
            if (classId != null && !classId.isEmpty()) {
                log.info("Adding classId backend filter: {}", classId);
                predicates.add(cb.equal(root.get("classId"), classId));
            }
            
            if (batchId != null && !batchId.isEmpty()) {
                log.info("Adding sectionId (batchId) backend filter: {}", batchId);
                predicates.add(cb.equal(root.get("batchId"), batchId));
            }
            
            if (studentIds != null && !studentIds.isEmpty()) {
                log.info("Adding studentIds backend filter with {} IDs", studentIds.size());
                predicates.add(root.get("studentId").in(studentIds));
            } else if (studentIds != null && studentIds.isEmpty()) {
                // If studentIds list is empty (no matches found), return no results
                log.info("StudentIds list is empty - no matches found, returning empty result");
                predicates.add(cb.disjunction()); // Always false condition
            }
            
            // Order by enrolledAt descending
            query.orderBy(cb.desc(root.get("enrolledAt")));
            
            Predicate finalPredicate = cb.and(predicates.toArray(new Predicate[0]));
            log.info("Built specification with {} predicates (clientId, !placeholder, and {} optional filters)", 
                predicates.size(), predicates.size() - 2);
            return finalPredicate;
        };
    }

    /**
     * Batch fetch student emails for given student IDs
     */
    private Map<String, String> fetchStudentEmails(Set<String> studentIds) {
        Map<String, String> studentEmailMap = new HashMap<>();
        for (String studentId : studentIds) {
            try {
                UserDTO user = getUserFromIdentityService(studentId);
                if (user != null && user.getEmail() != null) {
                    studentEmailMap.put(studentId, user.getEmail());
                }
            } catch (Exception e) {
                log.debug("Could not fetch email for student {}: {}", studentId, e.getMessage());
                // Continue - email will be null in DTO
            }
        }
        log.debug("Fetched emails for {}/{} unique students", studentEmailMap.size(), studentIds.size());
        return studentEmailMap;
    }
    
    public boolean isEnrolled(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        log.info("Checking enrollment: studentId={}, courseId={}, clientId={}", studentId, courseId, clientId);
        boolean enrolled = enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
        log.info("Enrollment check result: studentId={}, courseId={}, enrolled={}", studentId, courseId, enrolled);
        
        return enrolled;
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
        
        // Store enrollment details before deletion
        String sectionId = enrollment.getBatchId();
        String classId = enrollment.getClassId();
        String instituteId = enrollment.getInstituteId();
        
        // Log unenrollment event before deleting
        String currentUserId = getCurrentUserId();
        String currentUserEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "enrollmentId", enrollment.getId(),
            "studentId", studentId,
            "courseId", courseId,
            "instituteId", instituteId != null ? instituteId : "",
            "classId", classId != null ? classId : "",
            "sectionId", sectionId != null ? sectionId : ""
        );
        eventService.logUserAction("COURSE_UNENROLLED", currentUserId, currentUserEmail, "/api/courses/" + courseId + "/enroll", eventData);
        auditService.logCrud(clientId, "DELETE", "Enrollment", enrollment.getId(), currentUserId, currentUserEmail, eventData);

        enrollmentRepository.delete(enrollment);
        
        // CRITICAL: Check if student needs a placeholder enrollment to maintain section/class association
        // If this was their last course enrollment for the section/class, create a placeholder
        if (sectionId != null || classId != null) {
            try {
                // Get all remaining real course enrollments for this student in the same section/class
                List<Enrollment> remainingEnrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId)
                    .stream()
                    .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId())) // Exclude placeholders
                    .filter(e -> {
                        // Check if enrollment is for the same section or class
                        if (sectionId != null && sectionId.equals(e.getBatchId())) {
                            return true; // Same section
                        }
                        if (classId != null && classId.equals(e.getClassId()) && e.getBatchId() == null) {
                            return true; // Same class, no section
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
                
                if (remainingEnrollments.isEmpty()) {
                    // Student has no other course enrollments for this section/class
                    // Create placeholder to maintain association
                    Enrollment placeholder = new Enrollment();
                    placeholder.setId(UlidGenerator.nextUlid());
                    placeholder.setClientId(clientId);
                    placeholder.setStudentId(studentId);
                    placeholder.setCourseId("__PLACEHOLDER_ASSOCIATION__");
                    placeholder.setBatchId(sectionId);
                    placeholder.setClassId(classId);
                    placeholder.setInstituteId(instituteId);
                    
                    enrollmentRepository.save(placeholder);
                    
                    log.info("Created placeholder enrollment for student {} in section/class (sectionId: {}, classId: {}) after unenrolling from their last course", 
                        studentId, sectionId, classId);
                } else {
                    log.debug("Student {} still has {} other course enrollments in section/class, no placeholder needed", 
                        studentId, remainingEnrollments.size());
                }
            } catch (Exception e) {
                log.error("Failed to create placeholder enrollment for student {} after unenrolling from course {}: {}", 
                    studentId, courseId, e.getMessage(), e);
                // Don't fail the unenrollment if placeholder creation fails
            }
        }
    }

    /**
     * Delete an enrollment by ID (admin operation)
     */
    public void deleteEnrollment(String enrollmentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        
        // Verify enrollment belongs to current tenant
        if (!enrollment.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Enrollment does not belong to current tenant");
        }
        
        // Store enrollment details before deletion
        String studentId = enrollment.getStudentId();
        String courseId = enrollment.getCourseId();
        String sectionId = enrollment.getBatchId();
        String classId = enrollment.getClassId();
        String instituteId = enrollment.getInstituteId();
        
        // Log enrollment deletion event before deleting
        String currentUserId = getCurrentUserId();
        String currentUserEmail = getCurrentUserEmail();
        Map<String, Object> eventData = Map.of(
            "enrollmentId", enrollment.getId(),
            "studentId", studentId,
            "courseId", courseId,
            "instituteId", instituteId != null ? instituteId : "",
            "classId", classId != null ? classId : "",
            "sectionId", sectionId != null ? sectionId : ""
        );
        eventService.logUserAction("ENROLLMENT_DELETED", currentUserId, currentUserEmail, "/api/enrollments/" + enrollmentId, eventData);
        auditService.logCrud(clientId, "DELETE", "Enrollment", enrollmentId, currentUserId, currentUserEmail, eventData);

        enrollmentRepository.delete(enrollment);
        log.info("Deleted enrollment {} for student {} in course {}", 
            enrollmentId, studentId, courseId);
        
        // CRITICAL: Check if student needs a placeholder enrollment to maintain section/class association
        // Only do this for real course enrollments (not placeholders)
        if (!"__PLACEHOLDER_ASSOCIATION__".equals(courseId) && (sectionId != null || classId != null)) {
            try {
                // Get all remaining real course enrollments for this student in the same section/class
                List<Enrollment> remainingEnrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId)
                    .stream()
                    .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId())) // Exclude placeholders
                    .filter(e -> {
                        // Check if enrollment is for the same section or class
                        if (sectionId != null && sectionId.equals(e.getBatchId())) {
                            return true; // Same section
                        }
                        if (classId != null && classId.equals(e.getClassId()) && e.getBatchId() == null) {
                            return true; // Same class, no section
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
                
                if (remainingEnrollments.isEmpty()) {
                    // Student has no other course enrollments for this section/class
                    // Create placeholder to maintain association
                    Enrollment placeholder = new Enrollment();
                    placeholder.setId(UlidGenerator.nextUlid());
                    placeholder.setClientId(clientId);
                    placeholder.setStudentId(studentId);
                    placeholder.setCourseId("__PLACEHOLDER_ASSOCIATION__");
                    placeholder.setBatchId(sectionId);
                    placeholder.setClassId(classId);
                    placeholder.setInstituteId(instituteId);
                    
                    enrollmentRepository.save(placeholder);
                    
                    log.info("Created placeholder enrollment for student {} in section/class (sectionId: {}, classId: {}) after deleting their last course enrollment", 
                        studentId, sectionId, classId);
                } else {
                    log.debug("Student {} still has {} other course enrollments in section/class, no placeholder needed", 
                        studentId, remainingEnrollments.size());
                }
            } catch (Exception e) {
                log.error("Failed to create placeholder enrollment for student {} after deleting enrollment {}: {}", 
                    studentId, enrollmentId, e.getMessage(), e);
                // Don't fail the deletion if placeholder creation fails
            }
        }
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
        return getStudentsBySection(sectionId, null).getContent();
    }
    
    @Transactional(readOnly = true)
    public Page<SectionStudentDTO> getStudentsBySection(String sectionId, Pageable pageable) {
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
            return new PageImpl<>(new ArrayList<>(), pageable != null ? pageable : Pageable.unpaged(), 0);
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
        
        // Apply pagination if pageable is provided
        if (pageable != null && pageable.isPaged()) {
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), students.size());
            List<SectionStudentDTO> pagedStudents = start < students.size() 
                ? students.subList(start, end) 
                : new ArrayList<>();
            return new PageImpl<>(pagedStudents, pageable, students.size());
        }
        
        return new PageImpl<>(students, pageable != null ? pageable : Pageable.unpaged(), students.size());
    }
    
    public List<ClassStudentDTO> getStudentsByClass(String classId) {
        return getStudentsByClass(classId, null).getContent();
    }
    
    @Transactional(readOnly = true)
    public Page<ClassStudentDTO> getStudentsByClass(String classId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate class exists
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        // Get all enrollments for this class
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndClassId(clientId, classId);
        
        // Extract unique student IDs
        List<String> studentIds = enrollments.stream()
            .map(Enrollment::getStudentId)
            .distinct()
            .collect(Collectors.toList());
        
        if (studentIds.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable != null ? pageable : Pageable.unpaged(), 0);
        }
        
        // Fetch user details from identity service
        List<ClassStudentDTO> students = new ArrayList<>();
        for (String studentId : studentIds) {
            try {
                UserDTO user = getUserFromIdentityService(studentId);
                if (user != null) {
                    students.add(new ClassStudentDTO(
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
        
        // Apply pagination if pageable is provided
        if (pageable != null && pageable.isPaged()) {
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), students.size());
            List<ClassStudentDTO> pagedStudents = start < students.size() 
                ? students.subList(start, end) 
                : new ArrayList<>();
            return new PageImpl<>(pagedStudents, pageable, students.size());
        }
        
        return new PageImpl<>(students, pageable != null ? pageable : Pageable.unpaged(), students.size());
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
     * Automatically enroll a student in all published courses assigned to their section/class.
     * This method is called after a student is enrolled with a sectionId or classId.
     * Package-private to allow access from BulkStudentImportService.
     * 
     * @param studentId The student ID
     * @param sectionId The section ID (can be null)
     * @param classId The class ID (can be null)
     * @param instituteId The institute ID
     * @param clientId The client/tenant ID
     */
    void autoEnrollStudentInAssignedCourses(String studentId, String sectionId, String classId, 
                                                    String instituteId, UUID clientId) {
        List<CourseDTO> coursesToEnroll = new ArrayList<>();
        
        // Priority: If sectionId is provided, only enroll in section-assigned courses (more specific)
        // If only classId is provided, enroll in class-assigned courses
        if (sectionId != null && !sectionId.isBlank()) {
            log.debug("Auto-enrolling student {} in courses assigned to section {}", studentId, sectionId);
            try {
                String url = gatewayUrl + "/content/courses/section/" + sectionId;
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                ResponseEntity<CourseDTO[]> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CourseDTO[].class
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    coursesToEnroll = List.of(response.getBody());
                    log.info("Found {} published courses assigned to section {} for auto-enrollment", 
                        coursesToEnroll.size(), sectionId);
                } else {
                    log.warn("Content service returned non-2xx status when fetching courses for section {}: {}", 
                        sectionId, response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch courses assigned to section {} for auto-enrollment: {}", 
                    sectionId, e.getMessage(), e);
                return; // Don't proceed if we can't fetch courses
            }
        } else if (classId != null && !classId.isBlank()) {
            log.debug("Auto-enrolling student {} in courses assigned to class {}", studentId, classId);
            try {
                String url = gatewayUrl + "/content/courses/class/" + classId;
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                ResponseEntity<CourseDTO[]> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CourseDTO[].class
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    coursesToEnroll = List.of(response.getBody());
                    log.info("Found {} published courses assigned to class {} for auto-enrollment", 
                        coursesToEnroll.size(), classId);
                } else {
                    log.warn("Content service returned non-2xx status when fetching courses for class {}: {}", 
                        classId, response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch courses assigned to class {} for auto-enrollment: {}", 
                    classId, e.getMessage(), e);
                return; // Don't proceed if we can't fetch courses
            }
        } else {
            log.debug("No sectionId or classId provided, skipping auto-enrollment");
            return;
        }
        
        if (coursesToEnroll.isEmpty()) {
            log.debug("No published courses assigned to section {} / class {}, skipping auto-enrollment", 
                sectionId, classId);
            return;
        }
        
        // Enroll student in each course
        int enrolledCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        
        for (CourseDTO course : coursesToEnroll) {
            try {
                // Skip if already enrolled
                if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, course.getId())) {
                    log.debug("Student {} already enrolled in course {}, skipping", studentId, course.getId());
                    skippedCount++;
                    continue;
                }
                
                // Create enrollment directly (avoid recursion by not calling enrollStudent again)
                Enrollment enrollment = new Enrollment();
                enrollment.setId(UlidGenerator.nextUlid());
                enrollment.setClientId(clientId);
                enrollment.setStudentId(studentId);
                enrollment.setCourseId(course.getId());
                enrollment.setBatchId(sectionId); // Keep batchId for backward compatibility
                enrollment.setInstituteId(instituteId);
                enrollment.setClassId(classId);
                
                enrollmentRepository.save(enrollment);
                enrolledCount++;
                log.info("Auto-enrolled student {} in course {} (sectionId={}, classId={})", 
                    studentId, course.getId(), sectionId, classId);
                    
            } catch (Exception e) {
                failedCount++;
                log.warn("Failed to auto-enroll student {} in course {}: {}", 
                    studentId, course.getId(), e.getMessage(), e);
                // Continue with other courses even if one fails
            }
        }
        
        log.info("Auto-enrollment completed for student {}: {} enrolled, {} skipped (already enrolled), {} failed", 
            studentId, enrolledCount, skippedCount, failedCount);
    }
    
    /**
     * Fetch the first published course assigned to the given section or class (for transfer destination).
     * Section takes precedence if both are provided. Returns null if none assigned or on error.
     */
    private String getFirstAssignedCourseIdForDestination(String sectionId, String classId) {
        String url;
        String scope;
        if (sectionId != null && !sectionId.isBlank()) {
            url = gatewayUrl + "/content/courses/section/" + sectionId;
            scope = "section " + sectionId;
        } else if (classId != null && !classId.isBlank()) {
            url = gatewayUrl + "/content/courses/class/" + classId;
            scope = "class " + classId;
        } else {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<CourseDTO[]> response = getRestTemplate().exchange(url, HttpMethod.GET, entity, CourseDTO[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                String courseId = response.getBody()[0].getId();
                log.debug("First course assigned to {}: {}", scope, courseId);
                return courseId;
            }
            log.debug("No courses assigned to {}", scope);
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch courses for {}: {}", scope, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch ALL published course IDs assigned to the given section or class.
     * Used during transfer to decide whether existing enrollment courseIds should be kept or swapped.
     */
    private Set<String> getAssignedCourseIdsForDestination(String sectionId, String classId) {
        String url;
        String scope;
        if (sectionId != null && !sectionId.isBlank()) {
            url = gatewayUrl + "/content/courses/section/" + sectionId;
            scope = "section " + sectionId;
        } else if (classId != null && !classId.isBlank()) {
            url = gatewayUrl + "/content/courses/class/" + classId;
            scope = "class " + classId;
        } else {
            return Set.of();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<CourseDTO[]> response = getRestTemplate().exchange(url, HttpMethod.GET, entity, CourseDTO[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Set<String> courseIds = new java.util.LinkedHashSet<>();
                for (CourseDTO c : response.getBody()) {
                    courseIds.add(c.getId());
                }
                log.debug("Courses assigned to {}: {}", scope, courseIds);
                return courseIds;
            }
            log.debug("No courses assigned to {}", scope);
            return Set.of();
        } catch (Exception e) {
            log.warn("Failed to fetch courses for {}: {}", scope, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Inner class to deserialize course info from content service
     * Only includes fields needed for auto-enrollment
     */
    private static class CourseDTO {
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
    
    /**
     * Find student IDs by email search
     * Searches the identity service for students matching the email (contains match, not exact)
     */
    public List<String> findStudentIdsByEmail(String email) {
        List<String> studentIds = new ArrayList<>();
        log.info("Finding student IDs by email (contains match): {}", email);
        
        try {
            // Try email parameter first
            String url = gatewayUrl + "/idp/users/role/STUDENT/paginated?page=0&size=100&email=" + 
                        java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
            log.debug("Searching students with URL: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            try {
                ResponseEntity<StudentSearchResponse> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    StudentSearchResponse.class
                );
                
                log.debug("Identity service response status: {}", response.getStatusCode());
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<UserDTO> users = response.getBody().getContent();
                    log.debug("Identity service returned {} users", users != null ? users.size() : 0);
                    if (users != null) {
                        // Filter to ensure emails actually contain the search term (case-insensitive)
                        String searchTerm = email.toLowerCase();
                        List<UserDTO> filteredUsers = users.stream()
                            .filter(user -> user.getEmail() != null && 
                                    user.getEmail().toLowerCase().contains(searchTerm))
                            .collect(Collectors.toList());
                        
                        studentIds = filteredUsers.stream()
                            .map(UserDTO::getId)
                            .collect(Collectors.toList());
                        
                        // Log emails for debugging
                        List<String> emails = filteredUsers.stream()
                            .map(u -> u.getEmail() != null ? u.getEmail() : "no-email")
                            .collect(Collectors.toList());
                        log.info("Identity service returned {} users, filtered to {} matching '{}'. Emails: {}", 
                            users.size(), filteredUsers.size(), email, emails);
                        log.info("Student IDs: {}", studentIds);
                    }
                    return studentIds;
                }
            } catch (Exception emailError) {
                // If email parameter doesn't work, try search parameter
                log.debug("Email parameter failed, trying search parameter: {}", emailError.getMessage());
                url = gatewayUrl + "/idp/users/role/STUDENT/paginated?page=0&size=100&search=" + 
                      java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
                log.debug("Trying search parameter with URL: {}", url);
                
                ResponseEntity<StudentSearchResponse> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    StudentSearchResponse.class
                );
                
                log.debug("Identity service search response status: {}", response.getStatusCode());
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<UserDTO> users = response.getBody().getContent();
                    log.debug("Identity service search returned {} users", users != null ? users.size() : 0);
                    if (users != null) {
                        // Filter to ensure emails actually contain the search term (case-insensitive)
                        String searchTerm = email.toLowerCase();
                        List<UserDTO> filteredUsers = users.stream()
                            .filter(user -> user.getEmail() != null && 
                                    user.getEmail().toLowerCase().contains(searchTerm))
                            .collect(Collectors.toList());
                        
                        studentIds = filteredUsers.stream()
                            .map(UserDTO::getId)
                            .collect(Collectors.toList());
                        
                        // Log emails for debugging
                        List<String> emails = filteredUsers.stream()
                            .map(u -> u.getEmail() != null ? u.getEmail() : "no-email")
                            .collect(Collectors.toList());
                        log.info("Identity service search returned {} users, filtered to {} matching '{}'. Emails: {}", 
                            users.size(), filteredUsers.size(), email, emails);
                        log.info("Student IDs: {}", studentIds);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not search students by email {}: {}", email, e.getMessage(), e);
        }
        
        log.info("Returning {} student IDs for email contains match '{}'", studentIds.size(), email);
        return studentIds;
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
    
    /**
     * Response class for student search
     */
    private static class StudentSearchResponse {
        private List<UserDTO> content;
        private long totalElements;
        private int totalPages;
        
        public List<UserDTO> getContent() { return content; }
        public void setContent(List<UserDTO> content) { this.content = content; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }
    
    /**
     * Transfer a student's enrollment(s) to a destination section (and optionally change course).
     * <p>
     * This method transfers the specified enrollment AND all other enrollments the student has
     * in the same source section, so the student fully moves from the old section to the new one.
     * After transferring existing enrollments, it also auto-enrolls the student in any additional
     * courses assigned to the destination section/class that the student was not previously enrolled in.
     * <p>
     * Does not update Progress, LectureViewSession, or AssessmentSubmission (progress remains archived).
     */
    public EnrollmentDTO transferEnrollment(TransferEnrollmentRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        Enrollment enrollment = enrollmentRepository.findById(request.getEnrollmentId())
            .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + request.getEnrollmentId()));

        if (!enrollment.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Enrollment not found: " + request.getEnrollmentId());
        }

        // Placeholder enrollments should not be transferred
        if ("__PLACEHOLDER_ASSOCIATION__".equals(enrollment.getCourseId())) {
            throw new IllegalArgumentException("Cannot transfer placeholder enrollment");
        }

        boolean hasSection = request.getDestinationSectionId() != null && !request.getDestinationSectionId().isBlank();
        boolean hasClass = request.getDestinationClassId() != null && !request.getDestinationClassId().isBlank();
        if (!hasSection && !hasClass) {
            throw new IllegalArgumentException("Either destination section or destination class is required");
        }

        Class destClass;
        String destSectionId;
        if (hasSection) {
            Section destSection = sectionRepository.findByIdAndClientId(request.getDestinationSectionId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Destination section not found: " + request.getDestinationSectionId()));
            if (!Boolean.TRUE.equals(destSection.getIsActive())) {
                throw new IllegalArgumentException("Destination section is not active: " + request.getDestinationSectionId());
            }
            destClass = classRepository.findByIdAndClientId(destSection.getClassId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Destination class not found for section"));
            destSectionId = request.getDestinationSectionId();
        } else {
            destClass = classRepository.findByIdAndClientId(request.getDestinationClassId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Destination class not found: " + request.getDestinationClassId()));
            if (!Boolean.TRUE.equals(destClass.getIsActive())) {
                throw new IllegalArgumentException("Destination class is not active: " + request.getDestinationClassId());
            }
            destSectionId = null; // class-only transfer
        }

        String sourceSectionId = enrollment.getBatchId();
        String sourceClassId = enrollment.getClassId();
        String studentId = enrollment.getStudentId();

        // ── Step 1: Find ALL of this student's enrollments in the source section ──
        // This ensures the student is fully moved, not just one enrollment.
        List<Enrollment> studentEnrollmentsInSource = new ArrayList<>();
        if (sourceSectionId != null && !sourceSectionId.isBlank()) {
            studentEnrollmentsInSource = enrollmentRepository.findByClientIdAndBatchId(clientId, sourceSectionId)
                .stream()
                .filter(e -> studentId.equals(e.getStudentId()))
                .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()))
                .collect(Collectors.toList());
        }

        // If we couldn't find sibling enrollments (e.g. class-only enrollment), just use the single one
        if (studentEnrollmentsInSource.isEmpty()) {
            studentEnrollmentsInSource = List.of(enrollment);
        }

        // ── Step 2: Fetch courses assigned to the destination section/class ──
        // Used to determine: should we keep the original courseId or swap it?
        Set<String> destAssignedCourseIds = getAssignedCourseIdsForDestination(destSectionId, destClass.getId());

        String currentUserId = getCurrentUserId();
        String currentUserEmail = getCurrentUserEmail();
        Set<String> evictCourseIds = new java.util.HashSet<>();
        int siblingCount = 0;

        // ── Step 3: Transfer each enrollment ──
        for (Enrollment e : studentEnrollmentsInSource) {
            String oldCourseId = e.getCourseId();

            // Determine the new courseId for this enrollment:
            // - If admin explicitly specified a destinationCourseId, use it for the primary enrollment only
            // - If the enrollment's current course is also assigned to the destination, KEEP it (preserves progress link)
            // - Otherwise, don't force-swap — auto-enrollment (step 4) will handle new courses
            if (e.getId().equals(request.getEnrollmentId())
                    && request.getDestinationCourseId() != null && !request.getDestinationCourseId().isBlank()) {
                e.setCourseId(request.getDestinationCourseId());
            } else if (!destAssignedCourseIds.isEmpty() && !destAssignedCourseIds.contains(oldCourseId)) {
                // Current course is NOT assigned to destination — pick the first destination course
                String firstDestCourse = destAssignedCourseIds.iterator().next();
                e.setCourseId(firstDestCourse);
            }
            // else: keep original courseId (it's assigned to destination too)

            e.setBatchId(destSectionId);
            e.setClassId(destClass.getId());
            e.setInstituteId(destClass.getInstituteId());
            enrollmentRepository.save(e);

            // Track for cache eviction
            if (oldCourseId != null && !oldCourseId.isBlank()) {
                evictCourseIds.add(oldCourseId);
            }
            if (e.getCourseId() != null && !e.getCourseId().isBlank()) {
                evictCourseIds.add(e.getCourseId());
            }

            if (!e.getId().equals(request.getEnrollmentId())) {
                siblingCount++;
                log.info("Also transferred sibling enrollment {} (course: {} -> {}) for student {}",
                    e.getId(), oldCourseId, e.getCourseId(), studentId);
            }
        }

        // Reload the primary enrollment after save for the return value
        Enrollment saved = enrollmentRepository.findById(request.getEnrollmentId())
            .orElseThrow(() -> new IllegalStateException("Primary enrollment disappeared after save"));

        // ── Step 4: Auto-enroll in destination's assigned courses ──
        // This creates enrollments for any courses assigned to the destination section/class
        // that the student doesn't already have. Mirrors what happens when a student is first
        // added to a section.
        try {
            autoEnrollStudentInAssignedCourses(studentId, destSectionId, destClass.getId(),
                destClass.getInstituteId(), clientId);
        } catch (Exception e) {
            log.warn("Auto-enrollment after transfer failed for student {} in section {}: {}. " +
                     "Student was transferred but may be missing some course enrollments.",
                studentId, destSectionId, e.getMessage());
            // Don't fail the transfer — the primary operation succeeded
        }

        // ── Step 5: Clean up placeholder in source section ──
        // If transferring out was the student's last real enrollment in the source section,
        // we don't need to leave a placeholder because the student is fully leaving.
        if (sourceSectionId != null && !sourceSectionId.isBlank()) {
            List<Enrollment> remainingInSource = enrollmentRepository.findByClientIdAndBatchId(clientId, sourceSectionId)
                .stream()
                .filter(e -> studentId.equals(e.getStudentId()))
                .collect(Collectors.toList());
            // Delete any placeholders left in the source section for this student
            for (Enrollment remaining : remainingInSource) {
                if ("__PLACEHOLDER_ASSOCIATION__".equals(remaining.getCourseId())) {
                    enrollmentRepository.delete(remaining);
                    log.debug("Removed placeholder enrollment {} from source section {}", remaining.getId(), sourceSectionId);
                }
            }
        }

        // ── Audit & event logging ──
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("enrollmentId", saved.getId());
        eventData.put("studentId", studentId);
        eventData.put("sourceSectionId", sourceSectionId != null ? sourceSectionId : "");
        eventData.put("sourceClassId", sourceClassId != null ? sourceClassId : "");
        eventData.put("destinationSectionId", destSectionId != null ? destSectionId : "");
        eventData.put("destinationClassId", destClass.getId());
        eventData.put("siblingEnrollmentsTransferred", siblingCount);
        eventService.logUserAction("ENROLLMENT_TRANSFERRED", currentUserId, currentUserEmail, "/api/enrollments/transfer", eventData);
        auditService.logCrud(clientId, "TRANSFER", "Enrollment", saved.getId(), currentUserId, currentUserEmail, eventData);

        // ── Evict analytics caches ──
        if (sourceSectionId != null && !sourceSectionId.isBlank()) {
            sessionService.evictSectionAnalyticsCache(sourceSectionId);
        }
        if (destSectionId != null && !destSectionId.isBlank()) {
            sessionService.evictSectionAnalyticsCache(destSectionId);
        }
        if (sourceClassId != null && !sourceClassId.isBlank()) {
            sessionService.evictClassAnalyticsCache(sourceClassId);
        }
        sessionService.evictClassAnalyticsCache(destClass.getId());
        for (String courseId : evictCourseIds) {
            sessionService.evictCourseAnalyticsCache(courseId);
        }

        log.info("Transferred student {} from section {} to section {} (class {}). " +
                 "Primary enrollment: {}, sibling enrollments also transferred: {}",
            studentId, sourceSectionId, destSectionId, destClass.getId(),
            saved.getId(), siblingCount);

        return toDTO(saved);
    }

    /**
     * Bulk transfer enrollments to a destination section (and optionally change course for all).
     * Processes one-by-one; returns successes and per-item errors (no partial rollback).
     * <p>
     * Since transferEnrollment now moves ALL sibling enrollments for a student in the same
     * source section, we track already-processed students to skip duplicates when the admin
     * selects multiple enrollments belonging to the same student.
     */
    public BulkTransferEnrollmentResponse bulkTransferEnrollments(BulkTransferEnrollmentRequest request) {
        List<EnrollmentDTO> successes = new ArrayList<>();
        List<TransferEnrollmentError> errors = new ArrayList<>();
        Set<String> alreadyTransferredStudentSections = new java.util.HashSet<>();
        TransferEnrollmentRequest single = new TransferEnrollmentRequest();
        single.setDestinationSectionId(request.getDestinationSectionId());
        single.setDestinationClassId(request.getDestinationClassId());
        single.setDestinationCourseId(request.getDestinationCourseId());

        for (int i = 0; i < request.getEnrollmentIds().size(); i++) {
            String enrollmentId = request.getEnrollmentIds().get(i);
            try {
                // Check if this enrollment's student+section was already transferred by a prior iteration
                Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElse(null);
                if (enrollment != null) {
                    String key = enrollment.getStudentId() + "|" + (enrollment.getBatchId() != null ? enrollment.getBatchId() : "");
                    if (alreadyTransferredStudentSections.contains(key)) {
                        log.info("Skipping enrollment {} — student {} already transferred from section {}",
                            enrollmentId, enrollment.getStudentId(), enrollment.getBatchId());
                        // Still count as success since the student was already moved
                        successes.add(toDTO(enrollment));
                        continue;
                    }
                    alreadyTransferredStudentSections.add(key);
                }

                single.setEnrollmentId(enrollmentId);
                EnrollmentDTO dto = transferEnrollment(single);
                successes.add(dto);
            } catch (Exception e) {
                errors.add(new TransferEnrollmentError(i, enrollmentId, e.getMessage()));
                log.warn("Bulk transfer failed for enrollment {} at index {}: {}", enrollmentId, i, e.getMessage());
            }
        }

        return new BulkTransferEnrollmentResponse(successes, errors);
    }

    /**
     * Repair enrollment data for students in a given section.
     * <p>
     * Fixes data inconsistencies caused by the old transfer logic that only moved one enrollment
     * instead of all, and didn't auto-enroll in destination courses:
     * <ul>
     *   <li>For each student in the section, ensures they have enrollments for ALL published courses
     *       assigned to that section/class (creates missing ones)</li>
     *   <li>Updates any enrollments with mismatched classId/instituteId (from stale transfers)</li>
     * </ul>
     *
     * @param sectionId the section to repair
     * @return a summary map with repair statistics
     */
    public Map<String, Object> repairSectionEnrollments(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));

        Class sectionClass = classRepository.findByIdAndClientId(section.getClassId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found for section: " + sectionId));

        // Get all enrollments in this section
        List<Enrollment> sectionEnrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);

        // Get distinct students in this section
        Set<String> studentIds = sectionEnrollments.stream()
            .map(Enrollment::getStudentId)
            .collect(Collectors.toSet());

        // Get courses assigned to this section/class
        Set<String> assignedCourseIds = getAssignedCourseIdsForDestination(sectionId, section.getClassId());

        int studentsProcessed = 0;
        int enrollmentsCreated = 0;
        int enrollmentsFixed = 0;
        List<String> details = new ArrayList<>();

        for (String studentId : studentIds) {
            studentsProcessed++;

            // Get this student's enrollments in this section
            List<Enrollment> studentEnrollments = sectionEnrollments.stream()
                .filter(e -> studentId.equals(e.getStudentId()))
                .filter(e -> !"__PLACEHOLDER_ASSOCIATION__".equals(e.getCourseId()))
                .collect(Collectors.toList());

            Set<String> existingCourseIds = studentEnrollments.stream()
                .map(Enrollment::getCourseId)
                .collect(Collectors.toSet());

            // Fix mismatched classId/instituteId on existing enrollments
            for (Enrollment e : studentEnrollments) {
                boolean changed = false;
                if (!sectionClass.getId().equals(e.getClassId())) {
                    e.setClassId(sectionClass.getId());
                    changed = true;
                }
                if (sectionClass.getInstituteId() != null && !sectionClass.getInstituteId().equals(e.getInstituteId())) {
                    e.setInstituteId(sectionClass.getInstituteId());
                    changed = true;
                }
                if (changed) {
                    enrollmentRepository.save(e);
                    enrollmentsFixed++;
                    details.add("Fixed classId/instituteId on enrollment " + e.getId() + " for student " + studentId);
                }
            }

            // Create missing course enrollments
            for (String courseId : assignedCourseIds) {
                if (!existingCourseIds.contains(courseId)) {
                    // Also check globally (student might have an enrollment for this course in a different section — rare but possible)
                    if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId)) {
                        continue; // Skip — enrolled via a different path
                    }

                    Enrollment newEnrollment = new Enrollment();
                    newEnrollment.setId(UlidGenerator.nextUlid());
                    newEnrollment.setClientId(clientId);
                    newEnrollment.setStudentId(studentId);
                    newEnrollment.setCourseId(courseId);
                    newEnrollment.setBatchId(sectionId);
                    newEnrollment.setClassId(sectionClass.getId());
                    newEnrollment.setInstituteId(sectionClass.getInstituteId());
                    enrollmentRepository.save(newEnrollment);
                    enrollmentsCreated++;
                    details.add("Created missing enrollment for student " + studentId + " in course " + courseId);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sectionId", sectionId);
        result.put("studentsProcessed", studentsProcessed);
        result.put("enrollmentsCreated", enrollmentsCreated);
        result.put("enrollmentsFixed", enrollmentsFixed);
        result.put("assignedCourseIds", assignedCourseIds);
        result.put("details", details);

        log.info("Repair completed for section {}: {} students, {} enrollments created, {} enrollments fixed",
            sectionId, studentsProcessed, enrollmentsCreated, enrollmentsFixed);

        return result;
    }

    /**
     * Repair enrollment data for ALL sections in the tenant.
     * Iterates through all active sections and calls repairSectionEnrollments for each.
     *
     * @return summary of repairs across all sections
     */
    public Map<String, Object> repairAllSectionEnrollments() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        List<Section> allSections = sectionRepository.findByClientId(clientId);
        int totalStudents = 0;
        int totalCreated = 0;
        int totalFixed = 0;
        List<Map<String, Object>> sectionResults = new ArrayList<>();

        for (Section section : allSections) {
            try {
                Map<String, Object> result = repairSectionEnrollments(section.getId());
                totalStudents += (int) result.get("studentsProcessed");
                totalCreated += (int) result.get("enrollmentsCreated");
                totalFixed += (int) result.get("enrollmentsFixed");
                // Only include sections where something was actually fixed
                if ((int) result.get("enrollmentsCreated") > 0 || (int) result.get("enrollmentsFixed") > 0) {
                    sectionResults.add(result);
                }
            } catch (Exception e) {
                log.warn("Repair failed for section {}: {}", section.getId(), e.getMessage());
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("sectionId", section.getId());
                errorResult.put("error", e.getMessage());
                sectionResults.add(errorResult);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("sectionsProcessed", allSections.size());
        summary.put("totalStudentsProcessed", totalStudents);
        summary.put("totalEnrollmentsCreated", totalCreated);
        summary.put("totalEnrollmentsFixed", totalFixed);
        summary.put("sectionDetails", sectionResults);

        log.info("Full tenant repair completed: {} sections, {} students, {} created, {} fixed",
            allSections.size(), totalStudents, totalCreated, totalFixed);

        return summary;
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

