# Simulation v3 — "The Game" Design Document

**Date:** 2026-03-18
**Status:** Approved
**Supersedes:** v2 Career Tenure model (additive — builds on v2, does not replace)
**Feature Flag:** `SIMULATION` (premium, default off)

## Overview

Three major enhancements to the simulation feature:
1. **6 new interactive decision types** — negotiation, data analysis, hiring, crisis response, investment portfolio, stakeholder meetings
2. **Persistent budget & investment system** — real dollar allocations with calculated ROI, compounding across years
3. **Game-like UI** — RPG-style advisor character, dialog boxes, status HUD, animated year-end "board room" reviews

**Core constraint:** Heavy computation at creation time (AI generates all scenarios), zero AI at runtime. The runtime resolves decisions, calculates budget returns, and looks up pre-generated content.

## Part 1: New Decision Types

### 1.1 Full Inventory (13 types)

| # | Type | Interaction | Category |
|---|---|---|---|
| 1 | NARRATIVE_CHOICE | Click choice cards | Strategic |
| 2 | BUDGET_ALLOCATION | Percentage sliders summing to 100% | Financial |
| 3 | PRIORITY_RANKING | Drag-and-drop ordering | Strategic |
| 4 | TRADEOFF_SLIDER | Single slider between two extremes | Values |
| 5 | RESOURCE_ASSIGNMENT | Tokens into buckets (+/- buttons) | Resource |
| 6 | TIMELINE_CHOICE | Click milestone on timeline | Planning |
| 7 | COMPOUND | Multi-step wizard (combines other types) | Complex |
| 8 | **NEGOTIATION** | Multi-round back-and-forth with NPC | Interpersonal |
| 9 | **DASHBOARD_ANALYSIS** | View charts/metrics then decide | Analytical |
| 10 | **HIRE_FIRE** | Review candidate profiles, select one | People |
| 11 | **CRISIS_RESPONSE** | Timed decision (30s countdown) | Crisis |
| 12 | **INVESTMENT_PORTFOLIO** | Dollar allocation with projected ROI | Financial |
| 13 | **STAKEHOLDER_MEETING** | Pick N of M people to meet, reveals info | Information |

### 1.2 New Type Specifications

#### NEGOTIATION
- **UI:** Dialog exchange. Student sets offer amount/terms. NPC responds. Student accepts, counters, or walks away.
- **Config:**
```json
{
  "rounds": 3,
  "unit": "$",
  "npcName": "CFO",
  "initialOffer": 2000000,
  "npcResponses": [
    {
      "round": 1,
      "playerRange": {"min": 0, "max": 1500000},
      "response": "That's far too low. We need at least $2.5M.",
      "npcCounterOffer": 2500000
    },
    {
      "round": 1,
      "playerRange": {"min": 1500001, "max": 2500000},
      "response": "Getting closer. How about we meet in the middle?",
      "npcCounterOffer": 2200000
    },
    {
      "round": 1,
      "playerRange": {"min": 2500001, "max": 999999999},
      "response": "That's generous. I think we can work with that.",
      "npcCounterOffer": null
    }
  ],
  "outcomes": [
    {"condition": "accepted_round <= 1 && final_amount > 2500000", "choiceId": "y1_d3_a"},
    {"condition": "accepted_round <= 2", "choiceId": "y1_d3_b"},
    {"condition": "walked_away || accepted_round == 3", "choiceId": "y1_d3_c"},
    {"condition": "default", "choiceId": "y1_d3_b"}
  ]
}
```
- **Resolution:** Based on final amount + round of acceptance. Walking away has its own outcome.
- **Pre-generation:** NPC responses for each round × each player range = ~9 response variants per negotiation.

#### DASHBOARD_ANALYSIS
- **UI:** Metrics cards + optional chart, followed by choice cards.
- **Config:**
```json
{
  "metrics": [
    {"label": "Revenue", "value": "$12.4M", "trend": "up", "change": "+18%"},
    {"label": "Churn Rate", "value": "4.2%", "trend": "down", "change": "-1.1%"},
    {"label": "NPS Score", "value": "72", "trend": "up", "change": "+8"}
  ],
  "chartData": {
    "type": "line",
    "title": "Revenue vs Costs (Last 4 Quarters)",
    "labels": ["Q1", "Q2", "Q3", "Q4"],
    "datasets": [
      {"label": "Revenue", "data": [8.2, 9.5, 10.8, 12.4], "color": "#22C55E"},
      {"label": "Costs", "data": [7.1, 7.8, 8.2, 8.9], "color": "#EF4444"}
    ]
  },
  "question": "Based on this data, what should be your priority?"
}
```
- **Resolution:** Standard choice mapping (same as NARRATIVE_CHOICE but with data context).
- **Pre-generation:** Metrics and chart data are static per decision. The AI generates realistic numbers.

