# Projects Feature Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a group-based project management system where students are divided into groups, assigned problem statements, tracked through events with attendance/grading, and submit work via URL.

**Architecture:** Split across Student service (projects, groups, events, attendance, grades) and Content service (question bank). Feature-flagged behind `PROJECTS` in `TenantFeatureType`. Gateway routes added for both.

**Tech Stack:** Spring Boot (Java 21), Liquibase (YAML), PostgreSQL (jsonb, text[]), Next.js 14, shadcn/ui, shared-utils (tsup), React Query.

---

## Phase 1: Foundation (Feature Flag + Database + Entities)

### Task 1: Add PROJECTS Feature Flag

**Files:**
- Modify: `identity/src/main/java/com/datagami/edudron/identity/domain/TenantFeatureType.java`

**Step 1: Add PROJECTS to TenantFeatureType enum**

Find the existing enum values (SIMULATION is the last one added). Add after it:

```java
PROJECTS("projects", "Group-based project management", false),
```

The pattern is: `ENUM_NAME("key", "description", defaultValue)`.

**Step 2: Verify compilation**

Run: `./gradlew :core-api:compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add identity/src/main/java/com/datagami/edudron/identity/domain/TenantFeatureType.java
git commit -m "feat: add PROJECTS feature flag to TenantFeatureType"
```

---

### Task 2: Database Migration — Student Schema (Project Tables)

**Files:**
- Create: `student/src/main/resources/db/changelog/db.changelog-0022-projects.yaml`
- Modify: `student/src/main/resources/db/changelog/student-master.yaml`

**Step 1: Create migration file**

Create `student/src/main/resources/db/changelog/db.changelog-0022-projects.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: student-0022-01-project
      author: edudron
      changes:
        - createTable:
            tableName: project
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: course_id, type: varchar(26) }
              - column: { name: section_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: title, type: varchar(500), constraints: { nullable: false } }
              - column: { name: description, type: text }
              - column: { name: max_marks, type: int, defaultValueNumeric: 100 }
              - column: { name: submission_cutoff, type: timestamptz }
              - column: { name: late_submission_allowed, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: status, type: varchar(20), defaultValue: 'DRAFT', constraints: { nullable: false } }
              - column: { name: created_by, type: varchar(255) }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now() }
        - createIndex:
            tableName: project
            schemaName: student
            indexName: idx_project_client_id
            columns: [{ column: { name: client_id } }]
        - createIndex:
            tableName: project
            schemaName: student
            indexName: idx_project_section_id
            columns: [{ column: { name: section_id } }]
        - createIndex:
            tableName: project
            schemaName: student
            indexName: idx_project_course_id
            columns: [{ column: { name: course_id } }]
        - createIndex:
            tableName: project
            schemaName: student
            indexName: idx_project_status
            columns: [{ column: { name: status } }]

  - changeSet:
      id: student-0022-02-project-group
      author: edudron
      changes:
        - createTable:
            tableName: project_group
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: project_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: group_number, type: int, constraints: { nullable: false } }
              - column: { name: problem_statement_id, type: varchar(26) }
              - column: { name: submission_url, type: text }
              - column: { name: submitted_at, type: timestamptz }
              - column: { name: submitted_by, type: varchar(26) }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
        - createIndex:
            tableName: project_group
            schemaName: student
            indexName: idx_project_group_project_id
            columns: [{ column: { name: project_id } }]
        - createIndex:
            tableName: project_group
            schemaName: student
            indexName: idx_project_group_client_id
            columns: [{ column: { name: client_id } }]

  - changeSet:
      id: student-0022-03-project-group-member
      author: edudron
      changes:
        - createTable:
            tableName: project_group_member
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: group_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: student_id, type: varchar(26), constraints: { nullable: false } }
        - createIndex:
            tableName: project_group_member
            schemaName: student
            indexName: idx_project_group_member_group_id
            columns: [{ column: { name: group_id } }]
        - addUniqueConstraint:
            tableName: project_group_member
            schemaName: student
            constraintName: uq_project_group_member
            columnNames: group_id, student_id

  - changeSet:
      id: student-0022-04-project-event
      author: edudron
      changes:
        - createTable:
            tableName: project_event
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: project_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: name, type: varchar(255), constraints: { nullable: false } }
              - column: { name: date_time, type: timestamptz }
              - column: { name: zoom_link, type: text }
              - column: { name: has_marks, type: boolean, defaultValueBoolean: false }
              - column: { name: max_marks, type: int }
              - column: { name: sequence, type: int }
        - createIndex:
            tableName: project_event
            schemaName: student
            indexName: idx_project_event_project_id
            columns: [{ column: { name: project_id } }]

  - changeSet:
      id: student-0022-05-project-event-attendance
      author: edudron
      changes:
        - createTable:
            tableName: project_event_attendance
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: event_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: student_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: group_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: present, type: boolean, constraints: { nullable: false } }
        - addUniqueConstraint:
            tableName: project_event_attendance
            schemaName: student
            constraintName: uq_project_event_attendance
            columnNames: event_id, student_id

  - changeSet:
      id: student-0022-06-project-event-grade
      author: edudron
      changes:
        - createTable:
            tableName: project_event_grade
            schemaName: student
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: event_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: student_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: group_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: marks, type: int, constraints: { nullable: false } }
        - addUniqueConstraint:
            tableName: project_event_grade
            schemaName: student
            constraintName: uq_project_event_grade
            columnNames: event_id, student_id
```

