package com.datagami.edudron.student.service;

import com.datagami.edudron.student.domain.CalendarEvent;
import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CalendarEventSpecification {

    private CalendarEventSpecification() {
        // utility class
    }

    /**
     * Students see: TENANT_WIDE + CLASS (their classes) + SECTION (their sections) + own PERSONAL.
     * Students do NOT see FACULTY_ONLY.
     */
    public static Specification<CalendarEvent> forStudent(UUID clientId, String userId,
                                                          Set<String> classIds, Set<String> sectionIds,
                                                          OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // tenant + active + date range
            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.isTrue(root.get("isActive")));
            addDateRange(predicates, root, cb, startDate, endDate);

            // visibility: OR of allowed audiences
            List<Predicate> visibilityPredicates = new ArrayList<>();

            // TENANT_WIDE events
            visibilityPredicates.add(cb.equal(root.get("audience"), EventAudience.TENANT_WIDE));

            // CLASS events matching student's classes
            if (classIds != null && !classIds.isEmpty()) {
                visibilityPredicates.add(cb.and(
                        cb.equal(root.get("audience"), EventAudience.CLASS),
                        root.get("classId").in(classIds)
                ));
            }

            // SECTION events matching student's sections
            if (sectionIds != null && !sectionIds.isEmpty()) {
                visibilityPredicates.add(cb.and(
                        cb.equal(root.get("audience"), EventAudience.SECTION),
                        root.get("sectionId").in(sectionIds)
                ));
            }

            // Own PERSONAL events
            visibilityPredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.PERSONAL),
                    cb.equal(root.get("createdByUserId"), userId)
            ));

            predicates.add(cb.or(visibilityPredicates.toArray(new Predicate[0])));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Instructors see: TENANT_WIDE + FACULTY_ONLY + CLASS (their classes) + SECTION (their sections) + own PERSONAL.
     */
    public static Specification<CalendarEvent> forInstructor(UUID clientId, String userId,
                                                             Set<String> classIds, Set<String> sectionIds,
                                                             OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.isTrue(root.get("isActive")));
            addDateRange(predicates, root, cb, startDate, endDate);

            List<Predicate> visibilityPredicates = new ArrayList<>();

            visibilityPredicates.add(cb.equal(root.get("audience"), EventAudience.TENANT_WIDE));
            visibilityPredicates.add(cb.equal(root.get("audience"), EventAudience.FACULTY_ONLY));

            if (classIds != null && !classIds.isEmpty()) {
                visibilityPredicates.add(cb.and(
                        cb.equal(root.get("audience"), EventAudience.CLASS),
                        root.get("classId").in(classIds)
                ));
            }

            if (sectionIds != null && !sectionIds.isEmpty()) {
                visibilityPredicates.add(cb.and(
                        cb.equal(root.get("audience"), EventAudience.SECTION),
                        root.get("sectionId").in(sectionIds)
                ));
            }

            visibilityPredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.PERSONAL),
                    cb.equal(root.get("createdByUserId"), userId)
            ));

            predicates.add(cb.or(visibilityPredicates.toArray(new Predicate[0])));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Admins see: ALL institutional events (TENANT_WIDE, CLASS, SECTION, FACULTY_ONLY) + own PERSONAL.
     */
    public static Specification<CalendarEvent> forAdmin(UUID clientId, String userId,
                                                        OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.isTrue(root.get("isActive")));
            addDateRange(predicates, root, cb, startDate, endDate);

            // Admins see everything except other users' PERSONAL events
            List<Predicate> visibilityPredicates = new ArrayList<>();
            visibilityPredicates.add(root.get("audience").in(
                    EventAudience.TENANT_WIDE,
                    EventAudience.CLASS,
                    EventAudience.SECTION,
                    EventAudience.FACULTY_ONLY
            ));
            visibilityPredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.PERSONAL),
                    cb.equal(root.get("createdByUserId"), userId)
            ));

            predicates.add(cb.or(visibilityPredicates.toArray(new Predicate[0])));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // --- Optional filter predicates (combine with .and()) ---

    public static Specification<CalendarEvent> filterByEventType(EventType eventType) {
        return (root, query, cb) -> cb.equal(root.get("eventType"), eventType);
    }

    public static Specification<CalendarEvent> filterByClassId(String classId) {
        return (root, query, cb) -> cb.equal(root.get("classId"), classId);
    }

    public static Specification<CalendarEvent> filterBySectionId(String sectionId) {
        return (root, query, cb) -> cb.equal(root.get("sectionId"), sectionId);
    }

    public static Specification<CalendarEvent> filterByAudience(EventAudience audience) {
        return (root, query, cb) -> cb.equal(root.get("audience"), audience);
    }

    // --- Helper ---

    private static void addDateRange(List<Predicate> predicates,
                                     jakarta.persistence.criteria.Root<CalendarEvent> root,
                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("startDateTime"), endDate));
        }
    }
}
