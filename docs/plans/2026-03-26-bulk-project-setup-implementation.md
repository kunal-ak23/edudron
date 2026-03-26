# Bulk Project Setup — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a bulk project setup wizard that creates a project, generates groups from multiple sections, and assigns problem statements — all in one flow. Also add "Add Sections" to existing projects.

**Architecture:** New backend endpoint `POST /api/projects/bulk-setup` orchestrates the full flow in one transaction. Frontend adds a stepper wizard page. Reuses existing generateGroups/assignStatements logic, extended to support multiple sections.

**Tech Stack:** Spring Boot (student service), Next.js + shadcn/ui (admin dashboard), shared-utils API client.

---

### Task 1: Backend — Add `findSectionsWithCourseEnrollments` query

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/repo/EnrollmentRepository.java`

**Step 1: Add query to find distinct sectionIds by courseId**

In `EnrollmentRepository.java`, add after line 37:

```java
    @Query("SELECT DISTINCT e.batchId FROM Enrollment e WHERE e.clientId = :clientId AND e.courseId = :courseId AND e.batchId IS NOT NULL")
    List<String> findDistinctBatchIdsByClientIdAndCourseId(@Param("clientId") UUID clientId, @Param("courseId") String courseId);
```

**Step 2: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/repo/EnrollmentRepository.java
git commit -m "feat: add query to find sections with course enrollments"
```

---

### Task 2: Backend — Add `BulkProjectSetupRequest` DTO

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/BulkProjectSetupRequest.java`

**Step 1: Create the DTO**

```java
package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkProjectSetupRequest {

    @NotBlank(message = "Course ID is required")
    private String courseId;

    @NotEmpty(message = "At least one section must be selected")
    private List<String> sectionIds;

    @Min(value = 1, message = "Group size must be at least 1")
    private int groupSize;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private Integer maxMarks = 100;
    private OffsetDateTime submissionCutoff;
    private Boolean lateSubmissionAllowed = false;

    public BulkProjectSetupRequest() {}

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public List<String> getSectionIds() { return sectionIds; }
    public void setSectionIds(List<String> sectionIds) { this.sectionIds = sectionIds; }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Integer maxMarks) { this.maxMarks = maxMarks; }

    public OffsetDateTime getSubmissionCutoff() { return submissionCutoff; }
    public void setSubmissionCutoff(OffsetDateTime submissionCutoff) { this.submissionCutoff = submissionCutoff; }

    public Boolean getLateSubmissionAllowed() { return lateSubmissionAllowed; }
    public void setLateSubmissionAllowed(Boolean lateSubmissionAllowed) { this.lateSubmissionAllowed = lateSubmissionAllowed; }
}
```

**Step 2: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/BulkProjectSetupRequest.java
git commit -m "feat: add BulkProjectSetupRequest DTO"
```

---

### Task 3: Backend — Add `AddSectionsRequest` DTO

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/AddSectionsRequest.java`

**Step 1: Create the DTO**

```java
package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AddSectionsRequest {

    @NotEmpty(message = "At least one section must be selected")
    private List<String> sectionIds;

    @Min(value = 1, message = "Group size must be at least 1")
    private int groupSize;

    public AddSectionsRequest() {}

    public List<String> getSectionIds() { return sectionIds; }
    public void setSectionIds(List<String> sectionIds) { this.sectionIds = sectionIds; }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }
}
```

**Step 2: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/AddSectionsRequest.java
git commit -m "feat: add AddSectionsRequest DTO"
```

---

