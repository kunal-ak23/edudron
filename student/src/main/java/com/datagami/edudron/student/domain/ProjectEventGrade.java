package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "project_event_grade", schema = "student")
public class ProjectEventGrade {
    @Id
    @Column(name = "id")
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "marks", nullable = false)
    private Integer marks;

    // Constructors
    public ProjectEventGrade() {}

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = com.datagami.edudron.common.UlidGenerator.nextUlid();
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public Integer getMarks() { return marks; }
    public void setMarks(Integer marks) { this.marks = marks; }
}
