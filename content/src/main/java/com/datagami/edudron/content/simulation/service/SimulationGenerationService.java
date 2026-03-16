package com.datagami.edudron.content.simulation.service;

import com.datagami.edudron.content.service.FoundryAIService;
import com.datagami.edudron.content.simulation.domain.Simulation;
import com.datagami.edudron.content.simulation.dto.GenerateSimulationRequest;
import com.datagami.edudron.content.simulation.repo.SimulationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SimulationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationGenerationService.class);

    @Autowired
    private FoundryAIService foundryAIService;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Generate a complete simulation tree. Called by AIJobWorker.
     * Updates simulation entity: GENERATING -> REVIEW (or DRAFT on failure).
     */
    public void generateSimulation(String simulationId, GenerateSimulationRequest request) {
        Simulation sim = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));

        try {
            sim.setStatus(Simulation.SimulationStatus.GENERATING);
            simulationRepository.save(sim);

            // Phase 1: Generate golden path
            logger.info("Phase 1: Generating golden path for simulation: {}", simulationId);
            Map<String, Object> treeData = generateGoldenPath(request);

            // Phase 2: Generate failure + recovery branches
            logger.info("Phase 2: Generating branches for simulation: {}", simulationId);
            treeData = generateBranches(treeData, request);

            // Phase 3: Generate debriefs for all terminal nodes
            logger.info("Phase 3: Generating debriefs for simulation: {}", simulationId);
            treeData = generateDebriefs(treeData, request);

            // Phase 4: Validate tree integrity
            logger.info("Phase 4: Validating tree for simulation: {}", simulationId);
            validateTree(treeData);

            sim.setTreeData(treeData);
            sim.setMaxDepth(computeMaxDepth(treeData));
            sim.setStatus(Simulation.SimulationStatus.REVIEW);
            simulationRepository.save(sim);

            logger.info("Simulation generation completed: {}", simulationId);
        } catch (Exception e) {
            logger.error("Simulation generation failed for {}: {}", simulationId, e.getMessage(), e);
            sim.setStatus(Simulation.SimulationStatus.DRAFT);
            Map<String, Object> meta = sim.getMetadataJson() != null ? new HashMap<>(sim.getMetadataJson()) : new HashMap<>();
            meta.put("generationError", e.getMessage());
            sim.setMetadataJson(meta);
            simulationRepository.save(sim);
            throw new RuntimeException("Simulation generation failed", e);
        }
    }

    // ── Phase 1: Golden Path ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateGoldenPath(GenerateSimulationRequest request) {
        int targetDepth = request.getTargetDepth() != null ? request.getTargetDepth() : 15;
        int choicesPerNode = request.getChoicesPerNode() != null ? request.getChoicesPerNode() : 3;

        String systemPrompt = String.format("""
                You are a simulation designer creating an immersive branching decision simulation.

                CONCEPT: %s
                SUBJECT: %s
                AUDIENCE: %s

                Generate the OPTIMAL PATH through the simulation — this is the path a student takes \
                if they make the best decision at every step. Generate exactly %d decision points.

                CORE RULES:
                - The simulation NEVER names the concept being taught
                - Students learn ONLY through consequences of their choices
                - No guidance, hints, or corrections
                - Each decision must feel like a real-world judgment call, NOT a quiz
                - Stakes should escalate naturally
                - Consequences compound

                For each node, specify:
                - A vivid narrative (2-4 paragraphs) that continues the story
                - %d choices ordered from worst (quality=1) to best (quality=%d)
                - A decisionType from: NARRATIVE_CHOICE, BUDGET_ALLOCATION, PRIORITY_RANKING, \
                TRADEOFF_SLIDER, RESOURCE_ASSIGNMENT, TIMELINE_CHOICE
                - Vary decision types — don't use the same type more than 3 times in a row
                - For interactive types, include decisionConfig with appropriate structure \
                (buckets/items/labels)

                The FIRST node must establish a vivid character, setting, and goal.

                Output ONLY valid JSON with this structure:
                {
                  "rootNodeId": "node_001",
                  "nodes": {
                    "node_001": {
                      "id": "node_001",
                      "type": "SCENARIO",
                      "narrative": "...",
                      "decisionType": "NARRATIVE_CHOICE",
                      "decisionConfig": {},
                      "choices": [
                        {"id": "choice_001a", "text": "...", "nextNodeId": "node_002", "quality": 1},
                        {"id": "choice_001b", "text": "...", "nextNodeId": "node_002", "quality": 2},
                        {"id": "choice_001c", "text": "...", "nextNodeId": "node_002", "quality": 3}
                      ]
                    }
                  }
                }

                For the golden path, ALL choices at each node point to the SAME next node \
                (we will branch in Phase 2).
                The final node should be a TERMINAL with type "TERMINAL", a success narrative, \
                and score: 100.""",
                request.getConcept(), request.getSubject(), request.getAudience(),
                targetDepth, choicesPerNode, choicesPerNode);

        String userPrompt = "Generate the golden path simulation now.";
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            userPrompt = "Additional context: " + request.getDescription() + "\n\nGenerate the golden path simulation now.";
        }

        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            String json = extractJsonObject(response);
            Map<String, Object> treeData = objectMapper.readValue(json, new TypeReference<>() {});

            // Basic sanity check
            if (!treeData.containsKey("rootNodeId") || !treeData.containsKey("nodes")) {
                throw new IllegalStateException("Golden path response missing rootNodeId or nodes");
            }

            Map<String, Object> nodes = (Map<String, Object>) treeData.get("nodes");
            logger.info("Golden path generated with {} nodes", nodes.size());
            return treeData;
        } catch (Exception e) {
            throw new RuntimeException("Phase 1 (golden path) failed: " + e.getMessage(), e);
        }
    }

    // ── Phase 2: Failure + Recovery Branches ─────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateBranches(Map<String, Object> treeData, GenerateSimulationRequest request) {
        Map<String, Object> nodes = (Map<String, Object>) treeData.get("nodes");
        String rootNodeId = (String) treeData.get("rootNodeId");

        // Collect golden path node IDs in order
        List<String> goldenPathIds = collectGoldenPath(nodes, rootNodeId);

        // Split into segments if the golden path is long (>7 nodes per call)
        int segmentSize = 7;
        List<List<String>> segments = new ArrayList<>();
        for (int i = 0; i < goldenPathIds.size(); i += segmentSize) {
            segments.add(goldenPathIds.subList(i, Math.min(i + segmentSize, goldenPathIds.size())));
        }

        for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
            List<String> segment = segments.get(segIdx);
            logger.info("Phase 2 segment {}/{}: processing {} golden path nodes", segIdx + 1, segments.size(), segment.size());

            // Build a subset of the tree for this segment
            Map<String, Object> segmentNodes = new HashMap<>();
            for (String nodeId : segment) {
                if (nodes.containsKey(nodeId)) {
                    segmentNodes.put(nodeId, nodes.get(nodeId));
                }
            }

            String segmentJson;
            try {
                segmentJson = objectMapper.writeValueAsString(segmentNodes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize segment nodes", e);
            }

            String systemPrompt = String.format("""
                    You have a simulation golden path segment. Now generate FAILURE and RECOVERY branches.

                    CONCEPT: %s
                    SUBJECT: %s
                    AUDIENCE: %s

                    For EACH node in the segment, update the non-optimal choices:
                    - quality=1 choices: Create 1-2 new TERMINAL nodes (the journey ends badly \
                    within 1-2 more decisions)
                    - quality=2 choices: Create a RECOVERY path of 2-3 new SCENARIO nodes that \
                    reconnects to the golden path ~2 nodes ahead

                    Rules:
                    - Recovery paths should have their own choices (2-3 each)
                    - If a student makes 2 consecutive bad choices on a recovery path, they hit a TERMINAL
                    - Terminal nodes have type "TERMINAL", a failure narrative, but NO debrief yet (Phase 3)
                    - Give terminals a score based on how far the student got (0-90, never 100)
                    - Use varied decisionTypes on recovery paths too
                    - Keep existing node IDs and data unchanged; only add new nodes and update \
                    nextNodeId on non-optimal choices

                    Golden path node IDs in order: %s

                    Current segment nodes:
                    %s

                    Output ONLY valid JSON containing ALL nodes from this segment (updated) plus \
                    all NEW branch nodes. Format: { "nodes": { ... } }""",
                    request.getConcept(), request.getSubject(), request.getAudience(),
                    segment, segmentJson);

            String userPrompt = "Generate the failure and recovery branches now.";

            try {
                String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
                String json = extractJsonObject(response);
                Map<String, Object> branchResult = objectMapper.readValue(json, new TypeReference<>() {});

                Map<String, Object> branchNodes = (Map<String, Object>) branchResult.get("nodes");
                if (branchNodes == null) {
                    // The AI may have returned nodes at the top level
                    branchNodes = branchResult;
                }

                // Merge branch nodes into the main tree
                nodes.putAll(branchNodes);
                logger.info("Segment {}: merged {} nodes (including new branches)", segIdx + 1, branchNodes.size());
            } catch (Exception e) {
                logger.warn("Phase 2 segment {} failed, continuing with remaining segments: {}", segIdx + 1, e.getMessage());
            }
        }

        treeData.put("nodes", nodes);
        return treeData;
    }

    /**
     * Walk the golden path from root, following the highest-quality choice at each step.
     */
    @SuppressWarnings("unchecked")
    private List<String> collectGoldenPath(Map<String, Object> nodes, String rootNodeId) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentId = rootNodeId;

        while (currentId != null && !visited.contains(currentId) && nodes.containsKey(currentId)) {
            visited.add(currentId);
            path.add(currentId);

            Map<String, Object> node = (Map<String, Object>) nodes.get(currentId);
            String type = (String) node.get("type");
            if ("TERMINAL".equals(type)) {
                break;
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
            if (choices == null || choices.isEmpty()) {
                break;
            }

            // Follow the highest quality choice (golden path)
            String nextId = null;
            int maxQuality = -1;
            for (Map<String, Object> choice : choices) {
                int quality = choice.get("quality") instanceof Number
                        ? ((Number) choice.get("quality")).intValue()
                        : 0;
                if (quality > maxQuality) {
                    maxQuality = quality;
                    nextId = (String) choice.get("nextNodeId");
                }
            }
            currentId = nextId;
        }

        return path;
    }

    // ── Phase 3: Debriefs ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateDebriefs(Map<String, Object> treeData, GenerateSimulationRequest request) {
        Map<String, Object> nodes = (Map<String, Object>) treeData.get("nodes");

        // Collect all terminal nodes
        List<Map<String, Object>> terminalEntries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : nodes.entrySet()) {
            Map<String, Object> node = (Map<String, Object>) entry.getValue();
            if ("TERMINAL".equals(node.get("type"))) {
                terminalEntries.add(Map.of(
                        "id", entry.getKey(),
                        "narrative", node.getOrDefault("narrative", ""),
                        "score", node.getOrDefault("score", 0)
                ));
            }
        }

        if (terminalEntries.isEmpty()) {
            logger.warn("No terminal nodes found for debrief generation");
            return treeData;
        }

        String terminalsJson;
        try {
            terminalsJson = objectMapper.writeValueAsString(terminalEntries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize terminal nodes", e);
        }

        String systemPrompt = String.format("""
                Generate debriefs for each TERMINAL node in this simulation tree.

                The simulation teaches the concept of "%s" from %s.

                For each terminal node, generate:
                - yourPath: A neutral, factual 2-3 sentence summary of the key decisions that led here
                - conceptAtWork: Name the concept for the first time. 2-3 sentences showing how it \
                was embedded in the simulation
                - theGap: 1-2 sentences observing the distance between the student's reasoning and \
                where the concept would have taken them. Not a correction — an observation.
                - playAgain: A single engaging line inviting replay. e.g. "The same world. Different \
                choices. What would you do now?"

                Terminal nodes:
                %s

                Output ONLY valid JSON with this structure:
                {
                  "debriefs": {
                    "node_id": {
                      "yourPath": "...",
                      "conceptAtWork": "...",
                      "theGap": "...",
                      "playAgain": "..."
                    }
                  }
                }""",
                request.getConcept(), request.getSubject(), terminalsJson);

        String userPrompt = "Generate the debriefs for all terminal nodes now.";

        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            String json = extractJsonObject(response);
            Map<String, Object> debriefResult = objectMapper.readValue(json, new TypeReference<>() {});

            Map<String, Object> debriefs = (Map<String, Object>) debriefResult.get("debriefs");
            if (debriefs == null) {
                debriefs = debriefResult;
            }

            // Merge debriefs into terminal nodes
            int merged = 0;
            for (Map.Entry<String, Object> debriefEntry : debriefs.entrySet()) {
                String nodeId = debriefEntry.getKey();
                if (nodes.containsKey(nodeId)) {
                    Map<String, Object> node = (Map<String, Object>) nodes.get(nodeId);
                    node.put("debrief", debriefEntry.getValue());
                    merged++;
                }
            }
            logger.info("Merged debriefs for {} terminal nodes", merged);
        } catch (Exception e) {
            logger.warn("Phase 3 (debriefs) failed, tree will lack debriefs: {}", e.getMessage());
        }

        return treeData;
    }

    // ── Phase 4: Validation (pure code, no AI) ──────────────────────────

    @SuppressWarnings("unchecked")
    private void validateTree(Map<String, Object> treeData) {
        Map<String, Object> nodes = (Map<String, Object>) treeData.get("nodes");
        String rootId = (String) treeData.get("rootNodeId");

        if (rootId == null || !nodes.containsKey(rootId)) {
            throw new IllegalStateException("Tree has no valid root node");
        }

        // DFS to verify reachability, check for cycles, and validate structure
        Set<String> visited = new HashSet<>();
        Set<String> currentPath = new HashSet<>();
        List<String> errors = new ArrayList<>();

        dfsValidate(nodes, rootId, visited, currentPath, errors, 0);

        // Verify all nodes are reachable from root
        if (visited.size() < nodes.size()) {
            int unreachable = nodes.size() - visited.size();
            logger.warn("Tree has {} unreachable nodes out of {} total", unreachable, nodes.size());
        }

        // Check that all terminal nodes have a score
        for (Map.Entry<String, Object> entry : nodes.entrySet()) {
            Map<String, Object> node = (Map<String, Object>) entry.getValue();
            if ("TERMINAL".equals(node.get("type"))) {
                if (!node.containsKey("score")) {
                    errors.add("Terminal node " + entry.getKey() + " missing score");
                }
            }
        }

        if (!errors.isEmpty()) {
            logger.warn("Tree validation found {} issues: {}", errors.size(), errors);
            // Store warnings but don't fail — the tree is still usable
            if (treeData.get("_validationWarnings") == null) {
                treeData.put("_validationWarnings", errors);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void dfsValidate(Map<String, Object> nodes, String nodeId,
                             Set<String> visited, Set<String> currentPath,
                             List<String> errors, int depth) {
        if (currentPath.contains(nodeId)) {
            errors.add("Cycle detected at node: " + nodeId);
            return;
        }
        if (visited.contains(nodeId)) {
            return; // Already validated via another path
        }

        visited.add(nodeId);
        currentPath.add(nodeId);

        Map<String, Object> node = (Map<String, Object>) nodes.get(nodeId);
        if (node == null) {
            errors.add("Node referenced but missing: " + nodeId);
            currentPath.remove(nodeId);
            return;
        }

        String type = (String) node.get("type");
        if ("TERMINAL".equals(type)) {
            // Terminal nodes should not have choices leading elsewhere
            currentPath.remove(nodeId);
            return;
        }

        // Non-terminal nodes must have choices
        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) {
            errors.add("Non-terminal node " + nodeId + " has no choices (leaf but not TERMINAL)");
            currentPath.remove(nodeId);
            return;
        }

        // Validate each choice's nextNodeId
        for (Map<String, Object> choice : choices) {
            String nextNodeId = (String) choice.get("nextNodeId");
            if (nextNodeId == null) {
                errors.add("Choice in node " + nodeId + " has no nextNodeId");
                continue;
            }
            if (!nodes.containsKey(nextNodeId)) {
                errors.add("Choice in node " + nodeId + " points to missing node: " + nextNodeId);
                continue;
            }
            dfsValidate(nodes, nextNodeId, visited, currentPath, errors, depth + 1);
        }

        currentPath.remove(nodeId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Compute the maximum depth (longest path from root to any terminal).
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
    private int findMaxDepth(Map<String, Object> nodes, String nodeId, Set<String> visited, int currentDepth) {
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

    /**
     * Extract a JSON object or array from an AI response that may be wrapped in markdown
     * code blocks or contain surrounding text.
     */
    private String extractJsonObject(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Response is null or empty");
        }
        String trimmed = response.trim();

        // Remove markdown code blocks
        if (trimmed.startsWith("```json")) trimmed = trimmed.substring(7);
        else if (trimmed.startsWith("```")) trimmed = trimmed.substring(3);
        if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        trimmed = trimmed.trim();

        // Find the first '{' or '['
        int start = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start == -1) {
            throw new IllegalArgumentException("No JSON found in AI response");
        }

        // Find matching closing bracket
        char openChar = trimmed.charAt(start);
        char closeChar = (openChar == '{') ? '}' : ']';
        int depth = 0;
        int end = -1;
        boolean inString = false;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == openChar) depth++;
                if (c == closeChar) depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end == -1) {
            throw new IllegalArgumentException("Unbalanced JSON in AI response");
        }

        return trimmed.substring(start, end + 1);
    }
}
