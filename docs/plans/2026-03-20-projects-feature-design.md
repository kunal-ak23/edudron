# Projects Feature — Design Document

**Date:** 2026-03-20
**Status:** Approved

## Overview

A group-based project management feature where students are divided into groups within a section, assigned problem statements from a question bank, tracked through multiple events (attendance + optional grading), and submit their work via URL. Behind the `PROJECTS` feature flag.

## Requirements Summary

- **Groups:** Auto-created from a section's students (random, configurable group size). Uneven distribution allowed.
- **Problem Statements:** Project question bank tagged to courses. Round-robin assignment to groups. Bulk upload support.
- **Events:** Dynamic, per-project. Each event has name, date/time, Zoom link. Can be attendance-only or attendance + marks (with configurable max marks per event).
- **Attendance:** Per-student per-event, tracked within groups.
- **Grading:** Per-student per-event (when marks enabled). Project-level max marks configurable (default 100).
- **Submissions:** One per group (any member can submit). URL-based (GitHub, etc.). Cutoff date with strict/flexible mode.
- **Student Portal:** Full transparency — see group members, problem statement, attendance, marks, submission status.
- **Feature Flag:** `PROJECTS` in `TenantFeatureType`, default false. Enforced on frontend (sidebar) and backend (key endpoints).

## Architecture

**Approach 1 (chosen): Split across Student + Content services.**

- **Student service:** Project, groups, group members, events, attendance, grades, submissions
- **Content service:** Project question bank (same pattern as exam question bank)
- **Identity service:** `TenantFeatureType.PROJECTS` feature flag

## Data Model

### Student Schema — New Tables

```
project
  id              ULID (PK)
  client_id       UUID (NOT NULL)
  course_id       VARCHAR(26)
  section_id      VARCHAR(26) (NOT NULL)
  title           VARCHAR(500) (NOT NULL)
  description     TEXT
  max_marks       INT (DEFAULT 100)
  submission_cutoff TIMESTAMPTZ
  late_submission_allowed BOOLEAN (DEFAULT FALSE)
  status          VARCHAR(20) (DEFAULT 'DRAFT') — DRAFT, ACTIVE, COMPLETED, ARCHIVED
  created_by      VARCHAR(255)
  created_at      TIMESTAMPTZ
  updated_at      TIMESTAMPTZ

  Indexes: client_id, section_id, course_id, status

project_group
  id                  ULID (PK)
  client_id           UUID (NOT NULL)
  project_id          VARCHAR(26) (NOT NULL, FK project)
  group_number        INT (NOT NULL)
  problem_statement_id VARCHAR(26) — FK to content.project_question_bank
  submission_url      TEXT
  submitted_at        TIMESTAMPTZ
  submitted_by        VARCHAR(26) — studentId who submitted
  created_at          TIMESTAMPTZ

  Indexes: project_id, client_id

project_group_member
  id          ULID (PK)
  client_id   UUID (NOT NULL)
  group_id    VARCHAR(26) (NOT NULL, FK project_group)
  student_id  VARCHAR(26) (NOT NULL)

  Indexes: group_id, student_id
  Unique: (group_id, student_id)

project_event
  id          ULID (PK)
  client_id   UUID (NOT NULL)
  project_id  VARCHAR(26) (NOT NULL, FK project)
  name        VARCHAR(255) (NOT NULL)
  date_time   TIMESTAMPTZ
  zoom_link   TEXT
  has_marks   BOOLEAN (DEFAULT FALSE)
  max_marks   INT — only when has_marks = true
  sequence    INT

  Indexes: project_id

project_event_attendance
  id          ULID (PK)
  client_id   UUID (NOT NULL)
  event_id    VARCHAR(26) (NOT NULL, FK project_event)
  student_id  VARCHAR(26) (NOT NULL)
  group_id    VARCHAR(26) (NOT NULL, FK project_group)
  present     BOOLEAN (NOT NULL)

  Unique: (event_id, student_id)

project_event_grade
  id          ULID (PK)
  client_id   UUID (NOT NULL)
  event_id    VARCHAR(26) (NOT NULL, FK project_event)
  student_id  VARCHAR(26) (NOT NULL)
  group_id    VARCHAR(26) (NOT NULL, FK project_group)
  marks       INT (NOT NULL)

  Unique: (event_id, student_id)
```

### Content Schema — New Table

```
project_question_bank
  id                ULID (PK)
  client_id         UUID (NOT NULL)
  course_id         VARCHAR(26) (NOT NULL)
  title             VARCHAR(500) (NOT NULL)
  problem_statement TEXT (NOT NULL)
  key_technologies  TEXT[] — PostgreSQL array
  tags              TEXT[]
  difficulty        VARCHAR(20) — EASY, MEDIUM, HARD
  is_active         BOOLEAN (DEFAULT TRUE)
  created_at        TIMESTAMPTZ
  updated_at        TIMESTAMPTZ

  Indexes: client_id, course_id, is_active
```

