# Simulation Gameplay UI Improvements Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Expand the simulation gameplay UI to use the full screen width, adding a left sidebar for concept explanations and a right sidebar for decision history, scores, funds, and performance dashboard — incorporating all customer feedback.

**Architecture:** Transform the current single-column (max-w-2xl centered) layout into a 3-panel design: Left Panel (concept/keyword explanations), Center (existing gameplay), Right Panel (live dashboard with decisions, scores, funds, warnings). The StatusHUD expands to full-width. Backend needs minor additions to provide concept keywords and decision quality ratings.

**Tech Stack:** Next.js 14, React, Tailwind CSS, existing simulation components

---

## Current Layout Problem

```
┌──────────────────────────────────────────────────────────┐
│                     Top Nav Bar                          │
├──────────┬──────────────────────────┬────────────────────┤
│          │                          │                    │
│  EMPTY   │   Gameplay Content       │    EMPTY           │
│  SPACE   │   (max-w-2xl = 640px)    │    SPACE           │
│  ~350px  │                          │    ~350px          │
│          │                          │                    │
├──────────┴──────────────────────────┴────────────────────┤
│                   Status HUD (fixed)                     │
└──────────────────────────────────────────────────────────┘
```

## Target Layout

```
┌──────────────────────────────────────────────────────────┐
│                     Top Nav Bar                          │
├────────────┬────────────────────────┬────────────────────┤
│ LEFT PANEL │   Gameplay Content     │ RIGHT PANEL        │
│ ~280px     │   (flex-1, min 480px)  │ ~320px             │
│            │                        │                    │
│ ■ Concept  │   [Narrative Text]     │ ■ Score: 1250      │
│   Explain  │                        │ ■ Budget: $2.4M    │
│            │   [Decision Input]     │ ■ Role: VP Ops     │
│ ■ Keywords │                        │ ■ Band: THRIVING   │
│   - Term 1 │                        │ ■ Year 2/4         │
│   - Term 2 │                        │                    │
│   - Term 3 │   [Advisor Dialog]     │ ■ Decision History │
│            │                        │   ├ Q1: Good ✓     │
│ ■ Key      │                        │   ├ Q2: Bad ✗      │
│   Points   │                        │   └ Q3: Medium ~   │
│   from     │                        │                    │
│   previous │                        │ ■ Bad Decisions: 2 │
│   answers  │                        │ ■ Warning: 3 more  │
│            │                        │   bad = FIRED      │
│            │                        │                    │
│            │                        │ ■ Key Insights     │
│            │                        │   revealed after   │
│            │                        │   each question    │
├────────────┴────────────────────────┴────────────────────┤
│              Expanded Status HUD (full-width)            │
└──────────────────────────────────────────────────────────┘

On mobile: Panels collapse to tabs/drawer
```

---

## Task 1: Backend — Add concept keywords and decision quality to simulation state

**Files:**
- Modify: backend simulation state response to include concept keywords per decision and decision quality history

**What to add to the simulation data model:**

The simulation's `simulationData` JSON already contains all decisions. We need to:

1. Add `conceptKeywords` array to each decision in the generation prompt (AI-generated simulations):
   ```json
   {
     "decisionId": "y1d1",
     "narrative": "...",
     "conceptKeywords": [
       { "term": "Cross-functional team", "explanation": "A team composed of members from different departments..." },
       { "term": "RACI Matrix", "explanation": "A responsibility assignment chart that maps..." }
     ]
   }
   ```

2. The backend already tracks decision quality via scoring — we need to expose it in `SimulationStateDTO`:
   ```java
   // Add to state response
   private List<Map<String, Object>> decisionHistory; // [{decisionId, narrative snippet, choiceLabel, quality (GOOD/MEDIUM/BAD), score, year, decision}]
   private Integer badDecisionCount;
   private Integer goodDecisionCount;
   private List<String> keyInsights; // Accumulated key points from previous answers
   ```

**Step 1:** Modify the simulation state builder in the content service to include decision history, quality counts, and accumulated key insights.

**Step 2:** Compile and commit.

---

## Task 2: Frontend — Create Left Panel component (ConceptPanel)

**Files:**
- Create: `frontend/apps/student-portal/src/components/simulation/ConceptPanel.tsx`

**Features:**
- Shows concept being tested in this simulation (from SimulationDTO)
- Keyword explanations for current decision (tooltip-style cards)
- Key points/insights accumulated from previous answers
- Collapsible sections
- Dark theme matching gameplay

**Props:**
```typescript
interface ConceptPanelProps {
  simulationTitle: string
  concept: string
  keywords: Array<{ term: string; explanation: string }>
  keyInsights: string[]
  currentYear: number
  currentDecision: number
}
```

**UI:**
```
┌─ Concept Panel ──────────────┐
│                              │
│ 📘 Operating Model Design    │
│ (concept being tested)       │
│                              │
│ ─── Keywords ────────────── │
│ 🔑 Cross-functional team    │
│ A team composed of members   │
│ from different departments   │
│ working toward a common goal │
│                              │
│ 🔑 RACI Matrix              │
│ A responsibility assignment  │
│ chart...                     │
│                              │
│ ─── Key Insights ─────────  │
│ 💡 Investing in R&D early   │
│    yields compounding returns│
│                              │
│ 💡 Stakeholder alignment is │
│    critical before major     │
│    restructuring             │
└──────────────────────────────┘
```

---

## Task 3: Frontend — Create Right Panel component (DashboardPanel)

**Files:**
- Create: `frontend/apps/student-portal/src/components/simulation/DashboardPanel.tsx`

