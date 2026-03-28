package com.datagami.edudron.student.web;

import com.datagami.edudron.student.service.ResultsExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for exporting student results as Excel workbooks.
 * Supports scoping by section, class, or course.
 */
@RestController
@RequestMapping("/api/results")
@Tag(name = "Results Export", description = "Export student results as Excel files")
public class ResultsExportController {

    private static final Logger log = LoggerFactory.getLogger(ResultsExportController.class);

    private final ResultsExportService resultsExportService;

    public ResultsExportController(ResultsExportService resultsExportService) {
        this.resultsExportService = resultsExportService;
    }

    @GetMapping("/export")
    @Operation(summary = "Export results", description = "Export student results as an Excel workbook. Provide one of sectionId, classId, or courseId.")
    public ResponseEntity<byte[]> exportResults(
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String courseId) {

        if ((sectionId == null || sectionId.isBlank())
                && (classId == null || classId.isBlank())
                && (courseId == null || courseId.isBlank())) {
            throw new IllegalArgumentException("At least one of sectionId, classId, or courseId must be provided");
        }

        byte[] workbook;
        String filename;

        if (sectionId != null && !sectionId.isBlank()) {
            log.info("Exporting results for section: {}", sectionId);
            workbook = resultsExportService.exportBySection(sectionId);
            filename = "results-section-" + sectionId + ".xlsx";
        } else if (classId != null && !classId.isBlank()) {
            log.info("Exporting results for class: {}", classId);
            workbook = resultsExportService.exportByClass(classId);
            filename = "results-class-" + classId + ".xlsx";
        } else {
            log.info("Exporting results for course: {}", courseId);
            workbook = resultsExportService.exportByCourse(courseId);
            filename = "results-course-" + courseId + ".xlsx";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(workbook.length);

        return ResponseEntity.ok().headers(headers).body(workbook);
    }
}
