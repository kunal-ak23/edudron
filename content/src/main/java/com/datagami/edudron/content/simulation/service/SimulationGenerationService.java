package com.datagami.edudron.content.simulation.service;

import com.datagami.edudron.content.service.FoundryAIService;
import com.datagami.edudron.content.simulation.domain.Simulation;
import com.datagami.edudron.content.simulation.dto.GenerateSimulationRequest;
import com.datagami.edudron.content.simulation.repo.SimulationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Generate a complete simulation using the v2 career tenure pipeline.
     * Called by AIJobWorker. Updates simulation entity: GENERATING -> REVIEW (or DRAFT on failure).
     *
     * Pipeline:
     *   Phase 1: Generate role setup + role progression + metrics definitions (1 AI call)
     *   Phase 2: Generate decisions for each year (1 AI call per year)
     *   Phase 3: Generate year-end reviews with 3 variants per year (1-2 AI calls)
     *   Phase 4: Generate opening narratives with 3 variants per year (1 AI call)
     *   Phase 5: Generate final debrief + fired debrief (1 AI call)
     *   Phase 6: Validate structure integrity (code only)
     */
    public void generateSimulation(String simulationId, GenerateSimulationRequest request) {
        Simulation sim = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));

        try {
            sim.setStatus(Simulation.SimulationStatus.GENERATING);
            simulationRepository.save(sim);

            int targetYears = request.getTargetYears() != null ? request.getTargetYears() : 5;
            int decisionsPerYear = request.getDecisionsPerYear() != null ? request.getDecisionsPerYear() : 6;
            String concept = request.getConcept();
            String subject = request.getSubject();
            String audience = request.getAudience();

            logger.info("Simulation v2 generation started for {}: {} years x {} decisions/year",
                    simulationId, targetYears, decisionsPerYear);

            // ── Phase 1: Setup ──
            logger.info("Phase 1: Generating role setup, progression, and metrics for {}", simulationId);
            Map<String, Object> setupResult = phaseOneSetup(concept, subject, audience, targetYears);
            Map<String, Object> roleSetup = asMap(setupResult.get("roleSetup"));
            List<String> roleProgression = asList(setupResult.get("roleProgression"));
            Map<String, Object> metrics = asMap(setupResult.get("metrics"));
            logger.info("Phase 1 complete for {}: {} roles, {} metrics",
                    simulationId, roleProgression.size(), ((List<?>) metrics.get("definitions")).size());

            // ── Phase 2: Decisions (1 AI call per year) ──
            logger.info("Phase 2: Generating decisions for {} years for {}", targetYears, simulationId);
            List<List<Map<String, Object>>> allYearDecisions = new ArrayList<>();
            for (int year = 1; year <= targetYears; year++) {
                String currentTitle = roleProgression.get(year - 1);
                String previousContext = buildPreviousContext(allYearDecisions, year);
                List<Map<String, Object>> yearDecisions = phaseTwoDecisions(
                        concept, subject, audience, year, targetYears,
                        currentTitle, previousContext, decisionsPerYear);
                allYearDecisions.add(yearDecisions);
                logger.info("Phase 2: Year {} decisions generated ({} decisions) for {}",
                        year, yearDecisions.size(), simulationId);
            }
            logger.info("Phase 2 complete for {}", simulationId);

            // ── Phase 3: Year-End Reviews ──
            logger.info("Phase 3: Generating year-end reviews for {}", simulationId);
            Map<String, Object> yearEndReviews = phaseThreeYearEndReviews(
                    concept, subject, targetYears, metrics);
            logger.info("Phase 3 complete for {}", simulationId);

            // ── Phase 4: Opening Narratives ──
            logger.info("Phase 4: Generating opening narratives for {}", simulationId);
            Map<String, Object> openingNarratives = phaseFourOpeningNarratives(
                    concept, subject, audience, targetYears, roleProgression);
            logger.info("Phase 4 complete for {}", simulationId);

            // ── Phase 5: Debriefs ──
            logger.info("Phase 5: Generating final debriefs for {}", simulationId);
            Map<String, Object> debriefs = phaseFiveDebriefs(concept, subject, audience, targetYears);
            logger.info("Phase 5 complete for {}", simulationId);

            // ── Phase 6: Validation ──
            logger.info("Phase 6: Validating structure for {}", simulationId);
            phaseSixValidate(targetYears, decisionsPerYear, roleProgression,
                    allYearDecisions, yearEndReviews, openingNarratives, debriefs);
            logger.info("Phase 6 complete for {}", simulationId);

            // ── Assembly ──
            List<Map<String, Object>> yearsList = assembleYears(
                    targetYears, allYearDecisions, yearEndReviews, openingNarratives, roleProgression);

            Map<String, Object> simulationData = new LinkedHashMap<>();
            simulationData.put("roleSetup", roleSetup);
            simulationData.put("roleProgression", roleProgression);
            simulationData.put("metrics", metrics);
            simulationData.put("years", yearsList);
            simulationData.put("finalDebrief", debriefs);

            sim.setSimulationData(simulationData);
            sim.setStatus(Simulation.SimulationStatus.REVIEW);
            simulationRepository.save(sim);

            logger.info("Simulation generation completed successfully: {}", simulationId);
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

    // ════════════════════════════════════════════════════════════════════
    // Phase 1 — Setup: role, progression, metrics
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> phaseOneSetup(String concept, String subject, String audience, int targetYears) {
        String systemPrompt = """
                You are designing an immersive career simulation about %s in %s for %s students.

                Generate:
                1. A role setup with: character (vivid, named), world (specific company/industry), goal (concrete stakes)
                2. A role progression of %d titles from entry-level to senior (relevant to the subject)
                3. 4-6 KPI metric definitions with labels, units, and realistic starting values

                The simulation NEVER names the concept. Students learn through decisions and consequences.

                Output JSON:
                {
                  "roleSetup": {
                    "character": "You are [name], a [role] at [company]...",
                    "world": "[company description, industry, stakes]...",
                    "goal": "[what the student is trying to achieve]"
                  },
                  "roleProgression": ["Entry Title", "Mid Title", ..., "Senior Title"],
                  "metrics": {
                    "definitions": [
                      {"id": "revenue", "label": "Revenue", "unit": "$M", "startValue": 45},
                      {"id": "margin", "label": "Operating Margin", "unit": "%%", "startValue": 20}
                    ]
                  }
                }
                """.formatted(concept, subject, audience, targetYears);

        String userPrompt = "Generate the role setup, role progression, and metrics for this simulation. " +
                "Return ONLY the JSON object, no additional text.";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Phase 1 (setup) response", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 2 — Decisions (1 AI call per year)
    // ════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> phaseTwoDecisions(
            String concept, String subject, String audience,
            int year, int targetYears, String currentTitle,
            String previousContext, int decisionsPerYear) {

        String systemPrompt = """
                You are generating Year %d of a %d-year career simulation about %s in %s.

                The student's role: %s
                Context from previous years: %s

                Generate exactly %d decisions. Each decision must:
                - Feel like a real-world judgment call, NOT a quiz
                - Have a vivid narrative (2-3 paragraphs) setting up the choice
                - Have 2-3 choices ordered by quality (1=worst, 2=mid, 3=best)
                - Use varied decisionTypes: NARRATIVE_CHOICE, BUDGET_ALLOCATION, PRIORITY_RANKING, TRADEOFF_SLIDER, RESOURCE_ASSIGNMENT, TIMELINE_CHOICE
                - Don't use the same type more than twice in a row
                - For interactive types (not NARRATIVE_CHOICE), include decisionConfig with:
                  - Appropriate structure (buckets, items, labels, etc.)
                  - mappings array that maps input conditions to choiceIds

                Output a JSON array of decisions:
                [
                  {
                    "id": "y%d_d1",
                    "narrative": "...",
                    "decisionType": "BUDGET_ALLOCATION",
                    "decisionConfig": {
                      "total": 100, "unit": "%%",
                      "buckets": [...],
                      "mappings": [
                        {"condition": "rd >= 40", "choiceId": "y%d_d1_c"},
                        {"condition": "default", "choiceId": "y%d_d1_b"}
                      ]
                    },
                    "choices": [
                      {"id": "y%d_d1_a", "text": "...", "quality": 1},
                      {"id": "y%d_d1_b", "text": "...", "quality": 2},
                      {"id": "y%d_d1_c", "text": "...", "quality": 3}
                    ]
                  }
                ]
                """.formatted(year, targetYears, concept, subject,
                currentTitle, previousContext, decisionsPerYear,
                year, year, year, year, year, year);

        String userPrompt = "Generate the " + decisionsPerYear + " decisions for Year " + year +
                ". Return ONLY the JSON array, no additional text.";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Phase 2 (decisions) response for year " + year, e);
        }
    }

    private String buildPreviousContext(List<List<Map<String, Object>>> allYearDecisions, int currentYear) {
        if (currentYear == 1) {
            return "First year — no previous context.";
        }
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < allYearDecisions.size(); y++) {
            List<Map<String, Object>> yearDecisions = allYearDecisions.get(y);
            sb.append("Year ").append(y + 1).append(": ");
            sb.append(yearDecisions.size()).append(" decisions covering topics: ");
            List<String> topics = new ArrayList<>();
            for (Map<String, Object> d : yearDecisions) {
                String narrative = (String) d.get("narrative");
                if (narrative != null && narrative.length() > 80) {
                    topics.add(narrative.substring(0, 80) + "...");
                } else {
                    topics.add(narrative != null ? narrative : "unknown");
                }
            }
            sb.append(String.join("; ", topics));
            sb.append(". ");
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 3 — Year-End Reviews (1-2 AI calls)
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> phaseThreeYearEndReviews(
            String concept, String subject, int targetYears, Map<String, Object> metrics) {

        List<Map<String, Object>> definitions = (List<Map<String, Object>>) metrics.get("definitions");
        StringBuilder metricIds = new StringBuilder();
        StringBuilder startValues = new StringBuilder();
        for (Map<String, Object> def : definitions) {
            if (metricIds.length() > 0) {
                metricIds.append(", ");
                startValues.append(", ");
            }
            metricIds.append(def.get("id"));
            startValues.append(def.get("id")).append("=").append(def.get("startValue"));
        }

        if (targetYears <= 4) {
            // Single AI call for all years
            return generateYearEndReviewsBatch(concept, subject, 1, targetYears,
                    metricIds.toString(), startValues.toString());
        } else {
            // Split into 2 calls
            int splitAt = targetYears / 2;
            Map<String, Object> firstHalf = generateYearEndReviewsBatch(concept, subject,
                    1, splitAt, metricIds.toString(), startValues.toString());
            Map<String, Object> secondHalf = generateYearEndReviewsBatch(concept, subject,
                    splitAt + 1, targetYears, metricIds.toString(), startValues.toString());

            // Merge
            Map<String, Object> merged = new LinkedHashMap<>(firstHalf);
            merged.putAll(secondHalf);
            return merged;
        }
    }

    private Map<String, Object> generateYearEndReviewsBatch(
            String concept, String subject,
            int fromYear, int toYear,
            String metricIds, String startValues) {

        String yearRange = fromYear == toYear
                ? "year " + fromYear
                : "years " + fromYear + " to " + toYear;

        String systemPrompt = """
                Generate year-end reviews for a career simulation about %s in %s.

                For each year (%s), generate 3 performance variants: STRONG, MID, POOR.
                Each variant needs:
                - metrics: numeric values for the KPIs (building on previous year's values, compounding realistically)
                - feedback from 3 stakeholders: board, customers, investors

                Metric IDs: %s
                Starting values: %s

                Output JSON:
                {
                  "year1": {
                    "STRONG": {
                      "metrics": {"revenue": 52, "margin": 23},
                      "feedback": {
                        "board": "...",
                        "customers": "...",
                        "investors": "..."
                      }
                    },
                    "MID": { ... },
                    "POOR": { ... }
                  }
                }
                """.formatted(concept, subject, yearRange, metricIds, startValues);

        String userPrompt = "Generate the year-end reviews for " + yearRange +
                ". Return ONLY the JSON object, no additional text.";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Phase 3 (year-end reviews) response for " + yearRange, e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 4 — Opening Narratives (1 AI call)
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> phaseFourOpeningNarratives(
            String concept, String subject, String audience,
            int targetYears, List<String> roleProgression) {

        String systemPrompt = """
                You are writing opening narratives for a %d-year career simulation about %s in %s for %s students.

                Role progression: %s

                For each year (1 to %d), generate 3 short opening paragraphs (THRIVING, STEADY, STRUGGLING).
                These set the tone for how the year begins based on the student's past performance.

                Year 1 always uses STEADY as the default. Years 2+ adapt based on prior band.

                Make each opening vivid, immersive, and specific to the role for that year.

                Output JSON:
                {
                  "year1": {"THRIVING": "...", "STEADY": "...", "STRUGGLING": "..."},
                  "year2": { ... }
                }
                """.formatted(targetYears, concept, subject, audience,
                String.join(", ", roleProgression), targetYears);

        String userPrompt = "Generate the opening narratives for all " + targetYears +
                " years. Return ONLY the JSON object, no additional text.";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Phase 4 (opening narratives) response", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 5 — Debriefs (1 AI call)
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> phaseFiveDebriefs(
            String concept, String subject, String audience, int targetYears) {

        String systemPrompt = """
                Generate final debriefs for a %d-year career simulation about %s in %s for %s students.

                Create 4 variants:
                1. THRIVING — student did excellently across their career
                2. STEADY — student was adequate but not outstanding
                3. STRUGGLING — student performed poorly but wasn't fired
                4. FIRED — student was terminated due to consecutive poor performance

                Each debrief has:
                - yourPath: Neutral summary of their journey (2-3 sentences)
                - conceptAtWork: NOW name the concept (%s) for the first time. Explain how it was embedded in every decision they made. This is the reveal moment.
                - theGap: Observation about the distance between their intuitive reasoning and the formal concept
                - playAgain: Invitation to replay with a different strategy

                Output JSON:
                {
                  "THRIVING": {"yourPath": "...", "conceptAtWork": "...", "theGap": "...", "playAgain": "..."},
                  "STEADY": { ... },
                  "STRUGGLING": { ... },
                  "FIRED": { ... }
                }
                """.formatted(targetYears, concept, subject, audience, concept);

        String userPrompt = "Generate the 4 debrief variants. Return ONLY the JSON object, no additional text.";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Phase 5 (debriefs) response", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 6 — Validation (code only)
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void phaseSixValidate(
            int targetYears, int decisionsPerYear,
            List<String> roleProgression,
            List<List<Map<String, Object>>> allYearDecisions,
            Map<String, Object> yearEndReviews,
            Map<String, Object> openingNarratives,
            Map<String, Object> debriefs) {

        List<String> errors = new ArrayList<>();

        // Role progression length matches targetYears
        if (roleProgression.size() != targetYears) {
            errors.add("Role progression has " + roleProgression.size() +
                    " entries but expected " + targetYears);
        }

        // Every year has exactly decisionsPerYear decisions
        if (allYearDecisions.size() != targetYears) {
            errors.add("Expected " + targetYears + " years of decisions but got " + allYearDecisions.size());
        }
        for (int y = 0; y < allYearDecisions.size(); y++) {
            List<Map<String, Object>> decisions = allYearDecisions.get(y);
            if (decisions.size() != decisionsPerYear) {
                errors.add("Year " + (y + 1) + " has " + decisions.size() +
                        " decisions but expected " + decisionsPerYear);
            }
            // Every decision has choices with quality scores
            for (int d = 0; d < decisions.size(); d++) {
                Map<String, Object> decision = decisions.get(d);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) decision.get("choices");
                if (choices == null || choices.isEmpty()) {
                    errors.add("Year " + (y + 1) + " decision " + (d + 1) + " has no choices");
                } else {
                    for (Map<String, Object> choice : choices) {
                        if (choice.get("quality") == null) {
                            errors.add("Year " + (y + 1) + " decision " + (d + 1) +
                                    " choice " + choice.get("id") + " missing quality score");
                        }
                    }
                }
            }
        }

        // Every year has yearEndReview with 3 variants
        for (int y = 1; y <= targetYears; y++) {
            String key = "year" + y;
            Object review = yearEndReviews.get(key);
            if (review == null) {
                errors.add("Missing year-end review for " + key);
            } else if (review instanceof Map) {
                Map<String, Object> reviewMap = (Map<String, Object>) review;
                for (String variant : new String[]{"STRONG", "MID", "POOR"}) {
                    if (!reviewMap.containsKey(variant)) {
                        errors.add(key + " year-end review missing variant: " + variant);
                    }
                }
            }
        }

        // Every year has openingNarrative with 3 variants
        for (int y = 1; y <= targetYears; y++) {
            String key = "year" + y;
            Object narrative = openingNarratives.get(key);
            if (narrative == null) {
                errors.add("Missing opening narrative for " + key);
            } else if (narrative instanceof Map) {
                Map<String, Object> narrativeMap = (Map<String, Object>) narrative;
                for (String variant : new String[]{"THRIVING", "STEADY", "STRUGGLING"}) {
                    if (!narrativeMap.containsKey(variant)) {
                        errors.add(key + " opening narrative missing variant: " + variant);
                    }
                }
            }
        }

        // All 4 debrief variants exist
        for (String variant : new String[]{"THRIVING", "STEADY", "STRUGGLING", "FIRED"}) {
            if (!debriefs.containsKey(variant)) {
                errors.add("Missing debrief variant: " + variant);
            }
        }

        if (!errors.isEmpty()) {
            String errorMsg = "Validation failed with " + errors.size() + " error(s): " +
                    String.join("; ", errors);
            logger.warn("Phase 6 validation issues: {}", errorMsg);
            // Log warnings but don't fail — AI output may have minor structural differences
            // that can be edited by instructors in REVIEW status
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Assembly
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> assembleYears(
            int targetYears,
            List<List<Map<String, Object>>> allYearDecisions,
            Map<String, Object> yearEndReviews,
            Map<String, Object> openingNarratives,
            List<String> roleProgression) {

        List<Map<String, Object>> yearsList = new ArrayList<>();
        for (int y = 1; y <= targetYears; y++) {
            Map<String, Object> yearData = new LinkedHashMap<>();
            yearData.put("year", y);
            yearData.put("title", roleProgression.size() >= y ? roleProgression.get(y - 1) : "Year " + y);
            yearData.put("openingNarrative", openingNarratives.getOrDefault("year" + y, Map.of()));
            yearData.put("decisions", y <= allYearDecisions.size() ? allYearDecisions.get(y - 1) : List.of());
            yearData.put("yearEndReview", yearEndReviews.getOrDefault("year" + y, Map.of()));
            yearsList.add(yearData);
        }
        return yearsList;
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        throw new RuntimeException("Expected a Map but got: " + (obj != null ? obj.getClass().getName() : "null"));
    }

    @SuppressWarnings("unchecked")
    private List<String> asList(Object obj) {
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        throw new RuntimeException("Expected a List but got: " + (obj != null ? obj.getClass().getName() : "null"));
    }

    /**
     * Extract a JSON object or array from an AI response that may be wrapped in markdown
     * code blocks or contain surrounding text.
     */
    String extractJsonObject(String response) {
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
