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
                        currentTitle, previousContext, decisionsPerYear,
                        financialModel);
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

            // ── Phase 5.5: Mentor Enrichment (guided years 1-2, fading year 3) ──
            int guidedYears = Math.min(3, targetYears);
            logger.info("Phase 5.5: Generating mentor guidance for years 1-{} for {}", guidedYears, simulationId);
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            phaseMentorEnrichment(concept, subject, audience, guidedYears, allYearDecisions);
            logger.info("Phase 5.5 complete for {}", simulationId);

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
                    "personality": "Wise, direct, occasionally sarcastic",
                    "retirementYear": 3,
                    "backstory": "A 30-year veteran who has announced retirement in 2 years. Agreed to mentor you before leaving.",
                    "yearTone": {
                      "year1": "Warm, patient, detailed teaching — 'Let me show you how this works...'",
                      "year2": "More urgent, references departure — 'Pay attention, I won't be here next year to explain this...'",
                      "year3": "Already retired but left notes — 'I prepared these notes before I left. Use them wisely.'",
                      "year4plus": "Gone. Brief farewell at start of year 4 only."
                    },
                    "farewellMessage": "A heartfelt 2-3 sentence farewell from the mentor, referencing what they taught and expressing confidence in the student."
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
            String previousContext, int decisionsPerYear,
            Map<String, Object> financialModelParam) {

        // Build department ID list from financialModel so INVESTMENT_PORTFOLIO uses matching IDs
        String deptInstruction = "";
        if (financialModelParam != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fmDepts = (List<Map<String, Object>>) financialModelParam.get("departments");
            if (fmDepts != null && !fmDepts.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("CRITICAL — INVESTMENT_PORTFOLIO departments MUST use these exact IDs from the financial model:\n");
                for (Map<String, Object> d : fmDepts) {
                    sb.append("                  - id: \"").append(d.get("id"))
                      .append("\", label: \"").append(d.get("label")).append("\"\n");
                }
                sb.append("                Do NOT invent new department IDs. The year-end budget calculation matches by these IDs.\n");
                sb.append("                You may adapt the department labels and descriptions to the domain, but the \"id\" field MUST match exactly.");
                deptInstruction = sb.toString();
            }
        }

        String systemPrompt = """
                You are generating Year %d of a %d-year career simulation about %s in %s.

                The student's role: %s
                Context from previous years: %s

                Generate exactly %d decisions. Each decision must:
                - Feel like a real-world judgment call, NOT a quiz
                - Have a vivid narrative (2-3 paragraphs) setting up the choice
                - Have 2-3 choices ordered by quality (1=worst, 2=mid, 3=best)
                - For interactive types (not NARRATIVE_CHOICE), include decisionConfig with appropriate structure and mappings

                CRITICAL - ADAPT ALL LANGUAGE TO THE SUBJECT AND AUDIENCE:
                - Do NOT use generic corporate jargon if the simulation is about farming, healthcare, sports, etc.
                - Use terminology natural to the domain. Examples:
                  * Farming: "Cooperative Meeting" not "Stakeholder Meeting", "Seasonal Budget" not "Investment Portfolio"
                  * Healthcare: "Patient Care Conference" not "Stakeholder Meeting", "Department Funding" not "Budget Allocation"
                  * Sports: "Team Strategy Session" not "Stakeholder Meeting", "Player Draft" not "Hire/Fire"
                - The "displayLabel" field on each decision should be a context-appropriate name for the interaction type

                Available decision types (these are INTERNAL type codes — the student never sees these names):
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
                - "advisorDialog": 1-2 sentences the mentor says to set up this decision.
                  MENTOR STORY ARC — The mentor is retiring and this affects their tone:
                  * Year 1-2: Active mentor, present and engaged. Year 1 is warm/patient ("Let me walk you through this..."),
                    Year 2 is more urgent ("Pay close attention — I won't be here to explain this next year...")
                  * Year 3: Mentor has retired. advisorDialog should feel like written notes left behind
                    ("Before I left, I jotted down some thoughts on situations like this...")
                  * Year 4+: No mentor. Set advisorDialog to null and advisorMood to null.
                - "advisorReaction": {
                    "quality_3": {"mood": "...", "text": "..."},
                    "quality_2": {"mood": "...", "text": "..."},
                    "quality_1": {"mood": "...", "text": "..."}
                  }
                  For Year 3: reactions should feel like imagining what the mentor would say.
                  For Year 4+: set advisorReaction to null (student is on their own).

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
                %s

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
                    "displayLabel": "Seasonal Budget Planning",
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
                currentTitle, previousContext, decisionsPerYear, deptInstruction)
                .replace("{YEAR}", String.valueOf(year));

        String userPrompt = "Generate the " + decisionsPerYear + " decisions for Year " + year +
                ". Return ONLY valid strict JSON array. No comments, no trailing commas, no markdown. Just the raw JSON array starting with [ and ending with ].";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            return parseJsonLenient(json);
        } catch (Exception e) {
            // Log full JSON for debugging
            logger.error("Failed to parse Phase 2 JSON for year {}. JSON length: {}. Full JSON:\n{}",
                    year, json != null ? json.length() : 0, json);
            // Retry: ask AI to fix the JSON
            try {
                logger.info("Retrying Phase 2 parse for year {} with AI JSON repair", year);
                String fixPrompt = "The following JSON array has a syntax error. Fix ONLY the JSON syntax (do not change content). Return ONLY the corrected JSON array:\n\n" + json;
                String fixed = foundryAIService.callOpenAI("You are a JSON repair tool. Fix syntax errors in JSON. Return ONLY valid JSON.", fixPrompt);
                String fixedJson = extractJsonObject(fixed);
                return parseJsonLenient(fixedJson);
            } catch (Exception retryEx) {
                logger.error("AI JSON repair also failed for year {}", year);
            }
            throw new RuntimeException("Failed to parse Phase 2 (decisions) response for year " + year, e);
        }
    }

    private List<Map<String, Object>> parseJsonLenient(String json) throws Exception {
        // Sanitize common LLM JSON issues
        String sanitized = json
                .replaceAll(",\\s*}", "}")           // trailing commas before }
                .replaceAll(",\\s*]", "]")            // trailing commas before ]
                .replaceAll("//[^\n]*", "")           // remove JS-style line comments
                .replaceAll("/\\*.*?\\*/", "");        // remove block comments

        // Use lenient parser
        ObjectMapper lenientMapper = new ObjectMapper();
        lenientMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        lenientMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        lenientMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);

        return lenientMapper.readValue(sanitized, new TypeReference<List<Map<String, Object>>>() {});
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

    /**
     * Backfill mentor guidance on an existing simulation without regenerating anything else.
     * Loads the simulation, extracts decisions for years 1-3, runs Phase 5.5, and saves.
     */
    @SuppressWarnings("unchecked")
    public void regenerateMentorGuidance(String simulationId) {
        Simulation sim = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        Map<String, Object> simData = sim.getSimulationData();
        if (simData == null) {
            throw new IllegalStateException("Simulation has no generated data");
        }

        String concept = sim.getConcept();
        String subject = sim.getSubject();
        String audience = sim.getAudience();
        int targetYears = sim.getTargetYears();
        int guidedYears = Math.min(3, targetYears);

        List<Map<String, Object>> yearsList = (List<Map<String, Object>>) simData.get("years");
        if (yearsList == null || yearsList.isEmpty()) {
            throw new IllegalStateException("Simulation has no years data");
        }

        // Extract decisions per year into the same structure Phase 5.5 expects
        List<List<Map<String, Object>>> allYearDecisions = new ArrayList<>();
        for (Map<String, Object> yearData : yearsList) {
            List<Map<String, Object>> decisions = (List<Map<String, Object>>) yearData.get("decisions");
            allYearDecisions.add(decisions != null ? decisions : List.of());
        }

        logger.info("Regenerating mentor guidance for simulation {} (years 1-{})", simulationId, guidedYears);
        phaseMentorEnrichment(concept, subject, audience, guidedYears, allYearDecisions);

        // The enrichment mutates the decision maps in place, which are references
        // into yearsList → simData, so just save
        sim.setSimulationData(simData);
        simulationRepository.save(sim);
        logger.info("Mentor guidance regenerated for simulation {}", simulationId);
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 5.5 — Mentor Enrichment for guided years
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void phaseMentorEnrichment(
            String concept, String subject, String audience,
            int guidedYears,
            List<List<Map<String, Object>>> allYearDecisions) {

        // Build a compact summary of all decisions for the guided years
        StringBuilder decisionsSummary = new StringBuilder();
        for (int y = 0; y < guidedYears && y < allYearDecisions.size(); y++) {
            List<Map<String, Object>> yearDecisions = allYearDecisions.get(y);
            decisionsSummary.append("Year ").append(y + 1).append(":\n");
            for (Map<String, Object> d : yearDecisions) {
                decisionsSummary.append("  ").append(d.get("id")).append(" (").append(d.get("decisionType")).append("): ");
                decisionsSummary.append(((String) d.getOrDefault("narrative", "")).substring(0,
                    Math.min(120, ((String) d.getOrDefault("narrative", "")).length()))).append("...\n");
                decisionsSummary.append("  Choices (with quality 1=worst, 3=best): ");
                List<Map<String, Object>> choices = (List<Map<String, Object>>) d.get("choices");
                if (choices != null) {
                    for (Map<String, Object> c : choices) {
                        decisionsSummary.append("[").append(c.get("id"))
                            .append(" (quality=").append(c.get("quality")).append("): ")
                            .append(((String) c.getOrDefault("text", "")).substring(0,
                                Math.min(80, ((String) c.getOrDefault("text", "")).length())))
                            .append("...] ");
                    }
                }
                decisionsSummary.append("\n");
                // Include config details for interactive types
                Map<String, Object> config = (Map<String, Object>) d.get("decisionConfig");
                if (config != null) {
                    // Mappings: show which conditions map to which choices (critical for hint accuracy)
                    List<Map<String, Object>> mappings = (List<Map<String, Object>>) config.get("mappings");
                    if (mappings != null) {
                        decisionsSummary.append("  Scoring mappings: ");
                        for (Map<String, Object> m : mappings) {
                            decisionsSummary.append("[condition: ").append(m.get("condition"))
                                .append(" → choiceId: ").append(m.get("choiceId")).append("] ");
                        }
                        decisionsSummary.append("\n");
                    }
                    // Stakeholders
                    List<Map<String, Object>> stakeholders = (List<Map<String, Object>>) config.get("stakeholders");
                    if (stakeholders != null) {
                        decisionsSummary.append("  Stakeholders: ");
                        for (Map<String, Object> s : stakeholders) {
                            decisionsSummary.append("[").append(s.get("id")).append(": ")
                                .append(s.get("name")).append(" - ").append(s.get("role"))
                                .append(", teaser: ").append(s.get("teaser")).append("] ");
                        }
                        decisionsSummary.append("\n");
                    }
                    // Candidates
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) config.get("candidates");
                    if (candidates != null) {
                        decisionsSummary.append("  Candidates: ");
                        for (Map<String, Object> c : candidates) {
                            decisionsSummary.append("[").append(c.get("id")).append(": ")
                                .append(c.get("name")).append(" - ").append(c.get("title")).append("] ");
                        }
                        decisionsSummary.append("\n");
                    }
                }
            }
        }

        String guidanceLevel = guidedYears >= 3
            ? "Years 1-2: FULL guidance (always visible). Year 3: LIGHT guidance (hints available on request — shorter, less specific)."
            : "All years: FULL guidance (always visible).";

        String systemPrompt = """
                You are a course mentor creating learning guidance for a career simulation about %s in %s for %s students.

                The simulation teaches the concept through experiential decisions. For the first %d years,
                students receive mentor guidance to reinforce course learnings.

                %s

                MENTOR RETIREMENT STORY ARC:
                The mentor is a seasoned veteran who has announced retirement in 2 years. This shapes the tone:
                - Year 1: The mentor is actively present. Guidance is warm, patient, and detailed.
                  Voice: "Let me walk you through this..." / "In my 30 years, I've seen this play out..."
                  Include rich real-world examples and thorough explanations.
                - Year 2: The mentor is still present but departure looms. Tone becomes more urgent and focused.
                  Voice: "Pay close attention here — I won't be around next year to explain this..."
                  / "This is one of the most important lessons I can give you before I go..."
                  Guidance is still detailed but emphasizes what matters most.
                - Year 3 (if included): The mentor has retired. Guidance feels like written notes left behind.
                  Voice: "Before I left, I jotted down some thoughts on situations like this..."
                  / "You'll find my notes on this topic in the margin..."
                  Hints are shorter, vaguer — enough to point direction but not hold hands.

                For each decision, generate:
                1. "courseConnection": One sentence linking this decision to a specific course concept or theory
                   (e.g., "This tests your understanding of Porter's Five Forces from Chapter 3.")
                2. "realWorldExample": 1-2 sentences describing a real company/scenario that faced this exact type of decision
                   (e.g., "When Spotify expanded to India in 2019, they faced a similar market entry budget question...")
                   For Year 1-2: detailed and specific. For Year 3: briefer, just a pointer.
                3. "choiceHints": For EACH choice ID, provide:
                   - "hint": What this choice likely leads to (1 sentence, practical consequence)
                   - "risk": "low", "medium", or "high"
                   For Year 1: warm, educational hints. Year 2: direct, no-nonsense. Year 3: vague pointers.
                4. "mentorNote": A 1-sentence in-character note from the mentor, written in their voice for that year.
                   Year 1: encouraging teaching. Year 2: urgent wisdom. Year 3: a brief scribbled note.
                5. "mentorTip": A 1-2 sentence ACTIONABLE tip for what to consider in this specific decision.
                   Not abstract theory — concrete advice like "Look at who has the most relevant domain expertise"
                   or "Consider which allocation balances short-term revenue with long-term growth".
                   This helps the student understand WHAT TO DO, not just the theory behind it.
                6. For STAKEHOLDER_MEETING decisions: also include "stakeholderHints" keyed by stakeholder ID:
                   {"stakeholder_id": {"hint": "Why meeting this person could be valuable", "priority": "high|medium|low"}}
                7. For HIRE_FIRE decisions: also include "candidateHints" keyed by candidate ID:
                   {"candidate_id": {"hint": "What this person brings to the team", "fit": "strong|moderate|weak"}}

                IMPORTANT RULES:
                - Choice hints should educate, not give away the answer. Frame consequences
                  in terms of trade-offs, not "this is right/wrong". Even the best choice has downsides.
                - CRITICAL: The "Scoring mappings" show which conditions map to which choiceIds, and each
                  choice has a quality score (1=worst, 3=best). Your stakeholderHints and candidateHints
                  MUST be consistent with the actual scoring — if selecting stakeholder X leads to the
                  quality=3 choice via the mappings, that stakeholder should have priority "high".
                  Read the mapping conditions carefully to determine the correct priorities.

                Here are the decisions to enrich:

                %s

                Output JSON keyed by decision ID:
                {
                  "y1_d1": {
                    "courseConnection": "...",
                    "realWorldExample": "...",
                    "mentorNote": "...",
                    "mentorTip": "...",
                    "choiceHints": {
                      "y1_d1_a": {"hint": "Conservative approach preserves cash but may slow growth", "risk": "low"},
                      "y1_d1_b": {"hint": "Balanced spend, but market timing is uncertain", "risk": "medium"},
                      "y1_d1_c": {"hint": "Aggressive investment could capture market share or drain reserves", "risk": "high"}
                    },
                    "stakeholderHints": {
                      "stakeholder_1": {"hint": "Has deep domain expertise in the area you need", "priority": "high"}
                    },
                    "candidateHints": {
                      "candidate_1": {"hint": "Strong technical skills but limited leadership experience", "fit": "moderate"}
                    }
                  }
                }
                """.formatted(concept, subject, audience, guidedYears, guidanceLevel, decisionsSummary);

        String userPrompt = "Generate mentor guidance for all decisions in years 1-" + guidedYears +
                ". Return ONLY valid JSON object. No markdown, no comments.";

        String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
        String json = extractJsonObject(response);

        try {
            Map<String, Object> guidance = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            // Inject mentorGuidance into each decision
            for (int y = 0; y < guidedYears && y < allYearDecisions.size(); y++) {
                boolean isLightYear = (y == 2); // Year 3 = index 2
                for (Map<String, Object> decision : allYearDecisions.get(y)) {
                    String decisionId = (String) decision.get("id");
                    if (guidance.containsKey(decisionId)) {
                        Map<String, Object> mg = (Map<String, Object>) guidance.get(decisionId);
                        mg.put("guidanceLevel", isLightYear ? "LIGHT" : "FULL");
                        decision.put("mentorGuidance", mg);
                    }
                }
            }
            logger.info("Mentor enrichment applied to {} decisions", guidance.size());
        } catch (Exception e) {
            logger.warn("Failed to parse mentor enrichment response, skipping: {}", e.getMessage());
            // Non-fatal — simulation works without mentor guidance
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
