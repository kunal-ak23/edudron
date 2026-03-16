# Simulation Feature v2 — "Career Tenure" Model Design

**Date:** 2026-03-17
**Status:** Approved
**Supersedes:** 2026-03-16-simulation-feature-design.md (v1 branching tree model)
**Feature Flag:** `SIMULATION` (premium, default off)

## Overview

Immersive career-tenure simulations where students are "hired" into a role and make decisions across multiple years. Each year has 6 decisions. After each year, a Year-End Review shows dashboard metrics + multi-voice stakeholder feedback based on cumulative performance. The concept being taught is never named until the final debrief.

## What Changed from v1

| Aspect | v1 (Branching Tree) | v2 (Career Tenure) |
|---|---|---|
| Structure | Deep branching tree (~100 nodes) | Linear: Years × Decisions per Year (~30-36 nodes) |
| Feedback | Only at terminal | After every year (dashboard + stakeholders) |
| Engagement | Vague single choices | Concrete metrics, promotions, stakeholder voices |
| Narrative | Static per node | Adapts tone to performance band per year |
| Scoring | Score only at terminal | Running points + title progression + leaderboard |
| Early termination | Wrong branch → dead end | 2 consecutive STRUGGLING years → fired |
| Configurability | Depth 10-30 | Years 3-7, Decisions/year 4-8 |

## Key Decisions

| Decision | Choice |
|---|---|
| AI usage | Pre-generated only, zero AI at runtime |
| Creation flow | Instructor triggers AI, reviews/edits, publishes |
| Structure | Years × decisions/year, configurable |
| Year-end feedback | Dashboard metrics + multi-voice stakeholder feedback (3 variants per year) |
| Compounding | Metrics compound, narrative adapts, decisions are fixed |
| Scoring | Points per choice (0/5/10), promotions on THRIVING years, fired on 2× STRUGGLING |
| Roles | AI-generated role progression relevant to the simulation concept |
| Student access | Via section assignment OR available to all; embeddable in lectures |
| Replay | First attempt primary; replay unlocked after completion/termination |
| Tracking | Every play tracked with per-year scores, leaderboard-ready |
| Interactions | 7 decision types + compound; all resolve server-side |
| Storage | Hybrid entity + JSONB |
| Cross-tenant | JSON import/export |
| Feature gate | `SIMULATION` tenant feature flag |

## Simulation Flow

```
Role Setup → [Year 1 Opening] → D1 → D2 → D3 → D4 → D5 → D6 → [Year-End Review]
  → [Year 2 Opening (tone adapted)] → D1 → D2 → ... → D6 → [Year-End Review]
  → ... repeat for N years ...
  → [Final Debrief: concept revealed]
```

- **Role Setup:** Vivid character introduction, world, stakes, goal
- **Year Opening:** 3 variants (THRIVING/STEADY/STRUGGLING) — AI pre-generates all 3
- **6 Decisions:** Same for all students, varied interactive types
- **Year-End Review:** Dashboard metrics + board/customer/investor feedback (3 quality bands)
- **Final Debrief:** Your Path, The Concept at Work, The Gap, Play Again (3 variants)

## Scoring & Rewards

### Points per Decision
| Choice Quality | Points |
|---|---|
| Best (quality=3) | 10 |
| Mid (quality=2) | 5 |
| Worst (quality=1) | 0 |

### Per-Year Score → Performance Band
| Year Score (6 decisions) | Band |
|---|---|
| 40-60 pts | THRIVING |
| 20-39 pts | STEADY |
| 0-19 pts | STRUGGLING |

### Rewards
- **Promotion** on THRIVING year → title advances (AI-generated role progression)
- **Performance Review** on STRUGGLING year → cautionary feedback
- **Fired** after 2 consecutive STRUGGLING years → early termination
- **Final Score** = cumulative points / max possible × 100 (0-100 scale, leaderboard)

### Role Progression (AI-generated per simulation)
Example for DCF Valuation: `["Analyst", "Senior Analyst", "Associate VP", "VP", "Managing Director"]`
Example for Supply Chain: `["Logistics Coordinator", "Operations Manager", "Senior Ops Manager", "Head of Supply Chain", "COO"]`

The AI generates the progression during simulation creation. Students start at role[0] and advance on THRIVING years.

## Data Model

### `simulation` table (content schema)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (ULID) | Primary key |
| `client_id` | UUID | Tenant isolation |
| `course_id` | String (nullable) | Linked course |
| `lecture_id` | String (nullable) | Linked lecture |
| `title` | VARCHAR(500) | Display name |
| `concept` | VARCHAR(500) | Hidden concept (never shown to students) |
| `subject` | VARCHAR(255) | Subject/course |
| `audience` | VARCHAR(50) | UNDERGRADUATE / MBA / GRADUATE |
| `description` | TEXT | Student-facing description |
| `simulation_data` | JSONB | Full simulation structure (see JSON below) |
| `target_years` | INT | Number of years (default 5, range 3-7) |
| `decisions_per_year` | INT | Decisions per year (default 6, range 4-8) |
| `status` | ENUM | DRAFT / GENERATING / REVIEW / PUBLISHED / ARCHIVED |
| `visibility` | ENUM | ALL / ASSIGNED_ONLY |
| `assigned_to_section_ids` | TEXT[] | Sections with access |
| `created_by` | String | Creator |
| `published_at` | OffsetDateTime | |
| `created_at` | OffsetDateTime | |
| `updated_at` | OffsetDateTime | |
| `metadata_json` | JSONB | Extensible |

