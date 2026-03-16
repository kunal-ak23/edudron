package com.datagami.edudron.content.simulation.domain;

import com.datagami.edudron.common.UlidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "simulation", schema = "content")
public class Simulation {

    public enum SimulationStatus {
        DRAFT,
        GENERATING,
        REVIEW,
        PUBLISHED,
        ARCHIVED
    }

    public enum SimulationVisibility {
        ALL,
        ASSIGNED_ONLY
    }

    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "course_id")
    private String courseId;

    @Column(name = "lecture_id")
    private String lectureId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String concept;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 50)
    private String audience;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tree_data", columnDefinition = "jsonb")
    private Map<String, Object> treeData;

    @Column(name = "target_depth")
    private Integer targetDepth = 15;

    @Column(name = "choices_per_node")
    private Integer choicesPerNode = 3;

    @Column(name = "max_depth")
    private Integer maxDepth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimulationStatus status = SimulationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimulationVisibility visibility = SimulationVisibility.ALL;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "assigned_to_section_ids", columnDefinition = "text[]")
    private String[] assignedToSectionIds;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UlidGenerator.nextUlid();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getLectureId() {
        return lectureId;
    }

    public void setLectureId(String lectureId) {
        this.lectureId = lectureId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getTreeData() {
        return treeData;
    }

    public void setTreeData(Map<String, Object> treeData) {
        this.treeData = treeData;
    }

    public Integer getTargetDepth() {
        return targetDepth;
    }

    public void setTargetDepth(Integer targetDepth) {
        this.targetDepth = targetDepth;
    }

    public Integer getChoicesPerNode() {
        return choicesPerNode;
    }

    public void setChoicesPerNode(Integer choicesPerNode) {
        this.choicesPerNode = choicesPerNode;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public void setStatus(SimulationStatus status) {
        this.status = status;
    }

    public SimulationVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(SimulationVisibility visibility) {
        this.visibility = visibility;
    }

    public String[] getAssignedToSectionIds() {
        return assignedToSectionIds;
    }

    public void setAssignedToSectionIds(String[] assignedToSectionIds) {
        this.assignedToSectionIds = assignedToSectionIds;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(Map<String, Object> metadataJson) {
        this.metadataJson = metadataJson;
    }
}