#### HIRE_FIRE
- **UI:** Candidate profile cards (expandable), budget constraint, select button.
- **Config:**
```json
{
  "action": "hire",
  "budgetLimit": 160000,
  "candidates": [
    {
      "id": "patel",
      "name": "Dr. Patel",
      "title": "AI Ethics Researcher",
      "stats": {"experience": "15 years", "education": "PhD Stanford", "risk": "Low"},
      "salary": 180000,
      "bio": "Published 40+ papers on AI fairness...",
      "strengths": ["Deep expertise", "Industry connections"],
      "weaknesses": ["Over budget", "Academic mindset"]
    }
  ],
  "mappings": [
    {"condition": "selected == 'patel'", "choiceId": "y2_d5_a"},
    {"condition": "selected == 'lee'", "choiceId": "y2_d5_b"},
    {"condition": "selected == 'rodriguez'", "choiceId": "y2_d5_c"}
  ]
}
```
- **Resolution:** Direct mapping from selected candidate to choice.
- **Note:** `action: "fire"` variant shows current team members instead of candidates.

#### CRISIS_RESPONSE
- **UI:** Alert banner + countdown timer + choice cards. Timer is prominent and animated.
- **Config:**
```json
{
  "timeLimit": 30,
  "crisisTitle": "AI Model Produced Biased Diagnoses",
  "crisisDescription": "47 patients received potentially incorrect recommendations...",
  "severity": "critical",
  "defaultOnExpiry": "y1_d6_worst",
  "choices": [...]
}
```
- **Resolution:** If timer expires, maps to `defaultOnExpiry` (worst choice). Otherwise standard choice mapping.
- **Pre-generation:** One crisis scenario per decision. The timer and expiry are runtime behavior (no AI needed).

#### INVESTMENT_PORTFOLIO
- **UI:** Department budget sliders with dollar amounts. Shows projected ROI range per department. Remaining budget counter.
- **Config:**
```json
{
  "totalBudget": 5000000,
  "currency": "$",
  "departments": [
    {
      "id": "rd",
      "label": "R&D",
      "description": "Product development and innovation",
      "minAllocation": 500000,
      "maxAllocation": 3000000,
      "projectedRoiRange": "+12-18% (returns in Year+1)"
    }
  ],
  "mappings": [
    {"condition": "rd >= 2000000 && training >= 1000000", "choiceId": "y1_d2_c"},
    {"condition": "marketing >= 2500000", "choiceId": "y1_d2_a"},
    {"condition": "default", "choiceId": "y1_d2_b"}
  ]
}
```
- **Resolution:** Standard mapping based on allocation amounts. Additionally, the actual dollar allocations are saved to `budget_history_json` for year-end ROI calculation.
- **Key difference from BUDGET_ALLOCATION:** Real dollar amounts (not percentages), persistent across years, ROI calculated at year-end.

#### STAKEHOLDER_MEETING
- **UI:** Stakeholder cards with name, role, teaser. Checkbox selection (max N). After selection, reveals hidden info for chosen stakeholders.
- **Config:**
```json
{
  "maxSelections": 2,
  "instruction": "You have time for 2 meetings before the board presentation.",
  "stakeholders": [
    {
      "id": "cmo",
      "name": "Dr. Hayes",
      "role": "Chief Medical Officer",
      "teaser": "Has concerns about patient safety",
      "revealedInfo": "Dr. Hayes shows you internal data: the AI system's accuracy drops to 67% for patients over 65. She recommends age-stratified validation before any expansion."
    }
  ],
  "mappings": [
    {"condition": "selected_contains('cmo') && selected_contains('legal')", "choiceId": "y3_d1_c"},
    {"condition": "selected_contains('cmo')", "choiceId": "y3_d1_b"},
    {"condition": "default", "choiceId": "y3_d1_a"}
  ]
}
```
- **Resolution:** Based on combination of selected stakeholders.
- **UX flow:** Select → "Proceed to Meetings" → Info reveal cards animate in → "Continue" to next decision. The revealed info stays visible for the remainder of the year as context.

