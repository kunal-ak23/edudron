package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.client.ContentAssessmentClient;
import com.datagami.edudron.student.client.ContentExamClient;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.ProjectEventGradeRepository;
import com.datagami.edudron.student.repo.ProjectEventRepository;
import com.datagami.edudron.student.repo.ProjectRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultsExportServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ContentAssessmentClient contentAssessmentClient;
    @Mock
    private ContentExamClient contentExamClient;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private AssessmentSubmissionRepository submissionRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectEventRepository projectEventRepository;
    @Mock
    private ProjectEventGradeRepository projectEventGradeRepository;
    @Mock
    private SectionRepository sectionRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private StudentAuditService auditService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void exportBySectionIncludesOnlyAssessmentsAssignedToThatSection() throws Exception {
        UUID clientId = UUID.randomUUID();
        String sectionId = "section-d1";
        String courseId = "course-1";
        String emptyCourseId = "course-empty";
        String studentId = "student-1";

        TenantContext.setClientId(clientId.toString());

        ResultsExportService service = new ResultsExportService(
                contentAssessmentClient,
                contentExamClient,
                enrollmentRepository,
                submissionRepository,
                projectRepository,
                projectEventRepository,
                projectEventGradeRepository,
                sectionRepository,
                classRepository,
                auditService
        );
        ReflectionTestUtils.setField(service, "gatewayUrl", "http://127.0.0.1:1");

        Section section = new Section();
        section.setId(sectionId);
        section.setName("D1");

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setBatchId(sectionId);

        Enrollment emptyEnrollment = new Enrollment();
        emptyEnrollment.setStudentId(studentId);
        emptyEnrollment.setCourseId(emptyCourseId);
        emptyEnrollment.setBatchId(sectionId);

        when(sectionRepository.findByIdAndClientId(sectionId, clientId)).thenReturn(Optional.of(section));
        when(enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId)).thenReturn(List.of(enrollment, emptyEnrollment));
        when(contentAssessmentClient.getCourse(courseId)).thenReturn(json("{\"title\":\"Course Alpha\"}"));
        when(contentAssessmentClient.getCourse(emptyCourseId)).thenReturn(json("{\"title\":\"Course Empty\"}"));
        when(contentAssessmentClient.getAssessmentsForCourse(courseId)).thenReturn(List.of(
                json("{\"id\":\"exam-d1\",\"title\":\"D1 Exam Full Title\",\"courseId\":\"course-1\",\"sectionId\":\"section-d1\"}"),
                json("{\"id\":\"exam-d2\",\"title\":\"D2 Exam\",\"courseId\":\"course-1\",\"sectionId\":\"section-d2\"}"),
                json("{\"id\":\"exam-other-course\",\"title\":\"Other Course Exam\",\"courseId\":\"course-2\",\"sectionId\":\"section-d1\"}")
        ));
        when(contentAssessmentClient.getAssessmentsForCourse(emptyCourseId)).thenReturn(List.of());
        when(contentExamClient.getExam("exam-d1")).thenReturn(json("""
                {
                  "id": "exam-d1",
                  "courseId": "course-1",
                  "sectionId": "section-d1",
                  "questions": [
                    { "points": 40 },
                    { "points": 60 }
                  ]
                }
                """));
        AssessmentSubmissionRepository.ScoreSummaryProjection d1Score = scoreSummary(studentId, "exam-d1", 80);
        AssessmentSubmissionRepository.ScoreSummaryProjection d2Score = scoreSummary(studentId, "exam-d2", 70);
        AssessmentSubmissionRepository.ScoreSummaryProjection otherCourseScore = scoreSummary(studentId, "exam-other-course", 60);
        when(submissionRepository.findScoreSummariesByClientIdAndCourseId(clientId, courseId)).thenReturn(List.of(
                d1Score,
                d2Score,
                otherCourseScore
        ));
        when(projectRepository.findByClientIdAndCourseIdAndSectionId(clientId, courseId, sectionId)).thenReturn(List.of());
        when(projectRepository.findByClientIdAndCourseId(clientId, courseId)).thenReturn(List.of());
        when(projectRepository.findByClientIdAndCourseIdAndSectionId(clientId, emptyCourseId, sectionId)).thenReturn(List.of());
        when(projectRepository.findByClientIdAndCourseId(clientId, emptyCourseId)).thenReturn(List.of());

        byte[] workbookBytes = service.exportBySection(sectionId);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Row summaryHeader = workbook.getSheet("Summary").getRow(2);
            assertEquals("Course Alpha Score", summaryHeader.getCell(2).getStringCellValue());
            assertEquals("Course Alpha Max", summaryHeader.getCell(3).getStringCellValue());
            assertEquals("Course Alpha %", summaryHeader.getCell(4).getStringCellValue());

            Row summaryData = workbook.getSheet("Summary").getRow(3);
            assertEquals(80.0, summaryData.getCell(2).getNumericCellValue());
            assertEquals(100.0, summaryData.getCell(3).getNumericCellValue());

            Row courseHeader = workbook.getSheet("Course Alpha").getRow(0);
            assertEquals("D1 Exam Full Title", courseHeader.getCell(2).getStringCellValue());
            assertEquals("D1 Exam Full Title Max", courseHeader.getCell(3).getStringCellValue());
            assertNotNull(courseHeader.getCell(4));
            assertEquals("Course Total", courseHeader.getCell(4).getStringCellValue());
            assertNull(courseHeader.getCell(7));

            Row courseData = workbook.getSheet("Course Alpha").getRow(1);
            assertEquals(80.0, courseData.getCell(2).getNumericCellValue());
            assertEquals(100.0, courseData.getCell(3).getNumericCellValue());
            assertEquals(80.0, courseData.getCell(4).getNumericCellValue());
            assertEquals(100.0, courseData.getCell(5).getNumericCellValue());

            assertEquals(-1, workbook.getSheetIndex("Course Empty"));
        }
    }

    private JsonNode json(String json) throws Exception {
        return OBJECT_MAPPER.readTree(json);
    }

    private AssessmentSubmissionRepository.ScoreSummaryProjection scoreSummary(String studentId, String assessmentId, int score) {
        return scoreSummary(studentId, assessmentId, score, 100);
    }

    private AssessmentSubmissionRepository.ScoreSummaryProjection scoreSummary(String studentId, String assessmentId, int score, int maxScore) {
        AssessmentSubmissionRepository.ScoreSummaryProjection projection =
                mock(AssessmentSubmissionRepository.ScoreSummaryProjection.class);
        when(projection.getStudentId()).thenReturn(studentId);
        when(projection.getAssessmentId()).thenReturn(assessmentId);
        when(projection.getScore()).thenReturn(BigDecimal.valueOf(score));
        when(projection.getMaxScore()).thenReturn(BigDecimal.valueOf(maxScore));
        return projection;
    }
}
