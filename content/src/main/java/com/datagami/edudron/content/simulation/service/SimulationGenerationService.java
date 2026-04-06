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

            // ── Phase 2: Decisions (1 AI call per year, with validation + retry) ──
            logger.info("Phase 2: Generating decisions for {} years for {}", targetYears, simulationId);
            List<List<Map<String, Object>>> allYearDecisions = new ArrayList<>();
            for (int year = 1; year <= targetYears; year++) {
                String currentTitle = roleProgression.get(year - 1);
                String previousContext = buildPreviousContext(allYearDecisions, year, targetYears);

                List<Map<String, Object>> yearDecisions = null;
                int maxRetries = 2;
                for (int attempt = 0; attempt <= maxRetries; attempt++) {
                    yearDecisions = phaseTwoDecisions(
                            concept, subject, audience, year, targetYears,
                            currentTitle, previousContext, decisionsPerYear,
                            financialModel);

                    // Validate: correct count and each decision has choices
                    List<String> issues = validateYearDecisions(yearDecisions, year, decisionsPerYear);
                    if (issues.isEmpty()) {
                        logger.info("Phase 2: Year {} validated OK ({} decisions) for {}",
                                year, yearDecisions.size(), simulationId);
                        break;
                    }

                    if (attempt < maxRetries) {
                        logger.warn("Phase 2: Year {} validation failed (attempt {}/{}): {}. Retrying...",
                                year, attempt + 1, maxRetries + 1, issues);
                        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    } else {
                        logger.warn("Phase 2: Year {} still has issues after {} attempts: {}. Using best result.",
                                year, maxRetries + 1, issues);
                    }
                }

                allYearDecisions.add(yearDecisions);

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

        // Mentor guidance instruction — only for years 1-3
        String mentorGuidanceInstruction = "";
        if (year <= 3) {
            String guidanceLevel = year <= 2 ? "FULL" : "LIGHT";
            mentorGuidanceInstruction = """
                    MENTOR GUIDANCE (Years 1-3 only — do NOT include for year 4+):
                    For each decision in this year, ALSO generate a "mentorGuidance" object alongside the other decision fields:
                    {
                      "courseConnection": "One sentence linking this decision to a specific course concept/theory from [subject]",
                      "realWorldExample": "1-2 sentences about a real company that faced this exact type of decision. Name the company, year, and outcome.",
                      "mentorNote": "1-sentence in-character note from the mentor. Year 1: warm/patient. Year 2: urgent. Year 3: written note left behind.",
                      "mentorTip": "1-2 sentence ACTIONABLE tip. Not theory — specific to THIS decision's config. Reference actual values, departments, or candidates from the decisionConfig.",
                      "choiceHints": { for EACH choice ID: {"hint": "what this choice likely leads to (1 sentence)", "risk": "low|medium|high"} },
                      "stakeholderHints": (STAKEHOLDER_MEETING only) { for each stakeholder ID: {"hint": "why meeting this person is valuable", "priority": "high|medium|low"} },
                      "candidateHints": (HIRE_FIRE only) { for each candidate ID: {"hint": "what this person brings to the team", "fit": "strong|moderate|weak"} },
                      "guidanceLevel": "%s"
                    }
                    For years 4+: do NOT include mentorGuidance on any decision.
                    """.formatted(guidanceLevel);
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

                INCREMENTAL DIFFICULTY — THIS IS CRITICAL:
                Difficulty MUST scale gradually across years. Do NOT make early years hard.
                - Year 1 (INTRODUCTORY): Simple, clear-cut decisions. One choice is obviously better. Scenarios are
                  everyday operational situations (e.g., choosing a vendor, scheduling a team meeting, basic budget split).
                  Stakes are low and localized. Wrong answers have minor, recoverable consequences.
                - Year 2 (FOUNDATIONAL): Slightly more nuance. Choices have clearer trade-offs but the best path is still
                  identifiable with basic reasoning. Introduce one or two competing priorities.
                - Year 3 (INTERMEDIATE): Real trade-offs with no obviously right answer. Multiple stakeholders with
                  conflicting needs. Medium stakes — decisions affect a department or product line.
                - Year 4 (ADVANCED): Complex, multi-layered decisions. Incomplete information, time pressure, political
                  dynamics. Choices have long-term consequences that aren't immediately visible. High stakes.
                - Year 5+ (EXPERT): Ambiguous, high-stakes strategic decisions. Every option has significant downsides.
                  Requires weighing ethical considerations, long-term vision, and organizational survival.
                If targetYears < 5, compress the progression proportionally:
                  * 2 years: INTRODUCTORY → EXPERT
                  * 3 years: INTRODUCTORY → INTERMEDIATE → EXPERT
                  * 4 years: INTRODUCTORY → FOUNDATIONAL → ADVANCED → EXPERT

                REAL-WORLD SCENARIO GROUNDING — CRITICAL:
                Every scenario MUST feel like something that actually happens in real workplaces. Follow these rules:
                - Base scenarios on common, recognizable workplace situations: budget reviews, hiring decisions, client
                  complaints, product delays, team conflicts, vendor negotiations, regulatory changes, market shifts.
                - Use specific, plausible details: real-sounding company names, realistic dollar amounts, believable timelines,
                  named team members with relatable personalities.
                - AVOID scenarios that feel contrived, overly dramatic, or artificially constructed for teaching purposes.
                  Bad example: "A mysterious competitor suddenly offers your entire team double salary on the same day."
                  Good example: "Two of your senior engineers have received offers from a competitor. They haven't decided yet
                  but want to discuss their career growth with you."
                - Draw from common industry patterns: seasonal demand changes, technology migrations, team restructuring,
                  budget cuts, expansion into new markets, customer feedback driving product changes.
                - Narratives should read like a Monday morning briefing, not a movie plot twist.

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
                  ** "response" must be 2-3 sentences showing the NPC's personality, reasoning, and body language.
                  ** Each outcome "condition" should clearly explain what happens and why (2 sentences min).

                DASHBOARD_ANALYSIS: {"metrics": [{"label": "...", "value": "...", "trend": "up|down|flat", "change": "..."}],
                  "chartData": {"type": "line|bar", "title": "...", "labels": [...], "datasets": [{"label": "...", "data": [...], "color": "#hex"}]},
                  "question": "..."}
                  ** "question" must be 2-3 sentences explaining what the student should analyze and why it matters.
                  ** "change" should include context (e.g., "+12%% vs last quarter, driven by new client onboarding").

                HIRE_FIRE: {"action": "hire|fire", "budgetLimit": N,
                  "candidates": [{"id": "...", "name": "...", "title": "...", "characterId": "tech_young_1", "stats": {...}, "salary": N, "bio": "...", "strengths": [...], "weaknesses": [...]}],
                  "mappings": [...]}
                  ** "bio" MUST be 3-4 sentences covering: background, relevant experience, personality/work style, and
                     what they would bring to the team (or why they're being considered for removal).
                  ** "strengths" and "weaknesses" should each have 2-3 items, each being a specific sentence
                     (e.g., "Led a 15-person team through a product relaunch that increased retention by 22%%")
                     NOT vague one-worders like "leadership" or "communication".

                CRISIS_RESPONSE: {"timeLimit": 30, "crisisTitle": "...", "crisisDescription": "...",
                  "severity": "critical|high|medium", "defaultOnExpiry": "choiceId"}
                  ** "crisisDescription" must be 3-4 sentences: what happened, who is affected, what's at stake,
                     and what information is available so far. Give enough context for the student to reason about options.

                INVESTMENT_PORTFOLIO: {"totalBudget": N, "currency": "$",
                  "departments": [{"id": "...", "label": "...", "description": "...", "minAllocation": N, "maxAllocation": N, "projectedRoiRange": "..."}],
                  "mappings": [...]}
                  ** Each department "description" must be 2-3 sentences explaining: what this department does,
                     its current state (understaffed? thriving? needs modernization?), and why investing here
                     matters. NOT a generic one-liner like "Handles marketing".
                %s

                STAKEHOLDER_MEETING: {"maxSelections": 2, "instruction": "...",
                  "stakeholders": [{"id": "...", "name": "...", "role": "...", "characterId": "medical_female_1", "teaser": "...", "revealedInfo": "..."}],
                  "mappings": [...]}
                  ** "instruction" must be 2-3 sentences explaining the context: why the student is meeting people,
                     what they're trying to learn, and what constraints they face (time, political dynamics, etc.).
                  ** "teaser" MUST be 2-3 sentences giving the student enough context to decide if this person is
                     worth meeting. Include: their perspective/agenda, what kind of information they might have,
                     and any known biases or allegiances. NOT a vague one-liner like "Has insights on operations."
                     Good example: "Priya has been vocal about the need for better data infrastructure. She recently
                     presented a cost analysis showing the ops team spends 30%% of their time on manual reporting.
                     She may push for tech investment over hiring."
                  ** "revealedInfo" must be 3-4 sentences of substantive information that the student gains from
                     the meeting — specific data points, insider perspectives, or warnings that genuinely help
                     inform the decision at hand.

                RICH CONTEXT RULE (applies to ALL decision types):
                Every description, teaser, bio, narrative, and instruction field must give the student enough
                context to reason about the decision. One-liners are NEVER acceptable for any description field.
                The student is making consequential decisions — they need details, not summaries.

                CHOICE CONSEQUENCE FIELDS — REQUIRED ON EVERY CHOICE:
                Each choice MUST also include:
                - "consequenceTag": a unique camelCase label describing this choice's effect (e.g., "aggressiveRdSpend", "conservativeHiring"). Must be unique across ALL choices in this year's simulation.
                - "impactDescription": 1-2 sentences describing what happens AFTER the player picks this choice. Written in second person ("Your R&D team delivers..."). Vivid and specific to this scenario, not generic.
                - "metricImpacts": array of {metric: string (matching a KPI id from Phase 1), direction: "up"|"down"|"neutral", magnitude: "strong"|"moderate"|"slight"}. At least 2 metrics impacted per choice.

                For NPC characterIds (npcCharacterId, candidate characterId, stakeholder characterId),
                pick from: "mentor_female_1", "exec_male_1", "tech_young_1", "medical_female_1".
                Try to use different characters for different NPCs within the same year.

                %s

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
                      {"id": "y{YEAR}_d1_a", "text": "2-3 sentence choice describing what the student would do and its immediate implications", "quality": 1, "consequenceTag": "exampleConservativeApproach", "impactDescription": "Your team executes cautiously, preserving cash but missing the market window.", "metricImpacts": [{"metric": "revenue", "direction": "down", "magnitude": "slight"}, {"metric": "margin", "direction": "up", "magnitude": "moderate"}]},
                      {"id": "y{YEAR}_d1_b", "text": "2-3 sentence choice — NOT one-liners. Each choice must explain the action AND hint at trade-offs", "quality": 2, "consequenceTag": "exampleBalancedApproach", "impactDescription": "Your team delivers on time, but growth is slower than the board hoped.", "metricImpacts": [{"metric": "revenue", "direction": "up", "magnitude": "slight"}, {"metric": "margin", "direction": "neutral", "magnitude": "slight"}]},
                      {"id": "y{YEAR}_d1_c", "text": "2-3 sentence choice with enough detail that the student understands what they're committing to", "quality": 3, "consequenceTag": "exampleAggressiveInvestment", "impactDescription": "Your bold investment pays off — the product launches ahead of schedule and captures key market share.", "metricImpacts": [{"metric": "revenue", "direction": "up", "magnitude": "strong"}, {"metric": "margin", "direction": "down", "magnitude": "moderate"}]}
                    ]
                  }
                ]
                """.formatted(year, targetYears, concept, subject,
                currentTitle, previousContext, decisionsPerYear, deptInstruction, mentorGuidanceInstruction)
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

    private String buildPreviousContext(List<List<Map<String, Object>>> allYearDecisions, int currentYear, int targetYears) {
        String[] difficultyLabels = {"INTRODUCTORY", "FOUNDATIONAL", "INTERMEDIATE", "ADVANCED", "EXPERT"};
        // Compute difficulty label: spread 5 levels proportionally across targetYears
        int diffIdx;
        if (targetYears <= 1) {
            diffIdx = 0;
        } else if (targetYears >= 5) {
            diffIdx = Math.min(currentYear - 1, 4);
        } else {
            // Compress: e.g., 3-year sim → year1=0(INTRO), year2=2(INTERMEDIATE), year3=4(EXPERT)
            diffIdx = Math.min(Math.round((currentYear - 1) * 4.0f / (targetYears - 1)), 4);
        }
        String currentDifficulty = difficultyLabels[diffIdx];

        if (currentYear == 1) {
            return "First year — no previous context. This is the " + currentDifficulty + " year: keep decisions simple and approachable.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("REMINDER: This is Year ").append(currentYear)
          .append(" (").append(currentDifficulty).append(" difficulty). ")
          .append("Decisions must be noticeably harder than Year ").append(currentYear - 1)
          .append(" but appropriate for the ").append(currentDifficulty).append(" level.\n");

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
     * Validate a year's decisions: correct count, all have choices with quality scores,
     * and interactive types have the required config fields.
     */
    @SuppressWarnings("unchecked")
    private List<String> validateYearDecisions(List<Map<String, Object>> decisions, int year, int expectedCount) {
        List<String> issues = new ArrayList<>();
        if (decisions == null) {
            issues.add("Year " + year + ": decisions is null");
            return issues;
        }
        if (decisions.size() < expectedCount) {
            issues.add("Year " + year + ": only " + decisions.size() + "/" + expectedCount + " decisions generated");
        }
        for (int i = 0; i < decisions.size(); i++) {
            Map<String, Object> d = decisions.get(i);
            String did = (String) d.getOrDefault("id", "d" + i);
            String type = (String) d.get("decisionType");

            // Must have choices
            List<Map<String, Object>> choices = (List<Map<String, Object>>) d.get("choices");
            if (choices == null || choices.isEmpty()) {
                issues.add(did + ": no choices");
                continue;
            }

            // All choices must have quality scores and new consequence fields
            java.util.Set<String> seenConsequenceTags = new java.util.HashSet<>();
            for (Map<String, Object> c : choices) {
                String cid = (String) c.getOrDefault("id", "unknown");
                if (c.get("quality") == null) {
                    issues.add(did + ": choice " + cid + " missing quality");
                }
                if (c.get("consequenceTag") == null || ((String) c.get("consequenceTag")).isBlank()) {
                    issues.add(did + ": choice " + cid + " missing consequenceTag");
                } else {
                    String tag = (String) c.get("consequenceTag");
                    if (!seenConsequenceTags.add(tag)) {
                        issues.add(did + ": duplicate consequenceTag '" + tag + "'");
                    }
                }
                if (c.get("impactDescription") == null || ((String) c.get("impactDescription")).isBlank()) {
                    issues.add(did + ": choice " + cid + " missing impactDescription");
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> metricImpacts = (List<Map<String, Object>>) c.get("metricImpacts");
                if (metricImpacts == null || metricImpacts.isEmpty()) {
                    issues.add(did + ": choice " + cid + " missing metricImpacts");
                }
            }

            // Interactive types must have decisionConfig
            Map<String, Object> config = (Map<String, Object>) d.get("decisionConfig");
            if (type != null && !"NARRATIVE_CHOICE".equals(type) && (config == null || config.isEmpty())) {
                issues.add(did + " (" + type + "): missing decisionConfig");
            }

            // Type-specific config validation
            if (config != null && type != null) {
                switch (type) {
                    case "STAKEHOLDER_MEETING":
                        if (config.get("stakeholders") == null) issues.add(did + ": missing stakeholders");
                        break;
                    case "HIRE_FIRE":
                        if (config.get("candidates") == null) issues.add(did + ": missing candidates");
                        break;
                    case "INVESTMENT_PORTFOLIO":
                        if (config.get("departments") == null) issues.add(did + ": missing departments");
                        break;
                    case "DASHBOARD_ANALYSIS":
                        if (config.get("metrics") == null) issues.add(did + ": missing metrics");
                        break;
                    case "NEGOTIATION":
                        if (config.get("npcResponses") == null) issues.add(did + ": missing npcResponses");
                        break;
                }
            }
        }
        return issues;
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
