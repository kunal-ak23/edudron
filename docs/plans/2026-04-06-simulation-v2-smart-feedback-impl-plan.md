# Simulation V2: Smart Feedback Layer — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restructure the simulation generation pipeline to produce decision-aware feedback, inline mentor guidance, consequence mappings, and enhanced debriefs — then update the frontend to surface this data.

**Architecture:** Two-phase rollout. Phase A modifies the backend generation pipeline (SimulationGenerationService) to produce richer data structures with consequence tags, impact descriptions, cross-decision narratives, and decision breakdowns. Phase B updates the student portal frontend to display score deltas, decision highlights, pattern analysis, and improved mobile UX. All computation is pre-generated — zero runtime AI calls.

**Tech Stack:** Spring Boot (Java 21), Next.js 14, React, Tailwind CSS, Azure OpenAI (GPT), PostgreSQL (JSONB)

**Design Doc:** `docs/plans/2026-04-06-simulation-v2-smart-feedback-design.md`

---

## Phase A: Backend Generation Pipeline

### Task 1: Enhanced Phase 2 — Choice Consequence Fields

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java:274-449` (Phase 2 prompt)

**What to do:**

Update the Phase 2 system prompt (lines 298-449) to require three new fields on every choice object:

1. `consequenceTag` (string) — unique machine-readable label, e.g. `"aggressive_rd_spend"`
2. `impactDescription` (string) — 1-2 sentence human-readable consequence
3. `metricImpacts` (array) — `[{metric, direction, magnitude}]` where direction is "up"/"down"/"neutral" and magnitude is "strong"/"moderate"/"slight"

Add to the choice schema section of the prompt (around line 425-440) after the existing `quality` field:

```
Each choice MUST also include:
- "consequenceTag": a unique camelCase label describing this choice's effect (e.g., "aggressive_rd_spend", "conservative_hiring"). Must be unique across ALL choices in the simulation.
- "impactDescription": 1-2 sentences describing what happens AFTER the player picks this choice. Written in second person ("Your R&D team delivers..."). Vivid and specific to this scenario, not generic.
- "metricImpacts": array of {metric: string (matching a KPI id from Phase 1), direction: "up"|"down"|"neutral", magnitude: "strong"|"moderate"|"slight"}. At least 2 metrics impacted per choice.
```

**Validation:** Update `validateYearDecisions` (line 830) to check:
- Every choice has non-null `consequenceTag`, `impactDescription`, `metricImpacts`
- `consequenceTag` values are unique within the year
- `metricImpacts` has at least 1 entry per choice

**Commit:** `feat(simulation): add consequenceTag, impactDescription, metricImpacts to Phase 2 choices`

---

### Task 2: Inline Mentor Guidance in Phase 2

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java:298-449` (Phase 2 prompt)
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java:942-1107` (Phase 5.5 — to be removed)

**What to do:**

Add mentor guidance generation to the Phase 2 system prompt for years 1-3 (guided years). After the existing mentor story arc instructions (lines 372-386), add:

```
For decisions in years 1-3, ALSO generate a "mentorGuidance" object on each decision:
{
  "courseConnection": "One sentence linking this decision to a specific course concept/theory from [subject]",
  "realWorldExample": "1-2 sentences about a real company that faced this exact type of decision. Be specific — name the company, year, and outcome.",
  "mentorNote": "1-sentence in-character note from the mentor. Year 1: warm/patient. Year 2: urgent. Year 3: written note left behind.",
  "mentorTip": "1-2 sentence ACTIONABLE tip. Not theory — specific to THIS decision's config. Reference actual values, departments, or candidates from the decisionConfig.",
  "choiceHints": { for EACH choice ID: {"hint": "what this choice likely leads to (1 sentence)", "risk": "low|medium|high"} },
  "stakeholderHints": (STAKEHOLDER_MEETING only) { for each stakeholder ID: {"hint": "...", "priority": "high|medium|low"} },
  "candidateHints": (HIRE_FIRE only) { for each candidate ID: {"hint": "...", "fit": "strong|moderate|weak"} },
  "guidanceLevel": "FULL" for years 1-2, "LIGHT" for year 3
}
For years 4+, do NOT include mentorGuidance.
```

Then **remove the entire Phase 5.5 method** `phaseMentorEnrichment` (lines 942-1107) and its call in the orchestration method (around line 155-165).

**Also remove:**
- The `/regenerate-mentor-guidance` endpoint in `SimulationAdminController.java` (find and remove the endpoint + its service call)
- Any references to `phaseMentorEnrichment` in the orchestration flow

**Commit:** `feat(simulation): move mentor guidance inline to Phase 2, remove Phase 5.5`

---

### Task 3: New Phase 3.5 — Consequence Weaving

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java` (add new method + call in orchestrator)

