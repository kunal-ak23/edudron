package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_event", schema = "student")
public class ProjectEvent {
    @Id
    @Column(name = "id")
    private String id; // ULID

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "date_time")
    private OffsetDateTime dateTime;

    @Column(name = "zoom_link", columnDefinition = "text")
    private String zoomLink;

    @Column(name = "has_marks")
    private Boolean hasMarks = false;

    @Column(name = "max_marks")
    private Integer maxMarks;

    @Column(name = "sequence")
    private Integer sequence;

    @Column(name = "section_id", length = 26)
    private String sectionId;

    // Constructors
    public ProjectEvent() {}

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

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OffsetDateTime getDateTime() { return dateTime; }
    public void setDateTime(OffsetDateTime dateTime) { this.dateTime = dateTime; }

    public String getZoomLink() { return zoomLink; }
    public void setZoomLink(String zoomLink) { this.zoomLink = zoomLink; }

    public Boolean getHasMarks() { return hasMarks; }
    public void setHasMarks(Boolean hasMarks) { this.hasMarks = hasMarks; }

    public Integer getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Integer maxMarks) { this.maxMarks = maxMarks; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
}
