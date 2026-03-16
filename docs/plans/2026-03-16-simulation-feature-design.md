# Simulation Feature — Design Document

**Date:** 2026-03-16
**Status:** Approved
**Feature Flag:** `SIMULATION` (premium, default off)

## Overview

Immersive, branching decision simulations that teach academic concepts through experiential learning. Students play a character in a realistic scenario, make decisions with real stakes, and discover the underlying concept only through consequences — never through instruction during play.

The simulation never names the concept being taught. Students learn through lived experience. Only the chosen path is shown. A debrief at the end reveals the concept and analyzes the student's decision pattern.

## Key Decisions

| Decision | Choice |
|---|---|
| AI usage | Pre-generated tree only, zero AI at runtime |
| Creation flow | Instructor triggers AI generation, reviews/edits, publishes |
| Tree structure | Configurable depth (10-30), recovery paths, ~100 nodes at depth 15 |
| Debriefs | Pre-generated per terminal node |
| Student access | Via section assignment OR available to all; embeddable in lectures |
| Replay | First attempt is primary; replay unlocked after debrief |
| Tracking | Every play session tracked; leaderboard-ready with scores |
| Interactions | 6 decision types + compound; all resolve server-side |
| Storage | Hybrid entity + JSONB tree |
| Cross-tenant | JSON import/export |
| Feature gate | `SIMULATION` tenant feature flag |

## Data Model

### `simulation` table (content schema)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (ULID) | Primary key |
| `client_id` | UUID | Tenant isolation |
| `course_id` | String (nullable) | Linked course |
| `lecture_id` | String (nullable) | Linked lecture |
| `title` | VARCHAR | Display name ("The Pricing Dilemma") |
| `concept` | VARCHAR | Hidden concept ("Price Elasticity of Demand") |
| `subject` | VARCHAR | Subject/course ("Economics") |
| `audience` | VARCHAR | "UNDERGRADUATE" / "MBA" / "GRADUATE" |
| `description` | TEXT | Student-facing description (no concept spoilers) |
| `tree_data` | JSONB | Full branching decision tree |
| `target_depth` | INT | Configurable depth (default 15, range 10-30) |
| `choices_per_node` | INT | Choices per decision (default 3, range 2-4) |
| `max_depth` | INT | Actual deepest path length (computed after generation) |
| `status` | ENUM | DRAFT / GENERATING / REVIEW / PUBLISHED / ARCHIVED |
| `visibility` | ENUM | ALL / ASSIGNED_ONLY |
| `assigned_to_section_ids` | TEXT[] | Sections that can access this simulation |
| `created_by` | String | Admin/instructor who created it |
| `published_at` | OffsetDateTime | |
| `created_at` | OffsetDateTime | |
| `updated_at` | OffsetDateTime | |
| `metadata_json` | JSONB | Extensible (tags, difficulty, estimated time, etc.) |

### `simulation_play` table (content schema)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (ULID) | Primary key |
| `client_id` | UUID | Tenant isolation |
| `simulation_id` | String | FK to simulation |
| `student_id` | String | FK to user |
| `attempt_number` | INT | 1st, 2nd, 3rd attempt |
| `is_primary` | BOOLEAN | True for first attempt |
| `status` | ENUM | IN_PROGRESS / COMPLETED / ABANDONED |
| `path_json` | JSONB | Array of {nodeId, choiceId, input, timestamp} |
| `current_node_id` | String | Current position in tree |
| `final_node_id` | String | Terminal node reached |
| `score` | INT | Score from terminal node (0-100) |
| `decisions_made` | INT | Number of choices made |
| `started_at` | OffsetDateTime | |
| `completed_at` | OffsetDateTime | |

### Tree Data JSON Structure

```json
{
  "rootNodeId": "node_001",
  "nodes": {
    "node_001": {
      "id": "node_001",
      "type": "SCENARIO",
      "narrative": "You are Maya Chen, a 28-year-old product manager...",
      "decisionType": "NARRATIVE_CHOICE",
      "decisionConfig": { },
      "choices": [
        {
          "id": "choice_001a",
          "text": "Cut prices by 40% to match the competitor",
          "nextNodeId": "node_002a",
          "quality": 1
        },
        {
          "id": "choice_001b",
          "text": "Hold prices but add a premium bundle",
          "nextNodeId": "node_002b",
          "quality": 2
        },
        {
          "id": "choice_001c",
          "text": "Raise prices and double down on brand positioning",
          "nextNodeId": "node_002c",
          "quality": 3
        }
      ]
    },
    "node_end_fail_03": {
      "id": "node_end_fail_03",
      "type": "TERMINAL",
      "narrative": "Your company runs out of runway...",
      "debrief": {
        "yourPath": "You consistently prioritized...",
        "conceptAtWork": "This simulation explored Price Elasticity...",
        "theGap": "Your decisions assumed that...",
        "playAgain": "The same market. Different strategy. What would you do now?"
      },
      "score": 25
    }
  }
}
```

