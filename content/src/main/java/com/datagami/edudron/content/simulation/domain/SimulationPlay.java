package com.datagami.edudron.content.simulation.domain;

import com.datagami.edudron.common.UlidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "simulation_play", schema = "content")
public class SimulationPlay {

    public enum PlayStatus {
        IN_PROGRESS,
        COMPLETED,
        ABANDONED
    }

    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "simulation_id", nullable = false)
    private String simulationId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayStatus status = PlayStatus.IN_PROGRESS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> pathJson;

    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    @Column(name = "final_node_id", length = 100)
    private String finalNodeId;

    private Integer score;

    @Column(name = "decisions_made")
    private Integer decisionsMade = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UlidGenerator.nextUlid();
        }
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
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

    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public PlayStatus getStatus() {
        return status;
    }

    public void setStatus(PlayStatus status) {
        this.status = status;
    }

    public List<Map<String, Object>> getPathJson() {
        return pathJson;
    }

    public void setPathJson(List<Map<String, Object>> pathJson) {
        this.pathJson = pathJson;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getFinalNodeId() {
        return finalNodeId;
    }

    public void setFinalNodeId(String finalNodeId) {
        this.finalNodeId = finalNodeId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getDecisionsMade() {
        return decisionsMade;
    }

    public void setDecisionsMade(Integer decisionsMade) {
        this.decisionsMade = decisionsMade;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
