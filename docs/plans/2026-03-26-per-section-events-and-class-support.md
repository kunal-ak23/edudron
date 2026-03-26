# Per-Section Events & Class Support Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make project events section-specific (different names, dates, zoom links per section) with copy-across-sections support, and add class-level selection in the bulk project setup wizard.

**Architecture:** Add `section_id` to `project_event` table (nullable = global event). Wizard Step 5 shows a tabbed UI per section where events can be defined independently or copied from another section. Step 2 gains a class selector that auto-expands to show its sections. Backend `bulkSetup` accepts events keyed by section ID.

**Tech Stack:** Spring Boot (JPA, Liquibase), Next.js 14, React, Tailwind CSS, shadcn/ui

---

## Task 1: DB Migration — Add `section_id` to `project_event`

**Files:**
- Modify: `student/src/main/resources/db/changelog/db.changelog-0024-project-attachments.yaml`

**Step 1:** Add a new changeset to migration 0024 (unreleased branch work):

```yaml
  - changeSet:
      id: student-0024-event-section-id
      author: edudron
      changes:
        - addColumn:
            tableName: project_event
            schemaName: student
            columns:
              - column:
                  name: section_id
                  type: varchar(26)
        - createIndex:
            tableName: project_event
            schemaName: student
            indexName: idx_project_event_section_id
            columns:
              - column: { name: section_id }
```

**Step 2:** Verify migration file is syntactically valid, compile backend.

**Step 3:** Commit: `feat: add section_id column to project_event table`

---

## Task 2: Update `ProjectEvent` Entity and DTO

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/domain/ProjectEvent.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/ProjectEventDTO.java`

**Step 1:** Add `sectionId` field to `ProjectEvent.java`:

```java
@Column(name = "section_id", length = 26)
private String sectionId;

// getter/setter
public String getSectionId() { return sectionId; }
public void setSectionId(String sectionId) { this.sectionId = sectionId; }
```

**Step 2:** Add `sectionId` to `ProjectEventDTO.java` (field + fromEntity mapping + getter/setter).

**Step 3:** Compile: `./gradlew :core-api:compileJava`

**Step 4:** Commit: `feat: add sectionId to ProjectEvent entity and DTO`

---

## Task 3: Update `ProjectEventRepository` with section queries

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/repo/ProjectEventRepository.java`

**Step 1:** Add query methods:

```java
List<ProjectEvent> findByProjectIdAndSectionIdAndClientIdOrderBySequenceAsc(
    String projectId, String sectionId, UUID clientId);

List<ProjectEvent> findByProjectIdAndSectionIdIsNullAndClientIdOrderBySequenceAsc(
    String projectId, UUID clientId);
```

**Step 2:** Compile and commit: `feat: add section-scoped event repository queries`

---

## Task 4: Update `ProjectService.addEvent()` to accept `sectionId`

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ProjectService.java`

**Step 1:** Add `sectionId` parameter to `addEvent()` method signature. Set it on the entity before save.

**Step 2:** Update `ProjectController.addEvent()` to read `sectionId` from the request body and pass it through.

**Step 3:** Compile and commit: `feat: support section-scoped event creation`

---

## Task 5: Update `BulkProjectSetupRequest` events to be section-keyed

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/BulkProjectSetupRequest.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ProjectService.java`

**Step 1:** Change the events field in `BulkProjectSetupRequest`:

```java
// Replace: private List<EventInput> events;
// With: events keyed by sectionId (null key = global)
private Map<String, List<EventInput>> eventsBySectionId;
```

Keep backward compatibility — if `events` (flat list) is provided, treat as global events. If `eventsBySectionId` is provided, create section-scoped events.

**Step 2:** Update `bulkSetup()` step 6 to iterate `eventsBySectionId`:

```java
// For each sectionId -> list of events, create events with that sectionId
if (request.getEventsBySectionId() != null) {
    for (Map.Entry<String, List<EventInput>> entry : request.getEventsBySectionId().entrySet()) {
        String sectionId = entry.getKey();
        for (int i = 0; i < entry.getValue().size(); i++) {
            EventInput ev = entry.getValue().get(i);
            // ... create event with sectionId
        }
    }
} else if (request.getEvents() != null) {
    // backward compat: create global events (sectionId = null)
}
```

**Step 3:** Compile and commit: `feat: support per-section events in bulk project setup`

---

## Task 6: Update shared-utils types

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/projects.ts`

**Step 1:** Update `BulkProjectSetupRequest`:

```typescript
  events?: Array<{ name: string; dateTime?: string; zoomLink?: string; hasMarks?: boolean; maxMarks?: number; sequence?: number }>
  eventsBySectionId?: Record<string, Array<{ name: string; dateTime?: string; zoomLink?: string; hasMarks?: boolean; maxMarks?: number; sequence?: number }>>
