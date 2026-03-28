# Simulation Feature — Changelog (2026-03-27 / 2026-03-28)

> 31 commits across backend + frontend. Session covered bug fixes, mentor guided mode, scoring fixes, and UX improvements.

---

## 1. Critical Bug Fixes

### Budget Never Changes (FIXED)
- **Problem:** INVESTMENT_PORTFOLIO decisions used AI-generated department IDs ("search", "social") that didn't match financialModel departments ("rd", "marketing", "operations"). Year-end returns found zero matches → budget stayed at $5M.
- **Fix:** Phase 2 prompt now receives financialModel dept IDs. Added positional fallback in `BudgetCalculationService` for legacy simulations.
- **Files:** `SimulationGenerationService.java`, `BudgetCalculationService.java`

### STAKEHOLDER_MEETING Always Scores Quality=1 (FIXED)
- **Problem:** AI consistently generates empty `mappings: []` for STAKEHOLDER_MEETING decisions. With no mappings, the fallback picks the first choice (quality=1).
- **Fix:** Added `autoGenerateMappings()` in `DecisionMappingService` that generates `selected_contains()` conditions when mappings are missing. First two stakeholders = best combo.
- **Files:** `DecisionMappingService.java`

### INVESTMENT_PORTFOLIO Always Scores Quality=1 (FIXED)
- **Problem:** Same empty mappings issue. No auto-generate existed for this type.
- **Fix:** Added `resolveInvestmentPortfolioChoice()` that scores based on allocation balance using coefficient of variation. Balanced allocation = quality 3, concentrated = quality 1.
- **Files:** `DecisionMappingService.java`

### NEGOTIATION Scoring Broken (FIXED)
- **Problem:** Outcome conditions like `agreement_above_330000` weren't parseable by the mapping service.
- **Fix:** Added `agreement_above_N`, `agreement_below_N`, and `walked_away` condition handlers.
- **Files:** `DecisionMappingService.java`

### Student Can't See ASSIGNED_ONLY Simulations (FIXED)
- **Problem:** `getStudentSectionId()` called wrong URL (`/api/enrollments/students/...` instead of `/api/students/...`), causing 500 error → null sectionId → invisible simulations.
- **Fix:** Corrected URL to `/api/students/{id}/class-section`.
- **Files:** `SimulationStudentController.java`

### Student Simulation Details 403 Error (FIXED)
- **Problem:** Student portal called `GET /simulations/{id}` which hit the admin controller requiring admin role.
- **Fix:** Added student-facing `GET /{id}/details` endpoint that returns metadata without simulationData.
- **Files:** `SimulationStudentController.java`, `SimulationService.java`, `simulations.ts`

---

## 2. Mentor Guided Mode (NEW FEATURE)

### Phase 5.5 — Mentor Enrichment
- New AI generation phase that creates per-decision guidance for years 1-3
- **courseConnection:** Links decision to course theory
- **realWorldExample:** Real company scenario parallel
- **mentorTip:** Actionable "What to Consider" advice
- **mentorNote:** In-character mentor voice note
- **choiceHints:** Per-choice risk badges (low/medium/high) for NARRATIVE_CHOICE, DASHBOARD_ANALYSIS, CRISIS_RESPONSE
- **stakeholderHints:** Per-stakeholder priority badges (high/medium/low) for STAKEHOLDER_MEETING
- **candidateHints:** Per-candidate fit badges (strong/moderate/weak) for HIRE_FIRE
- **Files:** `SimulationGenerationService.java`, `SimulationDecisionDTO.java`, `SimulationService.java`

### Mentor Retirement Storyline
- Advisor character has `retirementYear: 3`, `backstory`, `yearTone`, `farewellMessage`
- Year 1: Warm teaching tone ("Let me walk you through this...")
- Year 2: Urgent departure ("I won't be here next year...")
- Year 3: Retired mentor's notes (LIGHT guidance, collapsed by default)
- Year 4+: No mentor, no guidance
- Farewell overlay at year transition when mentor retires
- **Files:** `SimulationGenerationService.java`, `YearTransition.tsx`

