package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CreateCalendarEventRequest(
    @NotBlank(message = "Title is required") String title,
    String description,
    @NotNull(message = "Event type is required") EventType eventType,
    String customTypeLabel,
    @NotNull(message = "Start date/time is required") OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    boolean allDay,
    @NotNull(message = "Audience is required") EventAudience audience,
    List<String> classIds,
    List<String> sectionIds,
    List<String> targetUserIds,
    boolean isRecurring,
    String recurrenceRule,
    String meetingLink,
    String location,
    String color,
    Map<String, Object> metadata
) {}
