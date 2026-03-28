package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "calendar_events", schema = "calendar")
public class CalendarEvent {

    @Id
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "custom_type_label")
    private String customTypeLabel;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time")
    private OffsetDateTime endDateTime;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private EventAudience audience;

    @Column(name = "class_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> classIds;

    @Column(name = "section_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> sectionIds;

    @Column(name = "target_user_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> targetUserIds;

    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @Column(name = "is_recurring", nullable = false)
    private boolean isRecurring;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Column(name = "recurrence_parent_id")
    private String recurrenceParentId;

    @Column(name = "meeting_link")
    private String meetingLink;

    @Column(name = "location")
    private String location;

    @Column(name = "color")
    private String color;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Constructors
    public CalendarEvent() {}

    @PrePersist
    public void prePersist() {
        if (id == null) id = com.datagami.edudron.common.UlidGenerator.nextUlid();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getCustomTypeLabel() { return customTypeLabel; }
    public void setCustomTypeLabel(String customTypeLabel) { this.customTypeLabel = customTypeLabel; }

    public OffsetDateTime getStartDateTime() { return startDateTime; }
    public void setStartDateTime(OffsetDateTime startDateTime) { this.startDateTime = startDateTime; }

    public OffsetDateTime getEndDateTime() { return endDateTime; }
    public void setEndDateTime(OffsetDateTime endDateTime) { this.endDateTime = endDateTime; }

    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }

    public EventAudience getAudience() { return audience; }
    public void setAudience(EventAudience audience) { this.audience = audience; }

    public List<String> getClassIds() { return classIds; }
    public void setClassIds(List<String> classIds) { this.classIds = classIds; }

    public List<String> getSectionIds() { return sectionIds; }
    public void setSectionIds(List<String> sectionIds) { this.sectionIds = sectionIds; }

    public List<String> getTargetUserIds() { return targetUserIds; }
    public void setTargetUserIds(List<String> targetUserIds) { this.targetUserIds = targetUserIds; }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public String getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; }

    public String getRecurrenceParentId() { return recurrenceParentId; }
    public void setRecurrenceParentId(String recurrenceParentId) { this.recurrenceParentId = recurrenceParentId; }

    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