### Frontend Components
- **MentorGuidanceBanner:** Shows mentorNote, mentorTip, courseConnection, realWorldExample. FULL mode = open, LIGHT = collapsed with "Check notes"
- **HighlightedText:** Highlights keyword terms in narrative after typewriter completes
- **ConceptPanel:** Keywords filtered to only those matching current narrative text
- **DecisionInput:** Passes mentorGuidance through to all input components
- **Files:** `MentorGuidanceBanner.tsx` (new), `HighlightedText.tsx` (new), `ConceptPanel.tsx`, `DecisionInput.tsx`, `NarrativeChoiceInput.tsx`, `DashboardAnalysisInput.tsx`, `CrisisResponseInput.tsx`, `StakeholderMeetingInput.tsx`, `HireFireInput.tsx`

### Hint Priority Computation
- Priorities/risk labels computed deterministically from mappings + choice quality scores
- Positional fallback when mappings are missing or use unparseable conditions
- AI-generated hint keys remapped from "Name - Role" format to stakeholder IDs
- **Files:** `SimulationGenerationService.java`

### Backfill Endpoint
- `POST /{id}/regenerate-mentor-guidance` — runs Phase 5.5 on existing simulations without changing decisions
- Admin UI: "Regenerate Mentor Hints" button (Sparkles icon)
- 120s frontend timeout, 180s Azure OpenAI HTTP timeout
- DB connection leak prevented by disabling `open-in-view`
- **Files:** `SimulationGenerationService.java`, `SimulationAdminController.java`, `application.yml`

---

## 3. Negotiation UX Improvements

### Offer Comparison Bar
- Shows "Their Offer" vs "Your Offer" with visual gap indicator
- Direction hints (negotiate higher/lower)
- Suggested range from npcResponses (e.g., "Aim around $300K–$340K")
- Final round warning (pulsing "FINAL ROUND")
- Enter key support for counter-offer

### NPC Synthetic Responses
- When AI only generates round 1 responses, rounds 2-3 get synthetic NPC behavior
- Response varies by gap: lowball → "Not even close", moderate → "Do better", close → "Getting closer"
- NPC moves 25% toward player (not 50%) — prevents lowball exploits
- Final round: NPC makes "take it or leave it" offer, player chooses Accept/Walk Away

### Mentor Strategy Tip
- Shown as highlighted blue hint above the negotiation chat
- **Files:** `NegotiationInput.tsx`, `DecisionInput.tsx`

---

## 4. Simulation Management (NEW FEATURE)

### Backend Endpoints
- `DELETE /{id}` — permanently deletes simulation + all play data
- `POST /{id}/move-to-draft` — Published/Archived → Review
- `POST /{id}/move-to-published` — re-publish from any status
- `POST /play/{playId}/abandon` — marks play as ABANDONED

### Admin Editor Actions
- Contextual buttons: Move to Review, Re-publish, Archive, Delete (with confirmation dialog)
- Export always available

### Replay Logic
- Changed validation from "must have completed play" to "must not have IN_PROGRESS play"
- Abandon + restart flow works correctly

---

## 5. Dashboard & UX Improvements

### DashboardPanel
- Progress bar shows year progress (decisions completed this year)
- Risk level always visible: Safe (green) → Moderate → Elevated → Critical
- Score bar: thicker, gradient fill, glow effect, 700ms animation
- **Files:** `DashboardPanel.tsx`

### Resume Play
- Simulation listing page fetches play history alongside available simulations
- If IN_PROGRESS play exists, shows "Resume" button + progress indicator
- "New Game" as secondary action
- **Files:** `page.tsx` (simulations listing)

