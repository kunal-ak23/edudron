# Simulation v3 "The Game" Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 6 new interactive decision types, persistent budget system with calculated ROI, and RPG-style game UI with advisor character — all building on existing v2 code.

**Architecture:** Extends existing simulation entities, services, and frontend components. New `BudgetCalculationService` handles financial model. Game UI is a visual redesign of the play page (dark theme, three-zone layout). All content pre-generated at creation time; zero AI at runtime.

**Tech Stack:** Spring Boot 3.3.4, JPA/Hibernate, Liquibase, Azure OpenAI, Next.js 14, Tailwind CSS, shadcn/ui, Recharts, Lucide icons.

---

## Task 1: Schema Migration — Budget Fields

**Files:**
- Create: `content/src/main/resources/db/changelog/db.changelog-0027-simulation-v3-budget.yaml`
- Modify: `content/src/main/resources/db/changelog/db.changelog-master.yaml`

**Changes:**

Add to `simulation_play` table:
```yaml
databaseChangeLog:
  - changeSet:
      id: content-0027-01-simulation-v3-budget
      author: edudron
      changes:
        - addColumn:
            tableName: simulation_play
            schemaName: content
            columns:
              - column: { name: current_budget, type: numeric, defaultValueNumeric: 0 }
              - column: { name: budget_history_json, type: jsonb }
```

Add include to `db.changelog-master.yaml`.

Also run the SQL manually for local dev:
```sql
ALTER TABLE content.simulation_play ADD COLUMN IF NOT EXISTS current_budget numeric DEFAULT 0;
ALTER TABLE content.simulation_play ADD COLUMN IF NOT EXISTS budget_history_json jsonb;
```

**Commit:** `feat(simulation-v3): schema migration for budget fields`

---

## Task 2: Update Domain Entity — SimulationPlay

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/domain/SimulationPlay.java`

**Add fields:**
```java
@Column(name = "current_budget")
private java.math.BigDecimal currentBudget = java.math.BigDecimal.ZERO;

