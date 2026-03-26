package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.ProjectEvent;

import java.time.OffsetDateTime;

public class ProjectEventDTO {
    private String id;
    private String projectId;
    private String name;
    private OffsetDateTime dateTime;
    private String zoomLink;
    private Boolean hasMarks;
    private Integer maxMarks;
    private Integer sequence;
    private String sectionId;

    public ProjectEventDTO() {}

    public static ProjectEventDTO fromEntity(ProjectEvent e) {
        ProjectEventDTO dto = new ProjectEventDTO();
        dto.setId(e.getId());
        dto.setProjectId(e.getProjectId());
        dto.setName(e.getName());
        dto.setDateTime(e.getDateTime());
        dto.setZoomLink(e.getZoomLink());
        dto.setHasMarks(e.getHasMarks());
        dto.setMaxMarks(e.getMaxMarks());
        dto.setSequence(e.getSequence());
        dto.setSectionId(e.getSectionId());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
