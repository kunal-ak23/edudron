# Simulation V2: Smart Feedback Layer — Design Document

**Date:** 2026-04-06
**Approach:** Smart Feedback Layer (Approach 2 — enhanced post-processing with inline mentor guidance)
**Backward Compatibility:** None required — existing simulations will be regenerated
**AI Cost Tolerance:** Quality over cost — willing to increase generation calls

---

## Problem Statement

The simulation feature generates rich, detailed worlds but the student experience doesn't surface that richness. Four core problems:

1. **Causality gap** — decisions don't visibly affect outcomes. Students make choices but don't see which ones drove their year-end results.
2. **Generic mentor/feedback** — mentor advice is generated as a separate pass (Phase 5.5) without decision context, resulting in fortune-cookie guidance that feels interchangeable between simulations.
3. **No consequence continuity** — each decision is an island. Year-end reviews say "revenue is up" without referencing which decisions caused it. The world doesn't remember your choices.
4. **Scoring/feedback opacity** — no score delta after decisions, no per-decision breakdown in debrief, firing happens without warning, students don't understand why they scored what they did.

---

## Solution Overview

Two-phase rollout:
- **Phase A (Generation):** Restructure the AI generation pipeline to produce decision-aware feedback, inline mentor guidance, consequence mappings, and enhanced debriefs.
- **Phase B (Presentation):** Update frontend to surface the new data — score deltas, impact descriptions, decision highlights in reviews, decision breakdowns in debrief, mobile fixes.

All improvements are pre-computed at generation time. Zero runtime AI calls during student gameplay.

---

## Phase A: Generation Pipeline Changes

### A1. Enhanced Decision Generation (Phase 2 Changes)

Each choice in Phase 2 now generates three additional fields:

```json
{
  "id": "y1_d2_a",
  "text": "Invest 60% of budget into R&D...",
  "quality": 3,
  "consequenceTag": "aggressive_rd_spend",
  "impactDescription": "Your R&D team delivers a breakthrough prototype, but cash reserves drop dangerously low heading into Q3.",
  "metricImpacts": [
    {"metric": "innovation", "direction": "up", "magnitude": "strong"},
    {"metric": "cash_reserves", "direction": "down", "magnitude": "moderate"}
  ]
}
```

- **`consequenceTag`**: Machine-readable label used to reference this choice in later narratives and reviews. Unique per choice.
- **`impactDescription`**: 1-2 sentence human-readable consequence shown to the student after they choose.
- **`metricImpacts`**: Structured metric effects (direction + magnitude) for visual feedback.

Mentor guidance is now generated **inline** with decisions (years 1-3 only), replacing Phase 5.5. The AI writes `courseConnection`, `realWorldExample`, `choiceHints`, and `mentorTip` while composing the decision narrative, so guidance is contextually grounded.

```json
{
  "id": "y1_d2",
  "narrative": "Your VP of Engineering walks in with a proposal...",
  "decisionType": "INVESTMENT_PORTFOLIO",
  "choices": [...],
  "mentorGuidance": {
    "courseConnection": "This mirrors the Innovator's Dilemma from Chapter 4...",
    "realWorldExample": "In 2015, Kodak faced this same choice...",
    "mentorNote": "I've seen this exact scenario at three companies. Two got it wrong.",
    "mentorTip": "Look at the ROI ranges — the highest ceiling isn't always safest. Check the floor too.",
    "choiceHints": {
      "y1_d2_a": {"hint": "Heavy R&D creates a 6-month cash crunch but positions you for Year 2 growth", "risk": "high"},
      "y1_d2_b": {"hint": "Spreading evenly keeps all departments functional but none exceptional", "risk": "low"},
      "y1_d2_c": {"hint": "Marketing-heavy spend drives short-term revenue but your product stagnates", "risk": "medium"}
    }
  }
}
```

**Phase 5.5 is eliminated.** Its regeneration endpoint (`/regenerate-mentor-guidance`) is removed.

### A2. Consequence Weaving (New Phase 3.5)

