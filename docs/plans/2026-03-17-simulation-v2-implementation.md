# Simulation v2 Implementation Plan — Career Tenure Model

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the simulation feature from a branching tree model to a year-based career tenure model with dashboard metrics, stakeholder feedback, scoring, and promotions.

**Architecture:** Modifies existing simulation entities, services, controllers, and frontend pages. No new tables — schema migration updates existing columns.

**Tech Stack:** Same as v1 — Spring Boot 3.3.4, JPA, Liquibase, Azure OpenAI, Next.js 14, React, Tailwind/shadcn.

---

## Task 1: Database Schema Migration

**Files:**
- Create: `content/src/main/resources/db/changelog/db.changelog-0026-simulation-v2.yaml`
- Modify: `content/src/main/resources/db/changelog/db.changelog-master.yaml`

**Changes:**

Changelog 0026 adds/modifies columns on existing tables:

**simulation table:**
- Rename `tree_data` → `simulation_data` (or add `simulation_data` JSONB, keep `tree_data` for backward compat)
- Add `target_years` INT DEFAULT 5
- Add `decisions_per_year` INT DEFAULT 6
- Remove `target_depth`, `choices_per_node`, `max_depth` (or leave as deprecated)

**simulation_play table:**
- Add `current_year` INT DEFAULT 1
- Add `current_decision` INT DEFAULT 0
- Add `current_role` VARCHAR(100)
- Add `cumulative_score` INT DEFAULT 0
- Add `year_scores_json` JSONB
- Add `performance_band` VARCHAR(20)
- Add `consecutive_struggling` INT DEFAULT 0
- Add `final_score` INT
- Change `status` enum to include 'FIRED'
- Remove `current_node_id`, `final_node_id`, `path_json` (or leave for v1 compat)

Add to master changelog.

**Commit:** `feat(simulation-v2): database schema migration for career tenure model`

---

## Task 2: Update Domain Entities

**Files:**
- Modify: `content/.../simulation/domain/Simulation.java`
- Modify: `content/.../simulation/domain/SimulationPlay.java`

**Simulation.java changes:**
- Add `simulationData` (Map, JSONB) — the new structure
- Add `targetYears` (Integer, default 5)
- Add `decisionsPerYear` (Integer, default 6)
- Keep `treeData` temporarily for backward compat (mark @Deprecated)
- Remove `targetDepth`, `choicesPerNode`, `maxDepth` or mark deprecated

**SimulationPlay.java changes:**
- Add: `currentYear`, `currentDecision`, `currentRole`, `cumulativeScore`, `yearScoresJson` (List<Map>), `performanceBand`, `consecutiveStruggling`, `finalScore`
- Add `FIRED` to PlayStatus enum
- Keep `currentNodeId`, `pathJson` etc. as deprecated

**Commit:** `feat(simulation-v2): update domain entities for career tenure model`

---

## Task 3: Update DTOs

**Files:**
- Modify: `content/.../simulation/dto/SimulationDTO.java`
- Modify: `content/.../simulation/dto/SimulationPlayDTO.java`
- Modify: `content/.../simulation/dto/SimulationNodeDTO.java` → rename to `SimulationDecisionDTO.java`
- Modify: `content/.../simulation/dto/GenerateSimulationRequest.java`
- Create: `content/.../simulation/dto/YearEndReviewDTO.java`
- Create: `content/.../simulation/dto/SimulationStateDTO.java`

**GenerateSimulationRequest:** Replace `targetDepth`/`choicesPerNode` with `targetYears`/`decisionsPerYear`.

**SimulationDTO:** Replace `treeData`/`maxDepth` with `simulationData`/`targetYears`/`decisionsPerYear`.

**SimulationDecisionDTO** (renamed from NodeDTO): Represents a single decision the student faces. Same structure (narrative, decisionType, decisionConfig, choices) but no terminal/debrief — those are separate.

**SimulationStateDTO:** Returned on every play interaction — includes current year, decision number, role, score, metrics, and either the next decision or a year-end review or a debrief.

```java
public class SimulationStateDTO {
    private String phase;  // "DECISION", "YEAR_END_REVIEW", "DEBRIEF", "FIRED"
    private int currentYear;
    private int currentDecision;  // 1-based within year
    private int totalDecisions;   // decisionsPerYear
    private String currentRole;
    private int cumulativeScore;
    private int yearScore;        // score for current year so far
    private String performanceBand;

    // For DECISION phase
    private SimulationDecisionDTO decision;

    // For YEAR_END_REVIEW phase
    private YearEndReviewDTO yearEndReview;

    // For DEBRIEF or FIRED phase
    private DebriefDTO debrief;
}
```