### Task 4: Backend — Implement `bulkSetup` in ProjectService

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ProjectService.java`

**Step 1: Add the bulkSetup method**

Add after the `createProject` method (after line 124):

```java
    @Transactional
    public ProjectDTO bulkSetup(BulkProjectSetupRequest request) {
        UUID clientId = getClientId();
        String createdBy = UserUtil.getCurrentUserEmail();

        // 1. Create the project
        Project project = new Project();
        project.setId(UlidGenerator.nextUlid());
        project.setClientId(clientId);
        project.setCourseId(request.getCourseId());
        project.setSectionId(String.join(",", request.getSectionIds())); // Store all section IDs
        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setMaxMarks(request.getMaxMarks() != null ? request.getMaxMarks() : 100);
        project.setSubmissionCutoff(request.getSubmissionCutoff());
        project.setLateSubmissionAllowed(request.getLateSubmissionAllowed() != null ? request.getLateSubmissionAllowed() : false);
        project.setStatus(ProjectStatus.DRAFT);
        project.setCreatedBy(createdBy);
        project = projectRepository.save(project);

        // 2. Collect students from all sections
        List<String> allStudentIds = new ArrayList<>();
        for (String sectionId : request.getSectionIds()) {
            List<String> sectionStudents = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId)
                    .stream()
                    .map(e -> e.getStudentId())
                    .distinct()
                    .collect(Collectors.toList());
            allStudentIds.addAll(sectionStudents);
        }
        // Remove duplicates (student might be in multiple sections)
        allStudentIds = allStudentIds.stream().distinct().collect(Collectors.toList());

        if (allStudentIds.isEmpty()) {
            log.warn("No students found in selected sections for bulk setup");
            return ProjectDTO.fromEntity(project);
        }

        // 3. Shuffle and generate groups
        Collections.shuffle(allStudentIds);
        int groupSize = request.getGroupSize();
        int numGroups = (int) Math.ceil((double) allStudentIds.size() / groupSize);

        List<ProjectGroup> groups = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            ProjectGroup group = new ProjectGroup();
            group.setId(UlidGenerator.nextUlid());
            group.setClientId(clientId);
            group.setProjectId(project.getId());
            group.setGroupNumber(i + 1);
            groups.add(projectGroupRepository.save(group));
        }

        // 4. Assign students round-robin to groups
        for (int i = 0; i < allStudentIds.size(); i++) {
            ProjectGroupMember member = new ProjectGroupMember();
            member.setId(UlidGenerator.nextUlid());
            member.setClientId(clientId);
            member.setGroupId(groups.get(i % numGroups).getId());
            member.setStudentId(allStudentIds.get(i));
            projectGroupMemberRepository.save(member);
        }

        log.info("Bulk setup: created project '{}' with {} groups from {} students across {} sections",
                project.getTitle(), numGroups, allStudentIds.size(), request.getSectionIds().size());

        // 5. Assign problem statements
        try {
            assignStatements(project.getId());
        } catch (Exception e) {
            log.warn("Problem statement assignment failed (can be done later): {}", e.getMessage());
        }

        return ProjectDTO.fromEntity(project);
    }