**Step 2: Register in student-master.yaml**

Add after the last include:
```yaml
  - include:
      file: db/changelog/db.changelog-0022-projects.yaml
```

**Step 3: Commit**

```bash
git add student/src/main/resources/db/changelog/
git commit -m "feat: add database migrations for project tables"
```

---

### Task 3: Database Migration — Content Schema (Question Bank)

**Files:**
- Create: `content/src/main/resources/db/changelog/db.changelog-0028-project-question-bank.yaml`
- Modify: `content/src/main/resources/db/changelog/db.changelog-master.yaml`

**Step 1: Create migration file**

```yaml
databaseChangeLog:
  - changeSet:
      id: content-0028-01-project-question-bank
      author: edudron
      changes:
        - createTable:
            tableName: project_question_bank
            schemaName: content
            columns:
              - column: { name: id, type: varchar(26), constraints: { primaryKey: true, nullable: false } }
              - column: { name: client_id, type: uuid, constraints: { nullable: false } }
              - column: { name: course_id, type: varchar(26), constraints: { nullable: false } }
              - column: { name: title, type: varchar(500), constraints: { nullable: false } }
              - column: { name: problem_statement, type: text, constraints: { nullable: false } }
              - column: { name: key_technologies, type: "text[]" }
              - column: { name: tags, type: "text[]" }
              - column: { name: difficulty, type: varchar(20) }
              - column: { name: is_active, type: boolean, defaultValueBoolean: true }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now() }
        - createIndex:
            tableName: project_question_bank
            schemaName: content
            indexName: idx_project_qb_client_id
            columns: [{ column: { name: client_id } }]
        - createIndex:
            tableName: project_question_bank
            schemaName: content
            indexName: idx_project_qb_course_id
            columns: [{ column: { name: course_id } }]
```

**Step 2: Register in content master changelog**

**Step 3: Commit**

```bash
git add content/src/main/resources/db/changelog/
git commit -m "feat: add database migration for project question bank"
```

---