**YearEndReviewDTO:**
```java
public class YearEndReviewDTO {
    private int year;
    private String band;  // STRONG, MID, POOR
    private Map<String, Object> metrics;  // {revenue: 52, margin: 23, ...}
    private Map<String, String> feedback; // {board: "...", customers: "...", investors: "..."}
    private String promotionTitle;  // non-null if promoted
    private boolean fired;
}
```

**Commit:** `feat(simulation-v2): update DTOs for year-based play flow`

---

## Task 4: Rewrite SimulationService Play Logic

**Files:**
- Modify: `content/.../simulation/service/SimulationService.java`

**Major changes to play methods:**

`startPlay()`:
- Set `currentYear = 1`, `currentDecision = 0`, `currentRole = roleProgression[0]`
- Set `performanceBand = "STEADY"` (everyone starts steady)

`getCurrentState(playId, studentId)` → returns `SimulationStateDTO`:
- If `currentDecision < decisionsPerYear`: phase = DECISION, return next decision
- If `currentDecision == decisionsPerYear` and year-end review not yet shown: phase = YEAR_END_REVIEW
- If all years complete: phase = DEBRIEF
- If fired: phase = FIRED

`submitDecision(playId, studentId, input)` → returns `SimulationStateDTO`:
- Resolve choice via DecisionMappingService (unchanged)
- Add points based on quality (10/5/0)
- Increment `currentDecision`
- Save to `decisionsJson`
- If this was the last decision of the year, calculate year score and band
- Return next state

`advanceYear(playId, studentId)` → called after student sees year-end review:
- Check if fired (2 consecutive struggling)
- If fired: set status FIRED, return fired debrief
- If more years: increment `currentYear`, reset `currentDecision = 0`
- If promoted: advance role
- Update `performanceBand`, `consecutiveStruggling`
- Return next state (year opening or DEBRIEF if final year done)