**What to do:**

Add a new method `phaseConsequenceWeaving(...)` that runs AFTER Phase 2 decisions and BEFORE Phase 3 year-end reviews.

**One AI call per year.** The prompt receives:
- All decisions for that year (with choices, consequenceTags, impactDescriptions)
- The metrics/KPIs from Phase 1
- The role setup context

System prompt structure:

```
You are creating consequence mappings for Year {YEAR} of a career simulation about [concept] in [subject].

Here are the {DECISIONS_PER_YEAR} decisions for this year:
{DECISIONS_JSON}

For each decision, generate a "choiceQualities" object with quality_3, quality_2, quality_1 variants. Each variant has:
- "boardImpact": 1 sentence — how the board reacts to this quality of decision
- "teamImpact": 1 sentence — how the team is affected
- "customerImpact": 1 sentence — how customers are affected

Then generate "crossDecisionNarratives" showing how decisions INTERACT:
- "quality_high": 1-2 sentences for when most decisions were quality 3
- "quality_mixed": 1-2 sentences for mixed quality
- "quality_low": 1-2 sentences for mostly quality 1

Finally, generate "warningSignals":
- "STRUGGLING": 1 sentence warning that appears if the student is performing poorly. Should feel like office gossip or a subtle hint, not a game notification.

Output format:
{
  "decisionConsequenceMap": [ { "decisionId": "...", "choiceQualities": { "quality_3": {...}, "quality_2": {...}, "quality_1": {...} } } ],
  "crossDecisionNarratives": { "quality_high": "...", "quality_mixed": "...", "quality_low": "..." },
  "warningSignals": { "STRUGGLING": "..." }
}
```

Store the output in `simulationData` under key `consequenceWeaving.year{N}`.

Add 3-second throttle between year calls (consistent with existing pattern).

Update the orchestration method to call this between Phase 2 and Phase 3. Adjust phase numbering in log messages.

**Commit:** `feat(simulation): add Phase 3.5 consequence weaving generation`

---

### Task 4: Decision-Aware Year-End Reviews (Phase 3 Enhancement)

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java:561-643` (Phase 3)

**What to do:**

Update the Phase 3 system prompt to receive consequence weaving output as additional context. The prompt currently generates generic STRONG/MID/POOR variants.

Add to the prompt:

```
CONSEQUENCE CONTEXT for this year:
{CONSEQUENCE_WEAVING_JSON}

Use this context to make feedback SPECIFIC to individual decisions. Your feedback MUST:
1. Reference at least 2 specific decisions by their displayLabel in the "feedback" fields
2. Include a "decisionHighlights" array — one entry per decision showing its impact:
   [{"decisionId": "y1_d1", "label": "displayLabel", "impact": "positive|negative|neutral", "summary": "1 sentence"}]
3. Include a "crossDecisionInsight" string — 1-2 sentences showing how decisions interacted (compound effects)

For the POOR variant, also include a warning signal from the consequence weaving data.
```

The output structure for each year/variant expands to include `decisionHighlights` and `crossDecisionInsight` alongside the existing `metrics` and `feedback` fields.

**Commit:** `feat(simulation): enhance Phase 3 year-end reviews with decision references`

---

### Task 5: Enhanced Debriefs (Phase 5 Enhancement)

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java:690-727` (Phase 5)

**What to do:**

Update the Phase 5 debrief prompt to receive all decisions with consequence tags and generate richer output.

Add to the prompt:

