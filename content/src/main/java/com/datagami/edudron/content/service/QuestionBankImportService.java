package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.QuestionBank;
import com.datagami.edudron.content.domain.QuestionBankOption;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.QuestionBankOptionRepository;
import com.datagami.edudron.content.repo.QuestionBankRepository;
import com.datagami.edudron.content.repo.SectionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for importing questions from CSV and Excel files into the Question Bank.
 */
@Service
@Transactional
public class QuestionBankImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionBankImportService.class);
    
    @Autowired
    private QuestionBankRepository questionBankRepository;
    
    @Autowired
    private QuestionBankOptionRepository optionRepository;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    /**
     * Import questions from a CSV or Excel file.
     * 
     * Expected format (with ID column for updates):
     * id,questionType,questionText,points,difficultyLevel,moduleIds,lectureId,option1,option1Correct,option2,option2Correct,option3,option3Correct,option4,option4Correct,explanation,tentativeAnswer
     * 
     * @param courseId The course to import questions into
     * @param defaultModuleId Optional default module ID - if provided and a row doesn't have moduleIds, this will be used
     * @param file The uploaded file (CSV or Excel)
     * @return Import result with success and error counts
     */
    public QuestionImportResult importQuestions(String courseId, String defaultModuleId, MultipartFile file) {
        return importQuestions(courseId, defaultModuleId, file, false);
    }
    
    /**
     * Import questions from a CSV or Excel file with optional upsert support.
     * 
     * Expected format (with ID column for updates):
     * id,questionType,questionText,points,difficultyLevel,moduleIds,lectureId,option1,option1Correct,option2,option2Correct,option3,option3Correct,option4,option4Correct,explanation,tentativeAnswer
     * 
     * @param courseId The course to import questions into
     * @param defaultModuleId Optional default module ID - if provided and a row doesn't have moduleIds, this will be used
     * @param file The uploaded file (CSV or Excel)
     * @param upsertExisting If true, update existing questions when ID is provided
     * @return Import result with detailed per-row results
     */
    public QuestionImportResult importQuestions(String courseId, String defaultModuleId, MultipartFile file, boolean upsertExisting) {
        UUID clientId = getClientId();
        
        // Verify course exists
        courseRepository.findByIdAndClientId(courseId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        
        // Verify default module exists if provided
        if (defaultModuleId != null && !defaultModuleId.isEmpty()) {
            sectionRepository.findByIdAndClientId(defaultModuleId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Default module not found: " + defaultModuleId));
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename is required");
        }
        
        String lowerFilename = filename.toLowerCase();
        
        try {
            if (lowerFilename.endsWith(".csv")) {
                return importFromCsv(courseId, defaultModuleId, file, clientId, upsertExisting);
            } else if (lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls")) {
                return importFromExcel(courseId, defaultModuleId, file, clientId, upsertExisting);
            } else {
                throw new IllegalArgumentException("Unsupported file format. Please upload a CSV or Excel (.xlsx, .xls) file.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to import questions", e);
            throw new RuntimeException("Failed to import questions: " + e.getMessage(), e);
        }
    }
    
    private QuestionImportResult importFromCsv(String courseId, String defaultModuleId, MultipartFile file, UUID clientId, boolean upsertExisting) throws Exception {
        List<String[]> rows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    // Skip header row
                    continue;
                }
                
                // Parse CSV line (handle quoted values)
                String[] values = parseCsvLine(line);
                if (values.length > 0) {
                    rows.add(values);
                }
            }
        }
        
        return processRows(courseId, defaultModuleId, rows, clientId, upsertExisting);
    }
    
    private QuestionImportResult importFromExcel(String courseId, String defaultModuleId, MultipartFile file, UUID clientId, boolean upsertExisting) throws Exception {
        List<String[]> rows = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;
            
            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    // Skip header row
                    continue;
                }
                
                List<String> values = new ArrayList<>();
                for (int i = 0; i < 17; i++) { // 17 expected columns (added ID column)
                    Cell cell = row.getCell(i);
                    values.add(getCellStringValue(cell));
                }
                
                if (values.stream().anyMatch(v -> v != null && !v.isEmpty())) {
                    rows.add(values.toArray(new String[0]));
                }
            }
        }
        
        return processRows(courseId, defaultModuleId, rows, clientId, upsertExisting);
    }
    
    private QuestionImportResult processRows(String courseId, String defaultModuleId, List<String[]> rows, UUID clientId, boolean upsertExisting) {
        List<QuestionImportRowResult> rowResults = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int failedCount = 0;
        
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2; // +2 because of 0-indexing and header row
            String[] row = rows.get(i);
            
            // Get question text for display (column 2 with ID column)
            String questionText = getValue(row, 2);
            String displayText = questionText != null ? 
                (questionText.length() > 50 ? questionText.substring(0, 50) + "..." : questionText) : "";
            
            try {
                QuestionImportRowResult rowResult = createOrUpdateQuestionFromRow(courseId, defaultModuleId, row, clientId, upsertExisting);
                rowResult.setRowNumber(rowNumber);
                rowResult.setQuestionText(displayText);
                rowResults.add(rowResult);
                
                if (rowResult.isSuccess()) {
                    if (rowResult.isUpdated()) {
                        updatedCount++;
                    } else {
                        createdCount++;
                    }
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                QuestionImportRowResult errorResult = new QuestionImportRowResult();
                errorResult.setRowNumber(rowNumber);
                errorResult.setQuestionText(displayText);
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(e.getMessage());
                rowResults.add(errorResult);
                failedCount++;
                logger.warn("Failed to import row {}: {}", rowNumber, e.getMessage());
            }
        }
        
        logger.info("Import completed. Created: {}, Updated: {}, Failed: {}", createdCount, updatedCount, failedCount);
        return new QuestionImportResult(rows.size(), createdCount + updatedCount, failedCount, createdCount, updatedCount, rowResults);
    }
    
    private QuestionImportRowResult createOrUpdateQuestionFromRow(String courseId, String defaultModuleId, String[] row, UUID clientId, boolean upsertExisting) {
        // Expected columns (with ID column):
        // 0: id (optional), 1: questionType, 2: questionText, 3: points, 4: difficultyLevel, 5: moduleIds, 6: lectureId,
        // 7: option1, 8: option1Correct, 9: option2, 10: option2Correct, 11: option3, 12: option3Correct,
        // 13: option4, 14: option4Correct, 15: explanation, 16: tentativeAnswer
        
        if (row.length < 3) {
            throw new IllegalArgumentException("Row has insufficient columns");
        }
        
        String existingId = getValue(row, 0);
        String questionTypeStr = getValue(row, 1);
        String questionText = getValue(row, 2);
        String pointsStr = getValue(row, 3);
        String difficultyStr = getValue(row, 4);
        String moduleIdsStr = getValue(row, 5);
        String lectureId = getValue(row, 6);
        String explanation = getValue(row, 15);
        String tentativeAnswer = getValue(row, 16);
        
        // Validate required fields
        if (questionTypeStr == null || questionTypeStr.isEmpty()) {
            throw new IllegalArgumentException("Question type is required");
        }
        if (questionText == null || questionText.isEmpty()) {
            throw new IllegalArgumentException("Question text is required");
        }
        
        // Use default module if moduleIds not specified in row
        if ((moduleIdsStr == null || moduleIdsStr.isEmpty()) && defaultModuleId != null && !defaultModuleId.isEmpty()) {
            moduleIdsStr = defaultModuleId;
        }
        
        if (moduleIdsStr == null || moduleIdsStr.isEmpty()) {
            throw new IllegalArgumentException("At least one module ID is required (either in file or selected in form)");
        }
        
        // Parse question type
        QuestionBank.QuestionType questionType;
        try {
            questionType = QuestionBank.QuestionType.valueOf(questionTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid question type: " + questionTypeStr);
        }
        
        // Parse points
        int points = 1;
        if (pointsStr != null && !pointsStr.isEmpty()) {
            try {
                points = Integer.parseInt(pointsStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid points value: " + pointsStr);
            }
        }
        
        // Parse difficulty
        QuestionBank.DifficultyLevel difficultyLevel = null;
        if (difficultyStr != null && !difficultyStr.isEmpty()) {
            try {
                difficultyLevel = QuestionBank.DifficultyLevel.valueOf(difficultyStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid difficulty level: " + difficultyStr);
            }
        }
        
        // Parse module IDs (comma-separated)
        List<String> moduleIds = Arrays.stream(moduleIdsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        
        // Verify all modules exist
        for (String moduleId : moduleIds) {
            sectionRepository.findByIdAndClientId(moduleId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));
        }
        
        QuestionImportRowResult result = new QuestionImportRowResult();
        boolean isUpdate = false;
        QuestionBank question;
        
        // Check if updating existing question
        if (existingId != null && !existingId.isEmpty() && upsertExisting) {
            Optional<QuestionBank> existingOpt = questionBankRepository.findById(existingId);
            if (existingOpt.isPresent()) {
                question = existingOpt.get();
                // Verify it belongs to this client
                if (!question.getClientId().equals(clientId)) {
                    throw new IllegalArgumentException("Question not found or access denied: " + existingId);
                }
                isUpdate = true;
                
                // Delete existing options for update
                optionRepository.deleteByQuestionIdAndClientId(existingId, clientId);
            } else {
                throw new IllegalArgumentException("Question not found for update: " + existingId);
            }
        } else {
            // Create new question
            question = new QuestionBank();
            question.setId(UlidGenerator.nextUlid());
            question.setClientId(clientId);
        }
        
        // Set/update fields
        question.setCourseId(courseId);
        question.setModuleIds(moduleIds);
        question.setSubModuleId(lectureId != null && !lectureId.isEmpty() ? lectureId : null);
        question.setQuestionType(questionType);
        question.setQuestionText(questionText);
        question.setDefaultPoints(points);
        question.setDifficultyLevel(difficultyLevel);
        question.setExplanation(explanation);
        question.setTentativeAnswer(tentativeAnswer);
        question.setIsActive(true);
        
        QuestionBank saved = questionBankRepository.save(question);
        
        // Create options for MULTIPLE_CHOICE and TRUE_FALSE
        if (questionType == QuestionBank.QuestionType.MULTIPLE_CHOICE ||
            questionType == QuestionBank.QuestionType.TRUE_FALSE) {
            List<QuestionBankOption> options = createOptionsFromRow(saved.getId(), clientId, row, questionType);
            if (!options.isEmpty()) {
                optionRepository.saveAll(options);
            }
        }
        
        result.setSuccess(true);
        result.setQuestionId(saved.getId());
        result.setUpdated(isUpdate);
        return result;
    }
    
    private List<QuestionBankOption> createOptionsFromRow(String questionId, UUID clientId, String[] row, QuestionBank.QuestionType questionType) {
        List<QuestionBankOption> options = new ArrayList<>();
        
        if (questionType == QuestionBank.QuestionType.TRUE_FALSE) {
            // For TRUE_FALSE, create True and False options based on tentativeAnswer
            // Column 16 is tentativeAnswer (shifted by 1 due to ID column)
            String tentativeAnswer = getValue(row, 16);
            boolean answerIsTrue = tentativeAnswer != null && 
                (tentativeAnswer.equalsIgnoreCase("true") || tentativeAnswer.equalsIgnoreCase("TRUE"));
            
            QuestionBankOption trueOption = new QuestionBankOption();
            trueOption.setId(UlidGenerator.nextUlid());
            trueOption.setClientId(clientId);
            trueOption.setQuestionId(questionId);
            trueOption.setOptionText("True");
            trueOption.setIsCorrect(answerIsTrue);
            trueOption.setSequence(1);
            options.add(trueOption);
            
            QuestionBankOption falseOption = new QuestionBankOption();
            falseOption.setId(UlidGenerator.nextUlid());
            falseOption.setClientId(clientId);
            falseOption.setQuestionId(questionId);
            falseOption.setOptionText("False");
            falseOption.setIsCorrect(!answerIsTrue);
            falseOption.setSequence(2);
            options.add(falseOption);
        } else {
            // For MULTIPLE_CHOICE, parse option pairs (option, isCorrect)
            // Columns 7-14 are options (shifted by 1 due to ID column)
            int sequence = 1;
            for (int i = 7; i <= 13; i += 2) {
                String optionText = getValue(row, i);
                String correctStr = getValue(row, i + 1);
                
                if (optionText != null && !optionText.isEmpty()) {
                    QuestionBankOption option = new QuestionBankOption();
                    option.setId(UlidGenerator.nextUlid());
                    option.setClientId(clientId);
                    option.setQuestionId(questionId);
                    option.setOptionText(optionText);
                    option.setIsCorrect(parseBoolean(correctStr));
                    option.setSequence(sequence++);
                    options.add(option);
                }
            }
        }
        
        return options;
    }
    
    private String getValue(String[] row, int index) {
        if (index >= row.length) return null;
        String value = row[index];
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
    
    private boolean parseBoolean(String value) {
        if (value == null) return false;
        value = value.trim().toLowerCase();
        return value.equals("true") || value.equals("yes") || value.equals("1");
    }
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // Return as integer if it's a whole number
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
    
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        
        return result.toArray(new String[0]);
    }
    
    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }
    
    /**
     * Generate a CSV template for import.
     * Includes ID column (empty for new questions, filled for updates).
     */
    public String generateCsvTemplate() {
        return "id,questionType,questionText,points,difficultyLevel,moduleIds,lectureId,option1,option1Correct,option2,option2Correct,option3,option3Correct,option4,option4Correct,explanation,tentativeAnswer\n" +
               ",MULTIPLE_CHOICE,\"What is 2+2?\",1,EASY,\"moduleId1,moduleId2\",,\"4\",true,\"3\",false,\"5\",false,\"6\",false,\"Basic arithmetic\",\n" +
               ",TRUE_FALSE,\"The sky is blue\",1,EASY,moduleId1,lectureId1,,,,,,,,,\"\",true\n" +
               ",SHORT_ANSWER,\"What is the capital of France?\",2,MEDIUM,moduleId1,,,,,,,,,,\"\",Paris\n";
    }
    
    /**
     * Export questions to CSV format.
     * Includes question ID for re-import/update functionality.
     * 
     * @param questions List of questions to export (with options loaded)
     * @return CSV string
     */
    public String exportQuestionsToCsv(List<QuestionBank> questions) {
        StringBuilder csv = new StringBuilder();
        
        // Header with ID column
        csv.append("id,questionType,questionText,points,difficultyLevel,moduleIds,lectureId,");
        csv.append("option1,option1Correct,option2,option2Correct,option3,option3Correct,option4,option4Correct,");
        csv.append("explanation,tentativeAnswer\n");
        
        for (QuestionBank q : questions) {
            // ID
            csv.append(escapeCsv(q.getId())).append(",");
            
            // Question type
            csv.append(q.getQuestionType() != null ? q.getQuestionType().name() : "").append(",");
            
            // Question text
            csv.append(escapeCsv(q.getQuestionText())).append(",");
            
            // Points
            csv.append(q.getDefaultPoints() != null ? q.getDefaultPoints() : 1).append(",");
            
            // Difficulty level
            csv.append(q.getDifficultyLevel() != null ? q.getDifficultyLevel().name() : "").append(",");
            
            // Module IDs (comma-separated, wrapped in quotes)
            String moduleIds = q.getModuleIds() != null ? String.join(",", q.getModuleIds()) : "";
            csv.append(escapeCsv(moduleIds)).append(",");
            
            // Lecture ID (subModuleId)
            csv.append(escapeCsv(q.getSubModuleId())).append(",");
            
            // Options (up to 4)
            List<QuestionBankOption> options = q.getOptions();
            for (int i = 0; i < 4; i++) {
                if (options != null && i < options.size()) {
                    QuestionBankOption opt = options.get(i);
                    csv.append(escapeCsv(opt.getOptionText())).append(",");
                    csv.append(opt.getIsCorrect() != null && opt.getIsCorrect() ? "true" : "false").append(",");
                } else {
                    csv.append(","); // Empty option text
                    csv.append(","); // Empty option correct
                }
            }
            
            // Explanation
            csv.append(escapeCsv(q.getExplanation())).append(",");
            
            // Tentative answer
            csv.append(escapeCsv(q.getTentativeAnswer()));
            
            csv.append("\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Escape a value for CSV format.
     * Wraps in quotes if contains comma, quote, or newline.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Result of an import operation (legacy, kept for backward compatibility).
     */
    public static class ImportResult {
        private final int successCount;
        private final List<ImportError> errors;
        
        public ImportResult(int successCount, List<ImportError> errors) {
            this.successCount = successCount;
            this.errors = errors;
        }
        
        public int getSuccessCount() { return successCount; }
        public List<ImportError> getErrors() { return errors; }
        public int getErrorCount() { return errors.size(); }
        public int getTotalRows() { return successCount + errors.size(); }
    }
    
    /**
     * An error that occurred during import (legacy, kept for backward compatibility).
     */
    public static class ImportError {
        private final int rowNumber;
        private final String message;
        
        public ImportError(int rowNumber, String message) {
            this.rowNumber = rowNumber;
            this.message = message;
        }
        
        public int getRowNumber() { return rowNumber; }
        public String getMessage() { return message; }
    }
    
    /**
     * Detailed result of an import operation with per-row results.
     */
    public static class QuestionImportResult {
        private final int totalRows;
        private final int successfulRows;
        private final int failedRows;
        private final int createdRows;
        private final int updatedRows;
        private final List<QuestionImportRowResult> rowResults;
        
        public QuestionImportResult(int totalRows, int successfulRows, int failedRows, 
                                     int createdRows, int updatedRows, List<QuestionImportRowResult> rowResults) {
            this.totalRows = totalRows;
            this.successfulRows = successfulRows;
            this.failedRows = failedRows;
            this.createdRows = createdRows;
            this.updatedRows = updatedRows;
            this.rowResults = rowResults;
        }
        
        public int getTotalRows() { return totalRows; }
        public int getSuccessfulRows() { return successfulRows; }
        public int getFailedRows() { return failedRows; }
        public int getCreatedRows() { return createdRows; }
        public int getUpdatedRows() { return updatedRows; }
        public List<QuestionImportRowResult> getRowResults() { return rowResults; }
        
        // For backward compatibility
        public int getSuccessCount() { return successfulRows; }
        public int getErrorCount() { return failedRows; }
    }
    
    /**
     * Result for a single row in the import.
     */
    public static class QuestionImportRowResult {
        private int rowNumber;
        private String questionText;
        private boolean success;
        private String questionId;
        private String errorMessage;
        private boolean updated;
        
        public QuestionImportRowResult() {}
        
        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
        
        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public boolean isUpdated() { return updated; }
        public void setUpdated(boolean updated) { this.updated = updated; }
    }
}