### 1.3 Decision Sequencing Rules

Enforced during AI generation:
1. Never repeat the same decision type consecutively
2. Each year MUST include at least: 1 INVESTMENT_PORTFOLIO, 1 interactive type, 1 DASHBOARD_ANALYSIS
3. CRISIS_RESPONSE max 1 per year
4. STAKEHOLDER_MEETING should precede a major decision (info-then-action pattern)
5. NARRATIVE_CHOICE max 2 per year (prevent MCQ fatigue)
6. Type order MUST vary between years — Year 1 and Year 2 cannot start with the same type
7. Decisions per year: 6-10 (configurable, default 8)

## Part 2: Persistent Budget & Investment System

### 2.1 Financial Model

Each simulation defines a financial model during AI generation:

```json
{
  "financialModel": {
    "startingBudget": 5000000,
    "currency": "$",
    "departments": [
      {
        "id": "rd",
        "label": "R&D",
        "returnFormula": "MODERATE_LONG",
        "baseRoi": 0.15,
        "volatility": 0.08,
        "lagYears": 1
      },
      {
        "id": "marketing",
        "label": "Marketing",
        "returnFormula": "HIGH_SHORT",
        "baseRoi": 0.22,
        "volatility": 0.15,
        "lagYears": 0
      },
      {
        "id": "operations",
        "label": "Operations",
        "returnFormula": "STABLE",
        "baseRoi": 0.08,
        "volatility": 0.02,
        "lagYears": 0
      },
      {
        "id": "training",
        "label": "Training",
        "returnFormula": "COMPOUND",
        "baseRoi": 0.05,
        "volatility": 0.03,
        "lagYears": 2
      }
    ],
    "performanceMultipliers": {
      "THRIVING": 1.2,
      "STEADY": 1.0,
      "STRUGGLING": 0.7
    }
  }
}
```

### 2.2 Return Calculation (Runtime, No AI)

```
actualReturn = allocation * baseRoi * performanceMultiplier * (1 + randomWithinVolatility)
```

Where:
- `allocation` = dollar amount the student put into that department
- `baseRoi` = the expected return rate for that department
- `performanceMultiplier` = based on student's current performance band
- `randomWithinVolatility` = random value in range [-volatility, +volatility], seeded per play for reproducibility
- `lagYears` = how many years before returns appear (R&D = 1 year, Training = 2 years)

### 2.3 Budget Flow

```
Year 1 Start: budget = startingBudget ($5M)
  → INVESTMENT_PORTFOLIO decision: student allocates $5M across departments
  → Year 1 End: calculate returns for lagYears=0 departments
  → Year 2 Start: budget = previous + net returns
  → INVESTMENT_PORTFOLIO: student re-allocates
  → Year 2 End: calculate returns (including lagYears=1 investments from Year 1)
  → ... compounds through all years
```

### 2.4 Year-End Financial Report

Shown as part of the Year-End Review:

```
Department    Invested    Return    ROI    Notes
R&D           $1.5M       —         —      Returns in Year 3 (lag: 1yr)
Marketing     $2.0M       $2.44M   +22%
Operations    $0.5M       $0.54M   +8%
Training      $1.0M       —         —      Returns in Year 4 (lag: 2yr)
TOTAL         $5.0M       $2.98M*
* Excludes lagged investments
Year 3 Budget: $5.48M
```

### 2.5 Data Model Changes

**simulation_play table additions:**
- `current_budget` NUMERIC — current available budget
- `budget_history_json` JSONB — array of yearly allocations + calculated returns

```json
[
  {
    "year": 1,
    "allocations": {"rd": 1500000, "marketing": 2000000, "ops": 500000, "training": 1000000},
    "returns": {"rd": null, "marketing": 2440000, "ops": 540000, "training": null},
    "totalInvested": 5000000,
    "totalReturns": 2980000,
    "endingBudget": 5480000
  }
]
```

## Part 3: Game UI — "The Advisor"

### 3.1 Visual Style

**Modern RPG Dialog** — clean, professional game aesthetic. Not pixel art. Dark theme with the existing brand palette adapted for a game atmosphere.

**Color Palette:**