- `quality` (1=worst, 2=mid, 3=best) — internal only, never shown to students
- `score` on terminal nodes — 0-100, determines leaderboard ranking
- Recovery paths: a quality=1 choice doesn't always terminate — it can lead to a harder path that reconnects

## Interactive Decision Types

| Type | UI | Mapping |
|---|---|---|
| `NARRATIVE_CHOICE` | Paragraph-style option cards | Direct 1:1 to choices |
| `BUDGET_ALLOCATION` | Sliders summing to 100% | Allocation ranges map to choices |
| `PRIORITY_RANKING` | Drag-and-drop ordering | Top items determine choice path |
| `TRADEOFF_SLIDER` | Slider between two extremes | Ranges map to choices |
| `RESOURCE_ASSIGNMENT` | Drag tokens into buckets | Assignment pattern maps to choice |
| `TIMELINE_CHOICE` | Clickable timeline milestones | Selection maps to choice |
| `COMPOUND` | 2 sequential interactive steps | Combined conditions map to choice |

All decision types resolve server-side to one of the pre-generated choices via mapping rules in `decisionConfig`. The frontend sends raw input; the backend evaluates mappings and returns the next node.

### Compound Decision Example

```json
{
  "decisionType": "COMPOUND",
  "decisionConfig": {
    "steps": [
      { "type": "BUDGET_ALLOCATION", "label": "Allocate expansion budget", "buckets": [...] },
      { "type": "PRIORITY_RANKING", "label": "Rank target markets", "items": [...] }
    ],
    "mappings": [
      { "condition": "step1.marketing >= 40 && step2.top == 'asia'", "choiceId": "choice_008a" },
      { "condition": "step1.rd >= 50 && step2.top == 'europe'", "choiceId": "choice_008b" },
      { "condition": "default", "choiceId": "choice_008c" }
    ]
  }
}
```

Compound decisions are capped at 2 steps per node and used sparingly (2-3 per simulation).

## AI Generation Pipeline

### Flow

```
Instructor fills form (concept, subject, audience, depth, courseId?)
  -> POST /content/api/simulations/generate
    -> Async job queued (AI_SIMULATION_GENERATION)
      -> Phase 1: Generate optimal path (target_depth nodes)
      -> Phase 2: Generate failure branches + recovery paths
      -> Phase 3: Generate debriefs for all terminal nodes
      -> Phase 4: Validate tree integrity
    -> Status: GENERATING -> REVIEW
  -> Instructor previews, edits tree in admin UI
  -> Instructor publishes -> status: PUBLISHED
```

### Phase Details

**Phase 1 — Golden Path (1 AI call):**
Uses the simulation designer prompt. Generates the full optimal path with target_depth sequential decision points. Each node has 2-3 choices with varied decision types. Output: ordered list of nodes with narratives, decision configs, and choices.

**Phase 2 — Failure & Recovery Branches (1-2 AI calls):**
For each golden-path node, generates consequences for non-optimal choices:
- quality=1 choices: terminal within 1-2 steps
- quality=2 choices: harder recovery path of 2-3 nodes, reconnects to golden path ~2 levels ahead
- Consecutive bad choices on recovery paths -> terminal

**Phase 3 — Debriefs (1 AI call):**
Generates debrief for each terminal node using the structure:
- Your Path: neutral summary of decisions
- The Concept at Work: reveals the concept
- The Gap: observation about reasoning distance
- Play Again: invitation to replay
- Score: 0-100 based on depth reached and choice quality

**Phase 4 — Validation (code, no AI):**
- Every choice leads to a valid node
- Every path terminates (no infinite loops)
- At least one path reaches maximum depth
- All terminal nodes have debriefs and scores

### Node Count Estimates

| Depth | Golden Path | Failure Branches | Recovery Paths | Total Nodes | Terminal Nodes |
|---|---|---|---|---|---|
| 10 | 11 | ~15 | ~30 | ~65 | ~30 |
| 15 | 16 | ~23 | ~60 | ~100 | ~45 |
| 20 | 21 | ~30 | ~80 | ~135 | ~60 |
| 30 | 31 | ~45 | ~120 | ~200 | ~90 |

