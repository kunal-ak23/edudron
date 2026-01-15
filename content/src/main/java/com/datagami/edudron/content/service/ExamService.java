package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
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
    
    public Assessment createExam(String courseId, String title, String description, String instructions,
                                 List<String> moduleIds, Assessment.ReviewMethod reviewMethod) {
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
        exam.setAssessmentType(Assessment.AssessmentType.EXAM);
        exam.setTitle(title);
        exam.setDescription(description);
        exam.setInstructions(instructions);
        exam.setSequence(nextSequence);
        exam.setStatus(Assessment.ExamStatus.DRAFT);
        exam.setReviewMethod(reviewMethod != null ? reviewMethod : Assessment.ReviewMethod.INSTRUCTOR);
        exam.setModuleIds(moduleIds != null ? moduleIds : List.of());
        
        Assessment saved = assessmentRepository.save(exam);
        logger.info("Created exam with ID: {} for course: {}", saved.getId(), courseId);
        return saved;
    }
    
    public Assessment generateExamWithAI(String examId, Integer numberOfQuestions, String difficulty) {
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
        
        logger.debug("Retrieved exam {} with {} questions", examId, 
            exam.getQuestions() != null ? exam.getQuestions().size() : 0);
        
        return exam;
    }
    
    /**
     * Auto-update exam status based on current time
     * This is called periodically by a scheduled task
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void updateExamStatus() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            return; // Skip if no tenant context
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        OffsetDateTime now = OffsetDateTime.now();
        
        List<Assessment> exams = assessmentRepository.findByAssessmentTypeAndStatusInAndClientIdOrderByStartTimeAsc(
            Assessment.AssessmentType.EXAM,
            List.of(Assessment.ExamStatus.SCHEDULED, Assessment.ExamStatus.LIVE),
            clientId
        );
        
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
                logger.debug("Updated exam {} status from {} to {}", exam.getId(), exam.getStatus(), newStatus);
            }
        }
    }
    
    public Assessment updateExam(String examId, String title, String description, String instructions,
                                 List<String> moduleIds, Assessment.ReviewMethod reviewMethod) {
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
        
        return assessmentRepository.save(exam);
    }
    
    public void deleteExam(String examId) {
        Assessment exam = getExamById(examId);
        
        if (exam.getStatus() == Assessment.ExamStatus.LIVE) {
            throw new IllegalStateException("Cannot delete a live exam");
        }
        
        assessmentRepository.delete(exam);
        logger.info("Deleted exam: {}", examId);
    }
}
