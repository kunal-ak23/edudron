# Calendar & Faculty Coordinator — Design Document

**Date:** 2026-03-28
**Status:** Approved

---

## Overview

Two related features for the EduDron platform:

1. **Faculty Coordinator** — Designate an instructor as the coordinator (class teacher) for a class or batch. Prerequisite for calendar permissions.
2. **Academic Calendar** — Tenant-wide calendar serving as the single source of truth for all academic events (holidays, exams, submissions, faculty meetings, reviews). Supports role-based visibility, recurring events, personal events, and bulk import/export.

---

## Feature 1: Faculty Coordinator

### What It Is

A faculty coordinator is like a class teacher — the point of contact for all coordination activities for a class or batch. It is NOT a new user role; it's a designation assigned to an existing `INSTRUCTOR` user.

### Data Model Changes

Add `coordinator_user_id VARCHAR` (nullable, references `idp.users`) to:
- `student.classes` table
- `student.batches` table

One coordinator per class, one per batch. They can be different people, or the same person. No coupling with `InstructorAssignment` — a coordinator doesn't need to be teaching in that class/batch.

### Constraints

- Only users with role `INSTRUCTOR` can be assigned as coordinator.
- Only `SYSTEM_ADMIN` and `TENANT_ADMIN` can assign/remove coordinators.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `PUT` | `/api/classes/{id}/coordinator` | Assign/remove coordinator for a class |
| `PUT` | `/api/batches/{id}/coordinator` | Assign/remove coordinator for a batch |
| `GET` | `/api/classes/{id}/coordinator` | Get coordinator details for a class |
| `GET` | `/api/batches/{id}/coordinator` | Get coordinator details for a batch |

Existing class/batch list and detail APIs will also return coordinator info in their responses.

### Audit Logging

| Action | Entity Type | Metadata |
|--------|------------|----------|
| Assign coordinator | `FacultyCoordinator` | classId/batchId, coordinatorUserId, assignedBy |
| Remove coordinator | `FacultyCoordinator` | classId/batchId, previous coordinatorUserId, removedBy |
| Change coordinator | `FacultyCoordinator` | classId/batchId, old → new coordinatorUserId, changedBy |

---

## Feature 2: Academic Calendar

### Architecture Decision

**Calendar lives in the student service** under a new `calendar` schema.

Rationale: Calendar events are fundamentally about "who sees what" — and the audience entities (students, instructors, batches, classes, sections) already live in the student service. Direct DB access to scope data means no inter-service calls, simpler queries, and lower latency.

### Data Model

#### `calendar.calendar_events` table

| Column | Type | Description |
|--------|------|-------------|
| `id` | `VARCHAR` (ULID) | Primary key |
| `client_id` | `UUID` | Tenant scope |
| `title` | `VARCHAR(255)` | Event title |
| `description` | `TEXT` | Optional details |
| `event_type` | `VARCHAR(50)` | See Event Types below |
| `custom_type_label` | `VARCHAR(100)` | Label when type is `CUSTOM` (nullable) |
| `start_date_time` | `TIMESTAMPTZ` | Event start |
| `end_date_time` | `TIMESTAMPTZ` | Event end (nullable for all-day events) |
| `all_day` | `BOOLEAN` | Whether it's an all-day event (default: false) |
| `audience` | `VARCHAR(30)` | See Audience Types below |
| `class_id` | `VARCHAR` | Target class (nullable) |
| `batch_id` | `VARCHAR` | Target batch (nullable) |
| `section_id` | `VARCHAR` | Target section (nullable) |
| `created_by_user_id` | `VARCHAR` | User who created the event |
| `is_recurring` | `BOOLEAN` | Default: false |
| `recurrence_rule` | `VARCHAR(255)` | iCal RRULE format (nullable) |
| `recurrence_parent_id` | `VARCHAR` | Points to original event for generated occurrences (nullable) |
| `meeting_link` | `VARCHAR(500)` | Zoom/Meet/Teams link (nullable) |
| `location` | `VARCHAR(255)` | Physical location (nullable) |
| `color` | `VARCHAR(7)` | Optional hex color override (nullable) |
| `metadata` | `JSONB` | Flexible extra data (attachments, room number, notes) |
| `is_active` | `BOOLEAN` | Soft delete (default: true) |
| `created_at` | `TIMESTAMPTZ` | Audit |
| `updated_at` | `TIMESTAMPTZ` | Audit |