Generation time: ~4-6 AI calls, ~3-8 minutes. Acceptable for one-time generation.

## API Endpoints

### Admin Endpoints

```
POST   /content/api/simulations/generate             - Submit generation job
GET    /content/api/simulations/generate/jobs/{id}    - Poll generation status
GET    /content/api/simulations                       - List simulations (paginated)
GET    /content/api/simulations/{id}                  - Get simulation with full tree
PUT    /content/api/simulations/{id}                  - Update metadata
PUT    /content/api/simulations/{id}/tree             - Update tree data
POST   /content/api/simulations/{id}/publish          - Publish
POST   /content/api/simulations/{id}/archive          - Archive
POST   /content/api/simulations/{id}/export           - Export as JSON
POST   /content/api/simulations/import                - Import from JSON
```

### Student Endpoints

```
GET    /content/api/simulations/available              - List available simulations
POST   /content/api/simulations/{id}/play              - Start new play session
GET    /content/api/simulations/{id}/play/{playId}     - Get current play state
POST   /content/api/simulations/{id}/play/{playId}/decide  - Submit decision
GET    /content/api/simulations/{id}/play/{playId}/debrief - Get debrief
GET    /content/api/simulations/{id}/history           - Play history for simulation
GET    /content/api/simulations/my-history             - All simulation history
```

### Security
- All endpoints require authentication
- Admin endpoints: SYSTEM_ADMIN and TENANT_ADMIN only
- Student endpoints: any authenticated student with access (section assignment / visibility check)
- Tree data with quality/score values is never sent to student endpoints — only narrative, decisionType, decisionConfig (without mappings)

## Student Play Flow

1. Student opens `/simulations` -> `GET /available` returns list with title, description, attempt count, best score
2. Student picks simulation -> `POST /play` creates `simulation_play` (attempt_number auto-incremented, is_primary=true for first)
3. Returns first node: narrative + decisionType + decisionConfig (mappings stripped)
4. Student interacts with UI (sliders, cards, etc.) -> `POST /decide { nodeId, input }`
5. Backend evaluates mapping rules server-side -> resolves to choiceId -> saves to path_json -> returns next node
6. Repeat until terminal node reached
7. Terminal: status=COMPLETED, score saved, debrief returned
8. "Play Again" -> `POST /play` creates new attempt (is_primary=false)

Key: tree is never sent in full to frontend. Only current node per request. Prevents inspection via browser dev tools.

## Frontend UI

### Admin Dashboard

- **`/simulations`** — List page with status filters, generation button, import
- **`/simulations/generate`** — Form: concept, subject, audience, course link, depth slider, choices count
- **`/simulations/[id]`** — Editor: visual tree view (left), node editor (right), assignment section, publish/export

### Student Portal

- **`/simulations`** — Card grid: title, description, best score, attempt count
- **`/simulations/[id]/play/[playId]`** — Full-screen immersive play view with typewriter animation and interactive decision UI
- **`/simulations/[id]/play/[playId]/debrief`** — Four-section debrief with score and play-again CTA
- **`/simulations/[id]/history`** — Attempt table with path replay

## Feature Flag

- `TenantFeatureType.SIMULATION` (default: false)
- Frontend: `useSimulationFeature()` hook
- Backend: controller-level check
- Settings page: "Simulations — Enable immersive decision-based simulations for students"

## Import/Export

**Export:** Strips tenant-specific fields (clientId, courseId, lectureId, sections, createdBy). Returns versioned JSON.

**Import:** Creates simulation in REVIEW status. Instructor links to course, assigns sections, publishes.

## Simulation Designer Prompt (for AI generation)

The following prompt template drives Phase 1 generation. Concept, subject, audience, and depth are injected at runtime:

```
You are a simulation designer. Your job is to create an immersive,
branching decision simulation that teaches [CONCEPT] from the subject
of [SUBJECT/COURSE] to [AUDIENCE] students.

CORE PHILOSOPHY:
- The simulation NEVER names the concept being taught during play
- The student learns ONLY through consequences of their own choices
- No guidance, hints, or corrections during the simulation
- Only the path the student chose is shown
- The concept reveals itself through lived experience, not instruction

[Full prompt as provided by the user — see implementation for complete version]
```

## Future Enhancements (out of scope for v1)

- Leaderboards (per simulation, per course, per section)
- Analytics dashboard (common failure points, average depth reached, concept comprehension rates)
- Multiplayer simulations (students play different roles in the same scenario)
- Timed decisions (pressure-based choices with countdown)
- Instructor-authored simulations (manual tree builder without AI)