@Column(name = "budget_history_json", columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
private List<Map<String, Object>> budgetHistoryJson;
```

Add getters/setters.

**Commit:** `feat(simulation-v3): add budget fields to SimulationPlay entity`

---

## Task 3: Create BudgetCalculationService

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/service/BudgetCalculationService.java`

**Implementation:**
```java
@Service
public class BudgetCalculationService {

    /**
     * Calculate year-end returns for all departments based on allocations.
     * Uses the financial model from simulationData.
     *
     * @param allocations Map of departmentId -> amount allocated
     * @param financialModel The financial model from simulationData
     * @param performanceBand Current band (THRIVING/STEADY/STRUGGLING)
     * @param previousYearAllocations For lagged returns (nullable)
     * @param playId Used as seed for reproducible randomness
     * @return Map with: returns per dept, totalReturns, endingBudget
     */
    public Map<String, Object> calculateYearEndReturns(
            Map<String, BigDecimal> allocations,
            Map<String, Object> financialModel,
            String performanceBand,
            List<Map<String, Object>> budgetHistory,
            String playId,
            int currentYear) {

        List<Map<String, Object>> departments =
            (List<Map<String, Object>>) financialModel.get("departments");
        Map<String, Object> multipliers =
            (Map<String, Object>) financialModel.get("performanceMultipliers");

        double perfMultiplier = ((Number) multipliers.getOrDefault(
            performanceBand, 1.0)).doubleValue();

        // Seed random with playId + year for reproducibility
        Random random = new Random((playId + "_" + currentYear).hashCode());

        Map<String, Object> returns = new LinkedHashMap<>();
        BigDecimal totalReturns = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;

        for (Map<String, Object> dept : departments) {
            String deptId = (String) dept.get("id");
            double baseRoi = ((Number) dept.get("baseRoi")).doubleValue();
            double volatility = ((Number) dept.get("volatility")).doubleValue();
            int lagYears = ((Number) dept.get("lagYears")).intValue();

            // Check if this is a current-year allocation or a lagged one
            BigDecimal allocation = BigDecimal.ZERO;
            int sourceYear = currentYear - lagYears;

            if (lagYears == 0) {
                // Current year allocation
                allocation = allocations.getOrDefault(deptId, BigDecimal.ZERO);
            } else if (sourceYear >= 1 && budgetHistory != null) {
                // Lagged: look up allocation from N years ago
                for (Map<String, Object> hist : budgetHistory) {
                    if (((Number) hist.get("year")).intValue() == sourceYear) {
                        Map<String, Object> pastAllocs =
                            (Map<String, Object>) hist.get("allocations");
                        if (pastAllocs != null && pastAllocs.containsKey(deptId)) {
                            allocation = new BigDecimal(pastAllocs.get(deptId).toString());
                        }
                        break;
                    }
                }
            }

            if (allocation.compareTo(BigDecimal.ZERO) <= 0) {
                // No allocation for this dept this cycle
                Map<String, Object> deptResult = new LinkedHashMap<>();
                deptResult.put("invested", 0);
                deptResult.put("return", null);
                deptResult.put("roi", null);
                deptResult.put("note", lagYears > 0 && lagYears > (currentYear - 1)
                    ? "Returns in Year " + (currentYear + lagYears - (currentYear - sourceYear))
                    : "No allocation");
                returns.put(deptId, deptResult);
                continue;
            }

            // Calculate return
            double volatilityFactor = 1.0 + (random.nextDouble() * 2 - 1) * volatility;
            double actualRoi = baseRoi * perfMultiplier * volatilityFactor;
            BigDecimal returnAmount = allocation.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(actualRoi)));

            totalReturns = totalReturns.add(returnAmount);
            totalInvested = totalInvested.add(allocation);

            Map<String, Object> deptResult = new LinkedHashMap<>();
            deptResult.put("invested", allocation);
            deptResult.put("return", returnAmount.setScale(0, java.math.RoundingMode.HALF_UP));
            deptResult.put("roi", String.format("%+.1f%%", actualRoi * 100));
            deptResult.put("note", null);
            returns.put(deptId, deptResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", returns);
        result.put("totalInvested", totalInvested);
        result.put("totalReturns", totalReturns.setScale(0, java.math.RoundingMode.HALF_UP));
        result.put("endingBudget", totalReturns.setScale(0, java.math.RoundingMode.HALF_UP));
        return result;
    }

    /**
     * Get the starting budget for a given year.
     */
    public BigDecimal getYearStartBudget(
            Map<String, Object> financialModel,
            List<Map<String, Object>> budgetHistory) {
        if (budgetHistory == null || budgetHistory.isEmpty()) {
            return new BigDecimal(financialModel.get("startingBudget").toString());
        }
        Map<String, Object> lastYear = budgetHistory.get(budgetHistory.size() - 1);
        return new BigDecimal(lastYear.get("endingBudget").toString());
    }
}
```

**Commit:** `feat(simulation-v3): add BudgetCalculationService for financial model`

---

## Task 4: Extend DecisionMappingService — 6 New Types

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/DecisionMappingService.java`

**Add new resolution logic in `resolveChoice()`:**

For **NEGOTIATION**: Extract `final_amount`, `accepted_round`, `walked_away` from input. Flatten into the existing condition evaluator.

For **DASHBOARD_ANALYSIS**: Same as NARRATIVE_CHOICE — the data display is frontend-only. Backend just resolves the choiceId.

For **HIRE_FIRE**: Extract `selected` candidate ID from input. Flatten as `selected == 'candidateId'`.

For **CRISIS_RESPONSE**: Check for `expired: true` in input. If expired, return `defaultOnExpiry` from config. Otherwise resolve normally.

For **INVESTMENT_PORTFOLIO**: Same as BUDGET_ALLOCATION mapping logic (amount-based conditions). Additionally, save allocations to a special output field for the service layer to pick up.

For **STAKEHOLDER_MEETING**: Extract `selected` array. Add `selected_contains('id')` as a new condition type in the evaluator.

**Add to flattenInput():**
```java
// HIRE_FIRE: selected -> "selected"
if (input.containsKey("selected")) {
    flat.put("selected", input.get("selected").toString());
}

// STAKEHOLDER_MEETING: selected array -> "selected_contains" checks
if (input.containsKey("selectedStakeholders")) {
    List<String> selected = (List<String>) input.get("selectedStakeholders");
    flat.put("selected_list", String.join(",", selected));
    // Add individual contains flags
    for (String s : selected) {
        flat.put("has_" + s, "true");
    }
}

// NEGOTIATION
if (input.containsKey("finalAmount")) {
    flat.put("final_amount", input.get("finalAmount").toString());
}
if (input.containsKey("acceptedRound")) {
    flat.put("accepted_round", input.get("acceptedRound").toString());
}
if (input.containsKey("walkedAway")) {
    flat.put("walked_away", input.get("walkedAway").toString());
}

// CRISIS_RESPONSE: check expiry
if (input.containsKey("expired") && Boolean.TRUE.equals(input.get("expired"))) {
    flat.put("expired", "true");
}
```

**Add `selected_contains()` condition evaluator:**
```java
// In evaluateCondition(), add handling for selected_contains('x')
if (condition.contains("selected_contains")) {
    // Parse: selected_contains('cmo')
    String id = condition.replaceAll(".*selected_contains\\('([^']+)'\\).*", "$1");
    return "true".equals(flatInput.get("has_" + id));
}
```

**Add crisis expiry check in resolveChoice():**
```java
// Before evaluating mappings, check crisis expiry
if ("CRISIS_RESPONSE".equals(decisionType)) {
    if ("true".equals(flatInput.get("expired"))) {
        String defaultChoice = (String) ((Map<String, Object>)
            node.get("decisionConfig")).get("defaultOnExpiry");
        if (defaultChoice != null) return defaultChoice;
    }
}
```

**Commit:** `feat(simulation-v3): extend DecisionMappingService for 6 new decision types`

---

## Task 5: Update SimulationService — Budget Integration

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java`

**Changes:**

1. Inject `BudgetCalculationService`.

2. In `startPlay()`: Set `currentBudget` from financial model's `startingBudget` (if financial model exists).

3. In `submitDecision()`: If the decision type is `INVESTMENT_PORTFOLIO`, save the allocation amounts to `budgetHistoryJson` (partial — year not yet complete).

4. In `advanceYear()`: After calculating the year band, call `budgetCalculationService.calculateYearEndReturns()` if a financial model exists. Update `currentBudget`. Add the financial report to the year-end review response.

5. In `getCurrentState()`: When phase is `YEAR_END_REVIEW`, include the financial report data. When phase is `DECISION` and the decision is `INVESTMENT_PORTFOLIO`, include `currentBudget` in the state so the frontend knows the available budget.

6. Update `SimulationStateDTO` to include:
```java
private BigDecimal currentBudget;
private Map<String, Object> financialReport;  // populated during YEAR_END_REVIEW
```

**Commit:** `feat(simulation-v3): integrate budget calculation into play flow`

---

## Task 6: Update SimulationPlayDTO and SimulationStateDTO

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationPlayDTO.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationStateDTO.java`

**SimulationPlayDTO** — add:
```java
private BigDecimal currentBudget;
```

**SimulationStateDTO** — add:
```java
private BigDecimal currentBudget;
private Map<String, Object> financialReport;
private Map<String, Object> advisorDialog;  // {mood, text}
private Map<String, Object> advisorReaction; // after decision: {mood, text}
```

**Commit:** `feat(simulation-v3): add budget and advisor fields to DTOs`

---

## Task 7: Update AI Generation — Phase 1 (Financial Model + Advisor)

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java`

**Phase 1 prompt additions:**

Add to the Phase 1 system prompt:
```
Also generate:

4. A "financialModel" object:
   - startingBudget: realistic dollar amount for the industry/role
   - currency: "$"
   - departments: 4-5 departments relevant to this simulation, each with:
     - id (snake_case), label, returnFormula (STABLE/MODERATE_LONG/HIGH_SHORT/COMPOUND),
       baseRoi (0.05-0.25), volatility (0.02-0.15), lagYears (0-2)
   - performanceMultipliers: { THRIVING: 1.2, STEADY: 1.0, STRUGGLING: 0.7 }

5. An "advisorCharacter" object:
   - name: a realistic mentor name
   - role: one-line description (e.g., "Your mentor and former division head")
   - portraitSet: one of ["professional_male", "professional_female", "academic_male", "academic_female"]
   - personality: brief personality description to keep dialog consistent
```

**Phase 1 response parsing:** Extract `financialModel` and `advisorCharacter` from the AI response and include them in the top-level `simulationData`.

**Commit:** `feat(simulation-v3): add financial model and advisor to AI generation phase 1`

---

## Task 8: Update AI Generation — Phase 2 (New Decision Types + Sequencing)

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java`

**Phase 2 prompt updates:**

Replace the decision type list with the full 13 types and add sequencing rules:

```
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
- "advisorMood": one of ["neutral", "concerned", "excited", "disappointed", "proud"]
- "advisorDialog": 1-2 sentences the mentor says to set up this decision
- "advisorReaction": {
    "quality_3": {"mood": "...", "text": "..."},
    "quality_2": {"mood": "...", "text": "..."},
    "quality_1": {"mood": "...", "text": "..."}
  }

DECISION CONFIG SCHEMAS BY TYPE:

NEGOTIATION:
{
  "rounds": 3, "unit": "$", "npcName": "...", "initialOffer": N,
  "npcResponses": [
    {"round": 1, "playerRange": {"min": N, "max": N}, "response": "...", "npcCounterOffer": N},
    ...
  ],
  "outcomes": [{"condition": "...", "choiceId": "..."}, ...]
}

DASHBOARD_ANALYSIS:
{
  "metrics": [{"label": "...", "value": "...", "trend": "up|down|flat", "change": "..."}],
  "chartData": {"type": "line|bar", "title": "...", "labels": [...], "datasets": [...]},
  "question": "..."
}

HIRE_FIRE:
{
  "action": "hire|fire", "budgetLimit": N,
  "candidates": [{"id": "...", "name": "...", "title": "...", "stats": {...}, "salary": N, "bio": "...", "strengths": [...], "weaknesses": [...]}],
  "mappings": [...]
}

CRISIS_RESPONSE:
{
  "timeLimit": 30, "crisisTitle": "...", "crisisDescription": "...",
  "severity": "critical|high|medium", "defaultOnExpiry": "choiceId"
}

INVESTMENT_PORTFOLIO:
{
  "totalBudget": N, "currency": "$",
  "departments": [{"id": "...", "label": "...", "description": "...", "minAllocation": N, "maxAllocation": N, "projectedRoiRange": "..."}],
  "mappings": [...]
}

STAKEHOLDER_MEETING:
{
  "maxSelections": 2, "instruction": "...",
  "stakeholders": [{"id": "...", "name": "...", "role": "...", "teaser": "...", "revealedInfo": "..."}],
  "mappings": [...]
}
```

**Commit:** `feat(simulation-v3): update AI generation prompts for new decision types and sequencing`

---

## Task 9: Update Frontend Types — shared-utils

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/simulations.ts`

**Add/update types:**

```typescript
// Update SimulationStateDTO
export interface SimulationStateDTO {
  phase: 'DECISION' | 'YEAR_END_REVIEW' | 'DEBRIEF' | 'FIRED'
  currentYear: number
  currentDecision: number
  totalDecisions: number
  currentRole: string
  cumulativeScore: number
  yearScore: number
  performanceBand: string
  currentBudget?: number           // NEW
  financialReport?: FinancialReport // NEW
  advisorDialog?: AdvisorDialog     // NEW
  advisorReaction?: AdvisorDialog   // NEW (after decision)
  decision?: SimulationDecisionDTO
  yearEndReview?: YearEndReviewDTO
  debrief?: DebriefDTO
  openingNarrative?: string
}

// NEW types
export interface AdvisorDialog {
  mood: 'neutral' | 'concerned' | 'excited' | 'disappointed' | 'proud'
  text: string
}

export interface FinancialReport {
  departments: Record<string, {
    invested: number
    return: number | null
    roi: string | null
    note: string | null
  }>
  totalInvested: number
  totalReturns: number
  endingBudget: number
}

// Update SimulationPlayDTO
export interface SimulationPlayDTO {
  // ... existing fields ...
  currentBudget?: number  // NEW
}
```

Build: `cd frontend/packages/shared-utils && npm run build`

**Commit:** `feat(simulation-v3): add budget and advisor types to shared-utils`

---

## Task 10: Create 6 New Decision Input Components

**Files to create in `frontend/apps/student-portal/src/components/simulation/`:**

### 10a: NegotiationInput.tsx
- Multi-round dialog interface
- Shows NPC name + speech bubble with their offer
- Number input for counter-offer OR Accept/Walk Away buttons
- Advances rounds based on pre-generated `npcResponses` matched by `playerRange`
- On final round or accept/walk away: calls `onSubmit({ input: { finalAmount, acceptedRound, walkedAway } })`
- Visual: dark card with dialog bubbles (left=NPC, right=player)

### 10b: DashboardAnalysisInput.tsx
- Top section: metrics cards grid (label, value, trend arrow, change %)
- Middle section: Recharts line/bar chart from `chartData`
- Bottom section: choice cards (reuses NarrativeChoiceInput internally)
- Import `recharts` — add to student-portal package.json if not present

### 10c: HireFireInput.tsx
- Candidate cards in a row/grid
- Each card: name, title, key stats, salary, expandable bio
- Budget indicator bar showing limit
- Select button on each card (radio-style — one selection)
- Over-budget candidates shown with warning but still selectable
- Calls `onSubmit({ input: { selected: candidateId } })`

### 10d: CrisisResponseInput.tsx
- Alert banner at top (red/amber based on severity)
- Prominent countdown timer (large, animated, circular or bar)
- Crisis title + description
- Choice cards below
- On timer expiry: auto-submit with `{ input: { expired: true }, choiceId: defaultOnExpiry }`
- Timer uses `useEffect` with `setInterval`, cleans up properly

### 10e: InvestmentPortfolioInput.tsx
- Similar to BudgetAllocationInput but with dollar amounts, not percentages
- Each department shows: label, description, projected ROI range
- Slider or stepper for each (min/max from config)
- Running total + remaining budget display
- Calls `onSubmit({ input: { rd: 1500000, marketing: 2000000, ... } })`

### 10f: StakeholderMeetingInput.tsx
- Stakeholder cards with checkbox selection (max N)
- Each card: name, role, teaser text
- Counter: "Selected: 2/2"
- "Proceed to Meetings" button (disabled until maxSelections reached)
- After proceeding: animate reveal of `revealedInfo` for selected stakeholders
- "Continue" button after reading
- Calls `onSubmit({ input: { selectedStakeholders: ['cmo', 'legal'] } })`

### Update DecisionInput.tsx router:
Add cases for all 6 new types with config validation fallbacks:
```typescript
case 'NEGOTIATION':
  if (!config.npcResponses?.length) return fallback
  return <NegotiationInput config={config} onSubmit={onSubmit} disabled={disabled} />
case 'DASHBOARD_ANALYSIS':
  if (!config.metrics?.length) return fallback
  return <DashboardAnalysisInput config={config} choices={choices} onSubmit={onSubmit} disabled={disabled} />
case 'HIRE_FIRE':
  if (!config.candidates?.length) return fallback
  return <HireFireInput config={config} onSubmit={onSubmit} disabled={disabled} />
case 'CRISIS_RESPONSE':
  return <CrisisResponseInput config={config} choices={choices} onSubmit={onSubmit} disabled={disabled} />
case 'INVESTMENT_PORTFOLIO':
  if (!config.departments?.length) return fallback
  return <InvestmentPortfolioInput config={config} onSubmit={onSubmit} disabled={disabled} />
case 'STAKEHOLDER_MEETING':
  if (!config.stakeholders?.length) return fallback
  return <StakeholderMeetingInput config={config} onSubmit={onSubmit} disabled={disabled} />
```

**Commit:** `feat(simulation-v3): add 6 new interactive decision input components`

---

## Task 11: Create Game UI Components

**Files to create in `frontend/apps/student-portal/src/components/simulation/`:**

### 11a: AdvisorDialog.tsx
```typescript
interface AdvisorDialogProps {
  mood: string           // neutral, concerned, excited, disappointed, proud
  text: string
  advisorName: string
  onDismiss: () => void
  autoAdvance?: number   // ms before auto-dismiss (for reactions)
}
```
- Bottom-anchored dialog box with dark border (`border-[#1E3A5F]`)
- Left side: advisor portrait (mood-based icon/illustration from Lucide or SVG)
- Right side: typewriter text in a speech bubble
- Click anywhere or "▼" indicator to dismiss
- Moods map to: neutral=`User`, concerned=`AlertTriangle`, excited=`Sparkles`, disappointed=`Frown`, proud=`Award` (Lucide icons as MVP, replace with illustrations later)

### 11b: StatusHUD.tsx
```typescript
interface StatusHUDProps {
  role: string
  year: number
  totalYears: number
  decision: number
  totalDecisions: number
  budget?: number
  score: number
  performanceBand: string
}
```
- Fixed bottom bar, dark background (`bg-[#0F1729]`)
- Left: role title badge
- Center: "Year 3 | Decision 4/8" + progress bar
- Right: budget (Fira Code font) + score
- Performance band dot (green/amber/red)
- Compact on mobile (stacked layout)

### 11c: YearTransition.tsx
```typescript
interface YearTransitionProps {
  yearCompleted: number
  onComplete: () => void
}
```
- Full-screen overlay, dark background fade-in
- Large centered text: "YEAR 2 COMPLETE" (fade in + slight scale)
- Auto-advances after 1.5s or click to skip
- Respects `prefers-reduced-motion`

### 11d: FinancialReport.tsx
```typescript
interface FinancialReportProps {
  report: FinancialReport
  currency: string
  onDismiss: () => void
}
```
- Dark card with table layout
- Columns: Department, Invested, Return, ROI
- Numbers animate (count up from 0 using requestAnimationFrame)
- ROI colored: green for positive, red for negative
- Lagged departments show "Returns in Year N" note
- Total row at bottom with ending budget highlighted
- Uses Fira Code font for all numbers

### 11e: PromotionCelebration.tsx
```typescript
interface PromotionCelebrationProps {
  oldTitle: string
  newTitle: string
  onDismiss: () => void
}
```
- Overlay with gold/amber accent (`#F97316`)
- Award icon (Lucide `Award`)
- "Promoted!" heading
- Old title → arrow → new title
- Auto-dismiss after 3s or click

**Commit:** `feat(simulation-v3): add game UI components — advisor, HUD, transitions`

---

## Task 12: Redesign Play Page — Dark Theme + Three-Zone Layout

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx`

**Major rewrite.** The page becomes a dark-themed, three-zone layout:

**Zone 1 — Main Content (top 70%):**
- Dark background: `bg-[#0F1729]`
- Content cards: `bg-[#1A2744]` with `border-[#1E3A5F]/30`
- Text: `text-[#E2E8F0]` primary, `text-[#94A3B8]` secondary
- This is where decision narratives + input components render
- Smooth transitions between decisions (fade out old, fade in new)

**Zone 2 — Advisor Dialog (bottom 20%):**
- Uses `AdvisorDialog` component
- Flow per decision:
  1. Advisor dialog appears with setup text (typewriter)
  2. Student dismisses dialog
  3. Decision content becomes active in Zone 1
  4. After submit: advisor reaction appears briefly (2s auto-advance)
  5. Transition to next decision

**Zone 3 — Status HUD (fixed bottom 10%):**
- Uses `StatusHUD` component
- Always visible, updates after each action

**Year-End Review sequence:**
After last decision of a year:
1. `YearTransition` overlay: "YEAR N COMPLETE" (1.5s)
2. Advisor dialog: "Let's see how the numbers came in..."
3. `FinancialReport` animates in (if budget system exists)
4. Advisor dialog: "The board has something to say..."
5. Stakeholder feedback cards (existing YearEndReview, restyled for dark theme)
6. `PromotionCelebration` if applicable
7. "Continue to Year N+1" button

**State machine for the play page:**
```
ADVISOR_SETUP → DECISION_ACTIVE → SUBMITTING → ADVISOR_REACTION →
  (if more decisions) → ADVISOR_SETUP
  (if year end) → YEAR_TRANSITION → FINANCIAL_REPORT → STAKEHOLDER_REVIEW → NEXT_YEAR
  (if fired) → FIRED_SCREEN
  (if complete) → DEBRIEF
```

**Commit:** `feat(simulation-v3): redesign play page with dark theme and game UI`

---

## Task 13: Restyle YearEndReview for Dark Theme

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/YearEndReview.tsx`

**Changes:**
- Background: `bg-[#0F1729]`
- Cards: `bg-[#1A2744]` with subtle borders
- Stakeholder icons: keep existing (Building2, Users, TrendingUp) but with teal/amber/orange accent colors
- Performance band badges: green/amber/red on dark background
- Text: light slate colors
- The component is now called as PART of the board room sequence (not standalone)
- Remove the "Continue" button from this component (the parent page controls the flow)

**Commit:** `feat(simulation-v3): restyle YearEndReview and FiredScreen for dark theme`

---

## Task 14: Install Recharts in Student Portal

**Files:**
- Modify: `frontend/apps/student-portal/package.json`

**Command:**
```bash
cd frontend/apps/student-portal && npm install recharts
```

Needed for `DashboardAnalysisInput` charts and `FinancialReport` visualizations.

**Commit:** `chore: add recharts to student portal for simulation charts`

---

## Task 15: Build, Verify, Push

**Steps:**

1. Build shared-utils:
```bash
cd frontend/packages/shared-utils && npm run build
```

2. Verify backend compiles:
```bash
cd /path/to/worktree && ./gradlew :content:compileJava
```

3. Run local SQL migration (if not auto-applied by Liquibase):
```bash
psql -U kunalsharma -d edudron -c "
ALTER TABLE content.simulation_play ADD COLUMN IF NOT EXISTS current_budget numeric DEFAULT 0;
ALTER TABLE content.simulation_play ADD COLUMN IF NOT EXISTS budget_history_json jsonb;
"
```

4. Push all changes:
```bash
git push origin feat/simulation
```

**Commit:** `chore: verification and cleanup for simulation v3`

---

## Implementation Order

```
Task 1  (Schema)           ──┐
Task 2  (Entity)           ──┤── Backend foundation
Task 3  (BudgetService)    ──┤
Task 4  (MappingService)   ──┘
                              │
Task 5  (SimulationService)───┤── Backend integration (depends on 1-4)
Task 6  (DTOs)             ───┘
                              │
Task 7  (AI Gen Phase 1)   ──┐
Task 8  (AI Gen Phase 2)   ──┘── AI Generation (parallel with frontend)
                              │
Task 9  (Shared types)     ──┐
Task 14 (Recharts install) ──┤── Frontend foundation
Task 10 (6 new components) ──┤── Frontend components (depends on 9)
Task 11 (Game UI components)──┘
                              │
Task 12 (Play page redesign)──┐── Frontend assembly (depends on 10, 11)
Task 13 (Dark theme restyle)──┘
                              │
Task 15 (Verify + push)    ────── Final

Parallelizable groups:
- Group A: Tasks 1-6 (backend)
- Group B: Tasks 7-8 (AI generation)
- Group C: Tasks 9-11, 14 (frontend components)
- Group D: Tasks 12-13 (frontend assembly, depends on C)
- Group E: Task 15 (final)

Groups A, B, C can run in parallel.
Group D depends on C.
Group E depends on all.
```