#### Event Types (Enum)

`HOLIDAY`, `EXAM`, `SUBMISSION_DEADLINE`, `FACULTY_MEETING`, `REVIEW`, `GENERAL`, `CUSTOM`, `PERSONAL`

#### Audience Types (Enum)

`TENANT_WIDE`, `CLASS`, `BATCH`, `SECTION`, `FACULTY_ONLY`, `PERSONAL`

#### Indexes

- `(client_id, start_date_time)` — primary query pattern
- `(client_id, audience)` — filter by audience type
- `(client_id, class_id)` — class scope filter
- `(client_id, batch_id)` — batch scope filter
- `(client_id, section_id)` — section scope filter
- `(client_id, created_by_user_id, audience)` — personal events lookup

### Recurrence Strategy: Materialized Occurrences

When a recurring event is created, individual event rows are generated for each occurrence, linked via `recurrence_parent_id` to the parent event.

- Occurrences generated up to 6 months or end of academic year (whichever is sooner)
- Querying is a simple date range filter — no on-the-fly computation
- Individual occurrences can be independently edited or deleted
- Series operations (edit all, delete all, delete future) work via `recurrence_parent_id`

| Operation | Implementation |
|-----------|---------------|
| Delete all occurrences | `SET is_active = false WHERE recurrence_parent_id = :parentId OR id = :parentId` |
| Delete single occurrence | `SET is_active = false WHERE id = :occurrenceId` |
| Delete this and future | `SET is_active = false WHERE recurrence_parent_id = :parentId AND start_date_time >= :date` |
| Edit all occurrences | Update parent, cascade to children |
| Edit single occurrence | Update just that row |

### API Endpoints

#### Calendar Event CRUD

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `POST` | `/api/calendar/events` | Create event (single or recurring) | Admin, Coordinator (scoped) |
| `GET` | `/api/calendar/events` | List events (date range + filters) | All (filtered by visibility) |
| `GET` | `/api/calendar/events/{id}` | Get event details | All (if visible) |
| `PUT` | `/api/calendar/events/{id}` | Update event | Creator, Admin |
| `DELETE` | `/api/calendar/events/{id}` | Soft-delete single event | Creator, Admin |
| `DELETE` | `/api/calendar/events/{id}/series` | Soft-delete all occurrences | Creator, Admin |
| `PUT` | `/api/calendar/events/{id}/series` | Update all occurrences | Creator, Admin |

#### Personal Events

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `POST` | `/api/calendar/events/personal` | Create personal event | Any logged-in user |
| `GET` | `/api/calendar/events/personal` | List own personal events | Any logged-in user |

#### Import / Export

| Method | Path | Description | Who |
|--------|------|-------------|-----|
| `POST` | `/api/calendar/events/import` | Bulk import from CSV/Excel | Admin only |
| `GET` | `/api/calendar/events/export` | Export events to CSV | Admin, Coordinator |
| `GET` | `/api/calendar/events/import/template` | Download CSV template | Admin |

**Import CSV columns:** `title, description, eventType, startDateTime, endDateTime, allDay, audience, classCode, batchName, sectionName, meetingLink, location`

Backend validates, resolves names to IDs, creates events. Returns: `{ created: N, errors: [{ row, message }] }`

#### Query Parameters for GET /api/calendar/events

| Param | Type | Description |
|-------|------|-------------|
| `startDate` | ISO date | Range start (required) |
| `endDate` | ISO date | Range end (required) |
| `classId` | String | Filter by class |
| `batchId` | String | Filter by batch |
| `sectionId` | String | Filter by section |
| `eventType` | String | Filter by type |
| `audience` | String | Filter by audience level |

### Gateway Route

```yaml
- id: calendar-events
  uri: lb://core-api
  predicates:
    - Path=/api/calendar/**
  filters:
    - StripPrefix=0
```

### Permissions Model