| Element | Color | Hex |
|---|---|---|
| Background | Dark navy | `#0F1729` |
| Content cards | Lighter navy | `#1A2744` |
| Advisor dialog border | Brand primary | `#1E3A5F` |
| Status bar accents | Brand teal | `#0891B2` |
| Score/points/achievements | Brand orange | `#F97316` |
| THRIVING indicators | Green | `#22C55E` |
| STEADY indicators | Amber | `#F59E0B` |
| STRUGGLING indicators | Red | `#EF4444` |
| Primary text | Light slate | `#E2E8F0` |
| Secondary text | Muted slate | `#94A3B8` |

**Typography:**
- Main UI: Manrope (existing)
- Numbers/metrics/budget: Fira Code (monospace, data-driven feel)

### 3.2 Screen Layout

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│           MAIN CONTENT AREA (70% height)                     │
│   Decision content: interactive inputs, charts, profiles,    │
│   candidate cards, negotiation dialog, etc.                  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│  ┌──────┐ ┌────────────────────────────────────────────┐    │
│  │      │ │ "The board is watching this one closely.    │    │
│  │ 😐  │ │  Your Q3 numbers will determine whether    │    │
│  │      │ │  they greenlight the expansion..."          │    │
│  │Mentor│ │                              ▼ Click       │    │
│  └──────┘ └────────────────────────────────────────────┘    │
│           ADVISOR DIALOG ZONE (20% height)                   │
├──────────────────────────────────────────────────────────────┤
│ 🏷 AI Innovation Lead │ Year 3 │ Dec 4/8 │ $4.2M │ Score 47│
│ STATUS HUD BAR (10% height)                                  │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 Advisor Character

Pre-generated per simulation:

```json
{
  "advisorCharacter": {
    "name": "Dr. Rivera",
    "role": "Your mentor and former division head",
    "portraitSet": "professional_female",
    "moods": ["neutral", "concerned", "excited", "disappointed", "proud"]
  }
}
```

**Per-decision advisor content:**
```json
{
  "advisorMood": "concerned",
  "advisorDialog": "The board is watching this one closely...",
  "advisorReaction": {
    "quality_3": {"mood": "excited", "text": "Bold move. I think this could work."},
    "quality_2": {"mood": "neutral", "text": "Safe choice. Let's see how it plays out."},
    "quality_1": {"mood": "disappointed", "text": "I had my doubts... but it's your call."}
  }
}
```

**Portrait assets:** 5 mood variants. Can be:
- SVG illustrations (generated or hand-drawn)
- AI-generated character portraits (generated once during simulation creation)
- Simple emoji-to-illustration mapping as MVP

### 3.4 Advisor Dialog UX

1. On each new decision: advisor dialog appears with typewriter text (16ms/char)
2. Student clicks "▼" or clicks anywhere in the dialog to dismiss
3. Decision content area becomes active
4. After student submits decision: advisor reaction appears (2-second auto-advance or click)
5. Transition to next decision

### 3.5 Year-End Review — "The Board Room"

Animated sequence (not a static page):

| Step | Duration | Content |
|---|---|---|
| 1 | 1.5s | Fade to dark → "YEAR 2 COMPLETE" title card |
| 2 | 2s | Advisor: "Let's see how the numbers came in..." |
| 3 | 3s | Financial report animates in (numbers count up) |
| 4 | 2s | Advisor: "The board has something to say..." |
| 5 | 4s | Stakeholder feedback cards slide in one by one |
| 6 | 2s | If promoted → celebration + title change |
| 7 | — | "Continue to Year N+1" button |

Total: ~15 seconds of guided review (all skippable with a "Skip" button for replays).

### 3.6 Status HUD Bar

Always visible at bottom of screen:

```
┌────────────────────────────────────────────────────────────┐
│ 🏷 [Role Title]  │  Year 3  │  Decision 4/8  │            │
│ Budget: $4.2M     │  Band: ● STEADY          │  Score: 47 │
│ ██████████░░░░░░░ (year progress bar)                      │
└────────────────────────────────────────────────────────────┘
```

## Part 4: AI Generation Changes

### 4.1 Updated Generation Phases

| Phase | Calls | What's Generated |
|---|---|---|
| 1: Setup | 1 | Role setup, role progression, advisor character, financial model |
| 2: Decisions (per year) | 1/year | 8 decisions with varied types, advisor dialog/reactions, configs |
| 3: Year-end reviews | 1-2 | 3 variants per year (metrics, feedback, advisor year-end dialog) |
| 4: Opening narratives | 1 | 3 variants per year (with advisor opening dialog) |
| 5: Debriefs | 1 | 3 final + 1 fired debrief |
| **Total (7 years)** | **~12** | **~100K tokens** |

