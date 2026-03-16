package com.datagami.edudron.content.simulation.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.simulation.domain.Simulation;
import com.datagami.edudron.content.simulation.domain.SimulationPlay;
import com.datagami.edudron.content.simulation.dto.DecisionInputDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDecisionDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDTO;
import com.datagami.edudron.content.simulation.dto.SimulationExportDTO;
import com.datagami.edudron.content.simulation.dto.SimulationPlayDTO;
import com.datagami.edudron.content.simulation.repo.SimulationPlayRepository;
import com.datagami.edudron.content.simulation.repo.SimulationRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationService.class);

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SimulationPlayRepository playRepository;

    @Autowired
    private DecisionMappingService decisionMappingService;

    // ============ ADMIN OPERATIONS ============

    @Transactional(readOnly = true)
    public SimulationDTO getSimulation(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));
        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationId(id));
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<SimulationDTO> listSimulations(Pageable pageable, String status) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Page<Simulation> page;
        if (status != null && !status.isEmpty()) {
            page = simulationRepository.findByClientIdAndStatusOrderByCreatedAtDesc(
                    clientId, Simulation.SimulationStatus.valueOf(status), pageable);
        } else {
            page = simulationRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable);
        }
        return page.map(sim -> {
            SimulationDTO dto = SimulationDTO.fromEntity(sim);
            dto.setTotalPlays((int) playRepository.countBySimulationId(sim.getId()));
            return dto;
        });
    }

    @Transactional
    public SimulationDTO updateSimulation(String id, SimulationDTO updates) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (updates.getTitle() != null) {
            sim.setTitle(updates.getTitle());
        }
        if (updates.getDescription() != null) {
            sim.setDescription(updates.getDescription());
        }
        if (updates.getConcept() != null) {
            sim.setConcept(updates.getConcept());
        }
        if (updates.getSubject() != null) {
            sim.setSubject(updates.getSubject());
        }
        if (updates.getAudience() != null) {
            sim.setAudience(updates.getAudience());
        }
        if (updates.getVisibility() != null) {
            sim.setVisibility(Simulation.SimulationVisibility.valueOf(updates.getVisibility()));
        }
        if (updates.getAssignedToSectionIds() != null) {
            sim.setAssignedToSectionIds(updates.getAssignedToSectionIds().toArray(new String[0]));
        }
        if (updates.getCourseId() != null) {
            sim.setCourseId(updates.getCourseId());
        }
        if (updates.getLectureId() != null) {
            sim.setLectureId(updates.getLectureId());
        }
        if (updates.getTargetYears() != null) {
            sim.setTargetYears(updates.getTargetYears());
        }
        if (updates.getDecisionsPerYear() != null) {
            sim.setDecisionsPerYear(updates.getDecisionsPerYear());
        }

        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationId(id));
        return dto;
    }

    @Transactional
    public SimulationDTO updateSimulationData(String id, Map<String, Object> simulationData) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        sim.setSimulationData(simulationData);
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationId(id));
        return dto;
    }

    @Transactional
    public SimulationDTO publish(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() != Simulation.SimulationStatus.REVIEW) {
            throw new IllegalStateException(
                    "Cannot publish simulation in status: " + sim.getStatus()
                            + ". Must be in REVIEW status.");
        }

        sim.setStatus(Simulation.SimulationStatus.PUBLISHED);
        sim.setPublishedAt(OffsetDateTime.now());
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationId(id));
        return dto;
    }

    @Transactional
    public SimulationDTO archive(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        sim.setStatus(Simulation.SimulationStatus.ARCHIVED);
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationId(id));
        return dto;
    }

    @Transactional(readOnly = true)
    public SimulationExportDTO exportSimulation(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        SimulationExportDTO.SimulationData data = new SimulationExportDTO.SimulationData();
        data.setTitle(sim.getTitle());
        data.setConcept(sim.getConcept());
        data.setSubject(sim.getSubject());
        data.setAudience(sim.getAudience());
        data.setDescription(sim.getDescription());
        data.setSimulationData(sim.getSimulationData());
        data.setTargetYears(sim.getTargetYears());
        data.setDecisionsPerYear(sim.getDecisionsPerYear());
        data.setMetadataJson(sim.getMetadataJson());

        SimulationExportDTO export = new SimulationExportDTO();
        export.setExportedAt(OffsetDateTime.now());
        export.setSimulation(data);
        return export;
    }

    @Transactional
    public SimulationDTO importSimulation(SimulationExportDTO exportData, String createdBy) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        SimulationExportDTO.SimulationData data = exportData.getSimulation();

        if (data == null) {
            throw new IllegalArgumentException("Export data must contain simulation data");
        }

        Simulation sim = new Simulation();
        sim.setClientId(clientId);
        sim.setTitle(data.getTitle());
        sim.setConcept(data.getConcept());
        sim.setSubject(data.getSubject());
        sim.setAudience(data.getAudience());
        sim.setDescription(data.getDescription());
        sim.setSimulationData(data.getSimulationData());
        sim.setTargetYears(data.getTargetYears());
        sim.setDecisionsPerYear(data.getDecisionsPerYear());
        sim.setMetadataJson(data.getMetadataJson());
        sim.setStatus(Simulation.SimulationStatus.REVIEW);
        sim.setCreatedBy(createdBy);

        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays(0);
        return dto;
    }

    // ============ STUDENT OPERATIONS ============

    @Transactional(readOnly = true)
    public List<SimulationDTO> getAvailableSimulations(String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<Simulation> published = simulationRepository.findByClientIdAndStatus(
                clientId, Simulation.SimulationStatus.PUBLISHED);

        return published.stream()
                .filter(sim -> sim.getVisibility() == Simulation.SimulationVisibility.ALL
                        || isStudentAssigned(studentId, sim))
                .map(sim -> {
                    SimulationDTO dto = SimulationDTO.fromEntity(sim);
                    dto.setSimulationData(null); // Strip data from list view
                    dto.setConcept(null);  // Concept must never be visible to students before play
                    dto.setCreatedBy(null);
                    dto.setTotalPlays((int) playRepository.countBySimulationId(sim.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private boolean isStudentAssigned(String studentId, Simulation sim) {
        return sim.getAssignedToSectionIds() == null || sim.getAssignedToSectionIds().length == 0;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public SimulationPlayDTO startPlay(String simulationId, String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(simulationId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() != Simulation.SimulationStatus.PUBLISHED) {
            throw new IllegalStateException("Simulation is not published");
        }

        int existingPlays = playRepository.countBySimulationIdAndStudentId(simulationId, studentId);
        boolean isPrimary = (existingPlays == 0);

        // If not primary (replay), verify student has at least one completed/fired play
        if (!isPrimary) {
            List<SimulationPlay> plays = playRepository
                    .findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(simulationId, studentId);
            boolean hasFinished = plays.stream()
                    .anyMatch(p -> p.getStatus() == SimulationPlay.PlayStatus.COMPLETED
                            || p.getStatus() == SimulationPlay.PlayStatus.FIRED);
            if (!hasFinished) {
                throw new IllegalStateException("Must complete simulation before replaying");
            }
        }

        // Get role progression from simulation data to set initial role
        Map<String, Object> simData = sim.getSimulationData();
        String initialRole = null;
        if (simData != null) {
            List<String> roleProgression = (List<String>) simData.get("roleProgression");
            if (roleProgression != null && !roleProgression.isEmpty()) {
                initialRole = roleProgression.get(0);
            }
        }

        SimulationPlay play = new SimulationPlay();
        play.setClientId(clientId);
        play.setSimulationId(simulationId);
        play.setStudentId(studentId);
        play.setAttemptNumber(existingPlays + 1);
        play.setIsPrimary(isPrimary);
        play.setCurrentYear(1);
        play.setCurrentDecision(0);
        play.setCurrentRole(initialRole);
        play.setCumulativeScore(0);
        play.setConsecutiveStruggling(0);
        play.setPerformanceBand("STEADY"); // First year starts as STEADY
        play.setDecisionsJson(new ArrayList<>());
        play.setYearScoresJson(new ArrayList<>());

        playRepository.save(play);
        return SimulationPlayDTO.fromEntity(play, sim.getTitle());
    }

    // TODO: v2 play flow methods (getCurrentState, submitDecision, getDebrief) will be
    // implemented in a subsequent task. The v1 tree-based play logic has been removed
    // as it is incompatible with the year-based career tenure model.

    @Transactional(readOnly = true)
    public List<SimulationPlayDTO> getPlayHistory(String simulationId, String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(simulationId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));
        List<SimulationPlay> plays = playRepository
                .findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(simulationId, studentId);
        return plays.stream()
                .map(p -> SimulationPlayDTO.fromEntity(p, sim.getTitle()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SimulationPlayDTO> getAllPlayHistory(String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<SimulationPlay> plays = playRepository
                .findByStudentIdAndClientIdOrderByStartedAtDesc(studentId, clientId);
        return plays.stream()
                .map(p -> {
                    String title = simulationRepository.findByIdAndClientId(p.getSimulationId(), clientId)
                            .map(Simulation::getTitle).orElse("Unknown");
                    return SimulationPlayDTO.fromEntity(p, title);
                })
                .collect(Collectors.toList());
    }
}