New phase after decisions, before year-end reviews. One AI call per year.

Generates per year:

```json
{
  "year1": {
    "decisionConsequenceMap": [
      {
        "decisionId": "y1_d2",
        "choiceQualities": {
          "quality_3": {
            "boardImpact": "The board praised your bold R&D investment — revenue projections jumped 18%.",
            "teamImpact": "Your team is energized but overworked after the sprint.",
            "customerImpact": "No immediate customer effect, but the prototype opens new market segments."
          },
          "quality_2": { ... },
          "quality_1": { ... }
        }
      }
    ],
    "crossDecisionNarratives": {
      "quality_high": "Your aggressive R&D bet (D2) combined with the strong hire (D4) created a team that delivered ahead of schedule.",
      "quality_mixed": "Your R&D investment (D2) showed promise, but the conservative staffing (D4) left the team stretched thin.",
      "quality_low": "Underfunding R&D (D2) and a weak hire (D4) left the department struggling to keep pace."
    },
    "warningSignals": {
      "STRUGGLING": "Two board members have privately expressed concern about your leadership direction."
    }
  }
}
```

- **`decisionConsequenceMap`**: Per-decision, per-quality impact on board/team/customers. Used by year-end reviews.
- **`crossDecisionNarratives`**: How decisions interact. Three variants (high/mixed/low) selected at runtime based on student's actual quality mix.
- **`warningSignals`**: Surfaced to struggling students before firing. Solves abrupt firing.

### A3. Decision-Aware Year-End Reviews (Phase 3 Changes)

Phase 3 now receives consequence weaving output as context. Reviews reference specific decisions:

```json
{
  "year1": {
    "STRONG": {
      "metrics": {"revenue": 52, "margin": 23},
      "feedback": {
        "board": "The board is particularly impressed by your R&D strategy (Decision 2) — the prototype is generating investor interest.",
        "customers": "Customer NPS jumped 12 points. Your stakeholder engagement (Decision 5) addressed their top complaints.",
        "investors": "Two VCs reached out after seeing your Q3 numbers."
      },
      "decisionHighlights": [
        {"decisionId": "y1_d2", "label": "R&D Investment", "impact": "positive", "summary": "Bold allocation drove prototype breakthrough"},
        {"decisionId": "y1_d5", "label": "Stakeholder Engagement", "impact": "positive", "summary": "Customer concerns addressed proactively"}
      ],
      "crossDecisionInsight": "Your aggressive R&D bet combined with strong stakeholder engagement created a virtuous cycle — innovation fueled by direct customer feedback."
    },
    "MID": { ... },
    "POOR": { ... }
  }
}
```

- **`decisionHighlights`**: Per-decision impact summary — the "report card per subject."
- **`crossDecisionInsight`**: Compound effects narrative.

### A4. Enhanced Debriefs (Phase 5 Changes)

Phase 5 receives all decisions with consequence tags and generates richer debriefs:

```json
{
  "THRIVING": {
    "yourPath": "...",
    "conceptAtWork": "...",
    "theGap": "You intuitively diversified in Year 1 — exactly what the theory prescribes. But in Year 3, you over-concentrated on marketing, which the theory warns against...",
    "playAgain": "...",
    "decisionBreakdown": [
      {
        "decisionId": "y1_d2",
        "label": "R&D Investment",
        "quality": 3,
        "whatHappened": "Your aggressive allocation funded the prototype that became the flagship product by Year 3.",
        "conceptLesson": "This is textbook exploration vs exploitation — you chose exploration when risk was lowest (Year 1)."
      }
    ],
    "patternAnalysis": "Your decisions show a shift from bold (Year 1-2) to conservative (Year 3-5). This is the 'success trap' the course warns about — early wins create risk aversion."
  }
}
```

- **`decisionBreakdown`**: Per-decision retrospective with `conceptLesson` connecting to course theory.
- **`patternAnalysis`**: Names the student's behavioral pattern using course terminology.
- **`theGap`**: Now references specific decisions, not generic observation.

### A5. Revised Pipeline Summary

