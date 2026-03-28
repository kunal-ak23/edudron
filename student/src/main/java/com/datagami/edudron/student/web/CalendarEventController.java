package com.datagami.edudron.student.web;

import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.service.CalendarEventService;
import com.datagami.edudron.student.service.CalendarImportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Calendar Events", description = "Calendar event management endpoints")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;
    private final CalendarImportExportService importExportService;

    public CalendarEventController(CalendarEventService calendarEventService,
                                   CalendarImportExportService importExportService) {
        this.calendarEventService = calendarEventService;
        this.importExportService = importExportService;
    }

    @PostMapping("/events")
    @Operation(summary = "Create event", description = "Create a new calendar event")
    public ResponseEntity<CalendarEventResponse> createEvent(
            @Valid @RequestBody CreateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        String actor = userEmail != null ? userEmail : userId;
        CalendarEventResponse response = calendarEventService.createEvent(request, userId, actor, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/events")
    @Operation(summary = "List events", description = "List calendar events with date range and optional filters")
    public ResponseEntity<List<CalendarEventResponse>> listEvents(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) EventAudience audience,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        OffsetDateTime start = parseDateTime(startDate);
        OffsetDateTime end = parseDateTime(endDate);
        List<CalendarEventResponse> events = calendarEventService.getEvents(
                start, end, classId, sectionId, eventType, audience, userId, userRole);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Get event", description = "Get a single calendar event by ID")
    public ResponseEntity<CalendarEventResponse> getEvent(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        CalendarEventResponse response = calendarEventService.getEventById(id, userId, userRole);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/events/{id}")
    @Operation(summary = "Update event", description = "Update a single calendar event")
    public ResponseEntity<CalendarEventResponse> updateEvent(
            @PathVariable String id,
            @Valid @RequestBody UpdateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        String actor = userEmail != null ? userEmail : userId;
        CalendarEventResponse response = calendarEventService.updateEvent(id, request, userId, actor);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/events/{id}")
    @Operation(summary = "Delete event", description = "Soft-delete a single calendar event")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        String actor = userEmail != null ? userEmail : userId;
        calendarEventService.deleteEvent(id, userId, actor);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/events/{id}/series")
    @Operation(summary = "Update series", description = "Update all events in a recurrence series")
    public ResponseEntity<Void> updateSeries(
            @PathVariable String id,
            @Valid @RequestBody UpdateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        String actor = userEmail != null ? userEmail : userId;
        calendarEventService.updateSeries(id, request, userId, actor);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/events/{id}/series")
    @Operation(summary = "Delete series", description = "Soft-delete all events in a recurrence series")
    public ResponseEntity<Void> deleteSeries(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        String actor = userEmail != null ? userEmail : userId;
        calendarEventService.deleteSeries(id, userId, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events/personal")
    @Operation(summary = "Create personal event", description = "Create a personal calendar event for the current user")
    public ResponseEntity<CalendarEventResponse> createPersonalEvent(
            @Valid @RequestBody CreateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        String actor = userEmail != null ? userEmail : userId;
        CalendarEventResponse response = calendarEventService.createPersonalEvent(request, userId, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/events/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import events", description = "Bulk import calendar events from a CSV file")
    public ResponseEntity<CalendarEventImportResult> importEvents(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        String actor = userEmail != null ? userEmail : userId;
        CalendarEventImportResult result = importExportService.importEvents(file, userId, actor);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/events/export")
    @Operation(summary = "Export events", description = "Export calendar events to a CSV file")
    public ResponseEntity<byte[]> exportEvents(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String sectionId) {
        OffsetDateTime start = parseDateTime(startDate);
        OffsetDateTime end = parseDateTime(endDate);
        byte[] csvBytes = importExportService.exportEvents(start, end, classId, sectionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "calendar-events.csv");

        return ResponseEntity.ok().headers(headers).body(csvBytes);
    }

    @GetMapping("/events/import/template")
    @Operation(summary = "Download import template", description = "Download a CSV template for bulk event import")
    public ResponseEntity<byte[]> getImportTemplate() {
        byte[] templateBytes = importExportService.getImportTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "calendar-import-template.csv");

        return ResponseEntity.ok().headers(headers).body(templateBytes);
    }

    // ---- Helpers ----

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date/time format (expected ISO 8601): " + value);
        }
    }
}