```

**Step 2: Add the addSections method**

Add after the `bulkSetup` method:

```java
    @Transactional
    public ProjectDTO addSections(String projectId, AddSectionsRequest request) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // Get existing group members to exclude
        List<ProjectGroup> existingGroups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        Set<String> existingStudentIds = new HashSet<>();
        for (ProjectGroup g : existingGroups) {
            List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(g.getId(), clientId);
            members.forEach(m -> existingStudentIds.add(m.getStudentId()));
        }

        // Collect new students from new sections (excluding already grouped)
        List<String> newStudentIds = new ArrayList<>();
        for (String sectionId : request.getSectionIds()) {
            List<String> sectionStudents = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId)
                    .stream()
                    .map(e -> e.getStudentId())
                    .distinct()
                    .filter(sid -> !existingStudentIds.contains(sid))
                    .collect(Collectors.toList());
            newStudentIds.addAll(sectionStudents);
        }
        newStudentIds = newStudentIds.stream().distinct().collect(Collectors.toList());

        if (newStudentIds.isEmpty()) {
            throw new IllegalStateException("No new students found in selected sections (all may already be in groups)");
        }

        // Determine next group number
        int maxGroupNumber = existingGroups.stream()
                .mapToInt(ProjectGroup::getGroupNumber)
                .max()
                .orElse(0);

        // Generate new groups
        Collections.shuffle(newStudentIds);
        int groupSize = request.getGroupSize();
        int numNewGroups = (int) Math.ceil((double) newStudentIds.size() / groupSize);

        List<ProjectGroup> newGroups = new ArrayList<>();
        for (int i = 0; i < numNewGroups; i++) {
            ProjectGroup group = new ProjectGroup();
            group.setId(UlidGenerator.nextUlid());
            group.setClientId(clientId);
            group.setProjectId(projectId);
            group.setGroupNumber(maxGroupNumber + i + 1);
            newGroups.add(projectGroupRepository.save(group));
        }

        // Assign students to new groups
        for (int i = 0; i < newStudentIds.size(); i++) {
            ProjectGroupMember member = new ProjectGroupMember();
            member.setId(UlidGenerator.nextUlid());
            member.setClientId(clientId);
            member.setGroupId(newGroups.get(i % numNewGroups).getId());
            member.setStudentId(newStudentIds.get(i));
            projectGroupMemberRepository.save(member);
        }

        // Update sectionId on project (append new sections)
        String existingSections = project.getSectionId() != null ? project.getSectionId() : "";
        Set<String> allSectionIds = new LinkedHashSet<>();
        if (!existingSections.isBlank()) {
            allSectionIds.addAll(Arrays.asList(existingSections.split(",")));
        }
        allSectionIds.addAll(request.getSectionIds());
        project.setSectionId(String.join(",", allSectionIds));
        projectRepository.save(project);

        // Assign problem statements to new groups only (continuing round-robin)
        try {
            assignStatementsToGroups(projectId, newGroups);
        } catch (Exception e) {
            log.warn("Problem statement assignment for new groups failed: {}", e.getMessage());
        }

        log.info("Added {} sections to project '{}': {} new students, {} new groups",
                request.getSectionIds().size(), project.getTitle(), newStudentIds.size(), numNewGroups);

        return ProjectDTO.fromEntity(project);
    }
```

**Step 3: Add helper `assignStatementsToGroups` method**

Add after `assignStatements` method (after line 343):

```java
    @SuppressWarnings("unchecked")
    private void assignStatementsToGroups(String projectId, List<ProjectGroup> targetGroups) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String courseId = project.getCourseId();
        if (courseId == null || courseId.isBlank()) return;

        // Find the highest assignment index from existing groups
        List<ProjectGroup> allGroups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        int assignedCount = (int) allGroups.stream()
                .filter(g -> g.getProblemStatementId() != null && !g.getProblemStatementId().isBlank())
                .count();

        String url = gatewayUrl + "/api/project-questions?courseId=" + courseId;
        ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        List<Map<String, Object>> questions = response.getBody();
        if (questions == null || questions.isEmpty()) return;

        // Continue round-robin from where existing assignment left off
        for (int i = 0; i < targetGroups.size(); i++) {
            int questionIndex = (assignedCount + i) % questions.size();
            Map<String, Object> question = questions.get(questionIndex);
            ProjectGroup group = targetGroups.get(i);
            group.setProblemStatementId((String) question.get("id"));
            projectGroupRepository.save(group);
        }
    }
```

**Step 4: Add missing imports at top of file**

Add these imports if not already present:

```java
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Arrays;
import com.datagami.edudron.student.dto.BulkProjectSetupRequest;
import com.datagami.edudron.student.dto.AddSectionsRequest;
```

**Step 5: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/ProjectService.java
git commit -m "feat: implement bulkSetup and addSections in ProjectService"
```

---

### Task 5: Backend — Add endpoints to ProjectController

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/web/ProjectController.java`

**Step 1: Add bulk-setup endpoint**

Add after the createProject endpoint (after line 37):

```java
    @PostMapping("/bulk-setup")
    @Operation(summary = "Bulk project setup",
               description = "Create project, generate groups from multiple sections, assign problem statements")
    public ResponseEntity<ProjectDTO> bulkSetup(@Valid @RequestBody BulkProjectSetupRequest request) {
        log.info("POST /api/projects/bulk-setup - Bulk setup '{}' for course {} with {} sections",
                request.getTitle(), request.getCourseId(), request.getSectionIds().size());
        ProjectDTO project = projectService.bulkSetup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }
```

**Step 2: Add sections-with-enrollment endpoint**

Add a GET endpoint to find sections that have enrollments for a course:

```java
    @GetMapping("/sections-by-course/{courseId}")
    @Operation(summary = "Get sections with enrollments for a course")
    public ResponseEntity<List<String>> getSectionsByCourse(@PathVariable String courseId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<String> sectionIds = enrollmentRepository.findDistinctBatchIdsByClientIdAndCourseId(clientId, courseId);
        return ResponseEntity.ok(sectionIds);
    }
```

Note: Inject `EnrollmentRepository` into the controller (or add a service method — prefer service). If the controller doesn't have enrollmentRepository, add a `getSectionsByCourse` method to `ProjectService` that delegates to the repo.

**Step 3: Add add-sections endpoint**

```java
    @PostMapping("/{id}/add-sections")
    @Operation(summary = "Add sections to existing project",
               description = "Add new sections, generate groups for new students, assign problem statements")
    public ResponseEntity<ProjectDTO> addSections(
            @PathVariable String id,
            @Valid @RequestBody AddSectionsRequest request) {
        log.info("POST /api/projects/{}/add-sections - Adding {} sections", id, request.getSectionIds().size());
        ProjectDTO project = projectService.addSections(id, request);
        return ResponseEntity.ok(project);
    }
```

**Step 4: Add imports**

```java
import com.datagami.edudron.student.dto.BulkProjectSetupRequest;
import com.datagami.edudron.student.dto.AddSectionsRequest;
```

**Step 5: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/web/ProjectController.java
git commit -m "feat: add bulk-setup and add-sections endpoints to ProjectController"
```

---

### Task 6: Frontend — Add API methods to shared-utils

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/projects.ts`

**Step 1: Add types**

After the `CreateProjectRequest` interface (line 64), add:

```typescript
export interface BulkProjectSetupRequest {
  courseId: string
  sectionIds: string[]
  groupSize: number
  title: string
  description?: string
  maxMarks?: number
  submissionCutoff?: string
  lateSubmissionAllowed?: boolean
}

export interface AddSectionsRequest {
  sectionIds: string[]
  groupSize: number
}
```

**Step 2: Add API methods**

In the `ProjectsApi` class, after the `createProject` method (line 94), add:

```typescript
  async bulkSetup(data: BulkProjectSetupRequest): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>('/api/projects/bulk-setup', data)
    return response.data
  }

  async getSectionsByCourse(courseId: string): Promise<string[]> {
    const response = await this.apiClient.get<string[]>(`/api/projects/sections-by-course/${courseId}`)
    return Array.isArray(response.data) ? response.data : (Array.isArray(response) ? response : [])
  }

  async addSections(projectId: string, data: AddSectionsRequest): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>(`/api/projects/${projectId}/add-sections`, data)
    return response.data
  }
```

**Step 3: Build shared-utils**

Run: `cd frontend/packages/shared-utils && npm run build`
Expected: Build success

**Step 4: Commit**

```bash
git add frontend/packages/shared-utils/src/api/projects.ts
git commit -m "feat: add bulkSetup, getSectionsByCourse, addSections API methods"
```

---

### Task 7: Frontend — Create bulk setup wizard page

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/projects/bulk-create/page.tsx`

**Step 1: Create the wizard page**

Create `/projects/bulk-create/page.tsx` with a multi-step wizard:

- **Step 1 — Course**: Select dropdown. On change, fetch question count via `projectQuestionsApi.listQuestions({ courseId })` and section IDs via `projectsApi.getSectionsByCourse(courseId)`. Display "X problem statements available".
- **Step 2 — Sections**: For each sectionId returned, load section details via `sectionsApi.getSection(id)`. Show checkboxes with section name + student count. Show total: "X students across Y sections". If none found, show message.
- **Step 3 — Groups**: Number input for group size. Auto-calculate: "X students → Y groups". Show round-robin info: "Y groups, Z statements (reuse starts at group Z+1)" or "no reuse needed".
- **Step 4 — Details**: Title (required), Description, Max Marks, Submission Cutoff, Late Submission toggle.
- **Step 5 — Review**: Summary table. "Create Project" button calls `projectsApi.bulkSetup(request)`. On success, redirect to `/projects/{id}`.

