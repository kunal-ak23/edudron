package com.datagami.edudron.student.web;

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
        
        try {
            BulkStudentImportRequest request = new BulkStudentImportRequest();
            request.setAutoGeneratePassword(autoGeneratePassword);
            request.setUpsertExisting(upsertExisting);
            request.setAutoEnroll(autoEnroll);
            
            if (defaultCourseIds != null && !defaultCourseIds.trim().isEmpty()) {
                request.setDefaultCourseIds(java.util.Arrays.asList(defaultCourseIds.split(",")));
            }
            
            BulkStudentImportResult result = bulkStudentImportService.importStudents(file, request);
            
            log.info("Bulk import completed: {} successful, {} failed out of {} total rows", 
                    result.getSuccessfulRows(), result.getFailedRows(), result.getTotalRows());
            
            return ResponseEntity.status(HttpStatus.OK).body(result);
            
        } catch (Exception e) {
            log.error("Bulk import failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}