### `simulation_play` table (content schema)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (ULID) | Primary key |
| `client_id` | UUID | Tenant isolation |
| `simulation_id` | String | FK to simulation |
| `student_id` | String | FK to user |
| `attempt_number` | INT | 1st, 2nd, 3rd attempt |
| `is_primary` | BOOLEAN | True for first attempt |
| `status` | ENUM | IN_PROGRESS / COMPLETED / FIRED / ABANDONED |
| `current_year` | INT | Which year the student is in (1-based) |
| `current_decision` | INT | Which decision within the year (1-based) |
| `current_role` | VARCHAR(100) | Current title in role progression |
| `cumulative_score` | INT | Running total points |
| `year_scores_json` | JSONB | Array of per-year scores and bands |
| `decisions_json` | JSONB | Full decision history |
| `performance_band` | VARCHAR(20) | Current band: THRIVING/STEADY/STRUGGLING |
| `consecutive_struggling` | INT | Count of consecutive STRUGGLING years (0-2) |
| `final_score` | INT | Normalized 0-100 score |
| `started_at` | OffsetDateTime | |
| `completed_at` | OffsetDateTime | |

### Simulation Data JSON Structure

```json
{
  "roleSetup": {
    "character": "You are Maya Chen, a newly promoted associate...",
    "world": "A mid-cap industrial technology company...",
    "goal": "Figure out what this company is truly worth."
  },
  "roleProgression": ["Analyst", "Senior Analyst", "Associate VP", "VP", "Managing Director"],
  "metrics": {
    "definitions": [
      {"id": "revenue", "label": "Revenue", "unit": "$M", "startValue": 45},
      {"id": "margin", "label": "Operating Margin", "unit": "%", "startValue": 20},
      {"id": "stockPrice", "label": "Stock Price", "unit": "$", "startValue": 34},
      {"id": "satisfaction", "label": "Stakeholder Confidence", "unit": "", "startValue": "Medium"}
    ]
  },
  "years": [
    {
      "year": 1,
      "title": "The First Test",
      "openingNarrative": {
        "THRIVING": "Confidence is high as you begin...",
        "STEADY": "The office hums with quiet expectation...",
        "STRUGGLING": "The atmosphere is tense. Your predecessor was let go..."
      },
      "decisions": [
        {
          "id": "y1_d1",
          "narrative": "Your first major call: the board wants a growth strategy...",
          "decisionType": "BUDGET_ALLOCATION",
          "decisionConfig": {
            "total": 100, "unit": "%",
            "buckets": [
              {"id": "rd", "label": "R&D"},
              {"id": "marketing", "label": "Marketing"},
              {"id": "ops", "label": "Operations"}
            ],
            "mappings": [
              {"condition": "rd >= 40", "choiceId": "y1_d1_c"},
              {"condition": "marketing >= 50", "choiceId": "y1_d1_a"},
              {"condition": "default", "choiceId": "y1_d1_b"}
            ]
          },
          "choices": [
            {"id": "y1_d1_a", "text": "Heavy marketing spend", "quality": 1},
            {"id": "y1_d1_b", "text": "Balanced allocation", "quality": 2},
            {"id": "y1_d1_c", "text": "R&D-focused investment", "quality": 3}
          ]
        }
      ],
      "yearEndReview": {
        "STRONG": {
          "metrics": {"revenue": 52, "margin": 23, "stockPrice": 41, "satisfaction": "High"},
          "feedback": {
            "board": "Exceptional first year. We're fast-tracking your development.",
            "customers": "Renewal rates jumped 15%. Product quality is noticed.",
            "investors": "Increasing our position. New leadership is delivering."
          }
        },
        "MID": {
          "metrics": {"revenue": 48, "margin": 19, "stockPrice": 35, "satisfaction": "Mixed"},
          "feedback": {
            "board": "Adequate start. We expected stronger margins.",
            "customers": "Service is fine. Nothing remarkable either way.",
            "investors": "Holding steady. Need to see more next year."
          }
        },
        "POOR": {
          "metrics": {"revenue": 42, "margin": 15, "stockPrice": 28, "satisfaction": "Low"},
          "feedback": {
            "board": "We need to talk about direction.",
            "customers": "Three major accounts are reviewing alternatives.",
            "investors": "You're on our watchlist."
          }
        }
      }
    }
  ],
  "finalDebrief": {
    "THRIVING": {
      "yourPath": "Over 5 years, you consistently...",
      "conceptAtWork": "This simulation explored Discounted Cash Flow...",
      "theGap": "Your reasoning closely mirrored...",
      "playAgain": "The same company. Different choices. What would you do now?"
    },
    "STEADY": { ... },
    "STRUGGLING": { ... }
  },
  "firedDebrief": {
    "yourPath": "Your tenure ended early...",
    "conceptAtWork": "...",
    "theGap": "...",
    "playAgain": "..."
  }
}
```

