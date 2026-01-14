package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.datagami.edudron.content.service.ExamService;
import com.datagami.edudron.content.service.ExamReviewService;
import com.datagami.edudron.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exams")
@Tag(name = "Exams", description = "Exam management endpoints for administrators")
public class ExamController {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamController.class);
    
    @Autowired
    private ExamService examService;
    
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    
    @Autowired
    private ExamReviewService examReviewService;
    
    @PostMapping
    @Operation(summary = "Create exam", description = "Create a new exam")
    public ResponseEntity<Assessment> createExam(@RequestBody Map<String, Object> request) {
        String courseId = (String) request.get("courseId");
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String instructions = (String) request.get("instructions");
        
        @SuppressWarnings("unchecked")
        List<String> moduleIds = (List<String>) request.get("moduleIds");
        
        Assessment.ReviewMethod reviewMethod = null;
        if (request.get("reviewMethod") != null) {
            try {
                reviewMethod = Assessment.ReviewMethod.valueOf((String) request.get("reviewMethod"));
            } catch (IllegalArgumentException e) {
                reviewMethod = Assessment.ReviewMethod.INSTRUCTOR;
            }
        }
        
        Assessment exam = examService.createExam(courseId, title, description, instructions, moduleIds, reviewMethod);
        return ResponseEntity.status(HttpStatus.CREATED).body(exam);
    }
    
    @GetMapping
    @Operation(summary = "List exams", description = "Get all exams")
    public ResponseEntity<List<Assessment>> getAllExams() {
        List<Assessment> exams = examService.getAllExams();
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get exam", description = "Get exam details by ID")
    public ResponseEntity<Assessment> getExam(@PathVariable String id) {
        Assessment exam = examService.getExamById(id);
        return ResponseEntity.ok(exam);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update exam", description = "Update an existing exam")
    public ResponseEntity<Assessment> updateExam(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String instructions = (String) request.get("instructions");
        
        @SuppressWarnings("unchecked")
        List<String> moduleIds = (List<String>) request.get("moduleIds");
        
        Assessment.ReviewMethod reviewMethod = null;
        if (request.get("reviewMethod") != null) {
            try {
                reviewMethod = Assessment.ReviewMethod.valueOf((String) request.get("reviewMethod"));
            } catch (IllegalArgumentException e) {
                // Ignore invalid review method
            }
        }
        
        Assessment exam = examService.updateExam(id, title, description, instructions, moduleIds, reviewMethod);
        return ResponseEntity.ok(exam);
    }
    
    @PostMapping("/{id}/generate")
    @Operation(summary = "Generate exam with AI", description = "Generate exam questions using AI based on selected modules")
    public ResponseEntity<Assessment> generateExamWithAI(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        
        Integer numberOfQuestions = request.get("numberOfQuestions") != null ? 
            ((Number) request.get("numberOfQuestions")).intValue() : 10;
        String difficulty = (String) request.get("difficulty");
        
        Assessment exam = examService.generateExamWithAI(id, numberOfQuestions, difficulty);
        return ResponseEntity.ok(exam);
    }
    
    @PutMapping("/{id}/schedule")
    @Operation(summary = "Schedule exam", description = "Schedule an exam with start and end times")
    public ResponseEntity<Assessment> scheduleExam(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        
        String startTimeStr = request.get("startTime");
        String endTimeStr = request.get("endTime");
        
        if (startTimeStr == null || endTimeStr == null) {
            return ResponseEntity.badRequest().build();
        }
        
        OffsetDateTime startTime = OffsetDateTime.parse(startTimeStr);
        OffsetDateTime endTime = OffsetDateTime.parse(endTimeStr);
        
        Assessment exam = examService.scheduleExam(id, startTime, endTime);
        return ResponseEntity.ok(exam);
    }
    
    @PutMapping("/{id}/questions/{questionId}/tentative-answer")
    @Operation(summary = "Update tentative answer", description = "Update the tentative answer for a subjective question")
    public ResponseEntity<QuizQuestion> updateTentativeAnswer(
            @PathVariable String id,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> request) {
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        QuizQuestion question = quizQuestionRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        if (!question.getAssessmentId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        
        String editedTentativeAnswer = (String) request.get("editedTentativeAnswer");
        if (editedTentativeAnswer != null) {
            question.setEditedTentativeAnswer(editedTentativeAnswer);
        }
        
        if (request.get("useTentativeAnswerForGrading") != null) {
            question.setUseTentativeAnswerForGrading((Boolean) request.get("useTentativeAnswerForGrading"));
        }
        
        QuizQuestion updated = quizQuestionRepository.save(question);
        return ResponseEntity.ok(updated);
    }
    
    @GetMapping("/live")
    @Operation(summary = "Get live exams", description = "Get all currently live exams")
    public ResponseEntity<List<Assessment>> getLiveExams() {
        List<Assessment> exams = examService.getLiveExams();
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/scheduled")
    @Operation(summary = "Get scheduled exams", description = "Get all scheduled (upcoming) exams")
    public ResponseEntity<List<Assessment>> getScheduledExams() {
        List<Assessment> exams = examService.getScheduledExams();
        return ResponseEntity.ok(exams);
    }
    
    @GetMapping("/{id}/submissions")
    @Operation(summary = "Get all submissions", description = "Get all submissions for an exam")
    public ResponseEntity<List<?>> getSubmissions(@PathVariable String id) {
        // TODO: Implement submission retrieval from student service
        return ResponseEntity.ok(new ArrayList<>());
    }
    
    @PostMapping("/{id}/submissions/{submissionId}/review")
    @Operation(summary = "Review submission with AI", description = "Trigger AI review for a submission")
    public ResponseEntity<?> reviewSubmission(
            @PathVariable String id,
            @PathVariable String submissionId) {
        try {
            examReviewService.reviewSubmissionWithAI(submissionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to review submission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete exam", description = "Delete an exam")
    public ResponseEntity<Void> deleteExam(@PathVariable String id) {
        examService.deleteExam(id);
        return ResponseEntity.noContent().build();
    }
}
