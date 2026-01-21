package com.datagami.edudron.content.web;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobProperties;
import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.CourseResource;
import com.datagami.edudron.content.repo.CourseResourceRepository;
import com.datagami.edudron.content.service.MediaUploadService;
import com.datagami.edudron.content.service.pdf.CourseBookPdfStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/content/courses")
@Tag(name = "Course Book PDF", description = "Course book PDF generation and download endpoints")
public class CourseBookPdfController {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final CourseBookPdfStorageService courseBookPdfStorageService;
    private final CourseResourceRepository courseResourceRepository;
    private final MediaUploadService mediaUploadService;

    public CourseBookPdfController(
            CourseBookPdfStorageService courseBookPdfStorageService,
            CourseResourceRepository courseResourceRepository,
            MediaUploadService mediaUploadService
    ) {
        this.courseBookPdfStorageService = courseBookPdfStorageService;
        this.courseResourceRepository = courseResourceRepository;
        this.mediaUploadService = mediaUploadService;
    }

    @PostMapping("/{courseId}/book.pdf/regenerate")
    @Operation(summary = "Regenerate course book PDF", description = "Generates the course book PDF, uploads it to Azure Blob, persists it as a CourseResource (PDF), and returns the PDF.")
    public ResponseEntity<byte[]> regenerate(@PathVariable String courseId) {
        CourseBookPdfStorageService.StoredCourseBookPdf stored = courseBookPdfStorageService.regenerateAndStore(courseId);

        String filename = "course-book-" + courseId + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, PDF_CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(stored.pdfBytes());
    }

    @GetMapping("/{courseId}/book.pdf")
    @Operation(summary = "Download stored course book PDF", description = "Serves the latest stored course book PDF. Does not auto-regenerate.")
    public ResponseEntity<?> download(@PathVariable String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant context is not set"));
        }
        UUID clientId = UUID.fromString(clientIdStr);

        CourseResource resource = courseResourceRepository
                .findFirstByCourseIdAndClientIdAndResourceType(courseId, clientId, CourseResource.ResourceType.PDF)
                .orElse(null);

        if (resource == null || resource.getFileUrl() == null || resource.getFileUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Course book PDF not generated yet. Call POST /content/courses/" + courseId + "/book.pdf/regenerate"
            ));
        }

        BlobClient blobClient = mediaUploadService.getBlobClientFromUrl(resource.getFileUrl());
        BlobProperties props = blobClient.getProperties();
        long contentLength = props.getBlobSize();
        String contentType = props.getContentType() != null ? props.getContentType() : PDF_CONTENT_TYPE;

        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = mediaUploadService.openDownloadStreamByUrl(resource.getFileUrl())) {
                in.transferTo(outputStream);
            }
        };

        String filename = "course-book-" + courseId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }
}

