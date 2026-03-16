package com.datagami.edudron.content.simulation.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.simulation.domain.Simulation;
import com.datagami.edudron.content.simulation.domain.SimulationPlay;
import com.datagami.edudron.content.simulation.dto.DecisionInputDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDTO;
import com.datagami.edudron.content.simulation.dto.SimulationExportDTO;
import com.datagami.edudron.content.simulation.dto.SimulationNodeDTO;
import com.datagami.edudron.content.simulation.dto.SimulationPlayDTO;
import com.datagami.edudron.content.simulation.repo.SimulationPlayRepository;
import com.datagami.edudron.content.simulation.repo.SimulationRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        if (updates.getTargetDepth() != null) {
            sim.setTargetDepth(updates.getTargetDepth());
        }
        if (updates.getChoicesPerNode() != null) {
            sim.setChoicesPerNode(updates.getChoicesPerNode());
        }

        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationId(id));
        return dto;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public SimulationDTO updateTree(String id, Map<String, Object> treeData) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        sim.setTreeData(treeData);
        sim.setMaxDepth(computeMaxDepth(treeData));
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
        data.setTreeData(sim.getTreeData());
        data.setTargetDepth(sim.getTargetDepth());
        data.setChoicesPerNode(sim.getChoicesPerNode());
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
        sim.setTreeData(data.getTreeData());
        sim.setTargetDepth(data.getTargetDepth());
        sim.setChoicesPerNode(data.getChoicesPerNode());
        sim.setMetadataJson(data.getMetadataJson());
        sim.setStatus(Simulation.SimulationStatus.REVIEW);
        sim.setCreatedBy(createdBy);

        if (data.getTreeData() != null) {
            sim.setMaxDepth(computeMaxDepth(data.getTreeData()));
        }

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

        // Filter by visibility and section assignment
        // For MVP: return ALL visibility simulations; ASSIGNED_ONLY requires section check
        return published.stream()
                .filter(sim -> sim.getVisibility() == Simulation.SimulationVisibility.ALL
                        || isStudentAssigned(studentId, sim))
                .map(sim -> {
                    SimulationDTO dto = SimulationDTO.fromEntity(sim);
                    dto.setTreeData(null); // Strip tree from list view
                    dto.setTotalPlays((int) playRepository.countBySimulationId(sim.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private boolean isStudentAssigned(String studentId, Simulation sim) {
        // For now, return true if assignedToSectionIds is null/empty
        // Full implementation would check student enrollment against section IDs
        return sim.getAssignedToSectionIds() == null || sim.getAssignedToSectionIds().length == 0;
    }

    @Transactional
    public SimulationPlayDTO startPlay(String simulationId, String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(simulationId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() != Simulation.SimulationStatus.PUBLISHED) {
            throw new IllegalStateException("Simulation is not published");
        }

        int existingPlays = playRepository.countBySimulationIdAndStudentId(simulationId, studentId);
        boolean isPrimary = (existingPlays == 0);

        // If not primary (replay), verify student has at least one completed play
        if (!isPrimary) {
            List<SimulationPlay> plays = playRepository
                    .findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(simulationId, studentId);
            boolean hasCompleted = plays.stream()
                    .anyMatch(p -> p.getStatus() == SimulationPlay.PlayStatus.COMPLETED);
            if (!hasCompleted) {
                throw new IllegalStateException("Must complete simulation before replaying");
            }
        }

        String rootNodeId = (String) sim.getTreeData().get("rootNodeId");
        if (rootNodeId == null) {
            throw new IllegalStateException("Simulation tree has no root node");
        }

        SimulationPlay play = new SimulationPlay();
        play.setClientId(clientId);
        play.setSimulationId(simulationId);
        play.setStudentId(studentId);
        play.setAttemptNumber(existingPlays + 1);
        play.setIsPrimary(isPrimary);
        play.setCurrentNodeId(rootNodeId);
        play.setPathJson(new ArrayList<>());

        playRepository.save(play);
        return SimulationPlayDTO.fromEntity(play, sim.getTitle());
    }

    @Transactional(readOnly = true)
    public SimulationNodeDTO getCurrentNode(String playId, String studentId) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play session not found"));

        if (play.getStatus() != SimulationPlay.PlayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Play session is not in progress");
        }

        Simulation sim = simulationRepository.findById(play.getSimulationId())
                .orElseThrow(() -> new IllegalStateException("Simulation not found"));
        Map<String, Object> node = getNodeFromTree(sim.getTreeData(), play.getCurrentNodeId());

        return toStudentNode(node);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public SimulationNodeDTO submitDecision(String playId, String studentId, DecisionInputDTO input) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play session not found"));

        if (play.getStatus() != SimulationPlay.PlayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Play session is not in progress");
        }

        Simulation sim = simulationRepository.findById(play.getSimulationId())
                .orElseThrow(() -> new IllegalStateException("Simulation not found"));
        Map<String, Object> currentNode = getNodeFromTree(sim.getTreeData(), play.getCurrentNodeId());

        // Verify the input nodeId matches current position
        if (!play.getCurrentNodeId().equals(input.getNodeId())) {
            throw new IllegalArgumentException("Node ID mismatch: expected "
                    + play.getCurrentNodeId() + " but got " + input.getNodeId());
        }

        // Resolve the decision to a choiceId
        String resolvedChoiceId = decisionMappingService.resolveChoice(
                currentNode, input.getChoiceId(), input.getInput());

        // Find the choice and get nextNodeId
        List<Map<String, Object>> choices = (List<Map<String, Object>>) currentNode.get("choices");
        String nextNodeId = null;
        for (Map<String, Object> choice : choices) {
            if (resolvedChoiceId.equals(choice.get("id"))) {
                nextNodeId = (String) choice.get("nextNodeId");
                break;
            }
        }
        if (nextNodeId == null) {
            throw new IllegalStateException("Choice not found: " + resolvedChoiceId);
        }

        // Record the decision in path
        List<Map<String, Object>> path = play.getPathJson() != null
                ? new ArrayList<>(play.getPathJson()) : new ArrayList<>();
        Map<String, Object> pathEntry = new LinkedHashMap<>();
        pathEntry.put("nodeId", play.getCurrentNodeId());
        pathEntry.put("choiceId", resolvedChoiceId);
        if (input.getInput() != null) {
            pathEntry.put("input", input.getInput());
        }
        pathEntry.put("timestamp", OffsetDateTime.now().toString());
        path.add(pathEntry);
        play.setPathJson(path);

        // Move to next node
        play.setCurrentNodeId(nextNodeId);
        play.setDecisionsMade(play.getDecisionsMade() + 1);

        // Check if next node is terminal
        Map<String, Object> nextNode = getNodeFromTree(sim.getTreeData(), nextNodeId);
        if ("TERMINAL".equals(nextNode.get("type"))) {
            play.setStatus(SimulationPlay.PlayStatus.COMPLETED);
            play.setFinalNodeId(nextNodeId);
            play.setCompletedAt(OffsetDateTime.now());
            Object scoreObj = nextNode.get("score");
            if (scoreObj instanceof Number) {
                play.setScore(((Number) scoreObj).intValue());
            }
        }

        playRepository.save(play);
        return toStudentNode(nextNode);
    }

    @Transactional(readOnly = true)
    public SimulationNodeDTO getDebrief(String playId, String studentId) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play session not found"));

        if (play.getStatus() != SimulationPlay.PlayStatus.COMPLETED) {
            throw new IllegalStateException("Play session is not completed");
        }

        Simulation sim = simulationRepository.findById(play.getSimulationId())
                .orElseThrow(() -> new IllegalStateException("Simulation not found"));
        Map<String, Object> terminalNode = getNodeFromTree(sim.getTreeData(), play.getFinalNodeId());
        return toStudentNode(terminalNode);
    }

    @Transactional(readOnly = true)
    public List<SimulationPlayDTO> getPlayHistory(String simulationId, String studentId) {
        Simulation sim = simulationRepository.findById(simulationId)
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
                    String title = simulationRepository.findById(p.getSimulationId())
                            .map(Simulation::getTitle).orElse("Unknown");
                    return SimulationPlayDTO.fromEntity(p, title);
                })
                .collect(Collectors.toList());
    }

    // ============ TREE HELPERS ============

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNodeFromTree(Map<String, Object> treeData, String nodeId) {
        Map<String, Object> nodes = (Map<String, Object>) treeData.get("nodes");
        if (nodes == null) {
            throw new IllegalStateException("Tree data has no nodes");
        }
        Map<String, Object> node = (Map<String, Object>) nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return node;
    }

    /**
     * Convert a raw tree node to a student-safe DTO.
     * Strips: quality from choices, nextNodeId from choices, mappings from decisionConfig.
     */
    @SuppressWarnings("unchecked")
    private SimulationNodeDTO toStudentNode(Map<String, Object> node) {
        SimulationNodeDTO dto = new SimulationNodeDTO();
        dto.setNodeId((String) node.get("id"));
        dto.setType((String) node.get("type"));
        dto.setNarrative((String) node.get("narrative"));
        dto.setTerminal("TERMINAL".equals(node.get("type")));

        if (!"TERMINAL".equals(node.get("type"))) {
            dto.setDecisionType((String) node.get("decisionType"));

            // Strip mappings from decisionConfig
            Map<String, Object> config = (Map<String, Object>) node.get("decisionConfig");
            if (config != null) {
                Map<String, Object> safeConfig = new LinkedHashMap<>(config);
                safeConfig.remove("mappings"); // Strip mapping rules
                dto.setDecisionConfig(safeConfig);
            }

            // Strip quality and nextNodeId from choices
            List<Map<String, Object>> rawChoices = (List<Map<String, Object>>) node.get("choices");
            if (rawChoices != null) {
                List<SimulationNodeDTO.ChoiceDTO> safeChoices = rawChoices.stream()
                        .map(c -> {
                            SimulationNodeDTO.ChoiceDTO choice = new SimulationNodeDTO.ChoiceDTO();
                            choice.setId((String) c.get("id"));
                            choice.setText((String) c.get("text"));
                            return choice;
                        })
                        .collect(Collectors.toList());
                dto.setChoices(safeChoices);
            }
        } else {
            // Terminal node -- include debrief and score
            Map<String, Object> debrief = (Map<String, Object>) node.get("debrief");
            if (debrief != null) {
                SimulationNodeDTO.DebriefDTO debriefDto = new SimulationNodeDTO.DebriefDTO();
                debriefDto.setYourPath((String) debrief.get("yourPath"));
                debriefDto.setConceptAtWork((String) debrief.get("conceptAtWork"));
                debriefDto.setTheGap((String) debrief.get("theGap"));
                debriefDto.setPlayAgain((String) debrief.get("playAgain"));
                dto.setDebrief(debriefDto);
            }
            Object scoreObj = node.get("score");
            if (scoreObj instanceof Number) {
                dto.setScore(((Number) scoreObj).intValue());
            }
        }

        return dto;
    }

    /**
     * Compute the maximum depth (longest path from root to any terminal node).
     */
    @SuppressWarnings("unchecked")
    private int computeMaxDepth(Map<String, Object> treeData) {
        Map<String, Object> nodes = (Map<String, Object>) treeData.get("nodes");
        String rootId = (String) treeData.get("rootNodeId");
        if (rootId == null || nodes == null) {
            return 0;
        }
        return findMaxDepth(nodes, rootId, new HashSet<>(), 0);
    }

    @SuppressWarnings("unchecked")
    private int findMaxDepth(Map<String, Object> nodes, String nodeId, Set<String> visited,
            int currentDepth) {
        if (nodeId == null || visited.contains(nodeId) || !nodes.containsKey(nodeId)) {
            return currentDepth;
        }

        visited.add(nodeId);
        Map<String, Object> node = (Map<String, Object>) nodes.get(nodeId);
        String type = (String) node.get("type");

        if ("TERMINAL".equals(type)) {
            visited.remove(nodeId);
            return currentDepth;
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) {
            visited.remove(nodeId);
            return currentDepth;
        }

        int maxDepth = currentDepth;
        for (Map<String, Object> choice : choices) {
            String nextNodeId = (String) choice.get("nextNodeId");
            int childDepth = findMaxDepth(nodes, nextNodeId, visited, currentDepth + 1);
            maxDepth = Math.max(maxDepth, childDepth);
        }

        visited.remove(nodeId);
        return maxDepth;
    }
}
