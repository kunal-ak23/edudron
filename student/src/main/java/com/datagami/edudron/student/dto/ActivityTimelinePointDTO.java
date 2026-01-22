package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;

public class ActivityTimelinePointDTO {
    private OffsetDateTime timestamp;
    private Long sessionCount;
    private Long uniqueStudents;

    // Constructors
    public ActivityTimelinePointDTO() {}

    public ActivityTimelinePointDTO(OffsetDateTime timestamp, Long sessionCount, Long uniqueStudents) {
        this.timestamp = timestamp;
        this.sessionCount = sessionCount;
        this.uniqueStudents = uniqueStudents;
    }

    // Getters and Setters
    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public Long getSessionCount() { return sessionCount; }
    public void setSessionCount(Long sessionCount) { this.sessionCount = sessionCount; }

    public Long getUniqueStudents() { return uniqueStudents; }
    public void setUniqueStudents(Long uniqueStudents) { this.uniqueStudents = uniqueStudents; }
}
