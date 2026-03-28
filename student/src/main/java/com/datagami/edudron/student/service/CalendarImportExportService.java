package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.dto.CalendarEventImportResult;
import com.datagami.edudron.student.dto.CalendarEventImportResult.ImportError;
import com.datagami.edudron.student.repo.CalendarEventRepository;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class CalendarImportExportService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarImportExportService.class);

    private static final String[] IMPORT_HEADERS = {
            "title", "description", "eventType", "startDateTime", "endDateTime",
            "allDay", "audience", "classCode", "sectionName", "meetingLink", "location"
    };

    private static final String[] EXPORT_HEADERS = {
            "title", "description", "eventType", "startDateTime", "endDateTime",
            "allDay", "audience", "classCode", "sectionName", "meetingLink", "location"
    };

    private final CalendarEventRepository calendarEventRepository;
    private final ClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final StudentAuditService auditService;

    public CalendarImportExportService(CalendarEventRepository calendarEventRepository,
                                       ClassRepository classRepository,
                                       SectionRepository sectionRepository,
                                       StudentAuditService auditService) {
        this.calendarEventRepository = calendarEventRepository;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.auditService = auditService;
    }

    // ---- Import ----

    @Transactional
    public CalendarEventImportResult importEvents(MultipartFile file, String userId, String userEmail) {
        UUID clientId = resolveClientId();

        List<ImportError> errors = new ArrayList<>();
        int created = 0;
        int rowNumber = 1; // 1-based, header is row 0

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader(IMPORT_HEADERS)
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                rowNumber++;
                try {
                    CalendarEvent event = parseRow(record, clientId, userId, rowNumber, errors);
                    if (event != null) {
                        calendarEventRepository.save(event);
                        created++;
                    }
                } catch (Exception e) {
                    errors.add(new ImportError(rowNumber, "Unexpected error: " + e.getMessage()));
                    logger.warn("Import row {} failed: {}", rowNumber, e.getMessage());
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV file: " + e.getMessage(), e);
        }

        // Audit the import operation
        auditService.logCrud(clientId, "IMPORT", "CalendarEvent", null, userId, userEmail,
                Map.of("fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                        "totalRows", rowNumber - 1,
                        "created", created,
                        "errors", errors.size()));

        logger.info("Calendar import completed: {} created, {} errors out of {} rows",
                created, errors.size(), rowNumber - 1);

        return new CalendarEventImportResult(created, errors.size(), errors);
    }

    // ---- Export ----

    @Transactional(readOnly = true)
    public byte[] exportEvents(OffsetDateTime startDate, OffsetDateTime endDate,
                               String classId, String sectionId) {
        UUID clientId = resolveClientId();

        Specification<CalendarEvent> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("clientId"), clientId),
                        cb.isTrue(root.get("isActive"))
                );

        if (startDate != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("startDateTime"), startDate));
        }
        if (endDate != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("startDateTime"), endDate));
        }
        if (classId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("classId"), classId));
        }
        if (sectionId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("sectionId"), sectionId));
        }

        List<CalendarEvent> events = calendarEventRepository.findAll(spec);

        // Build lookup maps for class codes and section names
        Map<String, String> classCodeMap = new HashMap<>();
        Map<String, String> sectionNameMap = new HashMap<>();

        for (CalendarEvent event : events) {
            if (event.getClassId() != null && !classCodeMap.containsKey(event.getClassId())) {
                classRepository.findByIdAndClientId(event.getClassId(), clientId)
                        .ifPresent(cls -> classCodeMap.put(event.getClassId(), cls.getCode()));
            }
            if (event.getSectionId() != null && !sectionNameMap.containsKey(event.getSectionId())) {
                sectionRepository.findByIdAndClientId(event.getSectionId(), clientId)
                        .ifPresent(sec -> sectionNameMap.put(event.getSectionId(), sec.getName()));
            }
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             CSVPrinter printer = new CSVPrinter(
                     new OutputStreamWriter(out, StandardCharsets.UTF_8),
                     CSVFormat.DEFAULT.builder().setHeader(EXPORT_HEADERS).build())) {

            for (CalendarEvent event : events) {
                printer.printRecord(
                        event.getTitle(),
                        event.getDescription(),
                        event.getEventType().name(),
                        event.getStartDateTime() != null ? event.getStartDateTime().toString() : "",
                        event.getEndDateTime() != null ? event.getEndDateTime().toString() : "",
                        event.isAllDay(),
                        event.getAudience().name(),
                        classCodeMap.getOrDefault(event.getClassId(), ""),
                        sectionNameMap.getOrDefault(event.getSectionId(), ""),
                        event.getMeetingLink() != null ? event.getMeetingLink() : "",
                        event.getLocation() != null ? event.getLocation() : ""
                );
            }

            printer.flush();
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate CSV export: " + e.getMessage(), e);
        }
    }

    // ---- Template ----

    public byte[] getImportTemplate() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             CSVPrinter printer = new CSVPrinter(
                     new OutputStreamWriter(out, StandardCharsets.UTF_8),
                     CSVFormat.DEFAULT.builder().setHeader(IMPORT_HEADERS).build())) {

            // Sample row to illustrate format
            printer.printRecord(
                    "Mid-Term Exam",
                    "Mid-term examination for all sections",
                    "EXAM",
                    "2026-04-15T09:00:00+05:30",
                    "2026-04-15T12:00:00+05:30",
                    "false",
                    "CLASS",
                    "CS101",
                    "",
                    "",
                    "Main Auditorium"
            );

            printer.flush();
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate import template: " + e.getMessage(), e);
        }
    }

    // ---- Helpers ----

    private CalendarEvent parseRow(CSVRecord record, UUID clientId, String userId,
                                   int rowNumber, List<ImportError> errors) {
        // Validate required fields
        String title = getField(record, "title");
        if (title == null || title.isBlank()) {
            errors.add(new ImportError(rowNumber, "title is required"));
            return null;
        }

        String eventTypeStr = getField(record, "eventType");
        if (eventTypeStr == null || eventTypeStr.isBlank()) {
            errors.add(new ImportError(rowNumber, "eventType is required"));
            return null;
        }

        EventType eventType;
        try {
            eventType = EventType.valueOf(eventTypeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            errors.add(new ImportError(rowNumber, "Invalid eventType: " + eventTypeStr
                    + ". Valid values: " + Arrays.toString(EventType.values())));
            return null;
        }

        String startDateTimeStr = getField(record, "startDateTime");
        if (startDateTimeStr == null || startDateTimeStr.isBlank()) {
            errors.add(new ImportError(rowNumber, "startDateTime is required"));
            return null;
        }

        OffsetDateTime startDateTime;
        try {
            startDateTime = OffsetDateTime.parse(startDateTimeStr.trim());
        } catch (DateTimeParseException e) {
            errors.add(new ImportError(rowNumber, "Invalid startDateTime format (expected ISO 8601): " + startDateTimeStr));
            return null;
        }

        String audienceStr = getField(record, "audience");
        if (audienceStr == null || audienceStr.isBlank()) {
            errors.add(new ImportError(rowNumber, "audience is required"));
            return null;
        }

        EventAudience audience;
        try {
            audience = EventAudience.valueOf(audienceStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            errors.add(new ImportError(rowNumber, "Invalid audience: " + audienceStr
                    + ". Valid values: " + Arrays.toString(EventAudience.values())));
            return null;
        }

        // Parse optional endDateTime
        OffsetDateTime endDateTime = null;
        String endDateTimeStr = getField(record, "endDateTime");
        if (endDateTimeStr != null && !endDateTimeStr.isBlank()) {
            try {
                endDateTime = OffsetDateTime.parse(endDateTimeStr.trim());
            } catch (DateTimeParseException e) {
                errors.add(new ImportError(rowNumber, "Invalid endDateTime format (expected ISO 8601): " + endDateTimeStr));
                return null;
            }
        }

        // Parse allDay
        String allDayStr = getField(record, "allDay");
        boolean allDay = "true".equalsIgnoreCase(allDayStr != null ? allDayStr.trim() : "false");

        // Resolve classCode -> classId
        String classId = null;
        String classCode = getField(record, "classCode");
        if (classCode != null && !classCode.isBlank()) {
            Optional<com.datagami.edudron.student.domain.Class> cls =
                    classRepository.findByCodeAndClientId(classCode.trim(), clientId);
            if (cls.isPresent()) {
                classId = cls.get().getId();
            } else {
                errors.add(new ImportError(rowNumber, "Class not found with code: " + classCode));
                return null;
            }
        }

        // Resolve sectionName -> sectionId
        String sectionId = null;
        String sectionName = getField(record, "sectionName");
        if (sectionName != null && !sectionName.isBlank()) {
            Optional<Section> section;
            if (classId != null) {
                section = sectionRepository.findByNameAndClientIdAndClassId(sectionName.trim(), clientId, classId);
            } else {
                section = sectionRepository.findByNameAndClientId(sectionName.trim(), clientId);
            }
            if (section.isPresent()) {
                sectionId = section.get().getId();
            } else {
                errors.add(new ImportError(rowNumber, "Section not found with name: " + sectionName
                        + (classId != null ? " in class " + classCode : "")));
                return null;
            }
        }

        // Validate audience scoping
        if (audience == EventAudience.CLASS && classId == null) {
            errors.add(new ImportError(rowNumber, "classCode is required for CLASS audience events"));
            return null;
        }
        if (audience == EventAudience.SECTION && sectionId == null) {
            errors.add(new ImportError(rowNumber, "sectionName is required for SECTION audience events"));
            return null;
        }

        // Build entity
        CalendarEvent event = new CalendarEvent();
        event.setClientId(clientId);
        event.setTitle(title.trim());
        event.setDescription(getFieldOrNull(record, "description"));
        event.setEventType(eventType);
        event.setStartDateTime(startDateTime);
        event.setEndDateTime(endDateTime);
        event.setAllDay(allDay);
        event.setAudience(audience);
        event.setClassId(classId);
        event.setSectionId(sectionId);
        event.setCreatedByUserId(userId);
        event.setMeetingLink(getFieldOrNull(record, "meetingLink"));
        event.setLocation(getFieldOrNull(record, "location"));

        return event;
    }

    private String getField(CSVRecord record, String name) {
        try {
            return record.get(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getFieldOrNull(CSVRecord record, String name) {
        String value = getField(record, name);
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private UUID resolveClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }
}