```

Update `ProjectEventDTO`:

```typescript
export interface ProjectEventDTO {
  // ... existing fields
  sectionId?: string
}
```

**Step 2:** Run `npm run build` in shared-utils.

**Step 3:** Commit: `feat: add section events types to shared-utils`

---

## Task 7: Frontend Step 2 — Add Class-Level Selection

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/bulk-create/page.tsx`

**Step 1:** After course is selected AND sections are loaded, group sections by `classId`. Show a class-level UI:

```
[ ] Morning Class (3 sections, 45 students)
    [x] Section A (15 students)
    [x] Section B (15 students)
    [x] Section C (15 students)
[ ] Evening Class (2 sections, 20 students)
    [x] Section D (10 students)
    [x] Section E (10 students)
```

- Clicking a class checkbox toggles all its sections
- Individual sections can still be toggled independently
- Use `classesApi.listClassesByInstitute()` or derive class info from the section data (sections already have `classId`)

**Step 2:** This is purely a UI grouping change — the `selectedSectionIds` set stays the same, just visually grouped by class.

**Step 3:** Commit: `feat: group sections by class in bulk project setup`

---

## Task 8: Frontend Step 5 — Per-Section Events UI

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/bulk-create/page.tsx`

**Step 1:** Replace the flat events list with a tabbed/sectioned UI:

State change:
```typescript
// Replace: const [events, setEvents] = useState<EventEntry[]>([])
// With:
const [eventsBySectionId, setEventsBySectionId] = useState<Record<string, EventEntry[]>>({})
const [activeEventSection, setActiveEventSection] = useState<string>('')
```

**Step 2:** UI structure:

```
Section tabs: [Morning] [Evening] [All Sections]

"Copy events from Morning to Evening" button

Event list for active tab:
  Event 1: [Name] [DateTime] [Zoom] [HasMarks] [MaxMarks] [x Delete]
  Event 2: ...
  [+ Add Event]
```

- "All Sections" tab creates events with no section_id (global)
- Section-specific tabs create events scoped to that section
- "Copy from X" dropdown copies all events from one section to another (overwrite or append option)

**Step 3:** Initialize sections from `selectedSections` when entering step 5. Default to first section tab.

**Step 4:** Commit: `feat: per-section events UI in bulk project wizard`

---

## Task 9: Wire Events in Submit + Review

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/bulk-create/page.tsx`

**Step 1:** Update submit to send `eventsBySectionId`:

```typescript
eventsBySectionId: Object.fromEntries(
  Object.entries(eventsBySectionId)
    .filter(([_, evts]) => evts.some(e => e.name.trim()))
    .map(([sid, evts]) => [
      sid,
      evts.filter(e => e.name.trim()).map((e, idx) => ({
        name: e.name.trim(),
        dateTime: e.dateTime ? new Date(e.dateTime).toISOString() : undefined,
        zoomLink: e.zoomLink.trim() || undefined,
        hasMarks: e.hasMarks,
        maxMarks: e.hasMarks ? e.maxMarks : undefined,
        sequence: idx + 1,
      }))
    ])
),
```

**Step 2:** Update Review step (Step 6) to show events grouped by section.

**Step 3:** Commit: `feat: wire per-section events into submit and review`

---

## Task 10: Add `classId` to Project Entity (optional enhancement)

**Files:**
- Modify: `student/src/main/resources/db/changelog/db.changelog-0024-project-attachments.yaml`
- Modify: `student/src/main/java/com/datagami/edudron/student/domain/Project.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/ProjectDTO.java`

**Step 1:** Add `class_id` column to project table (text, nullable). Add to entity and DTO.

**Step 2:** In `bulkSetup()`, if sections share a common classId, set it on the project.

**Step 3:** Commit: `feat: add classId to Project entity`

---

## Task 11: Final Integration Test

**Step 1:** Restart core-api to run migrations.
**Step 2:** Test bulk create flow end-to-end:
- Select course → select sections (grouped by class) → configure groups → project details → add per-section events with copy → review → create
**Step 3:** Verify events are created with correct section_ids in the database.
**Step 4:** Commit all remaining changes.

---

## Key Design Decisions

1. **`section_id` on `project_event` is nullable** — null means the event applies globally (backward compatible with existing events).
2. **`eventsBySectionId` uses section ID as key** — the special key `"_global"` can be used for events that apply to all sections.
3. **Class selection is a UI grouping** — no change to the underlying section-based model. Classes just provide a convenient toggle-all behavior.
4. **Copy events between sections** — this is purely a frontend operation (copies the event array from one section's state to another). The backend just receives the final per-section event lists.
5. **Backward compatible** — the flat `events` list in the request still works for global events.