```
ALL DECISIONS WITH CONSEQUENCES:
{ALL_DECISIONS_JSON — include decisionId, displayLabel, choices with consequenceTag and impactDescription}

For each debrief variant (THRIVING, STEADY, STRUGGLING, FIRED), generate:

1. Existing fields: yourPath, conceptAtWork, theGap, playAgain
   - "theGap" MUST reference 2-3 specific decisions by displayLabel where the student's likely behavior diverged from the formal concept

2. NEW "decisionBreakdown" array — one entry per decision:
   {
     "decisionId": "y1_d2",
     "label": "displayLabel from decision",
     "quality": 3,  // the quality for this variant's assumed performance
     "whatHappened": "1-2 sentences — the consequence of the assumed choice",
     "conceptLesson": "1 sentence — how this decision connects to [CONCEPT]. Use course-specific terminology."
   }
   For THRIVING: assume mostly quality 3 with 1-2 quality 2
   For STEADY: assume mix of quality 2 and 3
   For STRUGGLING: assume mostly quality 1-2
   For FIRED: assume mostly quality 1

3. NEW "patternAnalysis" string — 2-3 sentences identifying the decision-making pattern:
   - Name the pattern using course terminology (e.g., "success trap", "risk aversion cascade")
   - Show how it evolved across years
   - Connect to a specific course concept
```

**Commit:** `feat(simulation): enhance Phase 5 debriefs with decision breakdown and pattern analysis`

---

### Task 6: Expanded Validation (Phase 6 Enhancement)

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java:734-823` (Phase 6)

**What to do:**

Add validation checks for all new fields:

```java
// Choice-level validations
// - Every choice has non-null consequenceTag
// - Every choice has non-empty impactDescription
// - Every choice has non-empty metricImpacts array
// - consequenceTag values unique within each year

// Mentor guidance validations (years 1-3 only)
// - Each decision in years 1-3 has mentorGuidance object
// - mentorGuidance has courseConnection, realWorldExample, mentorNote, mentorTip
// - mentorGuidance has choiceHints with entry for each choice ID

// Consequence weaving validations
// - consequenceWeaving exists for each year
// - Each year has decisionConsequenceMap covering all decision IDs
// - Each year has crossDecisionNarratives with quality_high, quality_mixed, quality_low
// - Each year has warningSignals.STRUGGLING

// Year-end review validations
// - Each variant has decisionHighlights (non-empty array)
// - Each variant has crossDecisionInsight (non-empty string)

// Debrief validations
// - Each variant has decisionBreakdown (non-empty array)
// - Each variant has patternAnalysis (non-empty string)
```

Log issues as warnings (consistent with existing behavior — doesn't fail generation).

**Commit:** `feat(simulation): expand Phase 6 validation for new data fields`

---

### Task 7: Update Backend DTOs and State Logic

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationStateDTO.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/dto/YearEndReviewDTO.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/dto/DebriefDTO.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java:768-878` (submitDecision)
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java:889+` (advanceYear)

**What to do:**

**SimulationStateDTO** — add fields to the decide response:
```java
private Integer scoreDelta;          // points earned for this decision (10, 5, or 0)
private String impactDescription;    // consequence narrative from chosen option
private List<MetricImpactDTO> metricImpacts; // [{metric, direction, magnitude}]
```

Add inner class or separate DTO:
```java
public static class MetricImpactDTO {
    private String metric;
    private String direction; // "up", "down", "neutral"
    private String magnitude; // "strong", "moderate", "slight"
}
```

**YearEndReviewDTO** — add fields:
```java
private List<DecisionHighlightDTO> decisionHighlights;
private String crossDecisionInsight;
private String warningSignal; // nullable, only for STRUGGLING band

public static class DecisionHighlightDTO {
    private String decisionId;
    private String label;
    private String impact; // "positive", "negative", "neutral"
    private String summary;
}
```

**DebriefDTO** — add fields:
```java
private List<DecisionBreakdownDTO> decisionBreakdown;
private String patternAnalysis;

public static class DecisionBreakdownDTO {
    private String decisionId;
    private String label;
    private Integer quality;
    private String whatHappened;
    private String conceptLesson;
}
```

**SimulationService.submitDecision** (line 768) — after calculating quality and points:
- Read the chosen choice's `impactDescription` and `metricImpacts` from simulationData
- Set `scoreDelta`, `impactDescription`, `metricImpacts` on the returned SimulationStateDTO

**SimulationService.advanceYear** (line 889) — when building year-end review:
- Read `decisionHighlights` and `crossDecisionInsight` from the selected variant (STRONG/MID/POOR)
- Read `warningSignal` from consequence weaving if band is STRUGGLING
- Select `crossDecisionNarratives` variant based on actual quality mix:
  - If average quality >= 2.5 → quality_high
  - If average quality >= 1.5 → quality_mixed
  - Else → quality_low
- Set all new fields on YearEndReviewDTO

**SimulationService.getCurrentState** (line 563) — when building debrief:
- Read `decisionBreakdown` and `patternAnalysis` from the selected debrief variant
- Set on DebriefDTO

**Commit:** `feat(simulation): update DTOs and state logic for new feedback fields`

---

## Phase B: Frontend Presentation

### Task 8: Update SimulationsApi Types

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/simulations.ts`

