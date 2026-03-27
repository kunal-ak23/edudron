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
            Map<String, Object> financialModel = setupResult.get("financialModel") != null
                    ? asMap(setupResult.get("financialModel")) : null;
            Map<String, Object> advisorCharacter = setupResult.get("advisorCharacter") != null
                    ? asMap(setupResult.get("advisorCharacter")) : null;
            logger.info("Phase 1 complete for {}: {} roles, {} metrics, financialModel={}, advisor={}",
                    simulationId, roleProgression.size(), ((List<?>) metrics.get("definitions")).size(),
                    financialModel != null, advisorCharacter != null);

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

                // Throttle between years to avoid Azure OpenAI TPM rate limits
                if (year < targetYears) {
                    try {
                        Thread.sleep(5000); // 5 second delay between year generation calls
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            logger.info("Phase 2 complete for {}", simulationId);

            // Throttle between phases
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // ── Phase 3: Year-End Reviews ──
            logger.info("Phase 3: Generating year-end reviews for {}", simulationId);
            Map<String, Object> yearEndReviews = phaseThreeYearEndReviews(
                    concept, subject, targetYears, metrics);
            logger.info("Phase 3 complete for {}", simulationId);

            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // ── Phase 4: Opening Narratives ──
            logger.info("Phase 4: Generating opening narratives for {}", simulationId);
            Map<String, Object> openingNarratives = phaseFourOpeningNarratives(
                    concept, subject, audience, targetYears, roleProgression);
            logger.info("Phase 4 complete for {}", simulationId);

            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

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
            if (financialModel != null) {
                simulationData.put("financialModel", financialModel);
            }
            if (advisorCharacter != null) {
                simulationData.put("advisorCharacter", advisorCharacter);
            }
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
                  },
                  "financialModel": {
                    "startingBudget": 5000000,
                    "currency": "$",
                    "departments": [
                      {"id": "rd", "label": "R&D", "returnFormula": "MODERATE_LONG", "baseRoi": 0.15, "volatility": 0.08, "lagYears": 1},
                      {"id": "marketing", "label": "Marketing", "returnFormula": "HIGH_SHORT", "baseRoi": 0.22, "volatility": 0.15, "lagYears": 0},
                      {"id": "operations", "label": "Operations", "returnFormula": "STABLE", "baseRoi": 0.08, "volatility": 0.02, "lagYears": 0},
                      {"id": "training", "label": "Training", "returnFormula": "COMPOUND", "baseRoi": 0.05, "volatility": 0.03, "lagYears": 2}
                    ],
                    "performanceMultipliers": {"THRIVING": 1.2, "STEADY": 1.0, "STRUGGLING": 0.7}
                  },
                  "advisorCharacter": {
                    "name": "Dr. Rivera",
                    "role": "Your mentor and former division head",
                    "characterId": "mentor_female_1",
                    "personality": "Wise, direct, occasionally sarcastic"
                  }
                }

                Available characters (pick the best fit for the advisor's characterId):
                - "mentor_female_1": Professional woman, 40s, warm and authoritative
                - "exec_male_1": Corporate executive, 50s, serious and decisive
                - "tech_young_1": Young tech professional, 20s-30s, enthusiastic
                - "medical_female_1": Doctor/scientist, 30s-40s, analytical
                """.formatted(concept, subject, audience, targetYears);

        String userPrompt = "Generate the role setup, role progression, metrics, financial model, and advisor character for this simulation. " +
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
                - For interactive types (not NARRATIVE_CHOICE), include decisionConfig with appropriate structure and mappings

                Available decision types (use ALL of them across the simulation):
                NARRATIVE_CHOICE, BUDGET_ALLOCATION, PRIORITY_RANKING, TRADEOFF_SLIDER,
                RESOURCE_ASSIGNMENT, TIMELINE_CHOICE, COMPOUND, NEGOTIATION,
                DASHBOARD_ANALYSIS, HIRE_FIRE, CRISIS_RESPONSE, INVESTMENT_PORTFOLIO,
                STAKEHOLDER_MEETING

                SEQUENCING RULES (enforce strictly):
                1. Never repeat the same decision type in consecutive positions
                2. Each year MUST include at least:
                   - 1 INVESTMENT_PORTFOLIO (budget allocation with dollar amounts)
                   - 1 DASHBOARD_ANALYSIS (show metrics/charts then decide)
                   - 1 from {NEGOTIATION, CRISIS_RESPONSE, HIRE_FIRE, STAKEHOLDER_MEETING}
                3. CRISIS_RESPONSE: max 1 per year
                4. NARRATIVE_CHOICE: max 2 per year
                5. STAKEHOLDER_MEETING should appear BEFORE a related major decision
                6. Year N and Year N+1 must NOT start with the same type
                7. Vary the set of interactive types used each year

                For each decision, also generate:
                - "conceptKeywords": array of 2-3 business/management terms used in this decision that an undergraduate might not know.
                  Each keyword is {"term": "...", "explanation": "1-2 sentence plain English explanation suitable for an undergraduate student"}
                  Example: [{"term": "Cross-functional Team", "explanation": "A team with members from different departments working toward a common goal."}]
                - "advisorMood": one of ["neutral", "concerned", "excited", "disappointed", "proud"]
                - "advisorDialog": 1-2 sentences the mentor says to set up this decision
                - "advisorReaction": {
                    "quality_3": {"mood": "...", "text": "..."},
                    "quality_2": {"mood": "...", "text": "..."},
                    "quality_1": {"mood": "...", "text": "..."}
                  }

                DECISION CONFIG SCHEMAS BY TYPE:

                NEGOTIATION: {"rounds": 3, "unit": "$", "npcName": "...", "npcCharacterId": "exec_male_1", "initialOffer": N,
                  "npcResponses": [{"round": 1, "playerRange": {"min": N, "max": N}, "response": "...", "npcCounterOffer": N}],
                  "outcomes": [{"condition": "...", "choiceId": "..."}]}

                DASHBOARD_ANALYSIS: {"metrics": [{"label": "...", "value": "...", "trend": "up|down|flat", "change": "..."}],
                  "chartData": {"type": "line|bar", "title": "...", "labels": [...], "datasets": [{"label": "...", "data": [...], "color": "#hex"}]},
                  "question": "..."}

                HIRE_FIRE: {"action": "hire|fire", "budgetLimit": N,
                  "candidates": [{"id": "...", "name": "...", "title": "...", "characterId": "tech_young_1", "stats": {...}, "salary": N, "bio": "...", "strengths": [...], "weaknesses": [...]}],
                  "mappings": [...]}

                CRISIS_RESPONSE: {"timeLimit": 30, "crisisTitle": "...", "crisisDescription": "...",
                  "severity": "critical|high|medium", "defaultOnExpiry": "choiceId"}

                INVESTMENT_PORTFOLIO: {"totalBudget": N, "currency": "$",
                  "departments": [{"id": "...", "label": "...", "description": "...", "minAllocation": N, "maxAllocation": N, "projectedRoiRange": "..."}],
                  "mappings": [...]}

                STAKEHOLDER_MEETING: {"maxSelections": 2, "instruction": "...",
                  "stakeholders": [{"id": "...", "name": "...", "role": "...", "characterId": "medical_female_1", "teaser": "...", "revealedInfo": "..."}],
                  "mappings": [...]}

                For NPC characterIds (npcCharacterId, candidate characterId, stakeholder characterId),
                pick from: "mentor_female_1", "exec_male_1", "tech_young_1", "medical_female_1".
                Try to use different characters for different NPCs within the same year.

                Output a JSON array of decisions. Use "yYEAR_dN" as the ID pattern (e.g., "y{YEAR}_d1", "y{YEAR}_d2"):
                [
                  {
                    "id": "y{YEAR}_d1",
                    "narrative": "...",
                    "decisionType": "INVESTMENT_PORTFOLIO",
                    "conceptKeywords": [{"term": "Capital Allocation", "explanation": "The process of distributing financial resources across departments."}],
                    "advisorMood": "neutral",
                    "advisorDialog": "...",
                    "advisorReaction": {"quality_3": {"mood": "excited", "text": "..."}, "quality_2": {"mood": "neutral", "text": "..."}, "quality_1": {"mood": "disappointed", "text": "..."}},
                    "decisionConfig": { ... },
                    "choices": [
                      {"id": "y{YEAR}_d1_a", "text": "...", "quality": 1},
                      {"id": "y{YEAR}_d1_b", "text": "...", "quality": 2},
                      {"id": "y{YEAR}_d1_c", "text": "...", "quality": 3}
                    ]
                  }
                ]
                """.formatted(year, targetYears, concept, subject,
                currentTitle, previousContext, decisionsPerYear)
                .replace("{YEAR}", String.valueOf(year));

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