**Helper methods:**
- `calculateYearBand(yearScore, decisionsPerYear)` → THRIVING/STEADY/STRUGGLING
- `getDecisionFromSimData(simulationData, year, decisionIndex)` → extract decision node
- `getYearEndReview(simulationData, year, band)` → extract review for band
- `getOpeningNarrative(simulationData, year, band)` → extract year opening
- `toStudentDecision(decision)` → strip quality/mappings (same as v1's toStudentNode)
- `calculateFinalScore(cumulativeScore, maxPossible)` → 0-100 normalized

**Commit:** `feat(simulation-v2): rewrite play logic for year-based progression`

---

## Task 5: Rewrite AI Generation Service

**Files:**
- Modify: `content/.../simulation/service/SimulationGenerationService.java`

**New 6-phase pipeline:**

Phase 1 — Setup (1 AI call):
- Generate role setup (character, world, goal)
- Generate role progression (array of titles, length = targetYears)
- Generate metric definitions (4-6 KPIs with start values)

Phase 2 — Decisions per year (1 AI call per year):
- For each year, generate `decisionsPerYear` decisions
- Each decision has narrative, decisionType (varied), decisionConfig (with mappings for interactive types), and 2-3 choices with quality scores
- The AI prompt includes the year number and role for context

Phase 3 — Year-end reviews (1-2 AI calls):
- For each year, generate 3 variants (STRONG/MID/POOR)
- Each variant has metrics values (building on previous year) and 3 stakeholder feedback quotes

Phase 4 — Opening narratives (1 AI call):
- For each year, generate 3 variants (THRIVING/STEADY/STRUGGLING)
- Short paragraph setting the tone for the year

Phase 5 — Debriefs (1 AI call):
- Generate 3 final debriefs (THRIVING/STEADY/STRUGGLING) + 1 fired debrief
- Each follows the structure: Your Path, Concept at Work, The Gap, Play Again

Phase 6 — Validation (code only):
- Every year has the right number of decisions
- Every decision has choices with quality scores
- Every year has 3 review variants
- Every year has 3 opening variants
- All debriefs exist

**Commit:** `feat(simulation-v2): rewrite AI generation for year-based structure`

---

## Task 6: Update Async Job Worker

**Files:**
- Modify: `content/.../service/AIJobWorker.java`

Update `processSimulationGenerationJob()` to pass `targetYears` and `decisionsPerYear` to the generation service. Minor change — just add the new fields to the request map.

**Commit:** `feat(simulation-v2): update job worker for new generation params`

---

## Task 7: Update Controllers

**Files:**
- Modify: `content/.../simulation/web/SimulationAdminController.java`
- Modify: `content/.../simulation/web/SimulationStudentController.java`

**Admin controller:**
- Update `generateSimulation()` to use `targetYears`/`decisionsPerYear` instead of `targetDepth`/`choicesPerNode`
- `updateTree()` → `updateSimulationData()` (rename endpoint to PUT /{id}/data)

**Student controller:**
- `getCurrentNode()` → `getCurrentState()` — returns `SimulationStateDTO`
- `submitDecision()` → returns `SimulationStateDTO` (includes year progress)
- Add `POST /{id}/play/{playId}/advance-year` — called after student sees year-end review
- `getDebrief()` unchanged but returns from SimulationStateDTO

**Commit:** `feat(simulation-v2): update controllers for year-based endpoints`

---

## Task 8: Update Frontend API Client

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/simulations.ts`

Update types:
- `GenerateSimulationRequest`: `targetYears`/`decisionsPerYear` instead of `targetDepth`/`choicesPerNode`
- Add `SimulationStateDTO`, `YearEndReviewDTO` types
- Update `SimulationDTO` fields
- Add `advanceYear(simulationId, playId)` method
- `getCurrentNode` → `getCurrentState` (returns SimulationStateDTO)
- `submitDecision` returns `SimulationStateDTO`

Build shared-utils.

**Commit:** `feat(simulation-v2): update frontend API types for career tenure model`

---

## Task 9: Rewrite Student Play View

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/YearEndReview.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/MetricsDashboard.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/StakeholderFeedback.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/PromotionBanner.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/FiredScreen.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/PlayHeader.tsx`

**Play page redesign:**
- Top bar (`PlayHeader`): Current role/title + "Year X — Decision Y of 6" + running score
- Main area switches based on `phase`:
  - `DECISION` → show decision component (existing interactive types)
  - `YEAR_END_REVIEW` → show `YearEndReview` component
  - `DEBRIEF` → redirect to debrief page
  - `FIRED` → show `FiredScreen`

**YearEndReview component:**
- `MetricsDashboard`: animated counters for each KPI (count up/down effect)
- `StakeholderFeedback`: 3 cards (board, customers, investors) with appropriate icons
- `PromotionBanner`: shown if promoted — celebratory animation + new title
- "Continue to Year X" button → calls `advanceYear()`

**FiredScreen:**
- Dramatic full-screen "Your tenure has ended" with score
- Shows fired debrief
- Play Again button

**Commit:** `feat(simulation-v2): rewrite student play view with year-end reviews`

---

## Task 10: Update Admin Editor

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/simulations/[id]/page.tsx`
- Modify: `frontend/apps/admin-dashboard/src/components/simulation/SimulationTreeView.tsx` → rename to `SimulationYearView.tsx`
- Modify: `frontend/apps/admin-dashboard/src/components/simulation/SimulationNodeEditor.tsx` → rename to `SimulationDecisionEditor.tsx`

**Editor redesign:**
- Left panel: Year tabs (Year 1, Year 2, ...) instead of tree view
  - Under each year: 6 decision items + year-end review + opening narrative
  - Click any item to edit in right panel
- Right panel: Decision editor (same as before) OR review editor (edit 3 variants of metrics + feedback) OR opening editor (edit 3 narrative variants)
- Metadata section: `targetYears`/`decisionsPerYear` instead of depth/choices

**Commit:** `feat(simulation-v2): update admin editor for year-based navigation`

---

## Task 11: Update Generation Form + List Page

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/simulations/generate/page.tsx`
- Modify: `frontend/apps/admin-dashboard/src/app/simulations/page.tsx`

**Generation form:**
- Replace "Target Depth" with "Number of Years" (slider 3-7, default 5)
- Replace "Choices per Node" with "Decisions per Year" (slider 4-8, default 6)

**List page:**
- Replace "Depth" column with "Years × Decisions" (e.g., "5 × 6")

**Commit:** `feat(simulation-v2): update generation form and list page`

---

## Task 12: Update Debrief + History Pages

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/debrief/page.tsx`
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/history/page.tsx`

**Debrief page:**
- Add score breakdown: per-year scores with bands
- Show role progression achieved
- Show final metrics vs starting metrics

**History page:**
- Add columns: Final Role, Years Completed, Performance Band
- Color-code: FIRED attempts in red

**Commit:** `feat(simulation-v2): update debrief and history pages`

---

## Task 13: Final Verification + Cleanup

- Remove deprecated v1 fields if safe (or leave with @Deprecated)
- Build shared-utils: `cd frontend/packages/shared-utils && npm run build`
- Verify backend compiles: `./gradlew :content:compileJava`
- Bump version: `./scripts/manage-versions.sh bump content patch`
- Push all changes

**Commit:** `chore: cleanup and version bump for simulation v2`

---

## Implementation Order

| Task | Component | Depends On |
|---|---|---|
| 1 | DB Schema | — |
| 2 | Entities | 1 |
| 3 | DTOs | 2 |
| 4 | Play Logic | 2, 3 |
| 5 | AI Generation | 2, 3 |
| 6 | Job Worker | 5 |
| 7 | Controllers | 4, 6 |
| 8 | Frontend API | 3 |
| 9 | Student Play View | 8 |
| 10 | Admin Editor | 8 |
| 11 | Generation Form | 8 |
| 12 | Debrief + History | 9 |
| 13 | Verification | All |
