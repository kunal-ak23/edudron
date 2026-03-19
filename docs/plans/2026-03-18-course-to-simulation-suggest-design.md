# Course-to-Simulation Smart Suggest — Design Document

**Date:** 2026-03-18
**Status:** Approved

## Overview

A "Smart Suggest" feature on the simulation generation form that analyzes course content and existing simulations to auto-generate a simulation concept, subject, audience, and student-facing description. One backend AI call, no job queue.

## UX Flow

1. User opens simulation generation form
2. Selects a **course** from dropdown (moved to top of form)
3. Optionally selects specific **lectures** (multi-select checkboxes)
4. Clicks **"Smart Suggest"** button
5. Backend reads course/lecture content, checks existing simulations, calls AI
6. Response auto-fills: concept, subject, audience, description
7. Existing simulations for the course shown as a warning banner
8. User reviews/tweaks, clicks Generate

## Backend

### Endpoint

`POST /content/api/simulations/suggest-from-course`

### Request

```json
{
  "courseId": "01KKW...",
  "lectureIds": ["01KKX...", "01KKY..."]
}
```

- `courseId` — required
- `lectureIds` — optional. If provided, read those lectures' content. If empty, use course metadata + all lecture titles only.

### Processing

1. Load course (title, description)
2. If `lectureIds` provided → load lecture titles + content for those IDs
3. If `lectureIds` empty → load all lecture titles (no content)
4. Query existing simulations: `findByCourseIdAndClientId(courseId, clientId)`
5. Build AI prompt with course material + existing concepts to avoid
6. Single Azure OpenAI call (synchronous, not queued)
7. Return suggestion

### Response

```json
{
  "concept": "Credit Risk Assessment and Loan Portfolio Management",
  "subject": "Banking & Financial Services",
  "audience": "UNDERGRADUATE",
  "description": "Step into the role of a Credit Risk Analyst at a mid-sized commercial bank...",
  "existingSimulations": [
    { "id": "01KKW...", "title": "...", "concept": "...", "status": "PUBLISHED" }
  ]
}
```

### AI Prompt Strategy

```
You are analyzing course content to suggest a simulation concept.

Course: {title}
Description: {description}
Lectures: {lecture titles and content}

Existing simulations for this course (DO NOT duplicate these):
- {existing concept 1}
- {existing concept 2}

Suggest a NEW simulation that:
1. Teaches a core concept from this course material
2. Is different from existing simulations
3. Can be experienced as a 5-year career journey
4. Has a vivid, specific professional scenario

Return JSON:
{
  "concept": "the underlying concept being taught (never revealed to students until debrief)",
  "subject": "the academic subject area",
  "audience": "UNDERGRADUATE or MBA or GRADUATE (infer from course level)",
  "description": "2-3 sentence student-facing description of the simulation experience"
}
```

## Frontend

### Generation Form Redesign

**New field order:**
1. Course dropdown (moved to top, optional)
2. Lecture multi-select (shown when course selected)
3. Smart Suggest button (shown when course selected)
4. Concept (text input — auto-filled by suggest)
5. Subject (text input — auto-filled)
6. Audience (dropdown — auto-filled)
7. Description (textarea — auto-filled with rich description)
8. Existing simulations warning (if any)
9. Advanced options (years, decisions per year)
10. Generate button

### Smart Suggest Button States

- Hidden when no course selected
- "Smart Suggest" with sparkle icon when ready
- "Analyzing course content..." with spinner while loading
- Fields populate on success
- Error toast on failure

## Files Touched

| Layer | File | Change |
|-------|------|--------|
| Backend | `SimulationAdminController.java` | New endpoint |
| Backend | `SimulationService.java` | New `suggestFromCourse()` method |
| Backend | New `SimulationSuggestionRequest.java` | Request DTO |
| Backend | New `SimulationSuggestionResponse.java` | Response DTO |
| Frontend | `shared-utils/src/api/simulations.ts` | New API method + response type |
| Frontend | `admin-dashboard/simulations/generate/page.tsx` | Form redesign |

## Not In Scope

- Database schema changes
- Job queue (suggest is synchronous)
- Changes to actual simulation generation pipeline
- Student portal changes
