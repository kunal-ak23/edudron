# Additive Enrollment & Backlog Sections — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `isBacklog` flag to Class/Section entities and an "Add to Section" action for additive enrollment (non-destructive, alongside existing transfer).

**Architecture:** Extends existing student service entities (Class, Section) with a boolean column. New endpoints mirror the transfer pattern but create new enrollments without removing originals. Frontend adds a dialog on the Enrollments page alongside the existing Transfer dialog.

**Tech Stack:** Spring Boot (student service), Liquibase (YAML), Next.js + shadcn/ui (admin dashboard), shared-utils API client.

---

### Task 1: Database Migration — Add isBacklog to Classes and Sections

**Files:**
- Create: `student/src/main/resources/db/changelog/db.changelog-0021-backlog-flag.yaml`
- Modify: `student/src/main/resources/db/changelog/student-master.yaml`

**Step 1: Create the migration file**

Create `student/src/main/resources/db/changelog/db.changelog-0021-backlog-flag.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: student-0021-add-backlog-to-classes
      author: edudron
      changes:
        - addColumn:
            tableName: classes
            schemaName: student
            columns:
              - column:
                  name: is_backlog
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false

  - changeSet:
      id: student-0021-add-backlog-to-sections
      author: edudron
      changes:
        - addColumn:
            tableName: sections
            schemaName: student
            columns:
              - column:
                  name: is_backlog
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
```

**Step 2: Register migration in master changelog**

Add to `student/src/main/resources/db/changelog/student-master.yaml` after the last include:

```yaml
  - include:
      file: db/changelog/db.changelog-0021-backlog-flag.yaml
```

**Step 3: Verify migration runs locally**

Run: `cd core-api && ../gradlew bootRun`
Expected: Service starts, Liquibase applies 2 changesets. Check logs for `ChangeSet db/changelog/db.changelog-0021-backlog-flag.yaml::student-0021-add-backlog-to-classes` and `student-0021-add-backlog-to-sections` ran successfully.

**Step 4: Commit**

```bash
git add student/src/main/resources/db/changelog/db.changelog-0021-backlog-flag.yaml student/src/main/resources/db/changelog/student-master.yaml
git commit -m "feat: add is_backlog column to classes and sections tables"
```

---

### Task 2: Backend — Add isBacklog to Class and Section Entities

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/domain/Class.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/domain/Section.java`

**Step 1: Add isBacklog field to Class entity**

In `Class.java`, add after the `isActive` field (line 35):

```java
    @Column(name = "is_backlog", nullable = false)
    private Boolean isBacklog = false;
```

Add getter/setter after the `isActive` getter/setter:

```java
    public Boolean getIsBacklog() { return isBacklog; }
    public void setIsBacklog(Boolean isBacklog) { this.isBacklog = isBacklog; }
```

**Step 2: Add isBacklog field to Section entity**

In `Section.java`, add after the `isActive` field (line 37):

```java
    @Column(name = "is_backlog", nullable = false)
    private Boolean isBacklog = false;
```

Add getter/setter after the `isActive` getter/setter:

```java
    public Boolean getIsBacklog() { return isBacklog; }
    public void setIsBacklog(Boolean isBacklog) { this.isBacklog = isBacklog; }
```

**Step 3: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/domain/Class.java student/src/main/java/com/datagami/edudron/student/domain/Section.java
git commit -m "feat: add isBacklog field to Class and Section entities"
```

---

### Task 3: Backend — Add isBacklog to DTOs and Request Objects

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/CreateClassRequest.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/CreateSectionRequest.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/ClassDTO.java` (if exists, else check how classes are returned)
- Modify: `student/src/main/java/com/datagami/edudron/student/dto/SectionDTO.java` (if exists)

**Step 1: Add isBacklog to CreateClassRequest**

In `CreateClassRequest.java`, add after the `isActive` field:

```java
    private Boolean isBacklog = false;

    public Boolean getIsBacklog() { return isBacklog; }
    public void setIsBacklog(Boolean isBacklog) { this.isBacklog = isBacklog; }
```

**Step 2: Add isBacklog to CreateSectionRequest**

In `CreateSectionRequest.java`, add after `maxStudents`:

```java
    private Boolean isBacklog = false;

    public Boolean getIsBacklog() { return isBacklog; }
    public void setIsBacklog(Boolean isBacklog) { this.isBacklog = isBacklog; }
