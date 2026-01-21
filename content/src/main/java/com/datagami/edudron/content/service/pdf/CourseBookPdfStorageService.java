package com.datagami.edudron.content.service.pdf;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.CourseResource;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.repo.CourseResourceRepository;
import com.datagami.edudron.content.service.CourseService;
import com.datagami.edudron.content.service.MediaUploadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CourseBookPdfStorageService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final CourseBookPdfService courseBookPdfService;
    private final CourseService courseService;
    private final CourseResourceRepository courseResourceRepository;
    private final MediaUploadService mediaUploadService;

    public CourseBookPdfStorageService(
            CourseBookPdfService courseBookPdfService,
            CourseService courseService,
            CourseResourceRepository courseResourceRepository,
            MediaUploadService mediaUploadService
    ) {
        this.courseBookPdfService = courseBookPdfService;
        this.courseService = courseService;
        this.courseResourceRepository = courseResourceRepository;
        this.mediaUploadService = mediaUploadService;
    }

    public StoredCourseBookPdf regenerateAndStore(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        CourseDTO course = courseService.getCourseById(courseId);

        byte[] pdfBytes = courseBookPdfService.generateCourseBookPdf(courseId);
        String blobPath = buildBlobPath(clientIdStr, courseId);
        String fileUrl = mediaUploadService.uploadBytesToPath(pdfBytes, blobPath, PDF_CONTENT_TYPE);

        CourseResource resource = courseResourceRepository
                .findFirstByCourseIdAndClientIdAndResourceType(courseId, clientId, CourseResource.ResourceType.PDF)
                .orElseGet(() -> {
                    CourseResource r = new CourseResource();
                    r.setId(UlidGenerator.nextUlid());
                    r.setClientId(clientId);
                    r.setCourseId(courseId);
                    r.setResourceType(CourseResource.ResourceType.PDF);
                    r.setIsDownloadable(true);
                    return r;
                });

        resource.setTitle("Course Book: " + safe(course.getTitle()));
        resource.setDescription("Generated course book PDF");
        resource.setFileUrl(fileUrl);
        resource.setFileSizeBytes((long) pdfBytes.length);
        resource.setIsDownloadable(true);

        CourseResource saved = courseResourceRepository.save(resource);
        return new StoredCourseBookPdf(saved, pdfBytes);
    }

    private static String buildBlobPath(String tenantId, String courseId) {
        // tenantId/course-books/<courseId>/course-book.pdf
        return tenantId + "/course-books/" + courseId + "/course-book.pdf";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public record StoredCourseBookPdf(CourseResource resource, byte[] pdfBytes) {}
}

