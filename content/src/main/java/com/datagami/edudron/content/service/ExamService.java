package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.ExamQuestion;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.dto.BatchExamGenerationRequest;
import com.datagami.edudron.content.dto.BatchExamGenerationResponse;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ExamService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamService.class);
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private ExamGenerationService examGenerationService;
    
    @Autowired
    private ExamPaperGenerationService examPaperGenerationService;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();
    
    private RestTemplate getRestTemplate() {
        // Double-checked locking for thread-safe lazy initialization
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    logger.debug("Initializing RestTemplate for identity service calls. Gateway URL: {}", gatewayUrl);
                    RestTemplate newTemplate = new RestTemplate();
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
                                    logger.debug("Propagated Authorization header (JWT token) to identity service: {}", request.getURI());
                                } else {
                                    logger.debug("Authorization header already present in request to {}", request.getURI());
                                }
                            } else {
                                logger.debug("No Authorization header found in current request");
                            }
                        } else {
                            logger.debug("No ServletRequestAttributes found - cannot forward JWT token");
                        }
                        return execution.execute(request, body);
                    });
                    newTemplate.setInterceptors(interceptors);
                    logger.debug("RestTemplate initialized with TenantContextRestTemplateInterceptor and JWT token forwarding");
                    restTemplate = newTemplate;
                }
            }
        }
        return restTemplate;
    }
    
    public Assessment createExam(String courseId, String title, String description, String instructions,
                                 List<String> moduleIds, Assessment.ReviewMethod reviewMethod, String classId, String sectionId,
                                 Boolean randomizeQuestions, Boolean randomizeMcqOptions,
                                 Boolean enableProctoring, Assessment.ProctoringMode proctoringMode,
                                 Integer photoIntervalSeconds, Boolean requireIdentityVerification,
                                 Boolean blockCopyPaste, Boolean blockTabSwitch, Integer maxTabSwitchesAllowed,
                                 String timingMode, Integer passingScorePercentage) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot create exams
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot create exams");
        }
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify course exists
        courseRepository.findByIdAndClientId(courseId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        
        // Get next sequence
        Integer maxSequence = assessmentRepository.findMaxSequenceByCourseIdAndClientId(courseId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        Assessment exam = new Assessment();
        exam.setId(UlidGenerator.nextUlid());
        exam.setClientId(clientId);
        exam.setCourseId(courseId);
        exam.setClassId(classId); // Set class if provided (null = course-wide)
        exam.setSectionId(sectionId); // Set section if provided (null = not section-specific)
        exam.setAssessmentType(Assessment.AssessmentType.EXAM);
        exam.setTitle(title);
        exam.setDescription(description);
        exam.setInstructions(instructions);
        exam.setSequence(nextSequence);
        exam.setStatus(Assessment.ExamStatus.DRAFT);
        exam.setReviewMethod(reviewMethod != null ? reviewMethod : Assessment.ReviewMethod.INSTRUCTOR);
        exam.setModuleIds(moduleIds != null ? moduleIds : List.of());
        exam.setRandomizeQuestions(randomizeQuestions != null ? randomizeQuestions : false);
        exam.setRandomizeMcqOptions(randomizeMcqOptions != null ? randomizeMcqOptions : false);
        
        // Set proctoring configuration
        exam.setEnableProctoring(enableProctoring != null ? enableProctoring : false);
        exam.setProctoringMode(proctoringMode);
        exam.setPhotoIntervalSeconds(photoIntervalSeconds != null ? photoIntervalSeconds : 120);
        exam.setRequireIdentityVerification(requireIdentityVerification != null ? requireIdentityVerification : false);
        exam.setBlockCopyPaste(blockCopyPaste != null ? blockCopyPaste : false);
        exam.setBlockTabSwitch(blockTabSwitch != null ? blockTabSwitch : false);
        exam.setMaxTabSwitchesAllowed(maxTabSwitchesAllowed != null ? maxTabSwitchesAllowed : 3);
        
        // Set timing mode
        exam.setTimingMode(parseTimingMode(timingMode));
        
        // Set passing score percentage
        exam.setPassingScorePercentage(passingScorePercentage != null ? passingScorePercentage : 70);
        
        Assessment saved = assessmentRepository.save(exam);
        String audience = sectionId != null ? "section: " + sectionId : 
                         classId != null ? "class: " + classId : "all students";
        logger.info("Created exam with ID: {} for course: {}, {}", 
            saved.getId(), courseId, audience);
        return saved;
    }
    
    public Assessment generateExamWithAI(String examId, Integer numberOfQuestions, String difficulty) {
        // AI generation features are restricted to SYSTEM_ADMIN and TENANT_ADMIN only
        String userRole = getCurrentUserRole();
        if (userRole == null || (!"SYSTEM_ADMIN".equals(userRole) && !"TENANT_ADMIN".equals(userRole))) {
            throw new IllegalArgumentException("AI generation features are only available to SYSTEM_ADMIN and TENANT_ADMIN");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Assessment exam = assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        if (exam.getModuleIds() == null || exam.getModuleIds().isEmpty()) {
            throw new IllegalStateException("No modules selected for exam generation");
        }
        
        logger.info("Generating exam questions with AI for exam: {}", examId);
        examGenerationService.generateQuestionsFromModules(exam, exam.getModuleIds(), numberOfQuestions, difficulty);
        
        Assessment updated = assessmentRepository.save(exam);
        logger.info("Successfully generated exam questions for exam: {}", examId);
        return updated;
    }
    
    public Assessment scheduleExam(String examId, OffsetDateTime startTime, OffsetDateTime endTime) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Assessment exam = assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        
        if (startTime.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Start time cannot be in the past");
        }
        
        // Store old times for logging
        OffsetDateTime oldStartTime = exam.getStartTime();
        OffsetDateTime oldEndTime = exam.getEndTime();
        boolean isReschedule = oldStartTime != null || oldEndTime != null;
        
        exam.setStartTime(startTime);
        exam.setEndTime(endTime);
        
        // Set status based on current time
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(startTime)) {
            exam.setStatus(Assessment.ExamStatus.SCHEDULED);
        } else if (now.isAfter(endTime)) {
            exam.setStatus(Assessment.ExamStatus.COMPLETED);
        } else {
            exam.setStatus(Assessment.ExamStatus.LIVE);
        }
        
        Assessment updated = assessmentRepository.save(exam);
        if (isReschedule) {
            logger.info("Rescheduled exam: {} from {} to {} (was: {} to {})", 
                examId, startTime, endTime, oldStartTime, oldEndTime);
        } else {
            logger.info("Scheduled exam: {} from {} to {}", examId, startTime, endTime);
        }
        return updated;
    }
    
    public List<Assessment> getLiveExams() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        OffsetDateTime now = OffsetDateTime.now();
        return assessmentRepository.findByAssessmentTypeAndStatusInAndClientIdOrderByStartTimeAsc(
            Assessment.AssessmentType.EXAM,
            List.of(Assessment.ExamStatus.LIVE, Assessment.ExamStatus.SCHEDULED),
            clientId
        ).stream()
        .filter(exam -> {
            // FLEXIBLE_START exams with LIVE status are always available
            if (exam.getTimingMode() == Assessment.TimingMode.FLEXIBLE_START) {
                return exam.getStatus() == Assessment.ExamStatus.LIVE;
            }
            
            // FIXED_WINDOW exams need time-based filtering
            if (exam.getStartTime() == null || exam.getEndTime() == null) {
                // If no times set but exam is LIVE, include it
                return exam.getStatus() == Assessment.ExamStatus.LIVE;
            }
            return !now.isBefore(exam.getStartTime()) && !now.isAfter(exam.getEndTime());
        })
        .toList();
    }
    
    public List<Assessment> getScheduledExams() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return assessmentRepository.findByAssessmentTypeAndStatusAndClientIdOrderByStartTimeAsc(
            Assessment.AssessmentType.EXAM,
            Assessment.ExamStatus.SCHEDULED,
            clientId
        );
    }
    
    public List<Assessment> getAllExams() {
        return getAllExams(false);
    }
    
    /**
     * Get all exams, optionally including archived ones.
     * For instructors, only exams they have access to are returned.
     * @param includeArchived whether to include archived exams
     */
    public List<Assessment> getAllExams(boolean includeArchived) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Assessment> exams;
        if (includeArchived) {
            exams = assessmentRepository.findAllByAssessmentTypeAndClientIdIncludingArchivedOrderByCreatedAtDesc(
                Assessment.AssessmentType.EXAM,
                clientId
            );
        } else {
            exams = assessmentRepository.findActiveByAssessmentTypeAndClientIdOrderByCreatedAtDesc(
                Assessment.AssessmentType.EXAM,
                clientId
            );
        }
        
        // Apply instructor filtering if the current user is an instructor
        exams = filterExamsForInstructor(exams);
        
        return exams;
    }
    
    /**
     * Filter exams based on instructor's section/class/course assignments.
     * Non-instructors see all exams.
     */
    private List<Assessment> filterExamsForInstructor(List<Assessment> exams) {
        String userRole = getCurrentUserRole();
        if (!"INSTRUCTOR".equals(userRole)) {
            return exams; // Non-instructors see all exams
        }
        
        String userId = getCurrentUserId();
        if (userId == null) {
            logger.warn("Could not determine user ID for instructor filtering");
            return exams;
        }
        
        Map<String, Object> instructorAccess = getInstructorAccess(userId);
        if (instructorAccess == null) {
            logger.warn("Could not get instructor access for user {}", userId);
            return java.util.Collections.emptyList(); // No access means no exams
        }
        
        java.util.Set<String> allowedCourseIds = getSetFromAccess(instructorAccess, "allowedCourseIds");
        java.util.Set<String> allowedClassIds = getSetFromAccess(instructorAccess, "allowedClassIds");
        java.util.Set<String> allowedSectionIds = getSetFromAccess(instructorAccess, "allowedSectionIds");
        
        logger.debug("Instructor {} has access to courses: {}, classes: {}, sections: {}", 
            userId, allowedCourseIds, allowedClassIds, allowedSectionIds);
        
        return exams.stream()
            .filter(exam -> canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get exams with pagination and filtering.
     * @param status Filter by exam status (optional)
     * @param timingMode Filter by timing mode (optional)
     * @param search Search in title and description (optional)
     * @param page Page number (0-based)
     * @param size Page size
     * @return Page of exams with filters applied
     */
    public Page<Assessment> getExamsPaginated(String status, String timingMode, String search, int page, int size) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Parse status filter
        Assessment.ExamStatus examStatus = null;
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            try {
                examStatus = Assessment.ExamStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid status, ignore filter
            }
        }
        
        // Parse timing mode filter
        Assessment.TimingMode examTimingMode = null;
        if (timingMode != null && !timingMode.isEmpty() && !timingMode.equalsIgnoreCase("all")) {
            try {
                examTimingMode = Assessment.TimingMode.valueOf(timingMode.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid timing mode, ignore filter
            }
        }
        
        // Normalize search
        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        
        // For instructors, we need to filter after fetching since the repository doesn't know about instructor access
        // We fetch all matching exams and then filter + paginate in memory
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole)) {
            return getExamsPaginatedForInstructor(clientId, examStatus, examTimingMode, searchTerm, page, size);
        }
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        return assessmentRepository.findExamsWithFilters(
            Assessment.AssessmentType.EXAM,
            clientId,
            examStatus,
            examTimingMode,
            searchTerm,
            pageable
        );
    }
    
    /**
     * Get paginated exams for an instructor, filtered by their section/class/course assignments.
     */
    private Page<Assessment> getExamsPaginatedForInstructor(UUID clientId, Assessment.ExamStatus examStatus, 
            Assessment.TimingMode examTimingMode, String searchTerm, int page, int size) {
        
        String userId = getCurrentUserId();
        if (userId == null) {
            logger.warn("Could not determine user ID for instructor pagination");
            return Page.empty();
        }
        
        Map<String, Object> instructorAccess = getInstructorAccess(userId);
        if (instructorAccess == null) {
            logger.warn("Could not get instructor access for user {}", userId);
            return Page.empty();
        }
        
        java.util.Set<String> allowedCourseIds = getSetFromAccess(instructorAccess, "allowedCourseIds");
        java.util.Set<String> allowedClassIds = getSetFromAccess(instructorAccess, "allowedClassIds");
        java.util.Set<String> allowedSectionIds = getSetFromAccess(instructorAccess, "allowedSectionIds");
        
        // Fetch all exams without pagination (for filtering)
        Pageable unpaged = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Assessment> allExams = assessmentRepository.findExamsWithFilters(
            Assessment.AssessmentType.EXAM,
            clientId,
            examStatus,
            examTimingMode,
            searchTerm,
            unpaged
        );
        
        // Filter by instructor access
        List<Assessment> filteredExams = allExams.getContent().stream()
            .filter(exam -> canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds))
            .collect(java.util.stream.Collectors.toList());
        
        // Apply pagination manually
        int start = page * size;
        int end = Math.min(start + size, filteredExams.size());
        
        if (start >= filteredExams.size()) {
            return new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                filteredExams.size()
            );
        }
        
        List<Assessment> pageContent = filteredExams.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(
            pageContent,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
            filteredExams.size()
        );
    }
    
    /**
     * Get exam counts by status for filter badges.
     * For instructors, only counts exams they have access to.
     */
    public Map<String, Long> getExamCountsByStatus() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // For instructors, we need to count only accessible exams
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole)) {
            return getExamCountsByStatusForInstructor(clientId);
        }
        
        List<Object[]> results = assessmentRepository.countExamsByStatus(
            Assessment.AssessmentType.EXAM,
            clientId
        );
        
        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        for (Object[] row : results) {
            Assessment.ExamStatus status = (Assessment.ExamStatus) row[0];
            Long count = (Long) row[1];
            // Handle null status (exams without status set default to DRAFT)
            String statusName = status != null ? status.name() : Assessment.ExamStatus.DRAFT.name();
            counts.merge(statusName, count, Long::sum);
            total += count;
        }
        counts.put("all", total);
        
        // Ensure all statuses are present
        for (Assessment.ExamStatus status : Assessment.ExamStatus.values()) {
            counts.putIfAbsent(status.name(), 0L);
        }
        
        return counts;
    }
    
    /**
     * Get exam counts by status for an instructor, filtered by their section/class/course assignments.
     */
    private Map<String, Long> getExamCountsByStatusForInstructor(UUID clientId) {
        String userId = getCurrentUserId();
        Map<String, Long> counts = new HashMap<>();
        
        // Ensure all statuses are present with 0 count
        for (Assessment.ExamStatus status : Assessment.ExamStatus.values()) {
            counts.put(status.name(), 0L);
        }
        counts.put("all", 0L);
        
        if (userId == null) {
            logger.warn("Could not determine user ID for instructor exam counts");
            return counts;
        }
        
        Map<String, Object> instructorAccess = getInstructorAccess(userId);
        if (instructorAccess == null) {
            logger.warn("Could not get instructor access for user {}", userId);
            return counts;
        }
        
        java.util.Set<String> allowedCourseIds = getSetFromAccess(instructorAccess, "allowedCourseIds");
        java.util.Set<String> allowedClassIds = getSetFromAccess(instructorAccess, "allowedClassIds");
        java.util.Set<String> allowedSectionIds = getSetFromAccess(instructorAccess, "allowedSectionIds");
        
        // Fetch all exams and filter by instructor access
        List<Assessment> allExams = assessmentRepository.findActiveByAssessmentTypeAndClientIdOrderByCreatedAtDesc(
            Assessment.AssessmentType.EXAM,
            clientId
        );
        
        long total = 0;
        for (Assessment exam : allExams) {
            if (canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds)) {
                String statusName = exam.getStatus() != null ? 
                    exam.getStatus().name() : Assessment.ExamStatus.DRAFT.name();
                counts.merge(statusName, 1L, Long::sum);
                total++;
            }
        }
        counts.put("all", total);
        
        return counts;
    }
    
    public Assessment getExamById(String examId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Use JOIN FETCH to eagerly load exam questions (from question bank)
        Assessment exam = assessmentRepository.findByIdAndClientIdWithExamQuestions(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        // Ensure examQuestions are initialized (in case of lazy loading)
        if (exam.getExamQuestions() != null) {
            exam.getExamQuestions().size(); // Force initialization
            // Also ensure each question's options are initialized
            for (ExamQuestion eq : exam.getExamQuestions()) {
                if (eq.getQuestion() != null && eq.getQuestion().getOptions() != null) {
                    eq.getQuestion().getOptions().size(); // Force initialization
                }
            }
        }
        
        // Also load regular questions if any (for backward compatibility)
        if (exam.getQuestions() != null) {
            exam.getQuestions().size(); // Force initialization
        }
        
        return exam;
    }
    
    /**
     * Check if exam is currently accessible (time-based validation)
     * Returns true if current time is within exam's start and end time
     */
    public boolean isExamAccessible(String examId) {
        Assessment exam = getExamById(examId);
        return isExamAccessible(exam);
    }
    
    /**
     * Check if exam is currently accessible (time-based validation)
     */
    public boolean isExamAccessible(Assessment exam) {
        if (exam.getStartTime() == null || exam.getEndTime() == null) {
            // If no schedule, it depends on status
            return exam.getStatus() == Assessment.ExamStatus.LIVE;
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        return !now.isBefore(exam.getStartTime()) && !now.isAfter(exam.getEndTime());
    }
    
    /**
     * Get real-time status of exam based on current time
     */
    public Assessment.ExamStatus getRealTimeStatus(Assessment exam) {
        if (exam.getStartTime() == null || exam.getEndTime() == null) {
            return exam.getStatus();
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            return Assessment.ExamStatus.SCHEDULED;
        } else if (now.isAfter(exam.getEndTime())) {
            return Assessment.ExamStatus.COMPLETED;
        } else {
            return Assessment.ExamStatus.LIVE;
        }
    }
    
    /**
     * Auto-update exam status based on current time
     * This is called periodically by a scheduled task
     * Works across all tenants without requiring tenant context
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void updateExamStatus() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            
            // Get all exams across all tenants that might need status updates
            List<Assessment> exams = assessmentRepository.findByAssessmentTypeAndStatusInOrderByStartTimeAsc(
                Assessment.AssessmentType.EXAM,
                List.of(Assessment.ExamStatus.SCHEDULED, Assessment.ExamStatus.LIVE)
            );
            
            int updatedCount = 0;
            for (Assessment exam : exams) {
                if (exam.getStartTime() == null || exam.getEndTime() == null) {
                    continue;
                }
                
                Assessment.ExamStatus newStatus = null;
                if (now.isBefore(exam.getStartTime())) {
                    newStatus = Assessment.ExamStatus.SCHEDULED;
                } else if (now.isAfter(exam.getEndTime())) {
                    newStatus = Assessment.ExamStatus.COMPLETED;
                } else {
                    newStatus = Assessment.ExamStatus.LIVE;
                }
                
                if (newStatus != null && newStatus != exam.getStatus()) {
                    exam.setStatus(newStatus);
                    assessmentRepository.save(exam);
                    updatedCount++;
                    logger.info("Updated exam status: examId={}, oldStatus={}, newStatus={}, clientId={}", 
                        exam.getId(), exam.getStatus(), newStatus, exam.getClientId());
                }
            }
            
            if (updatedCount > 0) {
                logger.info("Exam status update completed: {} exams updated", updatedCount);
            }
        } catch (Exception e) {
            logger.error("Error updating exam statuses in scheduled task", e);
        }
    }
    
    public Assessment updateExam(String examId, String title, String description, String instructions,
                                 List<String> moduleIds, Assessment.ReviewMethod reviewMethod, String classId, String sectionId,
                                 Boolean randomizeQuestions, Boolean randomizeMcqOptions,
                                 Boolean enableProctoring, Assessment.ProctoringMode proctoringMode,
                                 Integer photoIntervalSeconds, Boolean requireIdentityVerification,
                                 Boolean blockCopyPaste, Boolean blockTabSwitch, Integer maxTabSwitchesAllowed,
                                 Assessment.TimingMode timingMode, Integer timeLimitSeconds, Integer passingScorePercentage) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot update exams
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot update exams");
        }
        
        Assessment exam = getExamById(examId);
        
        if (title != null) {
            exam.setTitle(title);
        }
        if (description != null) {
            exam.setDescription(description);
        }
        if (instructions != null) {
            exam.setInstructions(instructions);
        }
        if (moduleIds != null) {
            exam.setModuleIds(moduleIds);
        }
        if (reviewMethod != null) {
            exam.setReviewMethod(reviewMethod);
        }
        // Update class and section (null means all students in course)
        exam.setClassId(classId);
        exam.setSectionId(sectionId);
        
        // Update randomization settings
        if (randomizeQuestions != null) {
            exam.setRandomizeQuestions(randomizeQuestions);
        }
        if (randomizeMcqOptions != null) {
            exam.setRandomizeMcqOptions(randomizeMcqOptions);
        }
        
        // Update proctoring configuration
        if (enableProctoring != null) {
            exam.setEnableProctoring(enableProctoring);
        }
        if (proctoringMode != null) {
            exam.setProctoringMode(proctoringMode);
        }
        if (photoIntervalSeconds != null) {
            exam.setPhotoIntervalSeconds(photoIntervalSeconds);
        }
        if (requireIdentityVerification != null) {
            exam.setRequireIdentityVerification(requireIdentityVerification);
        }
        if (blockCopyPaste != null) {
            exam.setBlockCopyPaste(blockCopyPaste);
        }
        if (blockTabSwitch != null) {
            exam.setBlockTabSwitch(blockTabSwitch);
        }
        if (maxTabSwitchesAllowed != null) {
            exam.setMaxTabSwitchesAllowed(maxTabSwitchesAllowed);
        }
        
        // Update timing mode
        if (timingMode != null) {
            exam.setTimingMode(timingMode);
            
            // Clear start/end times when switching to FLEXIBLE_START
            // (these times are only meaningful for FIXED_WINDOW mode)
            if (timingMode == Assessment.TimingMode.FLEXIBLE_START) {
                exam.setStartTime(null);
                exam.setEndTime(null);
                logger.info("Cleared start/end times for exam {} (switched to FLEXIBLE_START)", examId);
            }
        }
        if (timeLimitSeconds != null) {
            exam.setTimeLimitSeconds(timeLimitSeconds);
        }
        
        // Update passing score percentage
        if (passingScorePercentage != null) {
            exam.setPassingScorePercentage(passingScorePercentage);
        }
        
        return assessmentRepository.save(exam);
    }
    
    /**
     * Delete or archive an exam based on submission count.
     * - No submissions: permanently delete
     * - Has submissions: archive (soft delete)
     * 
     * @return true if deleted, false if archived
     */
    public boolean deleteExam(String examId) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot delete exams
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot delete exams");
        }
        
        Assessment exam = getExamById(examId);
        
        if (exam.getStatus() == Assessment.ExamStatus.LIVE) {
            throw new IllegalStateException("Cannot delete a live exam");
        }
        
        // Check if there are any submissions for this exam
        int submissionCount = getSubmissionCount(examId);
        
        if (submissionCount > 0) {
            // Archive the exam instead of deleting
            exam.setArchived(true);
            assessmentRepository.save(exam);
            logger.info("Archived exam {} with {} submissions", examId, submissionCount);
            return false; // indicates archived, not deleted
        } else {
            // No submissions, safe to delete
            assessmentRepository.delete(exam);
            logger.info("Deleted exam: {}", examId);
            return true; // indicates deleted
        }
    }
    
    /**
     * Publish an exam (make it live/available to students).
     * For FIXED_WINDOW: requires start/end times to be set
     * For FLEXIBLE_START: immediately goes LIVE
     * Instructors can publish exams within their assigned scope.
     */
    public Assessment publishExam(String examId) {
        String userRole = getCurrentUserRole();
        
        // Support staff and students cannot publish
        if ("SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("SUPPORT_STAFF and STUDENT have view-only access and cannot publish exams");
        }
        
        Assessment exam = getExamById(examId);
        
        // Instructors can only publish exams within their assigned scope
        if ("INSTRUCTOR".equals(userRole)) {
            if (!canInstructorManageExam(exam)) {
                throw new IllegalArgumentException("You don't have permission to publish this exam. It's outside your assigned scope.");
            }
        }
        
        if (exam.getStatus() == Assessment.ExamStatus.LIVE) {
            throw new IllegalStateException("Exam is already live");
        }
        
        if (exam.getStatus() == Assessment.ExamStatus.COMPLETED) {
            throw new IllegalStateException("Cannot publish a completed exam");
        }
        
        // For FIXED_WINDOW, check if start/end times are set
        if (exam.getTimingMode() == Assessment.TimingMode.FIXED_WINDOW) {
            if (exam.getStartTime() == null || exam.getEndTime() == null) {
                throw new IllegalStateException("Start and end times must be set for Fixed Window exams before publishing");
            }
            
            OffsetDateTime now = OffsetDateTime.now();
            if (now.isBefore(exam.getStartTime())) {
                exam.setStatus(Assessment.ExamStatus.SCHEDULED);
                logger.info("Exam {} scheduled (starts in future)", examId);
            } else if (now.isAfter(exam.getEndTime())) {
                throw new IllegalStateException("Cannot publish an exam whose end time has already passed");
            } else {
                exam.setStatus(Assessment.ExamStatus.LIVE);
                logger.info("Exam {} is now LIVE", examId);
            }
        } else {
            // FLEXIBLE_START - immediately go live
            exam.setStatus(Assessment.ExamStatus.LIVE);
            logger.info("Exam {} (Flexible Start) is now LIVE", examId);
        }
        
        return assessmentRepository.save(exam);
    }
    
    /**
     * Unpublish an exam (move back to DRAFT status).
     * Can unpublish SCHEDULED or LIVE exams.
     * COMPLETED exams cannot be unpublished.
     */
    public Assessment unpublishExam(String examId) {
        String userRole = getCurrentUserRole();
        
        // Support staff and students cannot unpublish
        if ("SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("SUPPORT_STAFF and STUDENT have view-only access and cannot unpublish exams");
        }
        
        Assessment exam = getExamById(examId);
        
        // Instructors can only unpublish exams within their assigned scope
        if ("INSTRUCTOR".equals(userRole)) {
            if (!canInstructorManageExam(exam)) {
                throw new IllegalArgumentException("You don't have permission to unpublish this exam. It's outside your assigned scope.");
            }
        }
        
        if (exam.getStatus() == Assessment.ExamStatus.DRAFT) {
            throw new IllegalStateException("Exam is already in draft status");
        }
        
        if (exam.getStatus() == Assessment.ExamStatus.COMPLETED) {
            throw new IllegalStateException("Cannot unpublish a completed exam");
        }
        
        Assessment.ExamStatus previousStatus = exam.getStatus();
        exam.setStatus(Assessment.ExamStatus.DRAFT);
        
        logger.info("Exam {} unpublished (was {})", examId, previousStatus);
        
        return assessmentRepository.save(exam);
    }
    
    /**
     * Complete an exam (end it and prevent further submissions).
     * Instructors can complete exams within their assigned scope.
     */
    public Assessment completeExam(String examId) {
        String userRole = getCurrentUserRole();
        
        // Support staff and students cannot complete
        if ("SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("SUPPORT_STAFF and STUDENT have view-only access and cannot complete exams");
        }
        
        Assessment exam = getExamById(examId);
        
        // Instructors can only complete exams within their assigned scope
        if ("INSTRUCTOR".equals(userRole)) {
            if (!canInstructorManageExam(exam)) {
                throw new IllegalArgumentException("You don't have permission to complete this exam. It's outside your assigned scope.");
            }
        }
        
        if (exam.getStatus() == Assessment.ExamStatus.COMPLETED) {
            throw new IllegalStateException("Exam is already completed");
        }
        
        if (exam.getStatus() == Assessment.ExamStatus.DRAFT) {
            throw new IllegalStateException("Cannot complete a draft exam. Publish it first.");
        }
        
        exam.setStatus(Assessment.ExamStatus.COMPLETED);
        Assessment updated = assessmentRepository.save(exam);
        logger.info("Exam {} marked as COMPLETED", examId);
        return updated;
    }
    
    /**
     * Archive an exam (soft delete).
     */
    public void archiveExam(String examId) {
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot archive exams");
        }
        
        Assessment exam = getExamById(examId);
        
        if (exam.getStatus() == Assessment.ExamStatus.LIVE) {
            throw new IllegalStateException("Cannot archive a live exam");
        }
        
        exam.setArchived(true);
        assessmentRepository.save(exam);
        logger.info("Archived exam: {}", examId);
    }
    
    /**
     * Unarchive an exam (restore from soft delete).
     */
    public void unarchiveExam(String examId) {
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot unarchive exams");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Find exam including archived ones
        Assessment exam = assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        exam.setArchived(false);
        assessmentRepository.save(exam);
        logger.info("Unarchived exam: {}", examId);
    }
    
    /**
     * Get submission count for an exam by calling the student service.
     */
    public int getSubmissionCount(String examId) {
        try {
            String url = gatewayUrl + "/api/assessments/" + examId + "/submissions";
            ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().size();
            }
            return 0;
        } catch (Exception e) {
            logger.warn("Failed to get submission count for exam {}: {}", examId, e.getMessage());
            return 0; // Assume no submissions if we can't check
        }
    }
    
    /**
     * Batch generate exams for multiple sections with randomized question selection.
     * Each section gets a separate exam with different question order/selection.
     */
    public BatchExamGenerationResponse batchGenerateExamsForSections(BatchExamGenerationRequest request) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot create exams
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot create exams");
        }
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify course exists
        courseRepository.findByIdAndClientId(request.getCourseId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + request.getCourseId()));
        
        // Validate that modules are specified for question generation
        if (request.getModuleIds() == null || request.getModuleIds().isEmpty()) {
            throw new IllegalArgumentException("At least one module must be selected for question generation");
        }
        
        BatchExamGenerationResponse response = new BatchExamGenerationResponse(request.getSectionIds().size());
        
        // Fetch section names for better exam titles
        Map<String, String> sectionNames = fetchSectionNames(request.getSectionIds());
        
        // Create an exam for each section
        for (String sectionId : request.getSectionIds()) {
            try {
                String sectionName = sectionNames.getOrDefault(sectionId, "Section " + sectionId.substring(0, 8));
                String examTitle = request.getTitle() + " - " + sectionName;
                
                // Create the exam
                Assessment exam = createExamForBatch(request, sectionId, examTitle, clientId);
                
                // Generate questions from question bank
                int questionCount = generateQuestionsForExam(exam.getId(), request, clientId);
                
                response.addExam(exam, sectionName, questionCount);
                logger.info("Created exam {} for section {} with {} questions", exam.getId(), sectionId, questionCount);
                
            } catch (Exception e) {
                logger.error("Failed to create exam for section {}: {}", sectionId, e.getMessage());
                response.addError("Section " + sectionId + ": " + e.getMessage());
            }
        }
        
        return response;
    }
    
    private Assessment createExamForBatch(BatchExamGenerationRequest request, String sectionId, String title, UUID clientId) {
        // Get next sequence
        Integer maxSequence = assessmentRepository.findMaxSequenceByCourseIdAndClientId(request.getCourseId(), clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        Assessment exam = new Assessment();
        exam.setId(UlidGenerator.nextUlid());
        exam.setClientId(clientId);
        exam.setCourseId(request.getCourseId());
        exam.setSectionId(sectionId);
        exam.setAssessmentType(Assessment.AssessmentType.EXAM);
        exam.setTitle(title);
        exam.setDescription(request.getDescription());
        exam.setInstructions(request.getInstructions());
        exam.setSequence(nextSequence);
        exam.setStatus(Assessment.ExamStatus.DRAFT);
        exam.setModuleIds(request.getModuleIds());
        
        // Apply exam settings
        BatchExamGenerationRequest.ExamSettings settings = request.getExamSettings();
        if (settings != null) {
            exam.setReviewMethod(parseReviewMethod(settings.getReviewMethod()));
            exam.setTimeLimitSeconds(settings.getTimeLimitSeconds());
            exam.setPassingScorePercentage(settings.getPassingScorePercentage() != null ? settings.getPassingScorePercentage() : 70);
            exam.setRandomizeQuestions(settings.isRandomizeQuestions());
            exam.setRandomizeMcqOptions(settings.isRandomizeMcqOptions());
            exam.setEnableProctoring(settings.isEnableProctoring());
            exam.setProctoringMode(parseProctoringMode(settings.getProctoringMode()));
            exam.setPhotoIntervalSeconds(settings.getPhotoIntervalSeconds() != null ? settings.getPhotoIntervalSeconds() : 120);
            exam.setRequireIdentityVerification(settings.isRequireIdentityVerification());
            exam.setBlockCopyPaste(settings.isBlockCopyPaste());
            exam.setBlockTabSwitch(settings.isBlockTabSwitch());
            exam.setMaxTabSwitchesAllowed(settings.getMaxTabSwitchesAllowed() != null ? settings.getMaxTabSwitchesAllowed() : 3);
            Assessment.TimingMode timingMode = parseTimingMode(settings.getTimingMode());
            exam.setTimingMode(timingMode);
            
            // Set start and end times only for FIXED_WINDOW mode
            // (FLEXIBLE_START doesn't use scheduled times - just duration)
            if (timingMode == Assessment.TimingMode.FIXED_WINDOW) {
                if (settings.getStartTime() != null) {
                    exam.setStartTime(settings.getStartTime());
                }
                if (settings.getEndTime() != null) {
                    exam.setEndTime(settings.getEndTime());
                }
            }
        } else {
            exam.setReviewMethod(Assessment.ReviewMethod.INSTRUCTOR);
            exam.setTimingMode(Assessment.TimingMode.FIXED_WINDOW);
        }
        
        return assessmentRepository.save(exam);
    }
    
    private int generateQuestionsForExam(String examId, BatchExamGenerationRequest request, UUID clientId) {
        BatchExamGenerationRequest.GenerationCriteria criteria = request.getGenerationCriteria();
        
        ExamPaperGenerationService.GenerationCriteria genCriteria = new ExamPaperGenerationService.GenerationCriteria();
        genCriteria.setModuleIds(request.getModuleIds());
        genCriteria.setRandomize(true); // Always randomize for batch generation to ensure variety
        genCriteria.setClearExisting(true); // Start fresh
        
        if (criteria != null) {
            genCriteria.setNumberOfQuestions(criteria.getNumberOfQuestions() != null ? criteria.getNumberOfQuestions() : 10);
            
            if (criteria.getDifficultyLevel() != null) {
                genCriteria.setDifficultyLevel(QuestionBank.DifficultyLevel.valueOf(criteria.getDifficultyLevel()));
            }
            
            if (criteria.getQuestionTypes() != null && !criteria.getQuestionTypes().isEmpty()) {
                List<QuestionBank.QuestionType> types = criteria.getQuestionTypes().stream()
                    .map(QuestionBank.QuestionType::valueOf)
                    .toList();
                genCriteria.setQuestionTypes(types);
            }
            
            if (criteria.getDifficultyDistribution() != null && !criteria.getDifficultyDistribution().isEmpty()) {
                Map<QuestionBank.DifficultyLevel, Integer> distribution = new java.util.HashMap<>();
                for (Map.Entry<String, Integer> entry : criteria.getDifficultyDistribution().entrySet()) {
                    distribution.put(QuestionBank.DifficultyLevel.valueOf(entry.getKey()), entry.getValue());
                }
                genCriteria.setDifficultyDistribution(distribution);
            }
            
            // Pass score per difficulty configuration to override question default points
            if (criteria.getScorePerDifficulty() != null && !criteria.getScorePerDifficulty().isEmpty()) {
                Map<QuestionBank.DifficultyLevel, Integer> scores = new java.util.HashMap<>();
                for (Map.Entry<String, Integer> entry : criteria.getScorePerDifficulty().entrySet()) {
                    scores.put(QuestionBank.DifficultyLevel.valueOf(entry.getKey()), entry.getValue());
                }
                genCriteria.setScorePerDifficulty(scores);
            }
        } else {
            genCriteria.setNumberOfQuestions(10);
        }
        
        List<ExamQuestion> generated = examPaperGenerationService.generateExamPaper(examId, genCriteria);
        return generated.size();
    }
    
    private Map<String, String> fetchSectionNames(List<String> sectionIds) {
        Map<String, String> sectionNames = new java.util.HashMap<>();
        
        try {
            for (String sectionId : sectionIds) {
                String url = gatewayUrl + "/api/sections/" + sectionId;
                ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Object name = response.getBody().get("name");
                    if (name != null) {
                        sectionNames.put(sectionId, name.toString());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch section names: {}", e.getMessage());
        }
        
        return sectionNames;
    }
    
    private Assessment.ReviewMethod parseReviewMethod(String reviewMethod) {
        if (reviewMethod == null) {
            return Assessment.ReviewMethod.INSTRUCTOR;
        }
        try {
            return Assessment.ReviewMethod.valueOf(reviewMethod);
        } catch (IllegalArgumentException e) {
            return Assessment.ReviewMethod.INSTRUCTOR;
        }
    }
    
    private Assessment.ProctoringMode parseProctoringMode(String proctoringMode) {
        if (proctoringMode == null) {
            return Assessment.ProctoringMode.DISABLED;
        }
        try {
            return Assessment.ProctoringMode.valueOf(proctoringMode);
        } catch (IllegalArgumentException e) {
            return Assessment.ProctoringMode.DISABLED;
        }
    }
    
    private Assessment.TimingMode parseTimingMode(String timingMode) {
        if (timingMode == null) {
            return Assessment.TimingMode.FIXED_WINDOW;
        }
        try {
            return Assessment.TimingMode.valueOf(timingMode);
        } catch (IllegalArgumentException e) {
            return Assessment.TimingMode.FIXED_WINDOW;
        }
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
            logger.debug("Could not get instructor access for user {}: {}", instructorUserId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Helper method to extract a Set of strings from instructor access response
     */
    @SuppressWarnings("unchecked")
    private java.util.Set<String> getSetFromAccess(Map<String, Object> access, String key) {
        if (access == null) {
            return new java.util.HashSet<>();
        }
        Object value = access.get(key);
        if (value instanceof List) {
            return new java.util.HashSet<>((List<String>) value);
        } else if (value instanceof java.util.Set) {
            return (java.util.Set<String>) value;
        }
        return new java.util.HashSet<>();
    }
    
    /**
     * Check if instructor can access/view an exam based on their assignments
     */
    private boolean canInstructorAccessExam(Assessment exam, 
            java.util.Set<String> allowedCourseIds, 
            java.util.Set<String> allowedClassIds, 
            java.util.Set<String> allowedSectionIds) {
        // Check if exam's course is directly assigned
        if (allowedCourseIds.contains(exam.getCourseId())) {
            // If exam is section-specific, check section access
            if (exam.getSectionId() != null) {
                return allowedSectionIds.contains(exam.getSectionId());
            }
            // If exam is class-specific, check class access
            if (exam.getClassId() != null) {
                return allowedClassIds.contains(exam.getClassId());
            }
            return true; // Course-wide exam
        }
        
        // Check if exam's class or section is assigned
        if (exam.getSectionId() != null && allowedSectionIds.contains(exam.getSectionId())) {
            return true;
        }
        if (exam.getClassId() != null && allowedClassIds.contains(exam.getClassId())) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if the current instructor can manage (publish/complete) an exam
     */
    private boolean canInstructorManageExam(Assessment exam) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        
        Map<String, Object> instructorAccess = getInstructorAccess(userId);
        if (instructorAccess == null) {
            return false;
        }
        
        java.util.Set<String> allowedCourseIds = getSetFromAccess(instructorAccess, "allowedCourseIds");
        java.util.Set<String> allowedClassIds = getSetFromAccess(instructorAccess, "allowedClassIds");
        java.util.Set<String> allowedSectionIds = getSetFromAccess(instructorAccess, "allowedSectionIds");
        
        return canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds);
    }
    
    /**
     * Get the current user's ID from the identity service
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                return null;
            }
            
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
                Object id = response.getBody().get("id");
                return id != null ? id.toString() : null;
            }
        } catch (Exception e) {
            logger.debug("Could not determine user ID: {}", e.getMessage());
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
            logger.debug("Could not determine user role: {}", e.getMessage());
        }
        return null;
    }
}
