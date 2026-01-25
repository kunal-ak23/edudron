package com.datagami.edudron.student.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.dto.BulkStudentImportRequest;
import com.datagami.edudron.student.dto.BulkStudentImportResult;
import com.datagami.edudron.student.service.BulkStudentImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Bulk Student Import", description = "Bulk student import endpoints")
public class BulkStudentImportController {

    private static final Logger log = LoggerFactory.getLogger(BulkStudentImportController.class);

    @Autowired
    private BulkStudentImportService bulkStudentImportService;

    @PostMapping("/bulk-import")
    @Operation(summary = "Bulk import students", description = "Import students from CSV or Excel file")
    public ResponseEntity<BulkStudentImportResult> bulkImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoGeneratePassword", required = false, defaultValue = "true") Boolean autoGeneratePassword,
            @RequestParam(value = "upsertExisting", required = false, defaultValue = "false") Boolean upsertExisting,
            @RequestParam(value = "autoEnroll", required = false, defaultValue = "false") Boolean autoEnroll,
            @RequestParam(value = "defaultCourseIds", required = false) String defaultCourseIds) {
        
        String clientId = TenantContext.getClientId();
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        log.info("=== BULK IMPORT REQUEST STARTED ===");
        log.info("Tenant/Client ID: {}", clientId);
        log.info("File name: {}", fileName);
        log.info("File size: {} bytes ({} KB)", fileSize, fileSize / 1024);
        log.info("Content type: {}", file.getContentType());
        log.info("Options: autoGeneratePassword={}, upsertExisting={}, autoEnroll={}", 
                autoGeneratePassword, upsertExisting, autoEnroll);
        if (defaultCourseIds != null && !defaultCourseIds.trim().isEmpty()) {
            log.info("Default course IDs: {}", defaultCourseIds);
        }
        
        try {
            BulkStudentImportRequest request = new BulkStudentImportRequest();
            request.setAutoGeneratePassword(autoGeneratePassword);
            request.setUpsertExisting(upsertExisting);
            request.setAutoEnroll(autoEnroll);
            
            if (defaultCourseIds != null && !defaultCourseIds.trim().isEmpty()) {
                request.setDefaultCourseIds(java.util.Arrays.asList(defaultCourseIds.split(",")));
            }
            
            log.info("Starting bulk import processing...");
            long startTime = System.currentTimeMillis();
            
            BulkStudentImportResult result = bulkStudentImportService.importStudents(file, request);
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("=== BULK IMPORT COMPLETED ===");
            log.info("Duration: {} ms ({} seconds)", duration, duration / 1000.0);
            log.info("Total rows: {}", result.getTotalRows());
            log.info("Processed rows: {}", result.getProcessedRows());
            log.info("Successful rows: {}", result.getSuccessfulRows());
            log.info("Failed rows: {}", result.getFailedRows());
            log.info("Skipped rows: {}", result.getSkippedRows());
            
            if (result.getFailedRows() > 0) {
                log.warn("Import completed with {} failures. Check detailed results for error messages.", 
                        result.getFailedRows());
                
                // Log first few failures for quick diagnosis
                if (result.getRowResults() != null) {
                    result.getRowResults().stream()
                        .filter(r -> !r.getSuccess())
                        .limit(5)
                        .forEach(r -> log.warn("Row {} failed: {} - {}", 
                                r.getRowNumber(), r.getEmail(), r.getErrorMessage()));
                    
                    if (result.getFailedRows() > 5) {
                        log.warn("... and {} more failures. See full results for details.", 
                                result.getFailedRows() - 5);
                    }
                }
            } else {
                log.info("All rows imported successfully!");
            }
            
            return ResponseEntity.status(HttpStatus.OK).body(result);
            
        } catch (Exception e) {
            log.error("=== BULK IMPORT FAILED ===");
            log.error("Tenant/Client ID: {}", clientId);
            log.error("File name: {}", fileName);
            log.error("Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}

