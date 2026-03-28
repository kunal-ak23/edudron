package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.client.ContentAssessmentClient;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.repo.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for exporting student results as Excel workbooks.
 * Supports export by section, class, or course scope.
 *
 * Generates a workbook with:
 * - Summary sheet: student name, email, per-course total/%, grand total/overall %
 * - Per-course detail sheets: individual assessment scores, project event grades, totals
 */
@Service
@Transactional(readOnly = true)
public class ResultsExportService {

    private static final Logger log = LoggerFactory.getLogger(ResultsExportService.class);

    private final ContentAssessmentClient contentAssessmentClient;
    private final EnrollmentRepository enrollmentRepository;
    private final AssessmentSubmissionRepository submissionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectEventRepository projectEventRepository;
    private final ProjectEventGradeRepository projectEventGradeRepository;
    private final SectionRepository sectionRepository;
    private final ClassRepository classRepository;
    private final StudentAuditService auditService;

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();

    public ResultsExportService(ContentAssessmentClient contentAssessmentClient,
                                EnrollmentRepository enrollmentRepository,
                                AssessmentSubmissionRepository submissionRepository,
                                ProjectRepository projectRepository,
                                ProjectEventRepository projectEventRepository,
                                ProjectEventGradeRepository projectEventGradeRepository,
                                SectionRepository sectionRepository,
                                ClassRepository classRepository,
                                StudentAuditService auditService) {
        this.contentAssessmentClient = contentAssessmentClient;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.projectRepository = projectRepository;
        this.projectEventRepository = projectEventRepository;
        this.projectEventGradeRepository = projectEventGradeRepository;
        this.sectionRepository = sectionRepository;
        this.classRepository = classRepository;
        this.auditService = auditService;
    }

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate newTemplate = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new com.datagami.edudron.common.TenantContextRestTemplateInterceptor());
                    interceptors.add((request, body, execution) -> {
                        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest currentRequest = attributes.getRequest();
                            String authHeader = currentRequest.getHeader("Authorization");
                            if (authHeader != null && !authHeader.isBlank()) {
                                if (!request.getHeaders().containsKey("Authorization")) {
                                    request.getHeaders().add("Authorization", authHeader);
                                }
                            }
                        }
                        return execution.execute(request, body);
                    });
                    newTemplate.setInterceptors(interceptors);
                    restTemplate = newTemplate;
                }
            }
        }
        return restTemplate;
    }

    /**
     * Export results for all students in a section.
     */
    public byte[] exportBySection(String sectionId) {
        UUID clientId = getClientId();

        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));

        // batchId in enrollment maps to sectionId
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("No enrollments found for section: " + section.getName());
        }

        Set<String> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId)
                .filter(c -> !"__PLACEHOLDER_ASSOCIATION__".equals(c))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> studentIds = enrollments.stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String title = "Section: " + section.getName();
        byte[] workbook = generateWorkbook(clientId, studentIds, courseIds, sectionId, title);
        auditService.logCrud(clientId, "EXPORT", "ResultsExport", sectionId,
                null, null, Map.of("scope", "section", "studentCount", studentIds.size(),
                        "courseCount", courseIds.size()));
        return workbook;
    }

    /**
     * Export results for all students in a class (across all sections).
     */
    public byte[] exportByClass(String classId) {
        UUID clientId = getClientId();

        com.datagami.edudron.student.domain.Class clazz = classRepository.findByIdAndClientId(classId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));

        List<Section> sections = sectionRepository.findByClientIdAndClassId(clientId, classId);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("No sections found for class: " + clazz.getName());
        }

        List<Enrollment> allEnrollments = enrollmentRepository.findByClientIdAndClassId(clientId, classId);
        if (allEnrollments.isEmpty()) {
            throw new IllegalArgumentException("No enrollments found for class: " + clazz.getName());
        }

        Set<String> courseIds = allEnrollments.stream()
                .map(Enrollment::getCourseId)
                .filter(c -> !"__PLACEHOLDER_ASSOCIATION__".equals(c))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> studentIds = allEnrollments.stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String title = "Class: " + clazz.getName();
        byte[] workbook = generateWorkbook(clientId, studentIds, courseIds, null, title);
        auditService.logCrud(clientId, "EXPORT", "ResultsExport", classId,
                null, null, Map.of("scope", "class", "studentCount", studentIds.size(),
                        "courseCount", courseIds.size()));
        return workbook;
    }

    /**
     * Export results for all students enrolled in a course.
     */
    public byte[] exportByCourse(String courseId) {
        UUID clientId = getClientId();

        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndCourseId(clientId, courseId);
        if (enrollments.isEmpty()) {
            throw new IllegalArgumentException("No enrollments found for course: " + courseId);
        }

        Set<String> studentIds = enrollments.stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> courseIds = new LinkedHashSet<>();
        courseIds.add(courseId);

        JsonNode courseNode = contentAssessmentClient.getCourse(courseId);
        String courseTitle = courseNode != null && courseNode.has("title")
                ? courseNode.get("title").asText()
                : courseId;

        String title = "Course: " + courseTitle;
        byte[] workbook = generateWorkbook(clientId, studentIds, courseIds, null, title);
        auditService.logCrud(clientId, "EXPORT", "ResultsExport", courseId,
                null, null, Map.of("scope", "course", "studentCount", studentIds.size(),
                        "courseCount", 1));
        return workbook;
    }

    // -------------------------------------------------------------------------
    // Internal workbook generation
    // -------------------------------------------------------------------------

    private byte[] generateWorkbook(UUID clientId, Set<String> studentIds,
                                    Set<String> courseIds, String sectionId, String title) {
        // 1. Fetch student details (name, email) from identity service
        Map<String, StudentInfo> studentInfoMap = fetchStudentInfo(studentIds);

        // Sort students by name for consistent output
        List<String> sortedStudentIds = new ArrayList<>(studentIds);
        sortedStudentIds.sort((a, b) -> {
            String nameA = studentInfoMap.containsKey(a) ? studentInfoMap.get(a).name : a;
            String nameB = studentInfoMap.containsKey(b) ? studentInfoMap.get(b).name : b;
            return nameA.compareToIgnoreCase(nameB);
        });

        // 2. Per-course data structures
        List<CourseData> courseDataList = new ArrayList<>();
        for (String courseId : courseIds) {
            CourseData cd = buildCourseData(clientId, courseId, sectionId, sortedStudentIds);
            courseDataList.add(cd);
        }

        // 3. Generate Excel workbook
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Create cell styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

            // --- Summary sheet ---
            buildSummarySheet(workbook, headerStyle, percentStyle, sortedStudentIds,
                    studentInfoMap, courseDataList, title);

            // --- Per-course detail sheets ---
            for (CourseData cd : courseDataList) {
                buildCourseDetailSheet(workbook, headerStyle, percentStyle, sortedStudentIds,
                        studentInfoMap, cd);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel workbook", e);
        }
    }

    private CourseData buildCourseData(UUID clientId, String courseId, String sectionId,
                                       List<String> studentIds) {
        CourseData cd = new CourseData();
        cd.courseId = courseId;

        // Fetch course title from content service
        JsonNode courseNode = contentAssessmentClient.getCourse(courseId);
        cd.courseTitle = courseNode != null && courseNode.has("title")
                ? courseNode.get("title").asText()
                : courseId;

        // Fetch assessments for this course
        List<JsonNode> assessments = contentAssessmentClient.getAssessmentsForCourse(courseId);
        cd.assessments = assessments;

        // Build assessment ID -> title/maxScore maps
        cd.assessmentIds = new ArrayList<>();
        cd.assessmentTitles = new LinkedHashMap<>();
        cd.assessmentMaxScores = new LinkedHashMap<>();
        for (JsonNode a : assessments) {
            String aId = a.has("id") ? a.get("id").asText() : null;
            if (aId == null) continue;
            cd.assessmentIds.add(aId);
            cd.assessmentTitles.put(aId, a.has("title") ? a.get("title").asText() : aId);

            BigDecimal maxScore = BigDecimal.ZERO;
            if (a.has("maxScore") && !a.get("maxScore").isNull()) {
                maxScore = new BigDecimal(a.get("maxScore").asText());
            } else if (a.has("questions") && a.get("questions").isArray()) {
                // Calculate maxScore from questions
                for (JsonNode q : a.get("questions")) {
                    if (q.has("marks") && !q.get("marks").isNull()) {
                        maxScore = maxScore.add(new BigDecimal(q.get("marks").asText()));
                    }
                }
            }
            cd.assessmentMaxScores.put(aId, maxScore);
        }

        // Fetch all submissions for this course at once
        List<AssessmentSubmission> allSubmissions = submissionRepository.findByClientIdAndCourseId(clientId, courseId);

        // Build student -> assessment -> best score map
        cd.studentAssessmentScores = new HashMap<>();
        for (AssessmentSubmission sub : allSubmissions) {
            if (!studentIds.contains(sub.getStudentId())) continue;
            String key = sub.getStudentId() + "::" + sub.getAssessmentId();
            BigDecimal existing = cd.studentAssessmentScores.get(key);
            BigDecimal current = sub.getScore() != null ? sub.getScore() : BigDecimal.ZERO;
            if (existing == null || current.compareTo(existing) > 0) {
                cd.studentAssessmentScores.put(key, current);
            }
        }

        // Fetch project events for this course
        List<Project> projects;
        if (sectionId != null) {
            projects = projectRepository.findByClientIdAndCourseIdAndSectionId(clientId, courseId, sectionId);
            // Also get projects without section (course-level projects)
            List<Project> courseProjects = projectRepository.findByClientIdAndCourseId(clientId, courseId);
            for (Project p : courseProjects) {
                if (p.getSectionId() == null && !projects.contains(p)) {
                    projects.add(p);
                }
            }
        } else {
            projects = projectRepository.findByClientIdAndCourseId(clientId, courseId);
        }

        // Collect all project events that have marks
        cd.projectEvents = new ArrayList<>();
        cd.projectEventIds = new ArrayList<>();
        for (Project project : projects) {
            List<ProjectEvent> events;
            if (sectionId != null && project.getSectionId() != null) {
                events = projectEventRepository.findByProjectIdAndSectionIdAndClientIdOrderBySequenceAsc(
                        project.getId(), sectionId, clientId);
            } else {
                events = projectEventRepository.findByProjectIdAndClientIdOrderBySequenceAsc(
                        project.getId(), clientId);
            }
            for (ProjectEvent event : events) {
                if (Boolean.TRUE.equals(event.getHasMarks())) {
                    cd.projectEvents.add(event);
                    cd.projectEventIds.add(event.getId());
                }
            }
        }

        // Fetch all grades for these events
        cd.studentEventGrades = new HashMap<>();
        if (!cd.projectEventIds.isEmpty()) {
            List<ProjectEventGrade> allGrades = projectEventGradeRepository.findByClientIdAndEventIdIn(
                    clientId, cd.projectEventIds);
            for (ProjectEventGrade grade : allGrades) {
                if (!studentIds.contains(grade.getStudentId())) continue;
                String key = grade.getStudentId() + "::" + grade.getEventId();
                cd.studentEventGrades.put(key, grade.getMarks());
            }
        }

        // Calculate total max score for this course
        BigDecimal assessmentMaxTotal = BigDecimal.ZERO;
        for (BigDecimal ms : cd.assessmentMaxScores.values()) {
            assessmentMaxTotal = assessmentMaxTotal.add(ms);
        }
        int projectMaxTotal = 0;
        for (ProjectEvent pe : cd.projectEvents) {
            if (pe.getMaxMarks() != null) {
                projectMaxTotal += pe.getMaxMarks();
            }
        }
        cd.courseMaxScore = assessmentMaxTotal.add(BigDecimal.valueOf(projectMaxTotal));

        return cd;
    }

    private void buildSummarySheet(XSSFWorkbook workbook, CellStyle headerStyle, CellStyle percentStyle,
                                   List<String> studentIds, Map<String, StudentInfo> studentInfoMap,
                                   List<CourseData> courseDataList, String title) {
        Sheet sheet = workbook.createSheet("Summary");
        int rowIdx = 0;

        // Title row
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(headerStyle);

        rowIdx++; // blank row

        // Header row
        Row headerRow = sheet.createRow(rowIdx++);
        int col = 0;
        createHeaderCell(headerRow, col++, "Student Name", headerStyle);
        createHeaderCell(headerRow, col++, "Email", headerStyle);

        for (CourseData cd : courseDataList) {
            String shortTitle = truncate(cd.courseTitle, 20);
            createHeaderCell(headerRow, col++, shortTitle + " Score", headerStyle);
            createHeaderCell(headerRow, col++, shortTitle + " Max", headerStyle);
            createHeaderCell(headerRow, col++, shortTitle + " %", headerStyle);
        }
        if (courseDataList.size() > 1) {
            createHeaderCell(headerRow, col++, "Grand Total", headerStyle);
            createHeaderCell(headerRow, col++, "Grand Max", headerStyle);
            createHeaderCell(headerRow, col++, "Overall %", headerStyle);
        }

        // Data rows
        for (String studentId : studentIds) {
            Row row = sheet.createRow(rowIdx++);
            col = 0;
            StudentInfo info = studentInfoMap.get(studentId);
            row.createCell(col++).setCellValue(info != null ? info.name : studentId);
            row.createCell(col++).setCellValue(info != null ? info.email : "");

            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal grandMax = BigDecimal.ZERO;

            for (CourseData cd : courseDataList) {
                BigDecimal courseScore = getStudentCourseScore(studentId, cd);
                BigDecimal courseMax = cd.courseMaxScore;

                row.createCell(col++).setCellValue(courseScore.doubleValue());
                row.createCell(col++).setCellValue(courseMax.doubleValue());

                Cell pctCell = row.createCell(col++);
                if (courseMax.compareTo(BigDecimal.ZERO) > 0) {
                    pctCell.setCellValue(courseScore.multiply(BigDecimal.valueOf(100))
                            .divide(courseMax, 2, RoundingMode.HALF_UP).doubleValue());
                } else {
                    pctCell.setCellValue(0.0);
                }
                pctCell.setCellStyle(percentStyle);

                grandTotal = grandTotal.add(courseScore);
                grandMax = grandMax.add(courseMax);
            }

            if (courseDataList.size() > 1) {
                row.createCell(col++).setCellValue(grandTotal.doubleValue());
                row.createCell(col++).setCellValue(grandMax.doubleValue());

                Cell overallPctCell = row.createCell(col++);
                if (grandMax.compareTo(BigDecimal.ZERO) > 0) {
                    overallPctCell.setCellValue(grandTotal.multiply(BigDecimal.valueOf(100))
                            .divide(grandMax, 2, RoundingMode.HALF_UP).doubleValue());
                } else {
                    overallPctCell.setCellValue(0.0);
                }
                overallPctCell.setCellStyle(percentStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < col; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildCourseDetailSheet(XSSFWorkbook workbook, CellStyle headerStyle, CellStyle percentStyle,
                                        List<String> studentIds, Map<String, StudentInfo> studentInfoMap,
                                        CourseData cd) {
        String sheetName = sanitizeSheetName(cd.courseTitle);
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIdx = 0;

        // Header row
        Row headerRow = sheet.createRow(rowIdx++);
        int col = 0;
        createHeaderCell(headerRow, col++, "Student Name", headerStyle);
        createHeaderCell(headerRow, col++, "Email", headerStyle);

        // Assessment columns (score/max)
        for (String aId : cd.assessmentIds) {
            String title = truncate(cd.assessmentTitles.get(aId), 25);
            createHeaderCell(headerRow, col++, title, headerStyle);
            createHeaderCell(headerRow, col++, title + " Max", headerStyle);
        }

        // Project event columns
        for (ProjectEvent pe : cd.projectEvents) {
            String evName = truncate(pe.getName(), 25);
            createHeaderCell(headerRow, col++, evName, headerStyle);
            createHeaderCell(headerRow, col++, evName + " Max", headerStyle);
        }

        createHeaderCell(headerRow, col++, "Course Total", headerStyle);
        createHeaderCell(headerRow, col++, "Course Max", headerStyle);
        createHeaderCell(headerRow, col++, "Course %", headerStyle);

        // Data rows
        for (String studentId : studentIds) {
            Row row = sheet.createRow(rowIdx++);
            col = 0;
            StudentInfo info = studentInfoMap.get(studentId);
            row.createCell(col++).setCellValue(info != null ? info.name : studentId);
            row.createCell(col++).setCellValue(info != null ? info.email : "");

            BigDecimal totalScore = BigDecimal.ZERO;

            // Assessment scores
            for (String aId : cd.assessmentIds) {
                String key = studentId + "::" + aId;
                BigDecimal score = cd.studentAssessmentScores.getOrDefault(key, BigDecimal.ZERO);
                BigDecimal maxScore = cd.assessmentMaxScores.getOrDefault(aId, BigDecimal.ZERO);
                row.createCell(col++).setCellValue(score.doubleValue());
                row.createCell(col++).setCellValue(maxScore.doubleValue());
                totalScore = totalScore.add(score);
            }

            // Project event grades
            for (ProjectEvent pe : cd.projectEvents) {
                String key = studentId + "::" + pe.getId();
                Integer marks = cd.studentEventGrades.get(key);
                int gradeValue = marks != null ? marks : 0;
                int maxMarks = pe.getMaxMarks() != null ? pe.getMaxMarks() : 0;
                row.createCell(col++).setCellValue(gradeValue);
                row.createCell(col++).setCellValue(maxMarks);
                totalScore = totalScore.add(BigDecimal.valueOf(gradeValue));
            }

            row.createCell(col++).setCellValue(totalScore.doubleValue());
            row.createCell(col++).setCellValue(cd.courseMaxScore.doubleValue());

            Cell pctCell = row.createCell(col++);
            if (cd.courseMaxScore.compareTo(BigDecimal.ZERO) > 0) {
                pctCell.setCellValue(totalScore.multiply(BigDecimal.valueOf(100))
                        .divide(cd.courseMaxScore, 2, RoundingMode.HALF_UP).doubleValue());
            } else {
                pctCell.setCellValue(0.0);
            }
            pctCell.setCellStyle(percentStyle);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }

    private BigDecimal getStudentCourseScore(String studentId, CourseData cd) {
        BigDecimal total = BigDecimal.ZERO;
        for (String aId : cd.assessmentIds) {
            String key = studentId + "::" + aId;
            total = total.add(cd.studentAssessmentScores.getOrDefault(key, BigDecimal.ZERO));
        }
        for (ProjectEvent pe : cd.projectEvents) {
            String key = studentId + "::" + pe.getId();
            Integer marks = cd.studentEventGrades.get(key);
            if (marks != null) {
                total = total.add(BigDecimal.valueOf(marks));
            }
        }
        return total;
    }

    /**
     * Fetch student name/email from identity service for a set of student IDs.
     */
    private Map<String, StudentInfo> fetchStudentInfo(Set<String> studentIds) {
        Map<String, StudentInfo> map = new HashMap<>();
        for (String studentId : studentIds) {
            try {
                String url = gatewayUrl + "/idp/users/" + studentId;
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<?> entity = new HttpEntity<>(headers);

                ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                        url, HttpMethod.GET, entity, JsonNode.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode body = response.getBody();
                    StudentInfo info = new StudentInfo();
                    info.name = body.has("name") && !body.get("name").isNull()
                            ? body.get("name").asText() : studentId;
                    info.email = body.has("email") && !body.get("email").isNull()
                            ? body.get("email").asText() : "";
                    map.put(studentId, info);
                }
            } catch (Exception e) {
                log.debug("Could not fetch user details for student {}: {}", studentId, e.getMessage());
            }
        }
        log.debug("Fetched info for {}/{} students", map.size(), studentIds.size());
        return map;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createHeaderCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String sanitizeSheetName(String name) {
        if (name == null) return "Sheet";
        // Remove invalid characters for Excel sheet names
        String sanitized = name.replaceAll("[\\\\/:*?\\[\\]]", "");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        if (sanitized.isBlank()) {
            sanitized = "Sheet";
        }
        return sanitized;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    // -------------------------------------------------------------------------
    // Inner data holders
    // -------------------------------------------------------------------------

    private static class StudentInfo {
        String name;
        String email;
    }

    private static class CourseData {
        String courseId;
        String courseTitle;
        List<JsonNode> assessments;
        List<String> assessmentIds;
        Map<String, String> assessmentTitles;
        Map<String, BigDecimal> assessmentMaxScores;
        // key: "studentId::assessmentId" -> best score
        Map<String, BigDecimal> studentAssessmentScores;
        List<ProjectEvent> projectEvents;
        List<String> projectEventIds;
        // key: "studentId::eventId" -> marks
        Map<String, Integer> studentEventGrades;
        BigDecimal courseMaxScore;
    }
}
