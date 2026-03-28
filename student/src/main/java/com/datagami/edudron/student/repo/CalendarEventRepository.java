package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, String>, JpaSpecificationExecutor<CalendarEvent> {

    Optional<CalendarEvent> findByIdAndClientId(String id, UUID clientId);

    List<CalendarEvent> findByRecurrenceParentIdAndIsActiveTrue(String parentId);

    @Modifying
    @Query("UPDATE CalendarEvent e SET e.isActive = false WHERE e.recurrenceParentId = :parentId OR e.id = :parentId")
    int softDeleteSeries(@Param("parentId") String parentId);

    @Modifying
    @Query("UPDATE CalendarEvent e SET e.isActive = false WHERE (e.recurrenceParentId = :parentId OR e.id = :parentId) AND e.startDateTime >= :fromDate")
    int softDeleteFutureOccurrences(@Param("parentId") String parentId, @Param("fromDate") OffsetDateTime fromDate);

    /**
     * Student visibility: TENANT_WIDE + CLASS (overlapping classIds) + SECTION (overlapping sectionIds) + own PERSONAL.
     * Students do NOT see FACULTY_ONLY events.
     */
    @Query(value = """
        SELECT * FROM calendar.calendar_events e
        WHERE e.client_id = CAST(:clientId AS uuid) AND e.is_active = true
        AND e.start_date_time >= CAST(:startDate AS timestamptz)
        AND e.start_date_time <= CAST(:endDate AS timestamptz)
        AND (
            e.audience = 'TENANT_WIDE'
            OR (e.audience = 'CLASS' AND e.class_ids && CAST(:classIds AS text[]))
            OR (e.audience = 'SECTION' AND e.section_ids && CAST(:sectionIds AS text[]))
            OR (e.audience = 'PERSONAL' AND e.created_by_user_id = :userId)
        )
        ORDER BY e.start_date_time
        """, nativeQuery = true)
    List<CalendarEvent> findEventsForStudent(
        @Param("clientId") String clientId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        @Param("classIds") String[] classIds,
        @Param("sectionIds") String[] sectionIds,
        @Param("userId") String userId);

    /**
     * Instructor visibility: TENANT_WIDE + FACULTY_ONLY (all or targeted) + CLASS (overlapping) + SECTION (overlapping) + own PERSONAL.
     */
    @Query(value = """
        SELECT * FROM calendar.calendar_events e
        WHERE e.client_id = CAST(:clientId AS uuid) AND e.is_active = true
        AND e.start_date_time >= CAST(:startDate AS timestamptz)
        AND e.start_date_time <= CAST(:endDate AS timestamptz)
        AND (
            e.audience = 'TENANT_WIDE'
            OR (e.audience = 'FACULTY_ONLY' AND (e.target_user_ids IS NULL OR e.target_user_ids && CAST(:userIdArray AS text[])))
            OR (e.audience = 'CLASS' AND e.class_ids && CAST(:classIds AS text[]))
            OR (e.audience = 'SECTION' AND e.section_ids && CAST(:sectionIds AS text[]))
            OR (e.audience = 'PERSONAL' AND e.created_by_user_id = :userId)
        )
        ORDER BY e.start_date_time
        """, nativeQuery = true)
    List<CalendarEvent> findEventsForInstructor(
        @Param("clientId") String clientId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        @Param("classIds") String[] classIds,
        @Param("sectionIds") String[] sectionIds,
        @Param("userId") String userId,
        @Param("userIdArray") String[] userIdArray);

    /**
     * Admin visibility: all institutional events + own PERSONAL.
     */
    @Query(value = """
        SELECT * FROM calendar.calendar_events e
        WHERE e.client_id = CAST(:clientId AS uuid) AND e.is_active = true
        AND e.start_date_time >= CAST(:startDate AS timestamptz)
        AND e.start_date_time <= CAST(:endDate AS timestamptz)
        AND (
            e.audience IN ('TENANT_WIDE', 'CLASS', 'SECTION', 'FACULTY_ONLY')
            OR (e.audience = 'PERSONAL' AND e.created_by_user_id = :userId)
        )
        ORDER BY e.start_date_time
        """, nativeQuery = true)
    List<CalendarEvent> findEventsForAdmin(
        @Param("clientId") String clientId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        @Param("userId") String userId);
}
