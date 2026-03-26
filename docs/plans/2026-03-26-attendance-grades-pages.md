# Dedicated Attendance & Grades Pages Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace attendance/grades dialogs with dedicated pages that auto-save, load existing data, and support CSV download/upload for bulk operations.

**Architecture:** New Next.js pages at `/projects/[id]/events/[eventId]/attendance` and `/projects/[id]/events/[eventId]/grades`. Backend gets new GET endpoints for existing attendance/grades per event. Frontend uses debounced auto-save (save 1s after last change). CSV export downloads current data; CSV import parses and applies bulk updates.

**Tech Stack:** Next.js 14, React, Tailwind CSS, shadcn/ui, debounce via useRef/setTimeout

---

## Task 1: Backend — GET attendance and grades endpoints

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ProjectService.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/web/ProjectController.java`

**Step 1:** Add `getAttendance` and `getGrades` methods to ProjectService:

```java
@Transactional(readOnly = true)
public List<Map<String, Object>> getEventAttendance(String projectId, String eventId) {
    UUID clientId = getClientId();
    List<ProjectEventAttendance> records = projectEventAttendanceRepository.findByEventIdAndClientId(eventId, clientId);
    return records.stream().map(a -> {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("studentId", a.getStudentId());
        entry.put("groupId", a.getGroupId());
        entry.put("present", a.getPresent());
        return entry;
    }).collect(Collectors.toList());
}

@Transactional(readOnly = true)
public List<Map<String, Object>> getEventGrades(String projectId, String eventId) {
    UUID clientId = getClientId();
    List<ProjectEventGrade> records = projectEventGradeRepository.findByEventIdAndClientId(eventId, clientId);
    return records.stream().map(g -> {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("studentId", g.getStudentId());
        entry.put("groupId", g.getGroupId());
        entry.put("marks", g.getMarks());
        return entry;
    }).collect(Collectors.toList());
}
```

**Step 2:** Add GET endpoints to ProjectController:

```java
@GetMapping("/{id}/events/{eventId}/attendance")
@Operation(summary = "Get attendance", description = "Get existing attendance records for a project event")
public ResponseEntity<List<Map<String, Object>>> getAttendance(
        @PathVariable String id, @PathVariable String eventId) {
    return ResponseEntity.ok(projectService.getEventAttendance(id, eventId));
}

@GetMapping("/{id}/events/{eventId}/grades")
@Operation(summary = "Get grades", description = "Get existing grade records for a project event")
public ResponseEntity<List<Map<String, Object>>> getGrades(
        @PathVariable String id, @PathVariable String eventId) {
    return ResponseEntity.ok(projectService.getEventGrades(id, eventId));
}
```

**Step 3:** Compile: `./gradlew :core-api:compileJava`

**Step 4:** Commit: `feat: add GET endpoints for event attendance and grades`

---

## Task 2: Shared-utils — Add getAttendance/getGrades API methods

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/projects.ts`

**Step 1:** Add methods to ProjectsApi class:

```typescript
async getAttendance(id: string, eventId: string): Promise<Array<{ studentId: string; groupId: string; present: boolean }>> {
  const response = await this.apiClient.get<any[]>(`/api/projects/${id}/events/${eventId}/attendance`)
  return Array.isArray(response.data) ? response.data : []
}

async getGrades(id: string, eventId: string): Promise<Array<{ studentId: string; groupId: string; marks: number }>> {
  const response = await this.apiClient.get<any[]>(`/api/projects/${id}/events/${eventId}/grades`)
  return Array.isArray(response.data) ? response.data : []
}
```

**Step 2:** Build: `cd frontend/packages/shared-utils && npm run build`

**Step 3:** Commit: `feat: add getAttendance and getGrades to ProjectsApi`

---

## Task 3: Attendance Page — Create dedicated page with auto-save

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/[id]/events/[eventId]/attendance/page.tsx`

**Key features:**
- Full-page layout with back button to project detail
- Shows event name and date at top
- Table: Group Name | Student Name | Email | Present (checkbox)
- Grouped by project groups
- **Auto-save:** Debounced 1s after last change — shows "Saving..." / "Saved" indicator
- **Load existing:** On mount, fetch existing attendance via GET, merge with full student list
- **Select All / Deselect All** per group
- **CSV Download:** Export current attendance as CSV (Group, Student Name, Email, Present)
- **CSV Upload:** Parse CSV, match by student email/name, update attendance

**Auto-save pattern:**
```typescript
const saveTimerRef = useRef<NodeJS.Timeout | null>(null)
const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')

const triggerAutoSave = (entries: AttendanceEntry[]) => {
  if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
  setSaveStatus('saving')
  saveTimerRef.current = setTimeout(async () => {
    try {
      await projectsApi.saveAttendance(projectId, eventId, entries)
      setSaveStatus('saved')
    } catch {
      setSaveStatus('error')
    }
  }, 1000)
}
```

**Step 1:** Create the page component with all features above.

**Step 2:** Commit: `feat: add dedicated attendance page with auto-save and CSV support`

---

## Task 4: Grades Page — Create dedicated page with auto-save

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/[id]/events/[eventId]/grades/page.tsx`

**Key features:**
- Same layout pattern as attendance page
- Table: Group Name | Student Name | Email | Marks (number input)
- Shows max marks from event config
- **Auto-save:** Same debounce pattern as attendance
- **Load existing:** Fetch existing grades, merge with student list
- **Validation:** Marks cannot exceed event maxMarks
- **CSV Download:** Export as CSV (Group, Student Name, Email, Marks)
- **CSV Upload:** Parse CSV, match by student email/name, update marks

**Step 1:** Create the page component.

**Step 2:** Commit: `feat: add dedicated grades page with auto-save and CSV support`

---

## Task 5: Update project detail page — Link to new pages

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/[id]/page.tsx`

**Step 1:** Replace attendance/grades dialog triggers with navigation to new pages:

```typescript
// Replace openAttendanceDialog(event.id) with:
router.push(`/projects/${projectId}/events/${event.id}/attendance`)

// Replace openGradesDialog(event.id) with:
router.push(`/projects/${projectId}/events/${event.id}/grades`)
```

**Step 2:** Remove the attendance and grades dialog code (state, handlers, Dialog components).

**Step 3:** Commit: `refactor: replace attendance/grades dialogs with dedicated page links`

---

## Task 6: CSV utilities — shared download/upload helpers

**Files:**
- Create: `frontend/apps/admin-dashboard/src/lib/csv-utils.ts`

**Step 1:** Create CSV utility functions:

```typescript
export function downloadCSV(filename: string, headers: string[], rows: string[][]) {
  const csv = [headers.join(','), ...rows.map(r => r.map(c => `"${(c || '').replace(/"/g, '""')}"`).join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function parseCSV(text: string): string[][] {
  // Handle quoted fields, newlines in quotes, etc.
  // Return array of rows, each row is array of cell values
}
```

**Step 2:** Commit: `feat: add CSV download/upload utilities`

---

## Summary

| Page | URL | Features |
|------|-----|----------|
| Attendance | `/projects/[id]/events/[eventId]/attendance` | Checkboxes grouped by team, auto-save, CSV download/upload |
| Grades | `/projects/[id]/events/[eventId]/grades` | Number inputs grouped by team, auto-save, CSV download/upload, max marks validation |

Both pages load existing data on mount, auto-save 1s after changes, and show save status indicator.
