package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.client.IdentityUserClient;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.*;
import com.datagami.edudron.student.util.RecurrenceGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalendarEventService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarEventService.class);

    private final CalendarEventRepository calendarEventRepository;
    private final ClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final InstructorAssignmentRepository instructorAssignmentRepository;
    private final IdentityUserClient identityUserClient;
    private final StudentAuditService auditService;

    public CalendarEventService(CalendarEventRepository calendarEventRepository,
                                ClassRepository classRepository,
                                SectionRepository sectionRepository,
                                EnrollmentRepository enrollmentRepository,
                                InstructorAssignmentRepository instructorAssignmentRepository,
                                IdentityUserClient identityUserClient,
                                StudentAuditService auditService) {
        this.calendarEventRepository = calendarEventRepository;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.instructorAssignmentRepository = instructorAssignmentRepository;
        this.identityUserClient = identityUserClient;
        this.auditService = auditService;
    }

    // ---- Public API ----

    @Transactional
    public CalendarEventResponse createEvent(CreateCalendarEventRequest request,
                                             String userId, String userEmail, String userRole) {
        UUID clientId = resolveClientId();

        validateAudienceScoping(request.audience(), request.classIds(), request.sectionIds());
        validateCreatePermission(userId, userRole, clientId, request.audience(), request.classIds(), request.sectionIds());

        CalendarEvent event = mapRequestToEntity(request, userId, clientId);
        calendarEventRepository.save(event);

        // Generate recurrence occurrences if recurring
        if (event.isRecurring() && event.getRecurrenceRule() != null) {
            List<CalendarEvent> occurrences = RecurrenceGenerator.generateOccurrences(event);
            if (!occurrences.isEmpty()) {
                calendarEventRepository.saveAll(occurrences);
                logger.info("Created {} recurrence occurrences for event {}", occurrences.size(), event.getId());
            }
        }

        auditService.logCrud(clientId, "CREATE", "CalendarEvent", event.getId(), userId, userEmail,
                Map.of("title", event.getTitle(), "audience", event.getAudience().name(),
                        "eventType", event.getEventType().name()));

        return toResponse(event);
    }

    @Transactional
    public CalendarEventResponse createPersonalEvent(CreateCalendarEventRequest request,
                                                     String userId, String userEmail) {
        UUID clientId = resolveClientId();

        // Build a personal-only version of the request
        CalendarEvent event = new CalendarEvent();
        event.setClientId(clientId);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setEventType(EventType.PERSONAL);
        event.setCustomTypeLabel(request.customTypeLabel());
        event.setStartDateTime(request.startDateTime());
        event.setEndDateTime(request.endDateTime());
        event.setAllDay(request.allDay());
        event.setAudience(EventAudience.PERSONAL);
        event.setCreatedByUserId(userId);
        event.setRecurring(request.isRecurring());
        event.setRecurrenceRule(request.recurrenceRule());
        event.setMeetingLink(request.meetingLink());
        event.setLocation(request.location());
        event.setColor(request.color());
        event.setMetadata(request.metadata());

        calendarEventRepository.save(event);

        if (event.isRecurring() && event.getRecurrenceRule() != null) {
            List<CalendarEvent> occurrences = RecurrenceGenerator.generateOccurrences(event);
            if (!occurrences.isEmpty()) {
                calendarEventRepository.saveAll(occurrences);
            }
        }

        auditService.logCrud(clientId, "CREATE", "CalendarEvent", event.getId(), userId, userEmail,
                Map.of("title", event.getTitle(), "audience", "PERSONAL", "eventType", "PERSONAL"));

        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getEvents(OffsetDateTime startDate, OffsetDateTime endDate,
                                                  String filterClassId, String filterSectionId,
                                                  EventType eventType, EventAudience audience,
                                                  String userId, String userRole) {
        UUID clientId = resolveClientId();
        String clientIdStr = clientId.toString();

        // Default date range if not provided
        if (startDate == null) startDate = OffsetDateTime.now().minusYears(1);
        if (endDate == null) endDate = OffsetDateTime.now().plusYears(1);

        List<CalendarEvent> events = fetchRoleBasedEvents(clientIdStr, userId, userRole, startDate, endDate, clientId);

        // Apply optional in-memory filters (classId, sectionId, eventType, audience)
        return events.stream()
                .filter(e -> filterClassId == null || (e.getClassIds() != null && e.getClassIds().contains(filterClassId)))
                .filter(e -> filterSectionId == null || (e.getSectionIds() != null && e.getSectionIds().contains(filterSectionId)))
                .filter(e -> eventType == null || e.getEventType() == eventType)
                .filter(e -> audience == null || e.getAudience() == audience)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CalendarEventResponse getEventById(String id, String userId, String userRole) {
        UUID clientId = resolveClientId();

        CalendarEvent event = calendarEventRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));

        validateReadPermission(event, userId, userRole, clientId);

        return toResponse(event);
    }

    @Transactional
    public CalendarEventResponse updateEvent(String id, UpdateCalendarEventRequest request,
                                             String userId, String userEmail) {
        UUID clientId = resolveClientId();

        CalendarEvent event = calendarEventRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));

        validateOwnership(event, userId);

        Map<String, Object> changes = applyUpdates(event, request);
        calendarEventRepository.save(event);

        auditService.logCrud(clientId, "UPDATE", "CalendarEvent", event.getId(), userId, userEmail, changes);

        return toResponse(event);
    }

    @Transactional
    public void updateSeries(String id, UpdateCalendarEventRequest request,
                             String userId, String userEmail) {
        UUID clientId = resolveClientId();

        CalendarEvent event = calendarEventRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));

        // Find parent: either this event is the parent, or we follow recurrenceParentId
        String parentId = event.getRecurrenceParentId() != null ? event.getRecurrenceParentId() : event.getId();

        CalendarEvent parent = calendarEventRepository.findByIdAndClientId(parentId, clientId)
                .orElseThrow(() -> new IllegalStateException("Recurrence parent not found: " + parentId));

        validateOwnership(parent, userId);

        // Update parent
        applyUpdates(parent, request);
        calendarEventRepository.save(parent);

        // Update all occurrences
        List<CalendarEvent> occurrences = calendarEventRepository.findByRecurrenceParentIdAndIsActiveTrue(parentId);
        for (CalendarEvent occurrence : occurrences) {
            applyUpdates(occurrence, request);
        }
        calendarEventRepository.saveAll(occurrences);

        auditService.logCrud(clientId, "UPDATE", "CalendarEvent", parentId, userId, userEmail,
                Map.of("seriesUpdate", true, "occurrencesUpdated", occurrences.size()));
    }

    @Transactional
    public void deleteEvent(String id, String userId, String userEmail) {
        UUID clientId = resolveClientId();

        CalendarEvent event = calendarEventRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));

        validateOwnership(event, userId);

        event.setActive(false);
        calendarEventRepository.save(event);

        auditService.logCrud(clientId, "DELETE", "CalendarEvent", event.getId(), userId, userEmail,
                Map.of("title", event.getTitle()));
    }

    @Transactional
    public void deleteSeries(String id, String userId, String userEmail) {
        UUID clientId = resolveClientId();

        CalendarEvent event = calendarEventRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));

        String parentId = event.getRecurrenceParentId() != null ? event.getRecurrenceParentId() : event.getId();

        CalendarEvent parent = calendarEventRepository.findByIdAndClientId(parentId, clientId)
                .orElseThrow(() -> new IllegalStateException("Recurrence parent not found: " + parentId));

        validateOwnership(parent, userId);

        int deleted = calendarEventRepository.softDeleteSeries(parentId);

        auditService.logCrud(clientId, "DELETE", "CalendarEvent", parentId, userId, userEmail,
                Map.of("seriesDelete", true, "eventsDeactivated", deleted));
    }

    // ---- Helpers ----

    private CalendarEventResponse toResponse(CalendarEvent event) {
        List<String> classNames = null;
        List<String> sectionNames = null;
        List<String> targetUserNames = null;
        String createdByName = null;

        if (event.getClassIds() != null && !event.getClassIds().isEmpty()) {
            classNames = event.getClassIds().stream()
                    .map(cid -> classRepository.findById(cid)
                            .map(com.datagami.edudron.student.domain.Class::getName)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (event.getSectionIds() != null && !event.getSectionIds().isEmpty()) {
            sectionNames = event.getSectionIds().stream()
                    .map(sid -> sectionRepository.findById(sid)
                            .map(Section::getName)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (event.getTargetUserIds() != null && !event.getTargetUserIds().isEmpty()) {
            targetUserNames = event.getTargetUserIds().stream()
                    .map(uid -> {
                        try {
                            JsonNode user = identityUserClient.getUser(uid);
                            if (user != null && user.has("name")) {
                                return user.get("name").asText();
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to resolve user name for userId {}: {}", uid, e.getMessage());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (event.getCreatedByUserId() != null) {
            try {
                JsonNode user = identityUserClient.getUser(event.getCreatedByUserId());
                if (user != null && user.has("name")) {
                    createdByName = user.get("name").asText();
                }
            } catch (Exception e) {
                logger.warn("Failed to resolve creator name for userId {}: {}", event.getCreatedByUserId(), e.getMessage());
            }
        }

        return new CalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getCustomTypeLabel(),
                event.getStartDateTime(),
                event.getEndDateTime(),
                event.isAllDay(),
                event.getAudience(),
                event.getClassIds(),
                classNames,
                event.getSectionIds(),
                sectionNames,
                event.getTargetUserIds(),
                targetUserNames,
                event.getCreatedByUserId(),
                createdByName,
                event.isRecurring(),
                event.getRecurrenceRule(),
                event.getRecurrenceParentId(),
                event.getMeetingLink(),
                event.getLocation(),
                event.getColor(),
                event.getMetadata(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private Set<String> resolveStudentClassIds(String userId, UUID clientId) {
        return enrollmentRepository.findByClientIdAndStudentId(clientId, userId).stream()
                .map(e -> e.getClassId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> resolveStudentSectionIds(String userId, UUID clientId) {
        // batchId in Enrollment represents section ID
        return enrollmentRepository.findByClientIdAndStudentId(clientId, userId).stream()
                .map(e -> e.getBatchId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> resolveInstructorClassIds(String userId, UUID clientId) {
        return instructorAssignmentRepository.findByClientIdAndInstructorUserId(clientId, userId).stream()
                .map(InstructorAssignment::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> resolveInstructorSectionIds(String userId, UUID clientId) {
        return instructorAssignmentRepository.findByClientIdAndInstructorUserId(clientId, userId).stream()
                .map(InstructorAssignment::getSectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void validateCoordinatorPermission(String userId, UUID clientId,
                                               EventAudience audience, List<String> classIds, List<String> sectionIds) {
        if (audience == EventAudience.CLASS) {
            if (classIds == null || classIds.isEmpty()) return;
            for (String classId : classIds) {
                com.datagami.edudron.student.domain.Class cls = classRepository.findByIdAndClientId(classId, clientId)
                        .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
                if (!userId.equals(cls.getCoordinatorUserId())) {
                    throw new IllegalStateException("Only the class coordinator can create CLASS events for class: " + classId);
                }
            }
        } else if (audience == EventAudience.SECTION) {
            if (sectionIds == null || sectionIds.isEmpty()) return;
            for (String sectionId : sectionIds) {
                Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
                        .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
                if (!userId.equals(section.getCoordinatorUserId())) {
                    throw new IllegalStateException("Only the section coordinator can create SECTION events for section: " + sectionId);
                }
            }
        } else if (audience == EventAudience.TENANT_WIDE || audience == EventAudience.FACULTY_ONLY) {
            throw new IllegalStateException("Instructors cannot create TENANT_WIDE or FACULTY_ONLY events");
        }
    }

    private UUID resolveClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }

    private void validateAudienceScoping(EventAudience audience, List<String> classIds, List<String> sectionIds) {
        if (audience == EventAudience.CLASS && (classIds == null || classIds.isEmpty())) {
            throw new IllegalArgumentException("classIds is required for CLASS audience events");
        }
        if (audience == EventAudience.SECTION && (sectionIds == null || sectionIds.isEmpty())) {
            throw new IllegalArgumentException("sectionIds is required for SECTION audience events");
        }
    }

    private void validateCreatePermission(String userId, String userRole, UUID clientId,
                                          EventAudience audience, List<String> classIds, List<String> sectionIds) {
        if (userRole == null) {
            throw new IllegalStateException("User role not available. Please re-authenticate.");
        }
        switch (userRole) {
            case "SYSTEM_ADMIN":
            case "TENANT_ADMIN":
                // Admins can create any audience
                break;
            case "INSTRUCTOR":
                if (audience == EventAudience.PERSONAL) {
                    break; // Instructors can always create personal events
                }
                validateCoordinatorPermission(userId, clientId, audience, classIds, sectionIds);
                break;
            case "STUDENT":
                if (audience != EventAudience.PERSONAL) {
                    throw new IllegalStateException("Students can only create PERSONAL events");
                }
                break;
            default:
                throw new IllegalStateException("Unsupported role for calendar event creation: " + userRole);
        }
    }

    private CalendarEvent mapRequestToEntity(CreateCalendarEventRequest request, String userId, UUID clientId) {
        CalendarEvent event = new CalendarEvent();
        event.setClientId(clientId);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setEventType(request.eventType());
        event.setCustomTypeLabel(request.customTypeLabel());
        event.setStartDateTime(request.startDateTime());
        event.setEndDateTime(request.endDateTime());
        event.setAllDay(request.allDay());
        event.setAudience(request.audience());
        event.setClassIds(request.classIds());
        event.setSectionIds(request.sectionIds());
        event.setTargetUserIds(request.targetUserIds());
        event.setCreatedByUserId(userId);
        event.setRecurring(request.isRecurring());
        event.setRecurrenceRule(request.recurrenceRule());
        event.setMeetingLink(request.meetingLink());
        event.setLocation(request.location());
        event.setColor(request.color());
        event.setMetadata(request.metadata());
        return event;
    }

    private List<CalendarEvent> fetchRoleBasedEvents(String clientIdStr, String userId, String userRole,
                                                      OffsetDateTime startDate, OffsetDateTime endDate, UUID clientId) {
        if (userRole == null) userRole = "";
        switch (userRole) {
            case "STUDENT": {
                Set<String> studentClassIds = resolveStudentClassIds(userId, clientId);
                Set<String> studentSectionIds = resolveStudentSectionIds(userId, clientId);
                return calendarEventRepository.findEventsForStudent(
                        clientIdStr, startDate, endDate,
                        studentClassIds.toArray(new String[0]),
                        studentSectionIds.toArray(new String[0]),
                        userId);
            }
            case "INSTRUCTOR": {
                Set<String> instrClassIds = resolveInstructorClassIds(userId, clientId);
                Set<String> instrSectionIds = resolveInstructorSectionIds(userId, clientId);
                return calendarEventRepository.findEventsForInstructor(
                        clientIdStr, startDate, endDate,
                        instrClassIds.toArray(new String[0]),
                        instrSectionIds.toArray(new String[0]),
                        userId,
                        new String[]{userId});
            }
            case "SYSTEM_ADMIN":
            case "TENANT_ADMIN":
                return calendarEventRepository.findEventsForAdmin(clientIdStr, startDate, endDate, userId);
            default:
                // Fallback: only personal events (use student query with empty arrays)
                return calendarEventRepository.findEventsForStudent(
                        clientIdStr, startDate, endDate,
                        new String[0], new String[0], userId);
        }
    }

    private void validateReadPermission(CalendarEvent event, String userId, String userRole, UUID clientId) {
        // Admins can read everything except other users' personal events
        if ("SYSTEM_ADMIN".equals(userRole) || "TENANT_ADMIN".equals(userRole)) {
            if (event.getAudience() == EventAudience.PERSONAL && !userId.equals(event.getCreatedByUserId())) {
                throw new IllegalStateException("Cannot access another user's personal event");
            }
            return;
        }

        // Personal events: only creator
        if (event.getAudience() == EventAudience.PERSONAL) {
            if (!userId.equals(event.getCreatedByUserId())) {
                throw new IllegalStateException("Cannot access another user's personal event");
            }
            return;
        }

        // FACULTY_ONLY: only instructors and admins; if targetUserIds set, must be in list
        if (event.getAudience() == EventAudience.FACULTY_ONLY) {
            if ("STUDENT".equals(userRole)) {
                throw new IllegalStateException("Students cannot access faculty-only events");
            }
            if (event.getTargetUserIds() != null && !event.getTargetUserIds().isEmpty()
                    && !event.getTargetUserIds().contains(userId)) {
                throw new IllegalStateException("This faculty event is not targeted to you");
            }
            return;
        }

        // TENANT_WIDE: visible to all in the tenant
        if (event.getAudience() == EventAudience.TENANT_WIDE) {
            return;
        }

        // CLASS/SECTION scoped: check membership
        if ("STUDENT".equals(userRole)) {
            if (event.getAudience() == EventAudience.CLASS) {
                Set<String> classIds = resolveStudentClassIds(userId, clientId);
                if (event.getClassIds() == null || Collections.disjoint(classIds, event.getClassIds())) {
                    throw new IllegalStateException("Student is not enrolled in any of the target classes");
                }
            } else if (event.getAudience() == EventAudience.SECTION) {
                Set<String> sectionIds = resolveStudentSectionIds(userId, clientId);
                if (event.getSectionIds() == null || Collections.disjoint(sectionIds, event.getSectionIds())) {
                    throw new IllegalStateException("Student is not enrolled in any of the target sections");
                }
            }
        } else if ("INSTRUCTOR".equals(userRole)) {
            if (event.getAudience() == EventAudience.CLASS) {
                Set<String> classIds = resolveInstructorClassIds(userId, clientId);
                if (event.getClassIds() == null || Collections.disjoint(classIds, event.getClassIds())) {
                    throw new IllegalStateException("Instructor is not assigned to any of the target classes");
                }
            } else if (event.getAudience() == EventAudience.SECTION) {
                Set<String> sectionIds = resolveInstructorSectionIds(userId, clientId);
                if (event.getSectionIds() == null || Collections.disjoint(sectionIds, event.getSectionIds())) {
                    throw new IllegalStateException("Instructor is not assigned to any of the target sections");
                }
            }
        }
    }

    private void validateOwnership(CalendarEvent event, String userId) {
        if (!userId.equals(event.getCreatedByUserId())) {
            throw new IllegalStateException("Only the event creator can modify or delete this event");
        }
    }

    private Map<String, Object> applyUpdates(CalendarEvent event, UpdateCalendarEventRequest request) {
        Map<String, Object> changes = new HashMap<>();

        if (request.getTitle() != null) {
            changes.put("title", request.getTitle());
            event.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            changes.put("description", "updated");
            event.setDescription(request.getDescription());
        }
        if (request.getEventType() != null) {
            changes.put("eventType", request.getEventType().name());
            event.setEventType(request.getEventType());
        }
        if (request.getCustomTypeLabel() != null) {
            changes.put("customTypeLabel", request.getCustomTypeLabel());
            event.setCustomTypeLabel(request.getCustomTypeLabel());
        }
        if (request.getStartDateTime() != null) {
            changes.put("startDateTime", request.getStartDateTime().toString());
            event.setStartDateTime(request.getStartDateTime());
        }
        if (request.getEndDateTime() != null) {
            changes.put("endDateTime", request.getEndDateTime().toString());
            event.setEndDateTime(request.getEndDateTime());
        }
        if (request.getAllDay() != null) {
            changes.put("allDay", request.getAllDay());
            event.setAllDay(request.getAllDay());
        }
        if (request.getAudience() != null) {
            changes.put("audience", request.getAudience().name());
            event.setAudience(request.getAudience());
        }
        if (request.getClassIds() != null) {
            changes.put("classIds", request.getClassIds().toString());
            event.setClassIds(request.getClassIds());
        }
        if (request.getSectionIds() != null) {
            changes.put("sectionIds", request.getSectionIds().toString());
            event.setSectionIds(request.getSectionIds());
        }
        if (request.getTargetUserIds() != null) {
            changes.put("targetUserIds", request.getTargetUserIds().toString());
            event.setTargetUserIds(request.getTargetUserIds());
        }
        if (request.getMeetingLink() != null) {
            changes.put("meetingLink", request.getMeetingLink());
            event.setMeetingLink(request.getMeetingLink());
        }
        if (request.getLocation() != null) {
            changes.put("location", request.getLocation());
            event.setLocation(request.getLocation());
        }
        if (request.getColor() != null) {
            changes.put("color", request.getColor());
            event.setColor(request.getColor());
        }
        if (request.getMetadata() != null) {
            changes.put("metadata", "updated");
            event.setMetadata(request.getMetadata());
        }

        return changes;
    }
}