### Task 4: JPA Entities — Student Service (All 6 entities)

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/domain/Project.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/ProjectGroup.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/ProjectGroupMember.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/ProjectEvent.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/ProjectEventAttendance.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/ProjectEventGrade.java`

Follow the existing entity pattern in the codebase:
- ULID for `id` via `UlidGenerator.generate()` in `@PrePersist`
- `clientId` (UUID) on every entity
- `@Enumerated(EnumType.STRING)` for status enums
- `@PreUpdate` for `updatedAt` timestamp

Create an enum `Project.ProjectStatus { DRAFT, ACTIVE, COMPLETED, ARCHIVED }` inside the Project entity (same pattern as Simulation).

**Commit:** `git commit -m "feat: add Project JPA entities"`

---

### Task 5: JPA Entity — Content Service (Question Bank)

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/domain/ProjectQuestionBank.java`

Follow existing QuestionBank entity pattern. Use `@JdbcTypeCode(SqlTypes.ARRAY)` or `columnDefinition = "text[]"` for `keyTechnologies` and `tags` arrays.

**Commit:** `git commit -m "feat: add ProjectQuestionBank entity"`

---

### Task 6: Repositories — Student Service

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/repo/ProjectRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/ProjectGroupRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/ProjectGroupMemberRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/ProjectEventRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/ProjectEventAttendanceRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/ProjectEventGradeRepository.java`

Key query methods:
- `ProjectRepository`: `findByIdAndClientId`, `findByClientIdAndSectionId`, `findByClientIdAndCourseId`, `findByClientIdAndStatus`
- `ProjectGroupRepository`: `findByProjectIdAndClientId`, `findByIdAndClientId`
- `ProjectGroupMemberRepository`: `findByGroupIdAndClientId`, `findByStudentIdAndClientId`, `deleteByGroupId`
- `ProjectEventRepository`: `findByProjectIdAndClientIdOrderBySequenceAsc`, `findByIdAndClientId`
- `ProjectEventAttendanceRepository`: `findByEventIdAndClientId`, `findByStudentIdAndClientId`
- `ProjectEventGradeRepository`: `findByEventIdAndClientId`, `findByStudentIdAndClientId`

**Commit:** `git commit -m "feat: add Project repositories"`

---

### Task 7: Repository — Content Service

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/repo/ProjectQuestionBankRepository.java`

Key methods: `findByClientIdAndCourseIdAndIsActiveTrue`, `findByIdAndClientId`, `findByClientIdAndIsActiveTrue`

**Commit:** `git commit -m "feat: add ProjectQuestionBank repository"`

---

### Task 8: Gateway Routes

**Files:**
- Modify: `gateway/src/main/resources/application.yml`

Add two new routes:
```yaml
- id: projects-service
  uri: http://localhost:8085
  predicates:
    - Path=/api/projects/**
  filters:
    - StripPrefix=0

- id: project-questions-service
  uri: http://localhost:8082
  predicates:
    - Path=/api/project-questions/**
  filters:
    - StripPrefix=0
```

Place them alongside existing routes. Check existing route patterns for the exact format used.

**Commit:** `git commit -m "feat: add gateway routes for projects and project-questions"`

---

### Task 9: Verify Phase 1 Compilation

Run: `./gradlew :core-api:compileJava :content:compileJava :gateway:compileJava`

Expected: BUILD SUCCESSFUL for all three.

**Commit:** Any fixes needed.

---

## Phase 2: Backend Services & Controllers

### Task 10: DTOs — Student Service

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/ProjectDTO.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/ProjectGroupDTO.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/ProjectEventDTO.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CreateProjectRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/GenerateGroupsRequest.java` (just `groupSize` int)
- Create: `student/src/main/java/com/datagami/edudron/student/dto/BulkAttendanceRequest.java` (list of {studentId, present})
- Create: `student/src/main/java/com/datagami/edudron/student/dto/BulkGradeRequest.java` (list of {studentId, marks})
- Create: `student/src/main/java/com/datagami/edudron/student/dto/SubmitProjectRequest.java` (submissionUrl)

**Commit:** `git commit -m "feat: add Project DTOs and request objects"`

---

### Task 11: DTOs — Content Service

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/dto/ProjectQuestionDTO.java`
- Create: `content/src/main/java/com/datagami/edudron/content/dto/CreateProjectQuestionRequest.java`
- Create: `content/src/main/java/com/datagami/edudron/content/dto/BulkProjectQuestionRequest.java`

