package com.datagami.edudron.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.ExamQuestion;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.config.CacheConfig;
import com.datagami.edudron.content.dto.BatchExamGenerationRequest;
import com.datagami.edudron.content.dto.BatchExamGenerationResponse;
import com.datagami.edudron.content.dto.ExamDetailDTO;
import com.datagami.edudron.content.dto.InstructorAccessResponse;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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
    
    @Autowired
    private CacheManager cacheManager;
    
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
        evictExamCache(examId);
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
        
        if (exam.getTimingMode() != Assessment.TimingMode.FIXED_WINDOW) {
            throw new IllegalArgumentException("Schedule can only be set for Fixed Window exams");
        }
        
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole)) {
            if (!canInstructorManageExam(exam)) {
                throw new IllegalArgumentException("You don't have permission to adjust the schedule for this exam. It's outside your assigned scope.");
            }
        } else if ("SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("SUPPORT_STAFF and STUDENT have view-only access and cannot schedule exams");
        }
        
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        
        // Store old times for logging and reschedule detection
        OffsetDateTime oldStartTime = exam.getStartTime();
        OffsetDateTime oldEndTime = exam.getEndTime();
        boolean isReschedule = oldStartTime != null || oldEndTime != null;
        OffsetDateTime now = OffsetDateTime.now();
        
        if (!isReschedule && startTime.isBefore(now)) {
            throw new IllegalArgumentException("Start time cannot be in the past");
        }
        if (isReschedule && exam.getStatus() == Assessment.ExamStatus.LIVE && !endTime.isAfter(now)) {
            throw new IllegalArgumentException("When rescheduling a live exam, end time must be in the future");
        }
        
        exam.setStartTime(startTime);
        exam.setEndTime(endTime);
        
        // Set status based on current time
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
        evictExamCache(examId);
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
     * Uses /me/access (same as exam list pagination); non-instructors or failed access see all exams.
     */
    private List<Assessment> filterExamsForInstructor(List<Assessment> exams) {
        // Use /me/access so we don't depend on identity role; same as getExamsPaginated
        Map<String, Object> instructorAccess = getCurrentUserInstructorAccess();
        if (instructorAccess == null || !hasAnyAllowedIds(instructorAccess)) {
            return exams; // No access or no allowed IDs -> return all (non-instructor path)
        }
        
        java.util.Set<String> allowedCourseIds = getEffectiveAllowedCourseIds(instructorAccess);
        java.util.Set<String> allowedClassIds = getEffectiveAllowedClassIds(instructorAccess);
        java.util.Set<String> allowedSectionIds = getEffectiveAllowedSectionIds(instructorAccess);
        boolean sectionOnlyAccess = getBooleanFromAccess(instructorAccess, "sectionOnlyAccess", false);
        
        boolean allEmpty = allowedCourseIds.isEmpty() && allowedClassIds.isEmpty() && allowedSectionIds.isEmpty();
        logger.info("Instructor exam filter: allowedCourseIds={} (sample: {}), allowedClassIds={} (sample: {}), allowedSectionIds={} (sample: {}), sectionOnlyAccess={}, examsBefore={}",
            allowedCourseIds.size(), sampleIds(allowedCourseIds, 5),
            allowedClassIds.size(), sampleIds(allowedClassIds, 5),
            allowedSectionIds.size(), sampleIds(allowedSectionIds, 5),
            sectionOnlyAccess, exams.size());
        if (allEmpty) {
            logger.warn("Instructor exam filter: all allowed* sets are empty. No exams will be returned.");
        }
        
        List<Assessment> filtered = exams.stream()
            .filter(exam -> canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds, sectionOnlyAccess))
            .collect(java.util.stream.Collectors.toList());
        
        logger.info("Instructor exam filter: examsAfter={}", filtered.size());
        if (!exams.isEmpty() && filtered.isEmpty() && !allEmpty) {
            Assessment first = exams.get(0);
            logger.warn("Instructor exam filter: no exam matched. First exam: courseId={}, classId={}, sectionId={}",
                first.getCourseId(), first.getClassId(), first.getSectionId());
        }
        
        return filtered;
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
        
        // Use instructor access (same as courses page) to decide filtering, not identity role.
        // This way we only call the student service; no dependency on /idp/users/me for role.
        // If /me/access returns a body with any allowed IDs, treat as instructor and filter.
        Map<String, Object> instructorAccess = getCurrentUserInstructorAccess();
        boolean isInstructorWithAccess = instructorAccess != null && hasAnyAllowedIds(instructorAccess);
        if (isInstructorWithAccess) {
            logger.info("Exam list: using instructor path (access returned with allowed IDs)");
            return getExamsPaginatedForInstructor(clientId, examStatus, examTimingMode, searchTerm, page, size, instructorAccess);
        }
        if (instructorAccess != null) {
            logger.info("Exam list: access returned but no allowed IDs; returning all exams");
        } else {
            logger.info("Exam list: no instructor access (null); returning all exams (non-instructor or /me/access failed)");
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
     * True if the instructor access map has at least one non-empty allowed* set
     * (or effective sets from direct/derived/inherited when allowed* are empty).
     */
    private boolean hasAnyAllowedIds(Map<String, Object> instructorAccess) {
        if (instructorAccess == null) return false;
        return !getEffectiveAllowedCourseIds(instructorAccess).isEmpty()
            || !getEffectiveAllowedClassIds(instructorAccess).isEmpty()
            || !getEffectiveAllowedSectionIds(instructorAccess).isEmpty();
    }
    
    /**
     * Effective allowed course IDs: allowedCourseIds, or union of directCourseIds + derivedCourseIds when empty.
     * Handles case where student service returns empty allowed* but populated direct/derived.
     */
    private java.util.Set<String> getEffectiveAllowedCourseIds(Map<String, Object> access) {
        java.util.Set<String> allowed = getSetFromAccess(access, "allowedCourseIds");
        if (!allowed.isEmpty()) return allowed;
        java.util.Set<String> direct = getSetFromAccess(access, "directCourseIds");
        java.util.Set<String> derived = getSetFromAccess(access, "derivedCourseIds");
        java.util.Set<String> union = new java.util.HashSet<>(direct);
        union.addAll(derived);
        return union;
    }
    
    /**
     * Effective allowed class IDs: allowedClassIds, or union of directClassIds + inheritedClassIds when empty.
     */
    private java.util.Set<String> getEffectiveAllowedClassIds(Map<String, Object> access) {
        java.util.Set<String> allowed = getSetFromAccess(access, "allowedClassIds");
        if (!allowed.isEmpty()) return allowed;
        java.util.Set<String> direct = getSetFromAccess(access, "directClassIds");
        java.util.Set<String> inherited = getSetFromAccess(access, "inheritedClassIds");
        java.util.Set<String> union = new java.util.HashSet<>(direct);
        union.addAll(inherited);
        return union;
    }
    
    /**
     * Effective allowed section IDs: allowedSectionIds, or union of directSectionIds + inheritedSectionIds when empty.
     */
    private java.util.Set<String> getEffectiveAllowedSectionIds(Map<String, Object> access) {
        java.util.Set<String> allowed = getSetFromAccess(access, "allowedSectionIds");
        if (!allowed.isEmpty()) return allowed;
        java.util.Set<String> direct = getSetFromAccess(access, "directSectionIds");
        java.util.Set<String> inherited = getSetFromAccess(access, "inheritedSectionIds");
        java.util.Set<String> union = new java.util.HashSet<>(direct);
        union.addAll(inherited);
        return union;
    }
    
    /**
     * Get paginated exams for an instructor, filtered by their section/class/course assignments.
     * @param instructorAccess already-fetched access (from getCurrentUserInstructorAccess); must be non-null.
     */
    private Page<Assessment> getExamsPaginatedForInstructor(UUID clientId, Assessment.ExamStatus examStatus, 
            Assessment.TimingMode examTimingMode, String searchTerm, int page, int size,
            Map<String, Object> instructorAccess) {
        
        if (instructorAccess == null) {
            logger.warn("Instructor pagination: no access. Returning empty page.");
            return Page.empty();
        }
        
        java.util.Set<String> allowedCourseIds = getEffectiveAllowedCourseIds(instructorAccess);
        java.util.Set<String> allowedClassIds = getEffectiveAllowedClassIds(instructorAccess);
        java.util.Set<String> allowedSectionIds = getEffectiveAllowedSectionIds(instructorAccess);
        boolean sectionOnlyAccess = getBooleanFromAccess(instructorAccess, "sectionOnlyAccess", false);
        
        boolean allEmpty = allowedCourseIds.isEmpty() && allowedClassIds.isEmpty() && allowedSectionIds.isEmpty();
        logger.info("Instructor pagination: allowedCourseIds={} (sample: {}), allowedClassIds={} (sample: {}), allowedSectionIds={} (sample: {}), sectionOnlyAccess={}",
            allowedCourseIds.size(), sampleIds(allowedCourseIds, 5),
            allowedClassIds.size(), sampleIds(allowedClassIds, 5),
            allowedSectionIds.size(), sampleIds(allowedSectionIds, 5), sectionOnlyAccess);
        if (allEmpty) {
            logger.warn("Instructor pagination: all allowed* sets are empty. Instructor has no assignments or /me/access returned empty. No exams will be returned.");
        }
        
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
        
        long totalFetched = allExams.getTotalElements();
        logger.info("Instructor pagination: repository returned {} exams for tenant (status={}, timing={}, search={})",
            totalFetched, examStatus, examTimingMode, searchTerm);
        
        // Filter by instructor access (section-only instructors see only section-level exams)
        List<Assessment> filteredExams = allExams.getContent().stream()
            .filter(exam -> canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds, sectionOnlyAccess))
            .collect(java.util.stream.Collectors.toList());
        
        logger.info("Instructor pagination: after filter {} -> {} exams (page={}, size={})", 
            totalFetched, filteredExams.size(), page, size);
        if (totalFetched > 0 && filteredExams.isEmpty() && !allEmpty) {
            // Have exams and have allowed IDs but none matched - log first exam's IDs for debugging
            Assessment first = allExams.getContent().get(0);
            logger.warn("Instructor pagination: no exam matched. First exam: courseId={}, classId={}, sectionId={} (normalized: {}, {}, {})",
                first.getCourseId(), first.getClassId(), first.getSectionId(),
                normalizeId(first.getCourseId()), normalizeId(first.getClassId()), normalizeId(first.getSectionId()));
        }
        
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
        
        // Use instructor access (same as exam list) to decide; don't depend on identity role
        Map<String, Object> instructorAccess = getCurrentUserInstructorAccess();
        if (instructorAccess != null && hasAnyAllowedIds(instructorAccess)) {
            return getExamCountsByStatusForInstructor(clientId, instructorAccess);
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
     * @param instructorAccess already-fetched access (caller ensures non-null and has allowed IDs).
     */
    private Map<String, Long> getExamCountsByStatusForInstructor(UUID clientId, Map<String, Object> instructorAccess) {
        Map<String, Long> counts = new HashMap<>();
        
        // Ensure all statuses are present with 0 count
        for (Assessment.ExamStatus status : Assessment.ExamStatus.values()) {
            counts.put(status.name(), 0L);
        }
        counts.put("all", 0L);
        
        if (instructorAccess == null) {
            return counts;
        }
        
        java.util.Set<String> allowedCourseIds = getEffectiveAllowedCourseIds(instructorAccess);
        java.util.Set<String> allowedClassIds = getEffectiveAllowedClassIds(instructorAccess);
        java.util.Set<String> allowedSectionIds = getEffectiveAllowedSectionIds(instructorAccess);
        boolean sectionOnlyAccess = getBooleanFromAccess(instructorAccess, "sectionOnlyAccess", false);
        
        logger.info("Instructor exam counts: allowedCourseIds={}, allowedClassIds={}, allowedSectionIds={}, sectionOnlyAccess={}",
            allowedCourseIds.size(), allowedClassIds.size(), allowedSectionIds.size(), sectionOnlyAccess);
        
        // Fetch all exams and filter by instructor access (section-only: only section-level exams)
        List<Assessment> allExams = assessmentRepository.findActiveByAssessmentTypeAndClientIdOrderByCreatedAtDesc(
            Assessment.AssessmentType.EXAM,
            clientId
        );
        
        long total = 0;
        for (Assessment exam : allExams) {
            if (canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds, sectionOnlyAccess)) {
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
     * Get exam detail as DTO (cached in Redis). Same exam for many students = one DB hit per tenant.
     */
    @Cacheable(value = CacheConfig.EXAM_DETAIL_CACHE, key = "T(com.datagami.edudron.common.TenantContext).getClientId() + '::' + #examId", unless = "#result == null")
    public ExamDetailDTO getExamDetailDTO(String examId) {
        Assessment exam = getExamById(examId);
        return ExamDetailDTO.fromAssessment(exam);
    }
    
    /**
     * Evict cached exam detail after any mutation (update, schedule, publish, question change, etc.).
     */
    public void evictExamCache(String examId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            return;
        }
        String key = clientIdStr + "::" + examId;
        var cache = cacheManager.getCache(CacheConfig.EXAM_DETAIL_CACHE);
        if (cache != null) {
            cache.evict(key);
            logger.debug("Evicted exam cache for key: {}", key);
        }
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
        
        Assessment saved = assessmentRepository.save(exam);
        evictExamCache(examId);
        return saved;
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
            evictExamCache(examId);
            return false; // indicates archived, not deleted
        } else {
            // No submissions, safe to delete
            assessmentRepository.delete(exam);
            logger.info("Deleted exam: {}", examId);
            evictExamCache(examId);
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
        
        Assessment saved = assessmentRepository.save(exam);
        evictExamCache(examId);
        return saved;
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
        
        Assessment saved = assessmentRepository.save(exam);
        evictExamCache(examId);
        return saved;
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
        evictExamCache(examId);
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
        evictExamCache(examId);
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
        evictExamCache(examId);
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
     * Get instructor access for the current user from student service.
     * Resolves current user ID via identity (/idp/users/me) then calls
     * GET /api/instructor-assignments/instructor/{userId}/access so we do not rely on
     * the student service resolving the user from JWT on server-to-server calls.
     * Returns null if unable to determine access.
     */
    private Map<String, Object> getCurrentUserInstructorAccess() {
        String userId = getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            logger.debug("Instructor access: no current user ID (anonymous or /me failed)");
            return null;
        }
        Map<String, Object> access = getInstructorAccess(userId);
        if (access != null) {
            logger.info("Instructor access OK: userId={}. allowedCourseIds size: {}, allowedClassIds size: {}, allowedSectionIds size: {}",
                userId, listSize(access.get("allowedCourseIds")), listSize(access.get("allowedClassIds")), listSize(access.get("allowedSectionIds")));
        }
        return access;
    }
    
    /**
     * Get instructor access by instructor user ID (for callers that have the ID).
     * Uses typed DTO so Jackson deserializes JSON arrays as List&lt;String&gt; (avoids
     * Map&lt;String, Object&gt; type ambiguity that can yield size 0). Forwards X-Client-Id
     * and Authorization so the student service sees the same tenant and auth as the browser.
     * Returns null if unable to determine access.
     */
    private Map<String, Object> getInstructorAccess(String instructorUserId) {
        try {
            String accessUrl = gatewayUrl + "/api/instructor-assignments/instructor/" + instructorUserId + "/access";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String clientId = TenantContext.getClientId();
            if (clientId != null && !clientId.isBlank()) {
                headers.set("X-Client-Id", clientId);
            }
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request != null ? request.getHeader("Authorization") : null;
                if (authHeader != null && !authHeader.isBlank()) {
                    headers.set("Authorization", authHeader);
                }
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<InstructorAccessResponse> response = getRestTemplate().exchange(
                accessUrl,
                HttpMethod.GET,
                entity,
                InstructorAccessResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                InstructorAccessResponse dto = response.getBody();
                Map<String, Object> body = dto.toMap();
                logger.info("Instructor access response (content service): url={}, instructorUserId={}, allowedCourseIds size={}, allowedClassIds size={}, allowedSectionIds size={}, sectionOnlyAccess={}",
                        accessUrl,
                        dto.getInstructorUserId(),
                        dto.getAllowedCourseIds().size(),
                        dto.getAllowedClassIds().size(),
                        dto.getAllowedSectionIds().size(),
                        dto.isSectionOnlyAccess());
                return body;
            }
        } catch (Exception e) {
            logger.debug("Could not get instructor access for user {}: {}", instructorUserId, e.getMessage());
        }
        return null;
    }
    
    /**
     * Return size of a list/collection/array for logging (0 if null or not a list/array).
     * Handles List, Collection, Object[], and Jackson JsonNode (ArrayNode).
     */
    private static int listSize(Object value) {
        if (value == null) return 0;
        if (value instanceof List) return ((List<?>) value).size();
        if (value instanceof java.util.Collection) return ((java.util.Collection<?>) value).size();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value);
        if (value instanceof JsonNode) {
            JsonNode node = (JsonNode) value;
            return node.isArray() ? node.size() : 0;
        }
        return 0;
    }
    
    /**
     * Return a sample of IDs for logging (first n, or "none" if empty).
     */
    private static String sampleIds(java.util.Set<String> ids, int max) {
        if (ids == null || ids.isEmpty()) {
            return "none";
        }
        return ids.stream().limit(max).reduce((a, b) -> a + ", " + b).orElse("none");
    }
    
    /**
     * Normalize ID for comparison (trim, lowercase) so UUID/string format differences match.
     */
    private static String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim();
        return s.isEmpty() ? null : s.toLowerCase();
    }
    
    /**
     * Helper method to extract a Set of normalized strings from instructor access response.
     * Handles List/Set/array and converts elements to String (handles Number, UUID, etc. from JSON).
     * Tries exact key first, then case-insensitive match. Handles JSON arrays deserialized as Object[].
     */
    @SuppressWarnings("unchecked")
    private java.util.Set<String> getSetFromAccess(Map<String, Object> access, String key) {
        if (access == null) {
            return new java.util.HashSet<>();
        }
        Object value = access.get(key);
        if (value == null && !access.isEmpty()) {
            for (Map.Entry<String, Object> entry : access.entrySet()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        java.util.Set<String> result = new java.util.HashSet<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String id = idFromJsonElement(item);
                if (id != null) {
                    result.add(normalizeId(id));
                }
            }
        } else if (value instanceof java.util.Set) {
            for (Object item : (java.util.Set<?>) value) {
                String id = idFromJsonElement(item);
                if (id != null) {
                    result.add(normalizeId(id));
                }
            }
        } else if (value != null && value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(value, i);
                String id = idFromJsonElement(item);
                if (id != null) {
                    result.add(normalizeId(id));
                }
            }
        } else if (value instanceof JsonNode) {
            JsonNode node = (JsonNode) value;
            if (node.isArray()) {
                for (JsonNode child : node) {
                    String id = idFromJsonElement(child);
                    if (id != null) {
                        result.add(normalizeId(id));
                    }
                }
            }
        }
        return result;
    }

    private static String idFromJsonElement(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof String) {
            return (String) item;
        }
        if (item instanceof JsonNode) {
            JsonNode node = (JsonNode) item;
            if (node.isTextual()) return node.asText();
            if (node.isNull()) return null;
            return node.asText();
        }
        if (item instanceof java.util.Map) {
            Object id = ((java.util.Map<?, ?>) item).get("id");
            return id != null ? id.toString() : null;
        }
        return item.toString();
    }
    
    /**
     * Check if instructor can access/view an exam based on their assignments.
     * Uses normalized ID comparison so UUID/string format differences match.
     * When sectionOnlyAccess is true, only section-level exams (exam.sectionId set) in allowedSectionIds are shown.
     */
    private boolean canInstructorAccessExam(Assessment exam,
            java.util.Set<String> allowedCourseIds,
            java.util.Set<String> allowedClassIds,
            java.util.Set<String> allowedSectionIds,
            boolean sectionOnlyAccess) {
        String courseId = normalizeId(exam.getCourseId());
        String sectionId = normalizeId(exam.getSectionId());
        String classId = normalizeId(exam.getClassId());

        // Instructor with only section-level access: show only section-level exams assigned to their sections
        if (sectionOnlyAccess) {
            return sectionId != null && allowedSectionIds.contains(sectionId);
        }

        // Section-level exam: show if instructor has that section
        if (sectionId != null && allowedSectionIds.contains(sectionId)) {
            return true;
        }

        // Check if exam's course is directly assigned
        if (courseId != null && allowedCourseIds.contains(courseId)) {
            // If exam is section-specific, already handled above
            if (sectionId != null) {
                return allowedSectionIds.contains(sectionId);
            }
            // If exam is class-specific, check class access
            if (classId != null) {
                return allowedClassIds.contains(classId);
            }
            return true; // Course-wide exam
        }

        // Class-wide exam (no section)
        if (classId != null && allowedClassIds.contains(classId)) {
            return true;
        }

        return false;
    }
    
    /**
     * Check if the current instructor can manage (publish/complete) an exam
     */
    private boolean canInstructorManageExam(Assessment exam) {
        Map<String, Object> instructorAccess = getCurrentUserInstructorAccess();
        if (instructorAccess == null) {
            return false;
        }
        
        java.util.Set<String> allowedCourseIds = getEffectiveAllowedCourseIds(instructorAccess);
        java.util.Set<String> allowedClassIds = getEffectiveAllowedClassIds(instructorAccess);
        java.util.Set<String> allowedSectionIds = getEffectiveAllowedSectionIds(instructorAccess);
        boolean sectionOnlyAccess = getBooleanFromAccess(instructorAccess, "sectionOnlyAccess", false);
        
        return canInstructorAccessExam(exam, allowedCourseIds, allowedClassIds, allowedSectionIds, sectionOnlyAccess);
    }
    
    /**
     * Get boolean from instructor access map (e.g. sectionOnlyAccess).
     */
    private static boolean getBooleanFromAccess(Map<String, Object> access, String key, boolean defaultValue) {
        if (access == null) return defaultValue;
        Object value = access.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
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