## Interactive Decision Types

Same 7 types as v1, plus COMPOUND:

| Type | UI | Mapping |
|---|---|---|
| NARRATIVE_CHOICE | Styled option cards | Direct choiceId |
| BUDGET_ALLOCATION | Sliders summing to 100% | Allocation ranges → choiceId |
| PRIORITY_RANKING | Drag-and-drop ordering | Top items → choiceId |
| TRADEOFF_SLIDER | Slider between extremes | Ranges → choiceId |
| RESOURCE_ASSIGNMENT | Tokens into buckets | Pattern → choiceId |
| TIMELINE_CHOICE | Clickable timeline | Selection → choiceId |
| COMPOUND | 2 sequential steps | Combined conditions → choiceId |

All resolve server-side. Frontend sends raw input. Graceful fallback to NARRATIVE_CHOICE if mappings are missing.

## AI Generation Pipeline

### Flow
```
Instructor fills form (concept, subject, audience, years, decisions/year, courseId?)
  → POST /content/api/simulations/generate
    → Async job queued (SIMULATION_GENERATION)
      → Phase 1: Generate role setup + role progression + metrics definitions
      → Phase 2: Generate decisions for each year (1 AI call per year)
      → Phase 3: Generate year-end reviews (3 variants per year)
      → Phase 4: Generate opening narratives (3 variants per year)
      → Phase 5: Generate final debrief + fired debrief (3+1 variants)
      → Phase 6: Validate structure integrity
    → Status: GENERATING → REVIEW
```

### Estimated AI Calls & Cost (5 years, 6 decisions/year)
| Phase | Calls | Est. Tokens |
|---|---|---|
| Phase 1: Setup + roles + metrics | 1 | ~3,000 |
| Phase 2: Decisions (per year) | 5 | ~40,000 |
| Phase 3: Year-end reviews | 1-2 | ~10,000 |
| Phase 4: Opening narratives | 1 | ~5,000 |
| Phase 5: Debriefs | 1 | ~5,000 |
| **Total** | **~10** | **~63,000 tokens** |

**Cost:** ~$0.50 with GPT-4o, ~$0.03 with GPT-4o-mini

## Student Play Flow (Revised)

1. Start play → see role setup (character, world, goal) + starting title
2. See Year 1 opening narrative (based on band — first year always STEADY)
3. Make 6 decisions (varied interactive types)
4. After decision 6 → calculate year score → determine band
5. Show Year-End Review: metrics dashboard + 3 stakeholder quotes
6. If promoted (THRIVING) → show promotion celebration + new title
7. If fired (2nd consecutive STRUGGLING) → show termination + fired debrief → end
8. Otherwise → Year 2 opening (narrative adapted to band)
9. Repeat until final year or fired
10. Final debrief → concept revealed, score shown, play again CTA

## API Endpoints

Same as v1 (admin + student controllers). Key changes:

- `POST /decide` now also returns year progress (`year: 2, decision: 4/6`) and current metrics
- New response field on decide: if this was decision 6, response includes `yearEndReview` object
- `GET /play/{playId}` returns current state including year, decision, role, score, metrics

## Frontend UI Changes

### Student Play View
- **Top bar:** Current title/role + Year indicator + running score
- **Progress:** "Year 2 — Decision 3 of 6"
- **After 6th decision:** Transition to Year-End Review screen
  - Animated metrics dashboard (numbers count up/down)
  - Stakeholder feedback cards (board, customers, investors) with avatars
  - Promotion banner if applicable
  - "Continue to Year X" button
- **Fired screen:** Dramatic ending + fired debrief

### Admin Editor
- Year-based navigation (Year 1, Year 2, ...) instead of tree view
- Per-year: edit 6 decisions + 3 review variants + 3 opening variants
- Preview play-through mode

## Configuration

| Field | Default | Range | Purpose |
|---|---|---|---|
| `targetYears` | 5 | 3-7 | Number of simulated years |
| `decisionsPerYear` | 6 | 4-8 | Decisions per year |
| `choicesPerNode` | 3 | 2-4 | Options per decision |

## Import/Export

Same as v1 — JSON export strips tenant data, import creates in REVIEW status.

## Future Enhancements (out of scope)
- Leaderboards (per simulation, per section)
- Analytics dashboard (per-year heatmaps, common failure patterns)
- Multiplayer (different students play different roles in same company)
- Real-time metrics animation during play
