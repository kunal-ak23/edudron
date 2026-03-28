package com.datagami.edudron.student.service;

import com.datagami.edudron.student.domain.CalendarEvent;
import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Remaining filter specifications for optional query parameters.
 * Role-based visibility is now handled by native queries in CalendarEventRepository.
 */
public class CalendarEventSpecification {

    private CalendarEventSpecification() {
        // utility class
    }

    public static Specification<CalendarEvent> filterByEventType(EventType eventType) {
        return (root, query, cb) -> cb.equal(root.get("eventType"), eventType);
    }

    public static Specification<CalendarEvent> filterByAudience(EventAudience audience) {
        return (root, query, cb) -> cb.equal(root.get("audience"), audience);
    }
}