```

**Step 3: Add isBacklog to response DTOs**

Find `ClassDTO.java` and `SectionDTO.java` (or equivalent response classes). Add `isBacklog` field with getter/setter. If classes/sections are returned directly as entities, skip this — the entity field will be serialized automatically.

Run: `grep -r "class ClassDTO\|class SectionDTO" student/src/main/java/` to find DTO files.

**Step 4: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/
git commit -m "feat: add isBacklog to Class and Section DTOs"
```

---

### Task 4: Backend — Service Layer for isBacklog Enforcement

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ClassService.java` (or equivalent)
- Modify: `student/src/main/java/com/datagami/edudron/student/service/SectionService.java` (or equivalent)
- Modify: `student/src/main/java/com/datagami/edudron/student/repo/SectionRepository.java`

**Step 1: Add bulk update query to SectionRepository**

In `SectionRepository.java`, add:

```java
    @Modifying
    @Query("UPDATE Section s SET s.isBacklog = :isBacklog, s.updatedAt = CURRENT_TIMESTAMP WHERE s.classId = :classId AND s.clientId = :clientId")
    int updateIsBacklogByClassId(@Param("classId") String classId, @Param("clientId") UUID clientId, @Param("isBacklog") boolean isBacklog);
```

**Step 2: Update ClassService — handle isBacklog on update**

In the class update method (find with `grep -n "updateClass\|update.*Class" student/src/main/java/com/datagami/edudron/student/service/`), add after setting other fields from the request:

```java
    // Handle isBacklog
    if (request.getIsBacklog() != null) {
        existingClass.setIsBacklog(request.getIsBacklog());
        // When class is marked backlog, cascade to all child sections
        if (Boolean.TRUE.equals(request.getIsBacklog())) {
            sectionRepository.updateIsBacklogByClassId(existingClass.getId(), clientId, true);
        }
    }
```

**Step 3: Update SectionService — handle isBacklog on create and update**

In the section create method, add after setting other fields:

```java
    // Auto-inherit isBacklog from parent class
    Class parentClass = classRepository.findByIdAndClientId(request.getClassId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found"));
    if (Boolean.TRUE.equals(parentClass.getIsBacklog())) {
        section.setIsBacklog(true);
    } else if (request.getIsBacklog() != null) {
        section.setIsBacklog(request.getIsBacklog());
    }
```

In the section update method, add validation:

```java
    // Handle isBacklog
    if (request.getIsBacklog() != null) {
        if (Boolean.FALSE.equals(request.getIsBacklog())) {
            // Reject if parent class is backlog
            Class parentClass = classRepository.findByIdAndClientId(existingSection.getClassId(), clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Class not found"));
            if (Boolean.TRUE.equals(parentClass.getIsBacklog())) {
                throw new IllegalStateException("Cannot remove backlog flag from section when parent class is marked as backlog");
            }
        }
        existingSection.setIsBacklog(request.getIsBacklog());
    }
```

**Step 4: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/
git commit -m "feat: add isBacklog enforcement logic to Class and Section services"
```

---

### Task 5: Backend — Additive Enrollment DTO and Endpoints

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/AddToSectionRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/BulkAddToSectionRequest.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/web/EnrollmentController.java`

**Step 1: Create AddToSectionRequest DTO**

Create `student/src/main/java/com/datagami/edudron/student/dto/AddToSectionRequest.java`:

```java
package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public class AddToSectionRequest {
    @NotBlank(message = "Student ID is required")
    private String studentId;

    @NotBlank(message = "Destination section ID is required")
    private String destinationSectionId;

    private String destinationClassId; // Optional, derived from section if not provided

    public AddToSectionRequest() {}

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getDestinationSectionId() { return destinationSectionId; }
    public void setDestinationSectionId(String destinationSectionId) { this.destinationSectionId = destinationSectionId; }

    public String getDestinationClassId() { return destinationClassId; }
    public void setDestinationClassId(String destinationClassId) { this.destinationClassId = destinationClassId; }
}
```

**Step 2: Create BulkAddToSectionRequest DTO**

Create `student/src/main/java/com/datagami/edudron/student/dto/BulkAddToSectionRequest.java`:

```java
package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BulkAddToSectionRequest {
    @NotEmpty(message = "Student IDs are required")
    private List<String> studentIds;

    @NotBlank(message = "Destination section ID is required")
    private String destinationSectionId;

    private String destinationClassId;

    public BulkAddToSectionRequest() {}

    public List<String> getStudentIds() { return studentIds; }
    public void setStudentIds(List<String> studentIds) { this.studentIds = studentIds; }

    public String getDestinationSectionId() { return destinationSectionId; }
    public void setDestinationSectionId(String destinationSectionId) { this.destinationSectionId = destinationSectionId; }

    public String getDestinationClassId() { return destinationClassId; }
    public void setDestinationClassId(String destinationClassId) { this.destinationClassId = destinationClassId; }
}
```

**Step 3: Add endpoints to EnrollmentController**

In `EnrollmentController.java`, add after the bulk transfer endpoint (~line 168):

```java
    @PostMapping("/enrollments/add-to-section")
    @Operation(summary = "Add student to additional section",
               description = "Enroll a student in an additional section without removing existing enrollments")
    public ResponseEntity<EnrollmentDTO> addToSection(@Valid @RequestBody AddToSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.addToSection(request));
    }

    @PostMapping("/enrollments/bulk-add-to-section")
    @Operation(summary = "Bulk add students to additional section",
               description = "Enroll multiple students in an additional section without removing existing enrollments")
    public ResponseEntity<BulkEnrollmentResult> bulkAddToSection(@Valid @RequestBody BulkAddToSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.bulkAddToSection(request));
    }
