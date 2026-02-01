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

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            logger.debug("Initializing RestTemplate for identity service calls. Gateway URL: {}", gatewayUrl);
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
            restTemplate.setInterceptors(interceptors);
            logger.debug("RestTemplate initialized with TenantContextRestTemplateInterceptor and JWT token forwarding");
        }
        return restTemplate;
    }
    
    public Assessment createExam(String courseId, String title, String description, String instructions,
                                 List<String> moduleIds, Assessment.ReviewMethod reviewMethod, String classId, String sectionId,
                                 Boolean randomizeQuestions, Boolean randomizeMcqOptions,
                                 Boolean enableProctoring, Assessment.ProctoringMode proctoringMode,
                                 Integer photoIntervalSeconds, Boolean requireIdentityVerification,
                                 Boolean blockCopyPaste, Boolean blockTabSwitch, Integer maxTabSwitchesAllowed,
                                 String timingMode) {
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
            if (exam.getStartTime() == null || exam.getEndTime() == null) {
                return false;
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
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return assessmentRepository.findByAssessmentTypeAndClientIdOrderByCreatedAtDesc(
            Assessment.AssessmentType.EXAM,
            clientId
        );
    }
    
    public Assessment getExamById(String examId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Use JOIN FETCH to eagerly load questions
        Assessment exam = assessmentRepository.findByIdAndClientIdWithQuestions(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam: " + examId);
        }
        
        // Ensure questions are initialized (in case of lazy loading)
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
                                 Boolean blockCopyPaste, Boolean blockTabSwitch, Integer maxTabSwitchesAllowed) {
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
        
        return assessmentRepository.save(exam);
    }
    
    public void deleteExam(String examId) {
        // INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access - cannot delete exams
        String userRole = getCurrentUserRole();
        if ("INSTRUCTOR".equals(userRole) || "SUPPORT_STAFF".equals(userRole) || "STUDENT".equals(userRole)) {
            throw new IllegalArgumentException("INSTRUCTOR, SUPPORT_STAFF, and STUDENT have view-only access and cannot delete exams");
        }
        
        Assessment exam = getExamById(examId);
        
        if (exam.getStatus() == Assessment.ExamStatus.LIVE) {
            throw new IllegalStateException("Cannot delete a live exam");
        }
        
        assessmentRepository.delete(exam);
        logger.info("Deleted exam: {}", examId);
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
            exam.setTimingMode(parseTimingMode(settings.getTimingMode()));
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