**Commit:** `git commit -m "feat: add ProjectQuestion DTOs"`

---

### Task 12: ProjectService — Core CRUD + Group Generation

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/ProjectService.java`

Implement:
- `createProject()`, `getProject()`, `listProjects()`, `updateProject()`
- `activateProject()`, `completeProject()`
- `generateGroups(projectId, groupSize)` — shuffle students from section, divide into groups
- `getGroups(projectId)` — return groups with members
- `assignStatements(projectId)` — fetch questions from content service via RestTemplate, round-robin assign

The group generation logic:
```java
List<String> studentIds = getStudentIdsInSection(sectionId);
Collections.shuffle(studentIds);
int groupCount = (int) Math.ceil((double) studentIds.size() / groupSize);
for (int g = 0; g < groupCount; g++) {
    ProjectGroup group = new ProjectGroup();
    group.setGroupNumber(g + 1);
    // ... save group
    int start = g * groupSize;
    int end = Math.min(start + groupSize, studentIds.size());
    for (int s = start; s < end; s++) {
        ProjectGroupMember member = new ProjectGroupMember();
        member.setStudentId(studentIds.get(s));
        member.setGroupId(group.getId());
        // ... save member
    }
}
```

**Commit:** `git commit -m "feat: add ProjectService with CRUD and group generation"`

---

### Task 13: ProjectService — Events, Attendance, Grades, Submission

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ProjectService.java`

Implement:
- `addEvent()`, `updateEvent()`, `deleteEvent()`
- `saveAttendance(eventId, List<{studentId, present}>)` — upsert pattern (delete existing + insert)
- `saveGrades(eventId, List<{studentId, marks}>)` — upsert pattern
- `submitProject(projectId, groupId, studentId, submissionUrl)` — with cutoff enforcement
- `getMyProjects(studentId)` — find groups where student is a member, return their projects
- `getMyGroup(projectId, studentId)` — find the student's group
- `getMyAttendance(projectId, studentId)` — attendance + grades across events

**Commit:** `git commit -m "feat: add event, attendance, grade, and submission methods to ProjectService"`

---

### Task 14: ProjectController (Admin)

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/web/ProjectController.java`

All admin endpoints from the design doc. Use `@RestController`, `@RequestMapping("/api/projects")`.
Add `requireAdmin()` check (same pattern as other admin controllers).

**Commit:** `git commit -m "feat: add ProjectController with admin endpoints"`

---

### Task 15: ProjectStudentController (Student Portal)

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/web/ProjectStudentController.java`

Student-facing endpoints: `my-projects`, `my-group`, `my-group/submit`, `my-attendance`.
Resolve student ID via the same pattern as SimulationStudentController (call `/idp/users/me`).

**Commit:** `git commit -m "feat: add ProjectStudentController for student portal"`

---

### Task 16: ProjectQuestionBankService + Controller (Content)

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/service/ProjectQuestionBankService.java`
- Create: `content/src/main/java/com/datagami/edudron/content/web/ProjectQuestionBankController.java`

CRUD + bulk upload. Controller at `@RequestMapping("/api/project-questions")`.
Bulk upload accepts JSON array of questions.

**Commit:** `git commit -m "feat: add ProjectQuestionBank service and controller"`

---

### Task 17: Verify Phase 2 Compilation

Run: `./gradlew :core-api:compileJava :content:compileJava`

Expected: BUILD SUCCESSFUL.

---

## Phase 3: Frontend — Shared Utils API

### Task 18: ProjectsApi in shared-utils

**Files:**
- Create: `frontend/packages/shared-utils/src/api/projects.ts`
- Modify: `frontend/packages/shared-utils/src/index.ts` (export)

Add all API methods matching the backend endpoints. Include TypeScript interfaces for all DTOs.

Build: `cd frontend/packages/shared-utils && npm run build`

**Commit:** `git commit -m "feat: add ProjectsApi and ProjectQuestionsApi to shared-utils"`

---

### Task 19: Wire up API in admin-dashboard and student-portal

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/lib/api.ts`
- Modify: `frontend/apps/student-portal/src/lib/api.ts`