```
Phase 1: Setup (1 call) — unchanged
Phase 2: Decisions + Inline Mentor (1 call/year = 5 calls) — enhanced
Phase 3: Consequence Weaving (1 call/year = 5 calls) — NEW
Phase 4: Year-End Reviews (1-2 calls) — enhanced with consequence context
Phase 5: Opening Narratives (1 call) — unchanged
Phase 6: Debriefs (1 call) — enhanced with decision breakdown
Phase 7: Validation (code only) — expanded for new fields

Total: ~13-15 AI calls (up from ~10-12)
Removed: Phase 5.5 (mentor guidance enrichment)
```

---

## Phase B: Frontend Presentation Changes

### B1. Post-Decision Feedback Card

After submitting a decision, the student sees:

1. **Score delta badge** — "+10 pts" (green), "+5 pts" (yellow), "+0 pts" (red)
2. **Impact description** — `impactDescription` from the chosen option (1-2 sentences)
3. **Metric impact pills** — colored directional indicators from `metricImpacts`
4. **Advisor reaction** — contextual emotional response (existing, now richer)

### B2. Year-End Review Enhancements

Two new sections added:

1. **Decision Highlights** — card list from `decisionHighlights`, each with label + impact badge + summary
2. **Cross-Decision Insight** — callout box showing compound effects
3. **Warning banner** (STRUGGLING only) — surfaces `warningSignals`, amber styling

### B3. Debrief Enhancements

Added after existing debrief sections:

1. **Decision Breakdown** — expandable accordion per decision: choice, quality indicator, what happened, concept lesson
2. **Pattern Analysis** — highlighted callout naming behavioral pattern
3. **Attempt comparison** (replay) — table showing score across attempts + key change summary

### B4. Mobile Fixes

- **Status HUD** — collapsible header bar on mobile (not hidden)
- **Score delta** — full-width toast notification after decision submit
- **Dashboard auto-peek** — briefly show dashboard tab after year-end

### B5. Dashboard Panel Enhancements

- Decision history entries show quality color dots + consequence tag labels
- Budget trend sparkline
- Clearer risk indicator text: "1 more struggling year = career over"

---

## Data Structure Changes

### Backend DTOs (new/modified fields)

**SimulationStateDTO** (decide response):
- `scoreDelta`: int (points earned for this decision)
- `impactDescription`: String (consequence narrative)
- `metricImpacts`: List<MetricImpactDTO> (direction + magnitude per metric)

**YearEndReviewDTO**:
- `decisionHighlights`: List<DecisionHighlightDTO> (decisionId, label, impact, summary)
- `crossDecisionInsight`: String
- `warningSignal`: String (nullable, only for STRUGGLING)

**DebriefDTO**:
- `decisionBreakdown`: List<DecisionBreakdownDTO> (decisionId, label, quality, whatHappened, conceptLesson)
- `patternAnalysis`: String

### simulationData JSON (additive changes)

- Choices gain: `consequenceTag`, `impactDescription`, `metricImpacts`
- Decisions gain: inline `mentorGuidance` (years 1-3)
- New top-level key: `consequenceWeaving` (Phase 3 output)
- Year-end reviews gain: `decisionHighlights`, `crossDecisionInsight`
- Debriefs gain: `decisionBreakdown`, `patternAnalysis`

### SimulationPlay entity: unchanged

Runtime logic reads new fields from simulationData. No schema migration needed.

---

## Validation (Phase 7 Expansion)

New checks:
- Every choice has `consequenceTag` (non-null, unique within year)
- Every choice has `impactDescription` (non-empty)
- Every choice has `metricImpacts` (non-empty array)
- Decisions in years 1-3 have `mentorGuidance` with required fields
- Consequence weaving exists for each year with all decision IDs covered
- Year-end reviews have `decisionHighlights` (non-empty)
- Debriefs have `decisionBreakdown` covering all decisions
- Debriefs have `patternAnalysis` (non-empty)

Issues logged as warnings (same as today) — admin can fix in REVIEW status.
