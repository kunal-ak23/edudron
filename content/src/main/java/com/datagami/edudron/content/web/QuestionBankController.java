package com.datagami.edudron.content.web;

import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.service.QuestionBankImportService;
import com.datagami.edudron.content.service.QuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/question-bank")
@Tag(name = "Question Bank", description = "Question bank management endpoints")
public class QuestionBankController {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionBankController.class);
    
    @Autowired
    private QuestionBankService questionBankService;
    
    @Autowired
    private QuestionBankImportService questionBankImportService;
    
    @PostMapping
    @Operation(summary = "Create question", description = "Create a new question in the question bank (supports multiple modules)")
    public ResponseEntity<QuestionBank> createQuestion(@RequestBody Map<String, Object> request) {
        try {
            String courseId = (String) request.get("courseId");
            
            // Support both moduleId (legacy) and moduleIds (new)
            @SuppressWarnings("unchecked")
            List<String> moduleIds = (List<String>) request.get("moduleIds");
            if (moduleIds == null || moduleIds.isEmpty()) {
                String moduleId = (String) request.get("moduleId");
                if (moduleId != null) {
                    moduleIds = List.of(moduleId);
                }
            }
            
            String subModuleId = (String) request.get("subModuleId");
            String questionTypeStr = (String) request.get("questionType");
            String questionText = (String) request.get("questionText");
            Integer points = request.get("points") != null ? ((Number) request.get("points")).intValue() : 1;
            String difficultyStr = (String) request.get("difficultyLevel");
            String explanation = (String) request.get("explanation");
            String tentativeAnswer = (String) request.get("tentativeAnswer");
            
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> optionsData = (List<Map<String, Object>>) request.get("options");
            
            if (courseId == null || moduleIds == null || moduleIds.isEmpty() || questionTypeStr == null || questionText == null) {
                return ResponseEntity.badRequest().build();
            }
            
            QuestionBank.QuestionType questionType = QuestionBank.QuestionType.valueOf(questionTypeStr);
            QuestionBank.DifficultyLevel difficulty = difficultyStr != null ? 
                QuestionBank.DifficultyLevel.valueOf(difficultyStr) : null;
            
            List<QuestionBankService.OptionData> options = parseOptions(optionsData);
            
            QuestionBank question = questionBankService.createQuestion(
                courseId, moduleIds, subModuleId, questionType, questionText,
                points, difficulty, explanation, tags, options, tentativeAnswer);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(question);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request creating question: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error creating question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get question", description = "Get a question by ID")
    public ResponseEntity<QuestionBank> getQuestion(@PathVariable String id) {
        try {
            QuestionBank question = questionBankService.getQuestion(id);
            return ResponseEntity.ok(question);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error fetching question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping
    @Operation(summary = "List questions", description = "List questions with filters")
    public ResponseEntity<?> listQuestions(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String subModuleId,
            @RequestParam(required = false) String difficultyLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            // If subModuleId (lecture ID) is provided, get questions by lecture
            if (subModuleId != null) {
                List<QuestionBank> questions = questionBankService.getQuestionsBySubModule(subModuleId);
                return ResponseEntity.ok(questions);
            }
            
            // If moduleId is provided, get questions that contain this module in their moduleIds
            if (moduleId != null) {
                if (difficultyLevel != null) {
                    QuestionBank.DifficultyLevel difficulty = QuestionBank.DifficultyLevel.valueOf(difficultyLevel);
                    List<QuestionBank> questions = questionBankService.getQuestionsByModuleAndDifficulty(moduleId, difficulty);
                    return ResponseEntity.ok(questions);
                }
                List<QuestionBank> questions = questionBankService.getQuestionsByModule(moduleId);
                return ResponseEntity.ok(questions);
            }
            
            // If only courseId is provided
            if (courseId != null) {
                // Search by keyword if provided
                if (keyword != null && !keyword.isBlank()) {
                    List<QuestionBank> questions = questionBankService.searchQuestions(courseId, keyword);
                    return ResponseEntity.ok(questions);
                }
                
                // Return paginated results
                Pageable pageable = PageRequest.of(page, size);
                Page<QuestionBank> questions = questionBankService.getQuestionsByCourse(courseId, pageable);
                return ResponseEntity.ok(questions);
            }
            
            return ResponseEntity.badRequest().body("courseId or moduleId is required");
        } catch (Exception e) {
            logger.error("Error listing questions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/modules")
    @Operation(summary = "Get questions by modules", description = "Get questions from multiple modules")
    public ResponseEntity<List<QuestionBank>> getQuestionsByModules(
            @RequestParam List<String> moduleIds) {
        try {
            List<QuestionBank> questions = questionBankService.getQuestionsByModules(moduleIds);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("Error fetching questions by modules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update question", description = "Update an existing question (supports multiple modules)")
    public ResponseEntity<QuestionBank> updateQuestion(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        try {
            String questionText = (String) request.get("questionText");
            Integer points = request.get("points") != null ? ((Number) request.get("points")).intValue() : null;
            String difficultyStr = (String) request.get("difficultyLevel");
            String explanation = (String) request.get("explanation");
            String tentativeAnswer = (String) request.get("tentativeAnswer");
            String subModuleId = (String) request.get("subModuleId");
            
            // Support moduleIds array for updating multiple module associations
            @SuppressWarnings("unchecked")
            List<String> moduleIds = (List<String>) request.get("moduleIds");
            
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> optionsData = (List<Map<String, Object>>) request.get("options");
            
            QuestionBank.DifficultyLevel difficulty = difficultyStr != null ? 
                QuestionBank.DifficultyLevel.valueOf(difficultyStr) : null;
            
            List<QuestionBankService.OptionData> options = parseOptions(optionsData);
            
            QuestionBank question = questionBankService.updateQuestion(
                id, questionText, points, difficulty, explanation, tags, options, tentativeAnswer, subModuleId, moduleIds);
            
            return ResponseEntity.ok(question);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request updating question: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error updating question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete question", description = "Soft delete a question (mark as inactive)")
    public ResponseEntity<Void> deleteQuestion(@PathVariable String id) {
        try {
            questionBankService.deleteQuestion(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error deleting question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{id}/hard")
    @Operation(summary = "Hard delete question", description = "Permanently delete a question")
    public ResponseEntity<Void> hardDeleteQuestion(@PathVariable String id) {
        try {
            questionBankService.hardDeleteQuestion(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error hard deleting question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/bulk")
    @Operation(summary = "Bulk create questions", description = "Create multiple questions at once (supports multiple modules)")
    public ResponseEntity<List<QuestionBank>> bulkCreateQuestions(@RequestBody Map<String, Object> request) {
        try {
            String courseId = (String) request.get("courseId");
            
            // Support both moduleId (legacy) and moduleIds (new)
            @SuppressWarnings("unchecked")
            List<String> moduleIds = (List<String>) request.get("moduleIds");
            if (moduleIds == null || moduleIds.isEmpty()) {
                String moduleId = (String) request.get("moduleId");
                if (moduleId != null) {
                    moduleIds = List.of(moduleId);
                }
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsData = (List<Map<String, Object>>) request.get("questions");
            
            if (courseId == null || moduleIds == null || moduleIds.isEmpty() || questionsData == null || questionsData.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            List<QuestionBankService.QuestionData> questions = new ArrayList<>();
            for (Map<String, Object> data : questionsData) {
                QuestionBankService.QuestionData qd = new QuestionBankService.QuestionData();
                
                String questionTypeStr = (String) data.get("questionType");
                qd.setQuestionType(QuestionBank.QuestionType.valueOf(questionTypeStr));
                qd.setQuestionText((String) data.get("questionText"));
                qd.setPoints(data.get("points") != null ? ((Number) data.get("points")).intValue() : 1);
                
                String difficultyStr = (String) data.get("difficultyLevel");
                if (difficultyStr != null) {
                    qd.setDifficultyLevel(QuestionBank.DifficultyLevel.valueOf(difficultyStr));
                }
                
                qd.setExplanation((String) data.get("explanation"));
                qd.setTentativeAnswer((String) data.get("tentativeAnswer"));
                qd.setSubModuleId((String) data.get("subModuleId"));
                
                // Support per-question moduleIds override
                @SuppressWarnings("unchecked")
                List<String> questionModuleIds = (List<String>) data.get("moduleIds");
                qd.setModuleIds(questionModuleIds);
                
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) data.get("tags");
                qd.setTags(tags);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> optionsData = (List<Map<String, Object>>) data.get("options");
                qd.setOptions(parseOptions(optionsData));
                
                questions.add(qd);
            }
            
            List<QuestionBank> created = questionBankService.bulkCreateQuestions(courseId, moduleIds, questions);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request bulk creating questions: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error bulk creating questions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/count")
    @Operation(summary = "Get question count", description = "Get count of questions by course or module")
    public ResponseEntity<Map<String, Long>> getQuestionCount(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String moduleId) {
        try {
            long count;
            if (moduleId != null) {
                count = questionBankService.countByModule(moduleId);
            } else if (courseId != null) {
                count = questionBankService.countByCourse(courseId);
            } else {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            logger.error("Error getting question count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import questions", description = "Import questions from CSV or Excel file")
    public ResponseEntity<Map<String, Object>> importQuestions(
            @RequestParam String courseId,
            @RequestParam(required = false) String moduleId,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
            }
            
            QuestionBankImportService.ImportResult result = questionBankImportService.importQuestions(courseId, moduleId, file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());
            response.put("totalRows", result.getTotalRows());
            
            if (!result.getErrors().isEmpty()) {
                List<Map<String, Object>> errors = new ArrayList<>();
                for (QuestionBankImportService.ImportError error : result.getErrors()) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("row", error.getRowNumber());
                    errorMap.put("message", error.getMessage());
                    errors.add(errorMap);
                }
                response.put("errors", errors);
            }
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request importing questions: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error importing questions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to import questions: " + e.getMessage()));
        }
    }
    
    @GetMapping("/import/template")
    @Operation(summary = "Get import template", description = "Download CSV template for importing questions")
    public ResponseEntity<String> getImportTemplate() {
        try {
            String template = questionBankImportService.generateCsvTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "question-bank-template.csv");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(template);
        } catch (Exception e) {
            logger.error("Error generating import template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private List<QuestionBankService.OptionData> parseOptions(List<Map<String, Object>> optionsData) {
        if (optionsData == null) {
            return null;
        }
        List<QuestionBankService.OptionData> options = new ArrayList<>();
        for (Map<String, Object> optData : optionsData) {
            String text = (String) optData.get("text");
            Boolean correct = (Boolean) optData.get("correct");
            if (correct == null) {
                correct = optData.get("isCorrect") != null && (Boolean) optData.get("isCorrect");
            }
            options.add(new QuestionBankService.OptionData(text, correct != null && correct));
        }
        return options;
    }
}