### 4.2 Decision Type Distribution Prompt

```
SEQUENCING RULES (enforce strictly):
1. Never repeat the same decision type in consecutive positions
2. Each year MUST include at least:
   - 1 INVESTMENT_PORTFOLIO
   - 1 DASHBOARD_ANALYSIS
   - 1 from {NEGOTIATION, CRISIS_RESPONSE, HIRE_FIRE, STAKEHOLDER_MEETING}
3. CRISIS_RESPONSE: max 1 per year
4. NARRATIVE_CHOICE: max 2 per year
5. STAKEHOLDER_MEETING should appear before a related major decision
6. Year N and Year N+1 must NOT start with the same type
7. Vary interactive types across years — don't use the same set each year
```

## Part 5: Backend Changes Required

### 5.1 New Decision Type Resolution

Add to `DecisionMappingService.java`:

- **NEGOTIATION:** Track rounds, evaluate final amount + round → outcome
- **DASHBOARD_ANALYSIS:** Same as NARRATIVE_CHOICE (choice cards after data)
- **HIRE_FIRE:** Map selected candidate ID → choice
- **CRISIS_RESPONSE:** If expired flag → default choice; else standard mapping
- **INVESTMENT_PORTFOLIO:** Standard mapping + save allocations to budget history
- **STAKEHOLDER_MEETING:** Evaluate `selected_contains()` conditions

### 5.2 Budget Calculation Service

New `BudgetCalculationService.java`:
- `calculateYearEndReturns(play, simulationData)` — applies formulas, returns financial report
- `getNextYearBudget(play)` — returns available budget for next year
- Called during `advanceYear()` flow

### 5.3 Schema Migration

Add to `simulation_play`:
- `current_budget` NUMERIC
- `budget_history_json` JSONB

## Part 6: Frontend Components Required

### 6.1 New Decision Components

| Component | File |
|---|---|
| `NegotiationInput.tsx` | Multi-round dialog with offer input |
| `DashboardAnalysisInput.tsx` | Metrics cards + chart + choice cards |
| `HireFireInput.tsx` | Candidate profile cards with selection |
| `CrisisResponseInput.tsx` | Timer + choice cards |
| `InvestmentPortfolioInput.tsx` | Dollar sliders + ROI projections |
| `StakeholderMeetingInput.tsx` | Checkbox selection + info reveal |

### 6.2 Game UI Components

| Component | File |
|---|---|
| `AdvisorDialog.tsx` | Portrait + typewriter dialog box |
| `StatusHUD.tsx` | Bottom bar with role, year, budget, score |
| `YearTransition.tsx` | "YEAR N COMPLETE" animated title card |
| `FinancialReport.tsx` | Animated metrics table with ROI |
| `PromotionCelebration.tsx` | Title change animation |

### 6.3 Shared Assets

- Advisor portrait set (5 moods × N character types)
- Sound effects (optional, off by default): decision confirm, year complete, promotion, fired
- Chart component (lightweight — recharts or chart.js for DASHBOARD_ANALYSIS)

## Appendix: Example Play Session

```
START → Advisor: "Welcome to MedTech Solutions..."
     → Status HUD shows: Junior AI Coordinator, Year 1, Budget $5M

Y1D1 (STAKEHOLDER_MEETING) → Pick 2 of 4 stakeholders → Info revealed
Y1D2 (INVESTMENT_PORTFOLIO) → Allocate $5M across 4 departments
Y1D3 (NARRATIVE_CHOICE) → Strategic direction choice
Y1D4 (DASHBOARD_ANALYSIS) → View Q2 metrics → Decide priority
Y1D5 (NEGOTIATION) → Negotiate budget with CFO (3 rounds)
Y1D6 (HIRE_FIRE) → Hire Head of AI Ethics from 3 candidates
Y1D7 (CRISIS_RESPONSE) → 30s timer: AI bias incident
Y1D8 (TRADEOFF_SLIDER) → Speed vs. Safety spectrum

YEAR 1 END → Board Room sequence:
  → Financial Report: Marketing +22%, R&D (pending), Ops +8%
  → Stakeholder feedback (board, customers, investors)
  → Score: 52/80 → STEADY band
  → Budget: $5M → $5.48M
  → Advisor: "Not bad for your first year. Let's build on this."

Y2D1 (DASHBOARD_ANALYSIS) → Different starting type than Y1
...
```