**What to do:**

Update TypeScript types to match new DTO fields:

```typescript
// Add to SimulationStateDTO type
interface SimulationStateDTO {
  // ... existing fields ...
  scoreDelta?: number;
  impactDescription?: string;
  metricImpacts?: MetricImpact[];
}

interface MetricImpact {
  metric: string;
  direction: 'up' | 'down' | 'neutral';
  magnitude: 'strong' | 'moderate' | 'slight';
}

// Add to YearEndReview type
interface YearEndReview {
  // ... existing fields ...
  decisionHighlights?: DecisionHighlight[];
  crossDecisionInsight?: string;
  warningSignal?: string;
}

interface DecisionHighlight {
  decisionId: string;
  label: string;
  impact: 'positive' | 'negative' | 'neutral';
  summary: string;
}

// Add to Debrief type
interface Debrief {
  // ... existing fields ...
  decisionBreakdown?: DecisionBreakdownEntry[];
  patternAnalysis?: string;
}

interface DecisionBreakdownEntry {
  decisionId: string;
  label: string;
  quality: number;
  whatHappened: string;
  conceptLesson: string;
}
```

Run `npm run build` in shared-utils after changes.

**Commit:** `feat(simulation): update shared-utils types for new feedback fields`

---

### Task 9: Post-Decision Feedback Card

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx` (ADVISOR_REACTION phase handling)
- Create: `frontend/apps/student-portal/src/components/simulation/PostDecisionFeedback.tsx`

**What to do:**

Create a new `PostDecisionFeedback` component that replaces/wraps the current advisor reaction display in the ADVISOR_REACTION phase:

```
┌──────────────────────────────────────┐
│  +10 pts                  (green)    │
│                                      │
│  "Your R&D team delivers a break-    │
│   through prototype, but cash        │
│   reserves drop dangerously low."    │
│                                      │
│  📈 innovation ↑↑  💰 cash ↓        │
│                                      │
│  ─────────────────────────────       │
│  🧑‍🏫 "Bold move. That's exactly     │
│   what I would have done in '08."    │
└──────────────────────────────────────┘
```

- Score delta badge: green background for +10, yellow for +5, red/gray for +0
- Impact description: the `impactDescription` string from state
- Metric impact pills: small colored badges from `metricImpacts` — green for up, red for down, gray for neutral. Arrow icons for direction. Size text for magnitude.
- Advisor reaction below a divider (existing AdvisorDialog component)

In the play page, update the ADVISOR_REACTION phase handler to pass `scoreDelta`, `impactDescription`, and `metricImpacts` from the state to this new component.

**Commit:** `feat(simulation): add PostDecisionFeedback component with score delta and impact`

---

### Task 10: Enhanced Year-End Review

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/YearEndReview.tsx`

**What to do:**

Add two new sections to the existing year-end review component:

**1. Decision Highlights section** — after the existing metrics/feedback sections:
- Header: "Your Key Decisions This Year"
- Card list from `decisionHighlights` prop
- Each card: decision label + colored impact badge (green positive, red negative, gray neutral) + summary text
- Compact cards, 2-column grid on desktop, 1-column mobile

**2. Cross-Decision Insight** — after decision highlights:
- Callout box with blue-left-border styling
- Shows `crossDecisionInsight` text
- Icon: chain-link or connection icon

**3. Warning Banner** — if `warningSignal` is present (STRUGGLING):
- Amber/orange banner at top of review
- Warning icon + `warningSignal` text
- Subtle pulse animation to draw attention

