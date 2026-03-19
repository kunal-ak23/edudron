package com.datagami.edudron.content.simulation.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public class SimulationExportDTO {
    private String version = "2.0";
    private OffsetDateTime exportedAt;
    private SimulationData simulation;

    public static class SimulationData {
        private String title;
        private String concept;
        private String subject;
        private String audience;
        private String description;
        private Map<String, Object> simulationData;
        private Integer targetYears;
        private Integer decisionsPerYear;
        private Map<String, Object> metadataJson;

        // Getters and Setters
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

        public Map<String, Object> getSimulationData() { return simulationData; }
        public void setSimulationData(Map<String, Object> simulationData) { this.simulationData = simulationData; }

        public Integer getTargetYears() { return targetYears; }
        public void setTargetYears(Integer targetYears) { this.targetYears = targetYears; }

        public Integer getDecisionsPerYear() { return decisionsPerYear; }
        public void setDecisionsPerYear(Integer decisionsPerYear) { this.decisionsPerYear = decisionsPerYear; }

        public Map<String, Object> getMetadataJson() { return metadataJson; }
        public void setMetadataJson(Map<String, Object> metadataJson) { this.metadataJson = metadataJson; }
    }

    // Getters and Setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public OffsetDateTime getExportedAt() { return exportedAt; }
    public void setExportedAt(OffsetDateTime exportedAt) { this.exportedAt = exportedAt; }

    public SimulationData getSimulation() { return simulation; }
    public void setSimulation(SimulationData simulation) { this.simulation = simulation; }
}
