package com.datagami.edudron.content.simulation.domain;

import com.datagami.edudron.common.UlidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
        FIRED,
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

    @Column(name = "current_year")
    private Integer currentYear = 1;

    @Column(name = "current_decision")
    private Integer currentDecision = 0;

    @Column(name = "\"current_role\"", length = 100)
    private String currentRole;

    @Column(name = "cumulative_score")
    private Integer cumulativeScore = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "year_scores_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> yearScoresJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decisions_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> decisionsJson;

    @Column(name = "current_budget")
    private BigDecimal currentBudget = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "budget_history_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> budgetHistoryJson;

    @Column(name = "performance_band", length = 20)
    private String performanceBand;

    @Column(name = "consecutive_struggling")
    private Integer consecutiveStruggling = 0;

    @Column(name = "final_score")
    private Integer finalScore;

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

    public Integer getCurrentYear() {
        return currentYear;
    }

    public void setCurrentYear(Integer currentYear) {
        this.currentYear = currentYear;
    }

    public Integer getCurrentDecision() {
        return currentDecision;
    }

    public void setCurrentDecision(Integer currentDecision) {
        this.currentDecision = currentDecision;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(String currentRole) {
        this.currentRole = currentRole;
    }

    public Integer getCumulativeScore() {
        return cumulativeScore;
    }

    public void setCumulativeScore(Integer cumulativeScore) {
        this.cumulativeScore = cumulativeScore;
    }

    public List<Map<String, Object>> getYearScoresJson() {
        return yearScoresJson;
    }

    public void setYearScoresJson(List<Map<String, Object>> yearScoresJson) {
        this.yearScoresJson = yearScoresJson;
    }

    public List<Map<String, Object>> getDecisionsJson() {
        return decisionsJson;
    }

    public void setDecisionsJson(List<Map<String, Object>> decisionsJson) {
        this.decisionsJson = decisionsJson;
    }

    public String getPerformanceBand() {
        return performanceBand;
    }

    public void setPerformanceBand(String performanceBand) {
        this.performanceBand = performanceBand;
    }

    public Integer getConsecutiveStruggling() {
        return consecutiveStruggling;
    }

    public void setConsecutiveStruggling(Integer consecutiveStruggling) {
        this.consecutiveStruggling = consecutiveStruggling;
    }

    public Integer getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(Integer finalScore) {
        this.finalScore = finalScore;
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

    public BigDecimal getCurrentBudget() {
        return currentBudget;
    }

    public void setCurrentBudget(BigDecimal currentBudget) {
        this.currentBudget = currentBudget;
    }

    public List<Map<String, Object>> getBudgetHistoryJson() {
        return budgetHistoryJson;
    }

    public void setBudgetHistoryJson(List<Map<String, Object>> budgetHistoryJson) {
        this.budgetHistoryJson = budgetHistoryJson;
    }
}