All new sections are conditional — only render if the data exists (backward compat with old simulations that lack these fields, even though we said we don't need it — defensive coding).

**Commit:** `feat(simulation): add decision highlights and warning signals to year-end review`

---

### Task 11: Enhanced Debrief Page

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/debrief/page.tsx`

**What to do:**

Add two new sections after the existing debrief sections (yourPath, conceptAtWork, theGap, playAgain):

**1. Decision Breakdown** — expandable accordion:
- Header: "Your Decision-by-Decision Breakdown"
- One accordion entry per `decisionBreakdown` item
- Each entry shows:
  - Decision label + quality indicator (3 stars = green, 2 = yellow, 1 = red)
  - "What happened" text
  - "Concept lesson" text in a slightly different style (italic or different background)
- All collapsed by default, click to expand

**2. Pattern Analysis** — highlighted callout:
- Appear after decision breakdown
- Styled as a distinct card with a gradient or colored background
- Header: "Your Decision-Making Pattern"
- `patternAnalysis` text
- Icon: brain or chart-trending icon

Maintain the existing sequential reveal timing (500ms between sections). These new sections reveal last.

**Commit:** `feat(simulation): add decision breakdown and pattern analysis to debrief`

---

### Task 12: Dashboard Panel Enhancements

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/DashboardPanel.tsx`

**What to do:**

Three improvements to the right-side dashboard:

**1. Decision history quality indicators:**
- The existing decision history timeline shows past decisions
- Add a colored dot to each entry: green (quality 3), yellow (quality 2), red (quality 1)
- Add the `consequenceTag` as a small label below each entry (truncated if long)

**2. Risk indicator text:**
- Replace the current generic risk text with specific messaging
- If `consecutiveStruggling >= 1`: show "1 more struggling year = career over" in amber
- Otherwise keep existing risk level display

**3. Budget trend sparkline** (if budget exists):
- Small inline sparkline chart below the "Liquid Capital" display
- Shows budget value at end of each completed year
- Use a simple SVG polyline — no charting library needed

**Commit:** `feat(simulation): enhance dashboard with quality indicators and risk messaging`

---

### Task 13: Mobile UX Fixes

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/StatusHUD.tsx`
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx`

**What to do:**

**1. StatusHUD on mobile:**
- Currently hidden entirely on mobile (`hidden xl:flex` or similar)
- Change to show as a collapsible thin bar at top of screen on mobile
- Shows: role badge + "Y1 D3/6" + score + band color dot
- Tap to expand for full details
- Collapsed by default, stays out of the way

**2. Score delta toast on mobile:**
- After decision submission, show a brief full-width toast at top of screen
- "+10 pts" in green (or +5/+0 in yellow/red)
- Auto-dismiss after 2 seconds
- Only on mobile — desktop uses the PostDecisionFeedback card

**Commit:** `feat(simulation): fix mobile status HUD and add score toast`

---

### Task 14: Build, Test, and Version Bump

**Files:**
- Modify: `versions.json` (bump content service)

**What to do:**

1. Run `./gradlew :content:compileJava` — verify backend compiles
2. Run `cd frontend/packages/shared-utils && npm run build` — verify shared-utils builds
3. Run `cd frontend/apps/student-portal && npm run build` — verify student portal builds
4. Bump content version: `./scripts/manage-versions.sh bump content minor` (minor bump for feature)
5. Commit version bump

**Commit:** `chore: bump content to v0.4.0 for simulation v2`

---

## Execution Order & Dependencies

```
Task 1 (Phase 2 choices) ──┐
Task 2 (Inline mentor)  ───┤
                            ├─→ Task 3 (Consequence weaving) ─→ Task 4 (Year-end reviews)
                            │                                          │
                            │                                          ├─→ Task 6 (Validation)
                            │                                          │
                            └──────────────────────────────────────────┤
                                                                       ├─→ Task 5 (Debriefs)
                                                                       │
                                                                       └─→ Task 7 (DTOs + state logic)
                                                                                   │
                                                                                   ├─→ Task 8 (API types)
                                                                                   │       │
                                                                                   │       ├─→ Task 9 (Post-decision card)
                                                                                   │       ├─→ Task 10 (Year-end review)
                                                                                   │       ├─→ Task 11 (Debrief page)
                                                                                   │       ├─→ Task 12 (Dashboard)
                                                                                   │       └─→ Task 13 (Mobile fixes)
                                                                                   │
                                                                                   └─→ Task 14 (Build + version)
```

Tasks 1-2 can run in parallel.
Tasks 9-13 can run in parallel (all depend on Task 8).
Task 14 runs last.