Add `projectsApi` and `projectQuestionsApi` instances.

**Commit:** `git commit -m "feat: wire up projects API instances"`

---

## Phase 4: Frontend — Admin Dashboard

### Task 20: Projects List Page

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/page.tsx`

Status tabs (Draft, Active, Completed), search, course/section filters.
Same pattern as the Exams list page.

**Commit:** `git commit -m "feat: add Projects list page"`

---

### Task 21: Create Project Page

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/new/page.tsx`

Form: course picker, section picker, title, description, max marks, cutoff date, late submission toggle.

**Commit:** `git commit -m "feat: add Create Project page"`

---

### Task 22: Project Detail Page (Overview + Groups + Events tabs)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/[id]/page.tsx`

Tabs: Overview (edit metadata), Groups (list groups, generate, assign statements), Events (list, add, edit).

This is the largest frontend task. Key components:
- **Groups tab:** "Generate Groups" button → modal for group size → groups list with members and assigned statement
- **Events tab:** Add event form (name, datetime, zoom, hasMarks toggle, maxMarks) → events list
- **Attendance/Grades:** Link from each event → grid view for marking attendance and entering grades

**Commit:** `git commit -m "feat: add Project detail page with tabs"`

---

### Task 23: Project Question Bank Pages

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/project-questions/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/app/project-questions/new/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/app/project-questions/bulk-upload/page.tsx`

Question list with filters, create form, bulk JSON/CSV upload.

**Commit:** `git commit -m "feat: add Project Question Bank pages"`

---

### Task 24: Admin Sidebar + Feature Flag Hook

**Files:**
- Create: `frontend/apps/admin-dashboard/src/hooks/useProjectsFeature.ts`
- Modify: `frontend/apps/admin-dashboard/src/components/Sidebar.tsx`

Add "Projects" section to sidebar (with sub-items: "All Projects", "Question Bank"). Hide when feature flag is disabled.

**Commit:** `git commit -m "feat: add Projects to admin sidebar with feature flag"`

---

## Phase 5: Frontend — Student Portal

### Task 25: Student Projects List Page

**Files:**
- Create: `frontend/apps/student-portal/src/app/projects/page.tsx`

List active projects the student is part of. Show project title, group number, submission status.

**Commit:** `git commit -m "feat: add student Projects list page"`

---

### Task 26: Student Project Detail Page

**Files:**
- Create: `frontend/apps/student-portal/src/app/projects/[id]/page.tsx`

Show: group members, problem statement, events timeline with attendance/marks, submission form with cutoff.

**Commit:** `git commit -m "feat: add student Project detail page"`

---

### Task 27: Student Sidebar + Feature Flag

**Files:**
- Create: `frontend/apps/student-portal/src/hooks/useProjectsFeature.ts`
- Modify: `frontend/apps/student-portal/src/components/StudentLayout.tsx`

Add "Projects" to student sidebar, hidden when disabled.

**Commit:** `git commit -m "feat: add Projects to student sidebar with feature flag"`

---

## Phase 6: Integration & Polish

### Task 28: Build, Test, Deploy

**Step 1:** Rebuild shared-utils: `cd frontend/packages/shared-utils && npm run build`

**Step 2:** Verify backend compilation: `./gradlew :core-api:compileJava :content:compileJava :gateway:compileJava`

**Step 3:** Start services locally and test:
- Create a project question bank entry
- Create a project for a section
- Generate groups
- Assign statements
- Add events
- Mark attendance
- Enter grades
- Submit URL as student
- Verify student portal shows everything

**Step 4:** Bump versions, publish shared-utils, build Docker images, deploy (after confirming no exams running).