| Who | Institutional events | Personal events |
|-----|---------------------|----------------|
| `SYSTEM_ADMIN` | Create all | Create own |
| `TENANT_ADMIN` | Create all within tenant | Create own |
| Coordinator (`INSTRUCTOR`) | Create for assigned scope | Create own |
| Regular `INSTRUCTOR` | View only | Create own |
| `STUDENT` | View only | Create own |

### Visibility / Query Logic

**For STUDENT:**
- `TENANT_WIDE` events
- `CLASS` events matching their enrolled class
- `BATCH` events matching their enrolled batch
- `SECTION` events matching their enrolled section
- Own `PERSONAL` events
- Excludes `FACULTY_ONLY`

**For INSTRUCTOR:**
- `TENANT_WIDE` events
- `FACULTY_ONLY` events
- `CLASS`/`BATCH`/`SECTION` events matching their instructor assignments
- Own `PERSONAL` events

**For TENANT_ADMIN / SYSTEM_ADMIN:**
- All institutional events within the tenant
- Own personal events

Implemented as a single `Specification<CalendarEvent>` — one DB query per request.

### Audit Logging

| Action | Entity Type | Metadata |
|--------|------------|----------|
| Create event | `CalendarEvent` | title, eventType, audience, target scope, createdBy |
| Update event | `CalendarEvent` | changed fields (before/after), updatedBy |
| Delete event | `CalendarEvent` | title, single/series, deletedBy |
| Update series | `CalendarEvent` | parentId, occurrences affected, updatedBy |
| Delete series | `CalendarEvent` | parentId, occurrences deleted, deletedBy |
| Bulk import | `CalendarEvent` | fileName, totalRows, createdCount, errorCount, importedBy |

---

## Frontend Design

### Admin Dashboard

**Dashboard Widget:** "Upcoming Events" card showing next 5-7 events with colored dots, title, date/time, audience badge. "View All" link to `/calendar`.

**Calendar Page (`/calendar`):**
- Toggle between Month Grid and Agenda List view
- Month Grid: color-coded event indicators, click day to see events in side panel
- Agenda List: chronological with date groupings
- Filter bar: event type, class, batch, section
- "+ Create Event" button → modal/drawer form
- Import/Export buttons (admin only)
- Personal events shown with distinct style

**Coordinator view:** Same page, "Create Event" limited to assigned scope.

### Student Portal

**Dashboard Widget:** Same "Upcoming Events" card, filtered to student's visibility.

**Calendar Page (`/calendar`):**
- Same grid/list toggle
- Read-only for institutional events
- "+ Personal Event" button → simplified form (no audience/scope fields)
- Filter by event type only

### Shared UI Components

| Component | Used In | Description |
|-----------|---------|-------------|
| `CalendarGrid` | Both | Monthly calendar grid with event dots |
| `EventList` | Both | Agenda-style chronological list |
| `EventCard` | Both | Single event display |
| `EventDetailModal` | Both | Full event details on click |
| `EventFormDrawer` | Admin | Create/edit event form |
| `PersonalEventForm` | Student | Simplified personal event form |
| `CalendarWidget` | Both | Dashboard widget (upcoming events) |
| `EventImportModal` | Admin | CSV upload with validation results |

### Color Scheme (Tenant-Level Configurable)

Default colors per event type, configurable by tenant admin in settings:

| Type | Default Color |
|------|--------------|
| `HOLIDAY` | `#22C55E` (green) |
| `EXAM` | `#EF4444` (red) |
| `SUBMISSION_DEADLINE` | `#F97316` (orange) |
| `FACULTY_MEETING` | `#8B5CF6` (purple) |
| `REVIEW` | `#0891B2` (teal) |
| `GENERAL` | `#1E3A5F` (navy) |
| `CUSTOM` | `#6B7280` (gray) |
| `PERSONAL` | `#60A5FA` (light blue) |

Stored as tenant feature/setting:
```json
{
  "calendar_event_colors": {
    "HOLIDAY": "#22C55E",
    "EXAM": "#EF4444",
    ...
  }
}
```

Admin dashboard settings page gets a "Calendar Colors" section. Frontend reads config and falls back to defaults if not configured.
