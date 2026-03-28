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
import org.springframework.data.jpa.domain.Specification;
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

        validateAudienceScoping(request.audience(), request.classId(), request.sectionId());
        validateCreatePermission(userId, userRole, clientId, request.audience(), request.classId(), request.sectionId());

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
                                                  String classId, String sectionId,
                                                  EventType eventType, EventAudience audience,
                                                  String userId, String userRole) {
        UUID clientId = resolveClientId();

        Specification<CalendarEvent> spec = buildRoleBasedSpec(clientId, userId, userRole, startDate, endDate);

        // Apply optional filters
        if (classId != null) {
            spec = spec.and(CalendarEventSpecification.filterByClassId(classId));
        }
        if (sectionId != null) {
            spec = spec.and(CalendarEventSpecification.filterBySectionId(sectionId));
        }
        if (eventType != null) {
            spec = spec.and(CalendarEventSpecification.filterByEventType(eventType));
        }
        if (audience != null) {
            spec = spec.and(CalendarEventSpecification.filterByAudience(audience));
        }

        List<CalendarEvent> events = calendarEventRepository.findAll(spec);
        return events.stream().map(this::toResponse).collect(Collectors.toList());
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
        String className = null;
        String sectionName = null;
        String createdByName = null;

        if (event.getClassId() != null) {
            className = classRepository.findById(event.getClassId())
                    .map(com.datagami.edudron.student.domain.Class::getName)
                    .orElse(null);
        }

        if (event.getSectionId() != null) {
            sectionName = sectionRepository.findById(event.getSectionId())
                    .map(Section::getName)
                    .orElse(null);
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
                event.getClassId(),
                className,
                event.getSectionId(),
                sectionName,
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
                                               EventAudience audience, String classId, String sectionId) {
        if (audience == EventAudience.CLASS) {
            com.datagami.edudron.student.domain.Class cls = classRepository.findByIdAndClientId(classId, clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
            if (!userId.equals(cls.getCoordinatorUserId())) {
                throw new IllegalStateException("Only the class coordinator can create CLASS events for this class");
            }
        } else if (audience == EventAudience.SECTION) {
            Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
            if (!userId.equals(section.getCoordinatorUserId())) {
                throw new IllegalStateException("Only the section coordinator can create SECTION events for this section");
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

    private void validateAudienceScoping(EventAudience audience, String classId, String sectionId) {
        if (audience == EventAudience.CLASS && (classId == null || classId.isBlank())) {
            throw new IllegalArgumentException("classId is required for CLASS audience events");
        }
        if (audience == EventAudience.SECTION && (sectionId == null || sectionId.isBlank())) {
            throw new IllegalArgumentException("sectionId is required for SECTION audience events");
        }
    }

    private void validateCreatePermission(String userId, String userRole, UUID clientId,
                                          EventAudience audience, String classId, String sectionId) {
        switch (userRole) {
            case "SYSTEM_ADMIN":
            case "TENANT_ADMIN":
                // Admins can create any audience
                break;
            case "INSTRUCTOR":
                if (audience == EventAudience.PERSONAL) {
                    break; // Instructors can always create personal events
                }
                validateCoordinatorPermission(userId, clientId, audience, classId, sectionId);
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
        event.setClassId(request.classId());
        event.setSectionId(request.sectionId());
        event.setCreatedByUserId(userId);
        event.setRecurring(request.isRecurring());
        event.setRecurrenceRule(request.recurrenceRule());
        event.setMeetingLink(request.meetingLink());
        event.setLocation(request.location());
        event.setColor(request.color());
        event.setMetadata(request.metadata());
        return event;
    }

    private Specification<CalendarEvent> buildRoleBasedSpec(UUID clientId, String userId, String userRole,
                                                            OffsetDateTime startDate, OffsetDateTime endDate) {
        switch (userRole) {
            case "STUDENT":
                Set<String> studentClassIds = resolveStudentClassIds(userId, clientId);
                Set<String> studentSectionIds = resolveStudentSectionIds(userId, clientId);
                return CalendarEventSpecification.forStudent(clientId, userId, studentClassIds, studentSectionIds,
                        startDate, endDate);
            case "INSTRUCTOR":
                Set<String> instrClassIds = resolveInstructorClassIds(userId, clientId);
                Set<String> instrSectionIds = resolveInstructorSectionIds(userId, clientId);
                return CalendarEventSpecification.forInstructor(clientId, userId, instrClassIds, instrSectionIds,
                        startDate, endDate);
            case "SYSTEM_ADMIN":
            case "TENANT_ADMIN":
                return CalendarEventSpecification.forAdmin(clientId, userId, startDate, endDate);
            default:
                // Fallback: only personal events
                return CalendarEventSpecification.forStudent(clientId, userId, Set.of(), Set.of(),
                        startDate, endDate);
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

        // FACULTY_ONLY: only instructors and admins
        if (event.getAudience() == EventAudience.FACULTY_ONLY) {
            if ("STUDENT".equals(userRole)) {
                throw new IllegalStateException("Students cannot access faculty-only events");
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
                if (!classIds.contains(event.getClassId())) {
                    throw new IllegalStateException("Student is not enrolled in this class");
                }
            } else if (event.getAudience() == EventAudience.SECTION) {
                Set<String> sectionIds = resolveStudentSectionIds(userId, clientId);
                if (!sectionIds.contains(event.getSectionId())) {
                    throw new IllegalStateException("Student is not enrolled in this section");
                }
            }
        } else if ("INSTRUCTOR".equals(userRole)) {
            if (event.getAudience() == EventAudience.CLASS) {
                Set<String> classIds = resolveInstructorClassIds(userId, clientId);
                if (!classIds.contains(event.getClassId())) {
                    throw new IllegalStateException("Instructor is not assigned to this class");
                }
            } else if (event.getAudience() == EventAudience.SECTION) {
                Set<String> sectionIds = resolveInstructorSectionIds(userId, clientId);
                if (!sectionIds.contains(event.getSectionId())) {
                    throw new IllegalStateException("Instructor is not assigned to this section");
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
        if (request.getClassId() != null) {
            changes.put("classId", request.getClassId());
            event.setClassId(request.getClassId());
        }
        if (request.getSectionId() != null) {
            changes.put("sectionId", request.getSectionId());
            event.setSectionId(request.getSectionId());
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
