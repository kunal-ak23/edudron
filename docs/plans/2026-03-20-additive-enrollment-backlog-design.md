# Additive Enrollment & Backlog Sections — Design Document

**Date:** 2026-03-20
**Status:** Approved

## Overview

Two related features:

1. **Additive Enrollment** — Select students from one section and enroll them in an additional section without removing their original enrollment. This is a general-purpose feature (unlike transfer, which is destructive).
2. **Backlog Flag** — `isBacklog` boolean on Class and Section entities. Used primarily to create backlog exam groups and distinguish backlog scores in reports.

## Requirements

### Additive Enrollment
- Admin selects students on the Enrollments page and clicks "Add to Section"
- Students are enrolled in the destination section (and its assigned courses, if any)
- Original enrollments are retained — this is purely additive
- No restrictions on destination section type (works for backlog and non-backlog)
- Supports bulk operation (multiple students at once)
- Skips students already enrolled in the destination section

### Backlog Flag
- `isBacklog` on both Class and Section entities, default `false`
- When a Class is marked `isBacklog=true`, all child sections auto-inherit `isBacklog=true`
- A backlog class cannot have non-backlog sections
- A regular class CAN have individual backlog sections (mixed)
- Setting a class back to `isBacklog=false` does NOT auto-clear sections (manual)
- Backlog sections are primarily used for mapping exams (not courses)

### Reporting (Phase 2)
- Reports show backlog exam scores as additional columns
- Admin can mark which score (original or backlog) counts as final grade
- `isFinalGrade` boolean on AssessmentSubmission (deferred to Phase 2)

## Approach

**Approach 1 (chosen): Minimal — no new tables, no enrollment linking.**

- Add `is_backlog` column to `student.classes` and `student.sections`
- Add new endpoints for additive enrollment (single + bulk)
- Extend existing Class/Section update endpoints to handle `isBacklog`
- Frontend: new "Add to Section" action on Enrollments page alongside Transfer

Rejected alternatives:
- **Linked Enrollments** (parentEnrollmentId) — over-engineered for now, can add later
- **Enrollment Groups** — new table, too complex for current needs

## Database Changes

**Migration: `db.changelog-0019-backlog-flag.yaml`** (student schema)

```yaml
student.classes:
  + is_backlog: boolean, default false, not null

student.sections:
  + is_backlog: boolean, default false, not null
```

No changes to the `enrollments` table. No new tables.

**Enforcement:**
- Service layer (not DB constraint): when Class.isBacklog is set to true, bulk-update all child sections to isBacklog=true
- When creating a new section under a backlog class, auto-set isBacklog=true
- Reject setting section.isBacklog=false if parent class.isBacklog=true

## Backend API

### New Endpoints

**Additive Enrollment — Single**
```
POST /api/enrollments/add-to-section
Body: { studentId, destinationSectionId, destinationClassId? }
Response: EnrollmentDTO (new enrollment)
```
- Creates enrollment in destination section
- Auto-enrolls in destination section's assigned courses (if any)
- Does NOT touch original enrollments
- Skips if student already in destination section

**Additive Enrollment — Bulk**
```
POST /api/enrollments/bulk-add-to-section
Body: { studentIds[], destinationSectionId, destinationClassId? }
Response: BulkEnrollmentResult { total, enrolled, skipped, failed, errorMessages }
```

### Modified Endpoints

**Update Class** — `PUT /api/classes/{id}`
- Accepts `isBacklog` in body
- When set to true: bulk-updates all child sections to isBacklog=true

**Update Section** — `PUT /api/sections/{id}`
- Accepts `isBacklog` in body
- Rejects isBacklog=false if parent class has isBacklog=true

## Frontend Changes

### Enrollments Page
- New "Add to Section" button alongside "Transfer" (appears when students are selected)
- Dialog with:
  - Destination class dropdown (optional filter)
  - Destination section dropdown (filtered by class, shows "Backlog" badge)
  - Confirmation message: "X students will be enrolled in [Section]. Existing enrollments will be retained."
- Calls `POST /api/enrollments/bulk-add-to-section`
- Success/failure toast with counts

### Class & Section Forms
- Class edit: `isBacklog` toggle switch
  - Info text when ON: "All sections under this class will be marked as backlog"
- Section edit: `isBacklog` toggle switch
  - Disabled with tooltip if parent class is backlog: "Inherited from class"

### Section/Class List Views
- "Backlog" badge (amber) next to names where isBacklog=true

### Student Portal
- No changes. Students see enrolled sections and exams as usual.

## Reporting (Phase 2 — Deferred)

- Reports join enrollments → sections → isBacklog to identify backlog scores
- Backlog exam scores rendered as extra columns (e.g., "Math (Backlog)")
- `isFinalGrade` boolean on AssessmentSubmission for admin override
- Frontend report views dynamically add columns when backlog data exists