**Features:**
- Live score display (large, prominent)
- Budget/funds display (formatted, color-coded)
- Current role and performance band
- Decision history timeline with quality indicators (✓ Good, ~ Medium, ✗ Bad)
- Bad decision counter with firing warning
- Promotion tracker
- Year progress

**Props:**
```typescript
interface DashboardPanelProps {
  score: number
  budget: number
  role: string
  performanceBand: string
  currentYear: number
  totalYears: number
  currentDecision: number
  totalDecisions: number
  decisionHistory: Array<{
    year: number
    decision: number
    label: string
    quality: 'GOOD' | 'MEDIUM' | 'BAD'
    scoreChange: number
  }>
  badDecisionCount: number
  goodDecisionCount: number
  keyInsights: string[]
}
```

**UI:**
```
┌─ Dashboard Panel ────────────┐
│                              │
│ Score      1,250 pts         │
│ ████████████░░░ 75%          │
│                              │
│ Budget     $2.4M             │
│ Role       VP of Operations  │
│ Band       🟢 THRIVING      │
│                              │
│ Year 2 of 4 │ Decision 3/5  │
│                              │
│ ─── Decisions ────────────  │
│ Y1-Q1 ✓ Hired specialist    │
│ Y1-Q2 ✗ Cut R&D budget      │
│ Y1-Q3 ~ Partial outsource   │
│ Y2-Q1 ✓ Cross-train team    │
│ Y2-Q2 ✓ Invested in tech    │
│ Y2-Q3 → Current decision    │
│                              │
│ ─── Performance ──────────  │
│ ✓ Good:  4  │ ✗ Bad: 1     │
│ ⚠ Warning: 2 more bad       │
│   decisions = FIRED          │
│                              │
│ ─── Insights ─────────────  │
│ 💡 Early R&D investment     │
│    compounds over time       │
│ 💡 Team morale impacts      │
│    productivity by 2x        │
└──────────────────────────────┘
```

---

## Task 4: Frontend — Transform gameplay layout to 3-panel

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx`

**Changes:**
1. Replace `max-w-2xl mx-auto` center-only layout with 3-panel grid:
   ```tsx
   <div className="flex h-[calc(100vh-56px)]">
     {/* Left Panel - hidden on mobile */}
     <div className="hidden lg:block w-72 flex-shrink-0 overflow-y-auto border-r border-[#1E3A5F]/30">
       <ConceptPanel {...} />
     </div>

     {/* Center - gameplay */}
     <div className="flex-1 min-w-0 overflow-y-auto px-4 py-6 pb-32">
       {/* existing gameplay content, remove max-w-2xl */}
     </div>

     {/* Right Panel - hidden on mobile */}
     <div className="hidden lg:block w-80 flex-shrink-0 overflow-y-auto border-l border-[#1E3A5F]/30">
       <DashboardPanel {...} />
     </div>
   </div>
   ```

2. Mobile: Add tab buttons at top to toggle between "Game", "Concepts", "Dashboard"

3. Expand StatusHUD to span full width with more metrics visible

---

## Task 5: Frontend — Improve Budget Allocation Sliders

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/BudgetAllocationInput.tsx`

**Improvements:**
- Replace HTML range inputs with styled slider components
- Add visual bar chart showing allocation proportions
- Color-code sliders (green for within optimal range, red for extreme)
- Show allocation as both absolute $ and percentage
- Add "Reset to Equal" button
- Add tooltips explaining what each bucket does
- Animate changes smoothly

---

## Task 6: Frontend — Improve StatusHUD with expanded metrics

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/StatusHUD.tsx`

**Changes:**
- Expand to show: Score, Budget, Role, Band, Year, Decision, Bad Decision Count
- Add a mini decision trail (last 3 decisions with quality dots)
- Make budget more prominent (larger font, color-coded)
- Add subtle pulse animation when score changes

---

## Task 7: Backend — Export simulation with course mapping option

**Files:**
- Modify: content service export/import endpoints

**Changes:**
- On import, accept optional `targetCourseId` parameter
- If provided, map the simulation to the target course instead of the original
- Frontend: Add course selector dropdown in the import dialog

**Import request update:**
```java
// Add to import endpoint
@PostMapping("/import")
public ResponseEntity<SimulationDTO> importSimulation(
    @RequestBody SimulationExportDTO exportData,
    @RequestParam(required = false) String courseId) {
    // If courseId provided, override the simulation's courseId
}
```

---

## Task 8: Frontend — Add Leaderboard

**Files:**
- Create: `frontend/apps/student-portal/src/app/simulations/[id]/leaderboard/page.tsx`

**Features:**
- Top scores for the simulation across all students
- Show: Rank, Student Name, Score, Decisions (good/bad), Final Role, Completion Date
- Highlight current student's position
- Filter by section/class

**Backend endpoint needed:**
- `GET /content/api/simulations/{id}/leaderboard` — returns top plays with scores

---

## Summary of Customer Feedback Mapping

| Feedback Item | Task | Status |
|---------------|------|--------|
| Explain advanced keywords | Task 1 (backend) + Task 2 (left panel) | Planned |
| Show funds on right side, more visible | Task 3 (right panel) + Task 6 (HUD) | Planned |
| Decisions should be visible | Task 3 (decision history timeline) | Planned |
| Key points from answers on right side | Task 3 (key insights section) | Planned |
| Improve budget allocation sliders | Task 5 | Planned |
| Bad decisions count + firing warning | Task 3 (performance section) | Planned |
| Decision, promotion tracking | Task 3 (decision history + promotion) | Planned |
| Add scoreboard and leaderboard | Task 8 | Planned |
| Export: map to different course | Task 7 | Planned |