```

Add necessary imports at the top of the file.

**Step 4: Verify compilation (will fail — service methods not yet implemented)**

Run: `cd core-api && ../gradlew compileJava`
Expected: FAIL — `addToSection` and `bulkAddToSection` not found in EnrollmentService. This is expected.

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/AddToSectionRequest.java student/src/main/java/com/datagami/edudron/student/dto/BulkAddToSectionRequest.java student/src/main/java/com/datagami/edudron/student/web/EnrollmentController.java
git commit -m "feat: add additive enrollment DTOs and controller endpoints"
```

---

### Task 6: Backend — Additive Enrollment Service Logic

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/EnrollmentService.java`

**Step 1: Implement addToSection method**

Add to `EnrollmentService.java` (after the bulk transfer method):

```java
    @Transactional
    public EnrollmentDTO addToSection(AddToSectionRequest request) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        String studentId = request.getStudentId();
        String destSectionId = request.getDestinationSectionId();

        // Validate destination section exists
        Section destSection = sectionRepository.findByIdAndClientId(destSectionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Destination section not found"));

        String destClassId = request.getDestinationClassId() != null
                ? request.getDestinationClassId()
                : destSection.getClassId();

        // Check if student is already in this section
        List<Enrollment> existingInSection = enrollmentRepository.findByClientIdAndStudentIdAndBatchId(clientId, studentId, destSectionId);
        if (!existingInSection.isEmpty()) {
            // Already enrolled — return existing
            return EnrollmentDTO.fromEntity(existingInSection.get(0));
        }

        // Get courses assigned to the destination section
        // Use same pattern as transfer — fetch from content service
        List<String> destCourseIds = getCoursesForSection(destSectionId, destClassId, clientId);

        if (destCourseIds.isEmpty()) {
            // No courses assigned — create enrollment with section/class only
            Enrollment enrollment = new Enrollment();
            enrollment.setId(UlidGenerator.generate());
            enrollment.setClientId(clientId);
            enrollment.setStudentId(studentId);
            enrollment.setBatchId(destSectionId);
            enrollment.setClassId(destClassId);
            enrollment.setCourseId("__PLACEHOLDER_ASSOCIATION__");
            enrollment.setInstituteId(getInstituteIdForClass(destClassId, clientId));
            enrollment = enrollmentRepository.save(enrollment);
            return EnrollmentDTO.fromEntity(enrollment);
        }

        // Create enrollment for each assigned course
        Enrollment firstEnrollment = null;
        for (String courseId : destCourseIds) {
            // Skip if already enrolled in this course+section
            if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseIdAndBatchId(clientId, studentId, courseId, destSectionId)) {
                continue;
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setId(UlidGenerator.generate());
            enrollment.setClientId(clientId);
            enrollment.setStudentId(studentId);
            enrollment.setCourseId(courseId);
            enrollment.setBatchId(destSectionId);
            enrollment.setClassId(destClassId);
            enrollment.setInstituteId(getInstituteIdForClass(destClassId, clientId));
            enrollment = enrollmentRepository.save(enrollment);
            if (firstEnrollment == null) firstEnrollment = enrollment;
        }

        return EnrollmentDTO.fromEntity(firstEnrollment);
    }
