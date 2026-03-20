package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "project_group_member", schema = "student")
public class ProjectGroupMember {
    @Id
    @Column(name = "id")
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    // Constructors
    public ProjectGroupMember() {}

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = com.datagami.edudron.common.UlidGenerator.generate();
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
}
