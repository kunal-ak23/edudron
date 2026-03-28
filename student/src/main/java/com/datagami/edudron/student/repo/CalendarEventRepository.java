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
}
