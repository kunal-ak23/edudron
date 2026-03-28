package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import java.time.OffsetDateTime;
import java.util.Map;

public class UpdateCalendarEventRequest {
    private String title;
    private String description;
    private EventType eventType;
    private String customTypeLabel;
    private OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private Boolean allDay;
    private EventAudience audience;
    private String classId;
    private String sectionId;
    private String meetingLink;
    private String location;
    private String color;
    private Map<String, Object> metadata;

    // Getters and Setters

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

    public Boolean getAllDay() { return allDay; }
    public void setAllDay(Boolean allDay) { this.allDay = allDay; }

    public EventAudience getAudience() { return audience; }
    public void setAudience(EventAudience audience) { this.audience = audience; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
