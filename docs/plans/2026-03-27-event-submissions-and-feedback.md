# Event-Level Submissions & Feedback Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable per-event submissions (URL + file attachments) with faculty feedback and review status, plus project phase tracking via currentEventId.

**Architecture:** New `project_event_submission` and `project_event_feedback` tables. Events gain `hasSubmission` flag. Each group can submit per event independently. Faculty reviews submissions and leaves feedback with status (REVIEWED/NEEDS_REVISION). Project tracks current active phase via `current_event_id`. The old group-level `submissionUrl` is kept for backward compat but the new event-level submissions become the primary flow.

**Tech Stack:** Spring Boot (JPA, Liquibase), Next.js 14, React, Tailwind CSS, shadcn/ui

---

## Task 1: DB Migration — New tables and columns

**Files:**
- Create: `student/src/main/resources/db/changelog/db.changelog-0025-event-submissions.yaml`
- Modify: `student/src/main/resources/db/changelog/student-master.yaml`

**Step 1:** Create migration file:

```yaml
databaseChangeLog:
  # Add hasSubmission flag to project_event
  - changeSet:
      id: student-0025-event-has-submission
      author: edudron
      changes:
        - addColumn:
            tableName: project_event
            schemaName: student
            columns:
              - column:
                  name: has_submission
                  type: boolean
                  defaultValueBoolean: false

  # Add current_event_id to project (tracks active phase)
  - changeSet:
      id: student-0025-project-current-event
      author: edudron
      changes:
        - addColumn:
            tableName: project
            schemaName: student
            columns:
              - column:
                  name: current_event_id
                  type: varchar(26)

  # Event submission table (per group, per event)
  - changeSet:
      id: student-0025-event-submission
      author: edudron
      changes:
        - createTable:
            tableName: project_event_submission
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: project_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: event_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: group_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: submission_url, type: text }
              - column: { name: submission_text, type: text }
              - column: { name: submitted_by, type: varchar(255), constraints: { nullable: false } }
              - column: { name: submitted_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: version, type: int, defaultValueNumeric: 1, constraints: { nullable: false } }
              - column:
                  name: status
                  type: varchar(30)
                  defaultValue: SUBMITTED
                  constraints: { nullable: false }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now() }
        - createIndex:
            tableName: project_event_submission
            schemaName: student
            indexName: idx_event_submission_event_group
            columns:
              - column: { name: event_id }
              - column: { name: group_id }
        - createIndex:
            tableName: project_event_submission
            schemaName: student
            indexName: idx_event_submission_project
            columns:
              - column: { name: project_id }
        - createIndex:
            tableName: project_event_submission
            schemaName: student
            indexName: idx_event_submission_client
            columns:
              - column: { name: client_id }

  # Event feedback table (faculty feedback per submission)
  - changeSet:
      id: student-0025-event-feedback
      author: edudron
      changes:
        - createTable:
            tableName: project_event_feedback
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: submission_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: event_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: group_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: comment, type: text, constraints: { nullable: false } }
              - column: { name: feedback_by, type: varchar(255), constraints: { nullable: false } }
              - column: { name: feedback_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column:
                  name: status
                  type: varchar(30)
                  defaultValue: REVIEWED
                  constraints: { nullable: false }
                  remarks: "REVIEWED or NEEDS_REVISION"
        - createIndex:
            tableName: project_event_feedback
            schemaName: student
            indexName: idx_event_feedback_submission
            columns:
              - column: { name: submission_id }
        - createIndex:
            tableName: project_event_feedback
            schemaName: student
            indexName: idx_event_feedback_event_group
            columns:
              - column: { name: event_id }
              - column: { name: group_id }
```

**Step 2:** Register in student-master.yaml.

**Step 3:** Commit: `feat: add migration for event submissions and feedback tables`

---

## Task 2: JPA Entities

**Files:**
- Create: `student/.../domain/ProjectEventSubmission.java`
- Create: `student/.../domain/ProjectEventFeedback.java`
- Modify: `student/.../domain/ProjectEvent.java` — add `hasSubmission` field
- Modify: `student/.../domain/Project.java` — add `currentEventId` field

**ProjectEventSubmission fields:**
- id (ULID), clientId (UUID), projectId, eventId, groupId
- submissionUrl (text), submissionText (text)
- submittedBy, submittedAt, version (int), status (enum: PENDING, SUBMITTED, REVIEWED, NEEDS_REVISION, APPROVED)
- createdAt, updatedAt

**ProjectEventFeedback fields:**
- id (ULID), clientId (UUID), submissionId, eventId, groupId
- comment (text), feedbackBy, feedbackAt
- status (enum: REVIEWED, NEEDS_REVISION)

**Step 1:** Create both entity classes.
**Step 2:** Add `hasSubmission` (Boolean, default false) to ProjectEvent.
**Step 3:** Add `currentEventId` (String) to Project.
**Step 4:** Compile and commit.

---

## Task 3: Repositories

