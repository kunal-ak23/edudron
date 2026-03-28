package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CalendarEventResponse(
    String id,
    String title,
    String description,
    EventType eventType,
    String customTypeLabel,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    boolean allDay,
    EventAudience audience,
    List<String> classIds,
    List<String> classNames,
    List<String> sectionIds,
    List<String> sectionNames,
    List<String> targetUserIds,
    List<String> targetUserNames,
    String createdByUserId,
    String createdByName,
    boolean isRecurring,
    String recurrenceRule,
    String recurrenceParentId,
    String meetingLink,
    String location,
    String color,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