## Backend API

### Student Service — Project Management (Admin)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/projects` | Create project |
| GET | `/api/projects` | List projects (filter: courseId, sectionId, status) |
| GET | `/api/projects/{id}` | Get project details |
| PUT | `/api/projects/{id}` | Update project |
| POST | `/api/projects/{id}/activate` | Set status ACTIVE |
| POST | `/api/projects/{id}/complete` | Set status COMPLETED |
| POST | `/api/projects/{id}/generate-groups` | Auto-create random groups (body: {groupSize}) |
| GET | `/api/projects/{id}/groups` | List groups with members |
| PUT | `/api/projects/{id}/groups/{groupId}` | Update group (reassign members, change statement) |
| POST | `/api/projects/{id}/assign-statements` | Round-robin assign from question bank |
| POST | `/api/projects/{id}/events` | Add event |
| PUT | `/api/projects/{id}/events/{eventId}` | Update event |
| DELETE | `/api/projects/{id}/events/{eventId}` | Delete event |
| POST | `/api/projects/{id}/events/{eventId}/attendance` | Bulk save attendance |
| POST | `/api/projects/{id}/events/{eventId}/grades` | Bulk save grades |
| POST | `/api/projects/{id}/groups/{groupId}/submit` | Submit URL |

### Content Service — Project Question Bank

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/project-questions` | Create question |
| GET | `/api/project-questions` | List (filter: courseId, tags, difficulty) |
| GET | `/api/project-questions/{id}` | Get question |
| PUT | `/api/project-questions/{id}` | Update question |
| DELETE | `/api/project-questions/{id}` | Delete question |
| POST | `/api/project-questions/bulk-upload` | Bulk create from CSV/JSON |

### Student Portal Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/projects/my-projects` | List active projects for current student |
| GET | `/api/projects/{id}/my-group` | Get group, members, problem statement |
| POST | `/api/projects/{id}/my-group/submit` | Submit URL |
| GET | `/api/projects/{id}/my-attendance` | Attendance + grades across events |

## Frontend — Admin Dashboard

### Pages
- `/projects` — List all projects (status tabs, search, filters)
- `/projects/new` — Create project (course, section, title, max marks, cutoff, events)
- `/projects/[id]` — Detail with tabs: Overview, Groups, Events, Attendance/Grades
- `/project-questions` — Question bank list
- `/project-questions/new` — Create question
- `/project-questions/bulk-upload` — CSV/JSON upload

### Key UI Flows
1. **Create Project:** Pick course → section → title, max marks, cutoff, late toggle
2. **Generate Groups:** Enter group size → random assignment → admin can reassign
3. **Assign Statements:** Fetch from question bank → round-robin → admin can swap
4. **Attendance:** Grid (students grouped by group × present/absent checkboxes)
5. **Grading:** Grid (students × marks input) for events with hasMarks=true

### Sidebar
- "Projects" menu item with sub-items: "All Projects", "Question Bank"
- Hidden when PROJECTS feature flag is disabled

## Frontend — Student Portal

### Pages
- `/projects` — List active projects
- `/projects/[id]` — Group members, problem statement, events timeline, attendance/marks, submission form

### Sidebar
- "Projects" menu item, hidden when feature flag disabled

## Feature Flag

- Add `PROJECTS` to `TenantFeatureType` enum with `defaultValue: false`
- **Frontend:** `useProjectsFeature` hook (same pattern as `useSimulationFeature`)
- **Backend:** `requireProjectsEnabled()` on key endpoints (create project, generate groups, my-projects, submit)

## Gateway Routing

- `/api/projects/**` → Core API (student service) — needs new gateway route
- `/api/project-questions/**` → Content (8082) — needs new gateway route

## Key Business Logic

### Group Generation (generate-groups)
1. Get all students in the section (via enrollments)
2. Shuffle randomly
3. Divide into groups of `groupSize`
4. If remainder, distribute extra students to first N groups
5. Create `project_group` + `project_group_member` records

### Statement Assignment (assign-statements)
1. Fetch all active questions for the course from content service
2. Shuffle questions
3. Round-robin assign to groups (if more groups than questions, wrap around)
4. Update `project_group.problem_statement_id`

### Submission Cutoff
- When student submits via `/my-group/submit`:
  - If `submissionCutoff` is null → allow
  - If current time < cutoff → allow
  - If current time >= cutoff AND `lateSubmissionAllowed=true` → allow (mark as late)
  - If current time >= cutoff AND `lateSubmissionAllowed=false` → reject with error