**Files:**
- Create: `student/.../repo/ProjectEventSubmissionRepository.java`
- Create: `student/.../repo/ProjectEventFeedbackRepository.java`

**Key queries:**
```java
// Submissions
findByEventIdAndGroupIdAndClientId(eventId, groupId, clientId) -> List (version history)
findFirstByEventIdAndGroupIdAndClientIdOrderByVersionDesc(...) -> Optional (latest)
findByEventIdAndClientId(eventId, clientId) -> List (all groups for an event)
findByProjectIdAndClientId(projectId, clientId) -> List (all submissions for project)

// Feedback
findBySubmissionIdAndClientId(submissionId, clientId) -> List
findByEventIdAndGroupIdAndClientIdOrderByFeedbackAtDesc(...) -> List
```

**Step 1:** Create both repositories.
**Step 2:** Compile and commit.

---

## Task 4: DTOs

**Files:**
- Create: `student/.../dto/ProjectEventSubmissionDTO.java`
- Create: `student/.../dto/ProjectEventFeedbackDTO.java`
- Create: `student/.../dto/SubmitEventRequest.java`
- Create: `student/.../dto/EventFeedbackRequest.java`
- Modify: `student/.../dto/ProjectEventDTO.java` — add `hasSubmission`
- Modify: `student/.../dto/ProjectDTO.java` — add `currentEventId`

**SubmitEventRequest:**
```java
public class SubmitEventRequest {
    private String submissionUrl;
    private String submissionText;
    private List<SubmitProjectRequest.AttachmentInfo> attachments;
}
```

**EventFeedbackRequest:**
```java
public class EventFeedbackRequest {
    @NotBlank private String comment;
    @NotBlank private String status; // REVIEWED or NEEDS_REVISION
}
```

**Step 1:** Create all DTOs.
**Step 2:** Update existing DTOs.
**Step 3:** Compile and commit.

---

## Task 5: Service Layer — Event Submissions

**Files:**
- Modify: `student/.../service/ProjectService.java`

**New methods:**
```java
// Student submits to an event
ProjectEventSubmissionDTO submitToEvent(String projectId, String eventId,
    String groupId, String studentId, SubmitEventRequest request)

// Get latest submission for a group on an event
ProjectEventSubmissionDTO getLatestSubmission(String projectId, String eventId, String groupId)

// Get all submissions for an event (faculty view - all groups)
List<Map<String, Object>> getEventSubmissions(String projectId, String eventId)

// Get submission history for a group on an event
List<ProjectEventSubmissionDTO> getSubmissionHistory(String projectId, String eventId, String groupId)

// Faculty gives feedback
ProjectEventFeedbackDTO giveFeedback(String projectId, String eventId,
    String groupId, String submissionId, EventFeedbackRequest request)

// Get feedback for a submission
List<ProjectEventFeedbackDTO> getFeedback(String submissionId)

// Advance project to next event phase
ProjectDTO advancePhase(String projectId, String nextEventId)
```

**Submit logic:**
1. Verify event exists and `hasSubmission` is true
2. Verify student is member of group
3. Check deadline (event dateTime) if applicable
4. Find latest version for this group+event, increment
5. Save submission with status SUBMITTED
6. Save attachments (reuse existing attachment system with SUBMISSION context)
7. Return DTO

**Advance phase logic:**
1. Set `project.currentEventId = nextEventId`
2. Save and return

**Step 1:** Implement all methods.
**Step 2:** Compile and commit.

---

## Task 6: Controller Endpoints

**Files:**
- Modify: `student/.../web/ProjectController.java` (admin/faculty)
- Modify: `student/.../web/ProjectStudentController.java` (student)

**Admin endpoints:**
```
GET  /api/projects/{id}/events/{eventId}/submissions          — all group submissions for event
GET  /api/projects/{id}/events/{eventId}/submissions/{groupId} — latest + history for a group
POST /api/projects/{id}/events/{eventId}/submissions/{groupId}/feedback — give feedback
GET  /api/projects/{id}/events/{eventId}/submissions/{groupId}/feedback — get feedback
POST /api/projects/{id}/advance-phase                          — advance to next event
```

**Student endpoints:**
```
POST /api/projects/{id}/events/{eventId}/my-submission        — submit to event
GET  /api/projects/{id}/events/{eventId}/my-submission        — get my group's latest submission
GET  /api/projects/{id}/events/{eventId}/my-submission/history — submission version history
GET  /api/projects/{id}/events/{eventId}/my-submission/feedback — get feedback on my submission
```

**Step 1:** Add admin endpoints.
**Step 2:** Add student endpoints.
**Step 3:** Compile and commit.

---

## Task 7: Shared-utils Types & API Methods

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/projects.ts`
- Modify: `frontend/packages/shared-utils/src/index.ts`

**New types:**
```typescript
interface ProjectEventSubmissionDTO {
  id: string; projectId: string; eventId: string; groupId: string
  submissionUrl?: string; submissionText?: string
  submittedBy: string; submittedAt: string
  version: number; status: string
  attachments?: ProjectAttachmentDTO[]
}