### Portfolio Slider Rounding
- Tiny remainders (<0.5% of budget) from slider step rounding treated as zero
- **Files:** `InvestmentPortfolioInput.tsx`

### Restart Button (dev)
- Temporary "Restart (dev)" button in play page top bar
- Abandons current play, starts fresh
- **Files:** Play page

---

## 6. Generation Quality Improvements

### Per-Year Validation + Retry
- After generating each year's decisions, validates:
  - Correct decision count
  - All decisions have choices with quality scores
  - Interactive types have required config (stakeholders, candidates, departments, metrics, npcResponses)
- Retries failed years up to 2 times
- **Files:** `SimulationGenerationService.java`

### Budget Department ID Enforcement
- Phase 2 prompt now explicitly lists financialModel department IDs
- Instructs AI: "Do NOT invent new department IDs"
- **Files:** `SimulationGenerationService.java`

### Comprehensive Debug Logging
- INFO-level logging across entire flow: generation, state building, mapping evaluation, decision submission, hint computation
- **Files:** `SimulationGenerationService.java`, `SimulationService.java`, `DecisionMappingService.java`

---

## Known Remaining Issues

1. **Year 2 sometimes short** — AI occasionally generates 3-4 decisions instead of 6 for Year 2. Retry logic helps but doesn't always fix it.
2. **Mappings consistently empty** — AI almost never generates proper `selected_contains()` mappings. The `autoGenerateMappings` fallback handles this but it's a workaround.
3. **Opening narrative not shown in admin editor** — Phase 4 generation issue, not investigated yet.
4. **Budget allocation sliders** — Could use better UI (optimal range indicators, reset button) per the original plan.
5. **Leaderboard** — Not yet implemented (Task 8 in original plan).
6. **Year-end financial flow** — Needs audit (Task 12 in original plan).
7. **Simulation export: course mapping** — Not yet implemented (Task 7).
8. **Temp restart button** — Should be removed before production deploy.
9. **Debug logging** — INFO-level logs should be changed to DEBUG before production.

---

## Files Modified (Summary)

### Backend (content service)
- `SimulationGenerationService.java` — Phase 5.5, validation, hint priorities, key remapping
- `SimulationService.java` — mentor guidance wiring, abandon, delete, status changes, student details
- `DecisionMappingService.java` — auto-generate mappings, agreement conditions, debug logging
- `BudgetCalculationService.java` — positional fallback for dept ID mismatch
- `SimulationAdminController.java` — delete, status change, regenerate endpoints
- `SimulationStudentController.java` — student details, abandon, class-section URL fix
- `SimulationDecisionDTO.java` — mentorGuidance field
- `SimulationPlayRepository.java` — deleteBySimulationIdAndClientId
- `FoundryAIService.java` — 180s HTTP timeout
- `application.yml` — open-in-view: false

### Frontend (student portal)
- `MentorGuidanceBanner.tsx` (new)
- `HighlightedText.tsx` (new)
- `DecisionInput.tsx` — mentor guidance routing
- `NarrativeChoiceInput.tsx` — choice hint badges
- `DashboardAnalysisInput.tsx` — choice hint badges
- `CrisisResponseInput.tsx` — choice hint badges
- `StakeholderMeetingInput.tsx` — stakeholder hint badges
- `HireFireInput.tsx` — candidate hint badges
- `NegotiationInput.tsx` — offer comparison, range hints, NPC fallback
- `ConceptPanel.tsx` — keyword filtering
- `DashboardPanel.tsx` — year progress, risk level, score animation
- `YearTransition.tsx` — mentor farewell
- `InvestmentPortfolioInput.tsx` — rounding fix
- Play page — mentor guidance wiring, restart button, highlighted text
- Simulations listing page — resume play

### Frontend (admin dashboard)
- Simulation editor page — delete, status change, regenerate mentor hints

### Shared Utils
- `simulations.ts` — MentorGuidance type, new API methods
- `index.ts` — MentorGuidance export