Use shadcn/ui components: Card, Button, Select, Input, Textarea, Switch, Badge, Checkbox.
Use a `step` state variable (1-5) with Next/Back buttons.

Follow the existing create page (`projects/new/page.tsx`) for styling patterns and component imports.

**Step 2: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/projects/bulk-create/page.tsx
git commit -m "feat: add bulk project setup wizard page"
```

---

### Task 8: Frontend — Add "Bulk Create" button to projects list page

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/page.tsx`

**Step 1: Add button**

At line 99, alongside the existing "Create Project" button, add:

```tsx
<Button variant="outline" onClick={() => router.push('/projects/bulk-create')}>
  <Layers className="h-4 w-4 mr-2" />
  Bulk Setup
</Button>
```

Add `Layers` to the lucide-react import.

**Step 2: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/projects/page.tsx
git commit -m "feat: add Bulk Setup button to projects list page"
```

---

### Task 9: Frontend — Add "Add Sections" action on project detail page

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/projects/[id]/page.tsx`

**Step 1: Add "Add Sections" dialog**

On the project detail page, add a button "Add Sections" that opens a dialog:
- Fetches sections by course (`projectsApi.getSectionsByCourse(project.courseId)`)
- Filters out sections already in the project (from `project.sectionId` comma-separated)
- Multi-select checkboxes for remaining sections
- Group size input
- Confirm button calls `projectsApi.addSections(projectId, { sectionIds, groupSize })`
- On success, refresh project data and groups

**Step 2: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/projects/[id]/page.tsx
git commit -m "feat: add 'Add Sections' action to project detail page"
```

---

### Task 10: Frontend — Rename "Question Bank" label to "Problem Statements"

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/components/Sidebar.tsx` (line 139)

**Step 1: Update sidebar label**

Change line 139 from:
```tsx
{ name: 'Question Bank', href: '/project-questions', icon: FileText },
```
To:
```tsx
{ name: 'Problem Statements', href: '/project-questions', icon: FileText },
```

**Step 2: Update page headers**

In `frontend/apps/admin-dashboard/src/app/project-questions/page.tsx`, update any user-facing text that says "questions" to "problem statements" where it refers to the project question bank (e.g., empty state message).

In `frontend/apps/admin-dashboard/src/app/project-questions/bulk-upload/page.tsx`, update the CardTitle from "Bulk Upload Project Questions" to "Bulk Upload Problem Statements".

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/components/Sidebar.tsx frontend/apps/admin-dashboard/src/app/project-questions/
git commit -m "feat: rename Question Bank to Problem Statements in project UI"
```

---

### Task 11: Backend restart, build, and end-to-end verify

**Step 1: Rebuild shared-utils**

```bash
cd frontend/packages/shared-utils && npm run build
```

**Step 2: Restart content service (if changed) and core-api**

```bash
# Stop and restart core-api (has new endpoints)
cd core-api && ../gradlew bootRun
```

**Step 3: Test bulk setup API**

```bash
# Find sections with enrollments for a course
curl -s http://localhost:8080/api/projects/sections-by-course/<courseId> \
  -H "Authorization: Bearer <token>" \
  -H "X-Client-Id: <clientId>"

# Bulk setup
curl -X POST http://localhost:8080/api/projects/bulk-setup \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "X-Client-Id: <clientId>" \
  -d '{
    "courseId": "<courseId>",
    "sectionIds": ["<sectionId1>", "<sectionId2>"],
    "groupSize": 3,
    "title": "BFSI Capstone Project"
  }'
```

**Step 4: Test frontend wizard**

- Navigate to `/projects` → click "Bulk Setup"
- Walk through all 5 steps
- Verify project created with groups and statements assigned
- Go to project detail → verify "Add Sections" works

**Step 5: Commit any fixes**

```bash
git add -A
git commit -m "feat: complete bulk project setup feature"
```