interface ProjectEventFeedbackDTO {
  id: string; submissionId: string; eventId: string; groupId: string
  comment: string; feedbackBy: string; feedbackAt: string; status: string
}
```

**New API methods on ProjectsApi:**
```typescript
submitToEvent(id, eventId, data)
getMyEventSubmission(id, eventId)
getMyEventSubmissionHistory(id, eventId)
getMyEventFeedback(id, eventId)
getEventSubmissions(id, eventId)  // admin
getGroupEventSubmission(id, eventId, groupId)  // admin
giveEventFeedback(id, eventId, groupId, submissionId, data)  // admin
getEventFeedback(id, eventId, groupId)  // admin
advancePhase(id, nextEventId)  // admin
```

**Update existing:**
- Add `hasSubmission` to `ProjectEventDTO`
- Add `currentEventId` to `ProjectDTO`

**Step 1:** Add all types and methods.
**Step 2:** Build shared-utils.
**Step 3:** Commit.

---

## Task 8: Admin — Event Submissions Review Page

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/[id]/events/[eventId]/submissions/page.tsx`

**UI:**
```
← Back to Project    Event: LLD Review    Phase: 2 of 4

[Advance to Next Phase]

┌─────────────────────────────────────────────────────┐
│ Morning Group 1          SUBMITTED    [Review]      │
│ Submitted by: Student1   2 files attached           │
│ URL: github.com/...                                 │
├─────────────────────────────────────────────────────┤
│ Morning Group 2          NEEDS_REVISION             │
│ Last feedback: "Missing ER diagram" — Faculty       │
│ Re-submitted: v2   [Review]                         │
├─────────────────────────────────────────────────────┤
│ Morning Group 3          ⚠️ NOT SUBMITTED           │
├─────────────────────────────────────────────────────┤
│ Evening Group 1          APPROVED ✓                 │
│ Feedback: "Well structured" — Faculty               │
└─────────────────────────────────────────────────────┘
```

**Review dialog/panel:**
- Shows submission URL, text, attachments (download links)
- Submission version history
- Previous feedback
- Textarea for new feedback
- Status buttons: "Approve" / "Needs Revision"

**Step 1:** Create page.
**Step 2:** Add link from project detail events tab.
**Step 3:** Commit.

---

## Task 9: Admin — Update Project Detail Events Tab

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/[id]/page.tsx`

**Changes:**
- Add `hasSubmission` toggle when creating/editing events
- Show submission count per event (e.g., "3/6 submitted")
- Add "Submissions" button (links to submissions page) for events with hasSubmission
- Show current phase indicator
- Add "Advance Phase" button

**Step 1:** Update event creation form.
**Step 2:** Update events table with submission counts.
**Step 3:** Commit.

---

## Task 10: Student — Event Submissions UI

**Files:**
- Modify: `frontend/apps/student-portal/src/app/projects/[id]/page.tsx`

**Changes:**
- Events section shows submission status per event
- For events with `hasSubmission`: show submit form (URL + text + file upload)
- Show previous submissions (version history)
- Show faculty feedback with status badge
- Show "Needs Revision" alert when applicable

**UI per event:**
```
LLD Review — Mar 28, 2026
  Status: NEEDS_REVISION ⚠️
  Feedback: "Missing ER diagram, please add and resubmit" — Faculty

  Your submission (v1): github.com/... | report.pdf
  [Edit Submission]  ← opens form to submit v2

  Submission URL: [____________]
  Notes: [____________]
  Attach files: [Upload]
  [Submit v2]
```

**Step 1:** Update events display.
**Step 2:** Add submission form.
**Step 3:** Add feedback display.
**Step 4:** Commit.

---

## Task 11: Update Bulk Setup — hasSubmission on Events

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/bulk-create/page.tsx`
- Modify: `student/.../dto/BulkProjectSetupRequest.java`

**Changes:**
- Add "Accepts Submission" toggle per event in Step 5
- Pass `hasSubmission` in EventInput
- Backend creates events with hasSubmission flag

**Step 1:** Update EventInput DTO.
**Step 2:** Update wizard UI.
**Step 3:** Commit.

---

## Summary

| Component | What |
|-----------|------|
| Migration 0025 | event_submission, event_feedback tables, hasSubmission, currentEventId |
| Entities | ProjectEventSubmission, ProjectEventFeedback |
| Service | Submit, review, feedback, advance phase |
| Admin UI | Submissions review page, event updates, phase tracking |
| Student UI | Per-event submit form, feedback display, version history |
| Wizard | hasSubmission toggle on events |

### Phase Tracking Flow:
```
Project activated → currentEventId = first event (Briefing)
All groups submit Briefing → Faculty reviews → Advance to LLD
All groups submit LLD → Faculty reviews → Advance to HLD
... → Advance to Final Submission → Project completed
```
