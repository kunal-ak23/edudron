package com.datagami.edudron.content.simulation.dto;

import com.datagami.edudron.content.simulation.domain.Simulation;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SimulationDTO {
    private String id;
    private String title;
    private String concept;
    private String subject;
    private String audience;
    private String description;
    private String courseId;
    private String lectureId;
    private Map<String, Object> treeData;
    private Integer targetDepth;
    private Integer choicesPerNode;
    private Integer maxDepth;
    private String status;
    private String visibility;
    private List<String> assignedToSectionIds;
    private String createdBy;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private int totalPlays; // computed field

    public static SimulationDTO fromEntity(Simulation sim) {
        SimulationDTO dto = new SimulationDTO();
        dto.setId(sim.getId());
        dto.setTitle(sim.getTitle());
        dto.setConcept(sim.getConcept());
        dto.setSubject(sim.getSubject());
        dto.setAudience(sim.getAudience());
        dto.setDescription(sim.getDescription());
        dto.setCourseId(sim.getCourseId());
        dto.setLectureId(sim.getLectureId());
        dto.setTreeData(sim.getTreeData());
        dto.setTargetDepth(sim.getTargetDepth());
        dto.setChoicesPerNode(sim.getChoicesPerNode());
        dto.setMaxDepth(sim.getMaxDepth());
        dto.setStatus(sim.getStatus() != null ? sim.getStatus().name() : null);
        dto.setVisibility(sim.getVisibility() != null ? sim.getVisibility().name() : null);
        dto.setAssignedToSectionIds(sim.getAssignedToSectionIds() != null
                ? Arrays.asList(sim.getAssignedToSectionIds()) : null);
        dto.setCreatedBy(sim.getCreatedBy());
        dto.setPublishedAt(sim.getPublishedAt());
        dto.setCreatedAt(sim.getCreatedAt());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public Map<String, Object> getTreeData() { return treeData; }
    public void setTreeData(Map<String, Object> treeData) { this.treeData = treeData; }

    public Integer getTargetDepth() { return targetDepth; }
    public void setTargetDepth(Integer targetDepth) { this.targetDepth = targetDepth; }

    public Integer getChoicesPerNode() { return choicesPerNode; }
    public void setChoicesPerNode(Integer choicesPerNode) { this.choicesPerNode = choicesPerNode; }

    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public List<String> getAssignedToSectionIds() { return assignedToSectionIds; }
    public void setAssignedToSectionIds(List<String> assignedToSectionIds) { this.assignedToSectionIds = assignedToSectionIds; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public int getTotalPlays() { return totalPlays; }
    public void setTotalPlays(int totalPlays) { this.totalPlays = totalPlays; }
}