```

**Step 2: Implement bulkAddToSection method**

```java
    @Transactional
    public BulkEnrollmentResult bulkAddToSection(BulkAddToSectionRequest request) {
        int total = request.getStudentIds().size();
        int enrolled = 0;
        int skipped = 0;
        int failed = 0;
        List<String> enrolledStudentIds = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (String studentId : request.getStudentIds()) {
            try {
                AddToSectionRequest singleRequest = new AddToSectionRequest();
                singleRequest.setStudentId(studentId);
                singleRequest.setDestinationSectionId(request.getDestinationSectionId());
                singleRequest.setDestinationClassId(request.getDestinationClassId());

                EnrollmentDTO result = addToSection(singleRequest);
                if (result != null) {
                    enrolled++;
                    enrolledStudentIds.add(studentId);
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                errorMessages.add("Student " + studentId + ": " + e.getMessage());
            }
        }

        BulkEnrollmentResult result = new BulkEnrollmentResult();
        result.setTotalStudents(total);
        result.setEnrolledStudents(enrolled);
        result.setSkippedStudents(skipped);
        result.setFailedStudents(failed);
        result.setEnrolledStudentIds(enrolledStudentIds);
        result.setErrorMessages(errorMessages);
        return result;
    }
```

**Note:** The `getCoursesForSection`, `getInstituteIdForClass`, and `existsByClientIdAndStudentIdAndCourseIdAndBatchId` methods may need to be created or adapted from existing code. Check the transfer method for the pattern used to fetch courses for a destination section (it calls the content service via RestTemplate). Reuse that same helper.

**Step 3: Add any missing repository methods**

If `findByClientIdAndStudentIdAndBatchId` or `existsByClientIdAndStudentIdAndCourseIdAndBatchId` don't exist in `EnrollmentRepository`, add them:

```java
    List<Enrollment> findByClientIdAndStudentIdAndBatchId(UUID clientId, String studentId, String batchId);
    boolean existsByClientIdAndStudentIdAndCourseIdAndBatchId(UUID clientId, String studentId, String courseId, String batchId);
```

**Step 4: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/EnrollmentService.java student/src/main/java/com/datagami/edudron/student/repo/EnrollmentRepository.java
git commit -m "feat: implement addToSection and bulkAddToSection service methods"
```

---

### Task 7: Frontend — Add API Methods to shared-utils

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/enrollments.ts`

**Step 1: Add request/response types**

In `enrollments.ts`, add after the existing transfer types:

```typescript
export interface AddToSectionRequest {
  studentId: string
  destinationSectionId: string
  destinationClassId?: string
}

export interface BulkAddToSectionRequest {
  studentIds: string[]
  destinationSectionId: string
  destinationClassId?: string
}
```

**Step 2: Add API methods**

In the `EnrollmentsApi` class, add after the bulk transfer method:

```typescript
  async addToSection(request: AddToSectionRequest): Promise<Enrollment> {
    const response = await this.apiClient.post<Enrollment>('/api/enrollments/add-to-section', request)
    return response.data
  }

  async bulkAddToSection(request: BulkAddToSectionRequest): Promise<BulkEnrollmentResult> {
    const response = await this.apiClient.post<BulkEnrollmentResult>('/api/enrollments/bulk-add-to-section', request)
    return response.data
  }
```

**Step 3: Build shared-utils**

Run: `cd frontend/packages/shared-utils && npm run build`
Expected: Build success

**Step 4: Commit**

```bash
git add frontend/packages/shared-utils/src/api/enrollments.ts
git commit -m "feat: add addToSection and bulkAddToSection API methods"
```

---

### Task 8: Frontend — Add "Add to Section" Dialog on Enrollments Page

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/enrollments/page.tsx`

**Step 1: Add state variables**

After the existing transfer state variables (~line 88-97), add:

```typescript
  const [showAddToSectionDialog, setShowAddToSectionDialog] = useState(false)
  const [addToSectionEnrollments, setAddToSectionEnrollments] = useState<Enrollment[]>([])
  const [addToSectionDestSectionId, setAddToSectionDestSectionId] = useState('')
  const [addToSectionDestClassId, setAddToSectionDestClassId] = useState('')
  const [addingToSection, setAddingToSection] = useState(false)
```

**Step 2: Add handler functions**

After the existing transfer handlers, add:

```typescript
  const handleAddToSectionClick = () => {
    const selected = enrollments.filter(e => selectedEnrollmentIds.includes(e.id))
    if (selected.length === 0) return
    setAddToSectionEnrollments(selected)
    setAddToSectionDestSectionId('')
    setAddToSectionDestClassId('')
    setShowAddToSectionDialog(true)
  }

  const handleAddToSectionConfirm = async () => {
    if (!addToSectionDestSectionId) return
    setAddingToSection(true)
    try {
      const studentIds = [...new Set(addToSectionEnrollments.map(e => e.studentId))]
      const result = await enrollmentsApi.bulkAddToSection({
        studentIds,
        destinationSectionId: addToSectionDestSectionId,
        destinationClassId: addToSectionDestClassId || undefined,
      })
      toast({
        title: 'Students added to section',
        description: `${result.enrolledStudents} enrolled, ${result.skippedStudents} skipped, ${result.failedStudents} failed`,
      })
      setShowAddToSectionDialog(false)
      setSelectedEnrollmentIds([])
      loadEnrollments() // refresh the list
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to add students to section',
        description: extractErrorMessage(error),
      })
    } finally {
      setAddingToSection(false)
    }
  }
```

**Step 3: Add "Add to Section" button**

Find where the "Transfer Selected" button is rendered (in the bulk actions bar). Add alongside it:

```tsx
<Button
  variant="outline"
  size="sm"
  onClick={handleAddToSectionClick}
  disabled={selectedEnrollmentIds.length === 0}
>
  <Plus className="h-4 w-4 mr-1" />
  Add to Section
</Button>
```

**Step 4: Add the dialog**

After the existing Transfer dialog, add the Add to Section dialog. Follow the same pattern as the transfer dialog but simpler (no "move from" logic, just destination picker):

```tsx
<Dialog open={showAddToSectionDialog} onOpenChange={setShowAddToSectionDialog}>
  <DialogContent>
    <DialogHeader>
      <DialogTitle>Add to Section</DialogTitle>
      <DialogDescription>
        {addToSectionEnrollments.length} student(s) will be enrolled in the selected section.
        Their existing enrollments will be retained.
      </DialogDescription>
    </DialogHeader>
    <div className="space-y-4 py-4">
      {/* Class filter (optional) */}
      <div className="space-y-2">
        <Label>Filter by Class (optional)</Label>
        <SearchableSelect
          options={classes.map(c => ({ value: c.id, label: `${c.name}${c.isBacklog ? ' (Backlog)' : ''}` }))}
          value={addToSectionDestClassId}
          onChange={setAddToSectionDestClassId}
          placeholder="All classes..."
        />
      </div>
      {/* Destination section */}
      <div className="space-y-2">
        <Label>Destination Section</Label>
        <SearchableSelect
          options={sections
            .filter(s => !addToSectionDestClassId || s.classId === addToSectionDestClassId)
            .map(s => ({ value: s.id, label: `${s.name}${s.isBacklog ? ' (Backlog)' : ''}` }))}
          value={addToSectionDestSectionId}
          onChange={setAddToSectionDestSectionId}
          placeholder="Select section..."
        />
      </div>
    </div>
    <DialogFooter>
      <Button variant="outline" onClick={() => setShowAddToSectionDialog(false)}>Cancel</Button>
      <Button
        onClick={handleAddToSectionConfirm}
        disabled={!addToSectionDestSectionId || addingToSection}
      >
        {addingToSection ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
        Add to Section
      </Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

**Note:** The `classes` and `sections` lists should already be loaded for the transfer dialog. If not, check how the transfer dialog loads them and reuse that data. The `isBacklog` field will need to be part of the section/class response from the API.

**Step 5: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/enrollments/page.tsx
git commit -m "feat: add 'Add to Section' dialog on Enrollments page"
```

---

### Task 9: Frontend — Add isBacklog Toggle to Class and Section Edit Forms

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/classes/[id]/page.tsx`
- Modify: `frontend/apps/admin-dashboard/src/app/sections/[id]/page.tsx`

**Step 1: Class detail page — add isBacklog toggle**

In the class edit form, find where `isActive` is rendered and add after it:

```tsx
<div className="flex items-center justify-between">
  <div>
    <Label>Backlog Class</Label>
    <p className="text-sm text-muted-foreground">
      Mark this class as a backlog class. All sections will inherit this flag.
    </p>
  </div>
  <Switch
    checked={formData.isBacklog || false}
    onCheckedChange={(checked) => setFormData({ ...formData, isBacklog: checked })}
  />
</div>
```

Ensure `isBacklog` is included in the `formData` state initialization and the save handler sends it to the API.

**Step 2: Section detail page — add isBacklog toggle**

In the section edit form, add after `isActive`:

```tsx
<div className="flex items-center justify-between">
  <div>
    <Label>Backlog Section</Label>
    {parentClassIsBacklog ? (
      <p className="text-sm text-amber-600">Inherited from backlog class</p>
    ) : (
      <p className="text-sm text-muted-foreground">Mark this section for backlog exams</p>
    )}
  </div>
  <Switch
    checked={formData.isBacklog || false}
    onCheckedChange={(checked) => setFormData({ ...formData, isBacklog: checked })}
    disabled={parentClassIsBacklog}
  />
</div>
```

To determine `parentClassIsBacklog`, fetch the parent class when loading the section:

```typescript
const [parentClassIsBacklog, setParentClassIsBacklog] = useState(false)

// In loadSection effect, after loading section:
const parentClass = await classesApi.getClass(section.classId)
setParentClassIsBacklog(parentClass.isBacklog || false)
```

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/classes/[id]/page.tsx frontend/apps/admin-dashboard/src/app/sections/[id]/page.tsx
git commit -m "feat: add isBacklog toggle to class and section edit forms"
```

---

### Task 10: Frontend — Add Backlog Badges to List Views

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/classes/page.tsx` (or wherever classes are listed)
- Modify: `frontend/apps/admin-dashboard/src/app/sections/page.tsx` (or section list in class detail)

**Step 1: Add Backlog badge to class list**

Where class names are rendered in list views, add:

```tsx
{classItem.isBacklog && (
  <Badge variant="outline" className="ml-2 text-amber-600 border-amber-300 bg-amber-50">
    Backlog
  </Badge>
)}
```

**Step 2: Add Backlog badge to section list**

Same pattern for section names:

```tsx
{section.isBacklog && (
  <Badge variant="outline" className="ml-2 text-amber-600 border-amber-300 bg-amber-50">
    Backlog
  </Badge>
)}
```

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/classes/ frontend/apps/admin-dashboard/src/app/sections/
git commit -m "feat: add Backlog badges to class and section list views"
```

---

### Task 11: Build, Test, and Verify End-to-End

**Step 1: Rebuild shared-utils**

```bash
cd frontend/packages/shared-utils && npm run build
```

**Step 2: Start backend locally**

```bash
docker-compose -f docker-compose.db-only.yml up -d  # if DB not running
cd core-api && ../gradlew bootRun
```

Verify migration runs: check logs for `0021-backlog-flag` changesets.

**Step 3: Test backend APIs**

Test isBacklog on class:
```bash
# Update a class to isBacklog=true
curl -X PUT http://localhost:8080/api/classes/{classId} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "X-Client-Id: <clientId>" \
  -d '{"name":"Backlog Class","code":"BL","instituteId":"...","isBacklog":true}'
```

Test additive enrollment:
```bash
curl -X POST http://localhost:8080/api/enrollments/bulk-add-to-section \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "X-Client-Id: <clientId>" \
  -d '{"studentIds":["student1","student2"],"destinationSectionId":"section1"}'
```

**Step 4: Start frontend and test UI**

```bash
cd frontend/apps/admin-dashboard && npm run dev
```

- Navigate to Enrollments page
- Select students, verify "Add to Section" button appears
- Click it, verify dialog shows classes/sections with Backlog badges
- Confirm add, verify toast shows success counts
- Navigate to a class, toggle isBacklog, verify sections inherit
- Navigate to a section under a backlog class, verify toggle is disabled

**Step 5: Final commit**

```bash
git add -A
git commit -m "feat: complete additive enrollment and backlog sections feature"
```
