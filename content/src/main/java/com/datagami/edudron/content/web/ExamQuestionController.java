package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.ExamQuestion;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.service.ExamPaperGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams/{examId}/questions")
@Tag(name = "Exam Questions", description = "Exam question management endpoints")
public class ExamQuestionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamQuestionController.class);
    
    @Autowired
    private ExamPaperGenerationService examPaperService;
    
    @GetMapping
    @Operation(summary = "Get exam questions", description = "Get all questions for an exam from the question bank")
    public ResponseEntity<List<ExamQuestion>> getExamQuestions(@PathVariable String examId) {
        try {
            List<ExamQuestion> questions = examPaperService.getExamQuestions(examId);
            return ResponseEntity.ok(questions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error fetching exam questions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/add")
    @Operation(summary = "Add questions to exam", description = "Add questions from question bank to exam")
    public ResponseEntity<List<ExamQuestion>> addQuestionsToExam(
            @PathVariable String examId,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> questionIds = (List<String>) request.get("questionIds");
            
            if (questionIds == null || questionIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            List<ExamQuestion> added = examPaperService.addQuestionsToExam(examId, questionIds);
            return ResponseEntity.status(HttpStatus.CREATED).body(added);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request adding questions: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error adding questions to exam", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/add-single")
    @Operation(summary = "Add single question", description = "Add a single question with optional points override")
    public ResponseEntity<ExamQuestion> addSingleQuestion(
            @PathVariable String examId,
            @RequestBody Map<String, Object> request) {
        try {
            String questionId = (String) request.get("questionId");
            Integer pointsOverride = request.get("pointsOverride") != null ? 
                ((Number) request.get("pointsOverride")).intValue() : null;
            
            if (questionId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            ExamQuestion added = examPaperService.addQuestionToExam(examId, questionId, pointsOverride);
            return ResponseEntity.status(HttpStatus.CREATED).body(added);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request adding question: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error adding question to exam", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{questionId}")
    @Operation(summary = "Remove question from exam", description = "Remove a question from the exam")
    public ResponseEntity<Void> removeQuestion(
            @PathVariable String examId,
            @PathVariable String questionId) {
        try {
            examPaperService.removeQuestionFromExam(examId, questionId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error removing question from exam", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/reorder")
    @Operation(summary = "Reorder questions", description = "Reorder questions in the exam")
    public ResponseEntity<Void> reorderQuestions(
            @PathVariable String examId,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> questionIds = (List<String>) request.get("questionIds");
            
            if (questionIds == null || questionIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            examPaperService.reorderExamQuestions(examId, questionIds);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request reordering questions: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error reordering exam questions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{questionId}/points")
    @Operation(summary = "Update question points", description = "Update the points override for a question")
    public ResponseEntity<ExamQuestion> updateQuestionPoints(
            @PathVariable String examId,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> request) {
        try {
            Integer pointsOverride = request.get("pointsOverride") != null ? 
                ((Number) request.get("pointsOverride")).intValue() : null;
            
            ExamQuestion updated = examPaperService.updateQuestionPoints(examId, questionId, pointsOverride);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error updating question points", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/generate")
    @Operation(summary = "Auto-generate exam paper", description = "Auto-generate exam paper from question bank based on criteria")
    public ResponseEntity<List<ExamQuestion>> generateExamPaper(
            @PathVariable String examId,
            @RequestBody Map<String, Object> request) {
        try {
            ExamPaperGenerationService.GenerationCriteria criteria = new ExamPaperGenerationService.GenerationCriteria();
            
            @SuppressWarnings("unchecked")
            List<String> moduleIds = (List<String>) request.get("moduleIds");
            criteria.setModuleIds(moduleIds);
            
            if (request.get("numberOfQuestions") != null) {
                criteria.setNumberOfQuestions(((Number) request.get("numberOfQuestions")).intValue());
            }
            
            String difficultyStr = (String) request.get("difficultyLevel");
            if (difficultyStr != null) {
                criteria.setDifficultyLevel(QuestionBank.DifficultyLevel.valueOf(difficultyStr));
            }
            
            @SuppressWarnings("unchecked")
            List<String> questionTypeStrs = (List<String>) request.get("questionTypes");
            if (questionTypeStrs != null) {
                List<QuestionBank.QuestionType> questionTypes = questionTypeStrs.stream()
                    .map(QuestionBank.QuestionType::valueOf)
                    .toList();
                criteria.setQuestionTypes(questionTypes);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Integer> distributionMap = (Map<String, Integer>) request.get("difficultyDistribution");
            if (distributionMap != null) {
                Map<QuestionBank.DifficultyLevel, Integer> distribution = new HashMap<>();
                for (Map.Entry<String, Integer> entry : distributionMap.entrySet()) {
                    distribution.put(QuestionBank.DifficultyLevel.valueOf(entry.getKey()), entry.getValue());
                }
                criteria.setDifficultyDistribution(distribution);
            }
            
            if (request.get("randomize") != null) {
                criteria.setRandomize((Boolean) request.get("randomize"));
            }
            
            if (request.get("clearExisting") != null) {
                criteria.setClearExisting((Boolean) request.get("clearExisting"));
            }
            
            List<ExamQuestion> generated = examPaperService.generateExamPaper(examId, criteria);
            return ResponseEntity.status(HttpStatus.CREATED).body(generated);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request generating exam paper: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            logger.warn("State error generating exam paper: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            logger.error("Error generating exam paper", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping
    @Operation(summary = "Clear all questions", description = "Remove all questions from the exam")
    public ResponseEntity<Void> clearExamQuestions(@PathVariable String examId) {
        try {
            examPaperService.clearExamQuestions(examId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error clearing exam questions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/stats")
    @Operation(summary = "Get exam stats", description = "Get question count and total points for the exam")
    public ResponseEntity<Map<String, Object>> getExamStats(@PathVariable String examId) {
        try {
            long count = examPaperService.getQuestionCount(examId);
            Integer totalPoints = examPaperService.getTotalPoints(examId);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("questionCount", count);
            stats.put("totalPoints", totalPoints != null ? totalPoints : 0);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting exam stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
