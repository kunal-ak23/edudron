# Calendar & Faculty Coordinator Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add faculty coordinator designation to classes/batches, then build a full academic calendar with role-based visibility, recurring events, personal events, CSV import/export, and tenant-configurable colors.

**Architecture:** Faculty Coordinator adds a `coordinatorUserId` column to existing `Class` and `Batch` entities. Calendar uses a new `calendar` schema in the student service with a `CalendarEvent` entity. Both features live in the student module (runs inside core-api). Frontend gets a `/calendar` page + dashboard widget in both admin and student portal.

**Tech Stack:** Spring Boot (Java 21), JPA/Hibernate, Liquibase (YAML), PostgreSQL (jsonb, timestamptz), Next.js 14, React Query, Tailwind CSS, shadcn/ui

**Design Doc:** `docs/plans/2026-03-28-calendar-and-faculty-coordinator-design.md`

---

## Phase 1: Faculty Coordinator

### Task 1: Database Migration — Add coordinator columns

**Files:**
- Create: `student/src/main/resources/db/changelog/db.changelog-0028-faculty-coordinator.yaml`
- Modify: `student/src/main/resources/db/changelog/student-master.yaml` (add include at end)

**Step 1: Create the Liquibase changelog**

```yaml
databaseChangeLog:
  - changeSet:
      id: 0028-add-coordinator-to-classes
      author: edudron
      changes:
        - addColumn:
            schemaName: student
            tableName: classes
            columns:
              - column:
                  name: coordinator_user_id
                  type: varchar(255)
                  constraints:
                    nullable: true

  - changeSet:
      id: 0028-add-coordinator-to-batches
      author: edudron
      changes:
        - addColumn:
            schemaName: student
            tableName: batches
            columns:
              - column:
                  name: coordinator_user_id
                  type: varchar(255)
                  constraints:
                    nullable: true
```

**Step 2: Add include to student-master.yaml**

Add at the end of the includes list:
```yaml
  - include:
      file: db/changelog/db.changelog-0028-faculty-coordinator.yaml
```

**Step 3: Verify migration runs**

Run: `cd core-api && ../gradlew bootRun`
Expected: Service starts without Liquibase errors, new columns appear in DB.

**Step 4: Commit**

```bash
git add student/src/main/resources/db/changelog/db.changelog-0028-faculty-coordinator.yaml student/src/main/resources/db/changelog/student-master.yaml
git commit -m "feat: add coordinator_user_id columns to classes and batches tables"
```

---

### Task 2: Update Class & Batch Entities

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/domain/Class.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/domain/Batch.java`

**Step 1: Add coordinatorUserId field to Class.java**

After the `isBacklog` field (around line 38), add:

```java
@Column(name = "coordinator_user_id")
private String coordinatorUserId;
```

Add getter and setter in the getters/setters section:

```java
public String getCoordinatorUserId() { return coordinatorUserId; }
public void setCoordinatorUserId(String coordinatorUserId) { this.coordinatorUserId = coordinatorUserId; }
```

**Step 2: Add coordinatorUserId field to Batch.java**

After the `isActive` field, add the same field + getter/setter:

```java
@Column(name = "coordinator_user_id")
private String coordinatorUserId;
```

```java
public String getCoordinatorUserId() { return coordinatorUserId; }
public void setCoordinatorUserId(String coordinatorUserId) { this.coordinatorUserId = coordinatorUserId; }
```

**Step 3: Verify compilation**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/domain/Class.java student/src/main/java/com/datagami/edudron/student/domain/Batch.java
git commit -m "feat: add coordinatorUserId field to Class and Batch entities"
```

---

### Task 3: Coordinator DTO

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CoordinatorAssignmentRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CoordinatorResponse.java`

**Step 1: Create request DTO**

```java
package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public record CoordinatorAssignmentRequest(
    @NotBlank(message = "coordinatorUserId is required")
    String coordinatorUserId
) {}
```

**Step 2: Create response DTO**

```java
package com.datagami.edudron.student.dto;

public record CoordinatorResponse(
    String coordinatorUserId,
    String coordinatorName,
    String coordinatorEmail
) {}
```

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/CoordinatorAssignmentRequest.java student/src/main/java/com/datagami/edudron/student/dto/CoordinatorResponse.java
git commit -m "feat: add coordinator DTOs for assignment request and response"
```

---

### Task 4: Coordinator Service Logic

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/service/ClassService.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/service/BatchService.java`

The service needs access to the identity module to validate that the user exists and has INSTRUCTOR role. Check how inter-module user lookups currently work.

**Reference:** Look at `student/src/main/java/com/datagami/edudron/student/client/` for existing service client patterns. If there's a `UserClient` or similar, use it. Otherwise, since student runs inside core-api, the identity module's `UserRepository` may be directly accessible.

**Step 1: Add coordinator methods to ClassService.java**

Add these methods:

```java
@Transactional
public CoordinatorResponse assignClassCoordinator(String classId, String coordinatorUserId, String actorEmail) {
    UUID clientId = TenantContext.getCurrentTenant();
    Class clazz = classRepository.findByIdAndClientId(classId, clientId)
        .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));

    // Validate user exists and is an instructor — use the identity client/repository
    // to fetch user by ID and verify role == INSTRUCTOR
    // If user not found or not INSTRUCTOR, throw IllegalArgumentException

    String previousCoordinator = clazz.getCoordinatorUserId();
    clazz.setCoordinatorUserId(coordinatorUserId);
    classRepository.save(clazz);

    // Audit log
    Map<String, Object> meta = new HashMap<>();
    meta.put("classId", classId);
    meta.put("className", clazz.getName());
    meta.put("coordinatorUserId", coordinatorUserId);
    if (previousCoordinator != null) {
        meta.put("previousCoordinatorUserId", previousCoordinator);
        auditService.logCrud(clientId, "UPDATE", "FacultyCoordinator", classId, null, actorEmail, meta);
    } else {
        auditService.logCrud(clientId, "CREATE", "FacultyCoordinator", classId, null, actorEmail, meta);
    }

    // Return coordinator details (name, email from user lookup)
    return new CoordinatorResponse(coordinatorUserId, /* name */, /* email */);
}

@Transactional
public void removeClassCoordinator(String classId, String actorEmail) {
    UUID clientId = TenantContext.getCurrentTenant();
    Class clazz = classRepository.findByIdAndClientId(classId, clientId)
        .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));

    String previousCoordinator = clazz.getCoordinatorUserId();
    if (previousCoordinator == null) {
        throw new IllegalArgumentException("Class has no coordinator assigned");
    }

    clazz.setCoordinatorUserId(null);
    classRepository.save(clazz);

    Map<String, Object> meta = new HashMap<>();
    meta.put("classId", classId);
    meta.put("previousCoordinatorUserId", previousCoordinator);
    auditService.logCrud(clientId, "DELETE", "FacultyCoordinator", classId, null, actorEmail, meta);
}

@Transactional(readOnly = true)
public CoordinatorResponse getClassCoordinator(String classId) {
    UUID clientId = TenantContext.getCurrentTenant();
    Class clazz = classRepository.findByIdAndClientId(classId, clientId)
        .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));

    if (clazz.getCoordinatorUserId() == null) {
        return null;
    }

    // Fetch user details and return CoordinatorResponse
    return new CoordinatorResponse(clazz.getCoordinatorUserId(), /* name */, /* email */);
}
```

**Step 2: Add equivalent methods to BatchService.java**

Same pattern as above but operating on `Batch` entity and `batchRepository`.

**Step 3: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/ClassService.java student/src/main/java/com/datagami/edudron/student/service/BatchService.java
git commit -m "feat: add coordinator assignment/removal service methods"
```

---

### Task 5: Coordinator Controller Endpoints

**Files:**
- Modify: `student/src/main/java/com/datagami/edudron/student/web/ClassController.java`
- Modify: `student/src/main/java/com/datagami/edudron/student/web/BatchController.java`

**Step 1: Add coordinator endpoints to ClassController.java**

```java
@PutMapping("/classes/{id}/coordinator")
public ResponseEntity<CoordinatorResponse> assignClassCoordinator(
        @PathVariable String id,
        @Valid @RequestBody CoordinatorAssignmentRequest request,
        @RequestAttribute(value = "userEmail", required = false) String actorEmail) {
    CoordinatorResponse response = classService.assignClassCoordinator(id, request.coordinatorUserId(), actorEmail);
    return ResponseEntity.ok(response);
}

@DeleteMapping("/classes/{id}/coordinator")
public ResponseEntity<Void> removeClassCoordinator(
        @PathVariable String id,
        @RequestAttribute(value = "userEmail", required = false) String actorEmail) {
    classService.removeClassCoordinator(id, actorEmail);
    return ResponseEntity.noContent().build();
}

@GetMapping("/classes/{id}/coordinator")
public ResponseEntity<CoordinatorResponse> getClassCoordinator(@PathVariable String id) {
    CoordinatorResponse response = classService.getClassCoordinator(id);
    if (response == null) {
        return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(response);
}
```

**Step 2: Add equivalent endpoints to BatchController.java**

Same pattern: `PUT /api/batches/{id}/coordinator`, `DELETE /api/batches/{id}/coordinator`, `GET /api/batches/{id}/coordinator`.

**Step 3: Test endpoints manually**

Test with curl or Postman:
```bash
# Assign coordinator
curl -X PUT http://localhost:8080/api/classes/{classId}/coordinator \
  -H "Authorization: Bearer <admin-token>" \
  -H "X-Client-Id: <tenant-id>" \
  -H "Content-Type: application/json" \
  -d '{"coordinatorUserId": "<instructor-user-id>"}'
```

**Step 4: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/web/ClassController.java student/src/main/java/com/datagami/edudron/student/web/BatchController.java
git commit -m "feat: add coordinator REST endpoints for classes and batches"
```

---

### Task 6: Update Class/Batch DTOs to Include Coordinator

**Files:**
- Check: existing class and batch DTOs in `student/src/main/java/com/datagami/edudron/student/dto/`
- Modify: the DTO conversion methods in ClassService and BatchService (the `toDTO()` methods)

**Step 1: Find existing DTOs**

Check `student/src/main/java/com/datagami/edudron/student/dto/` for `ClassDTO`, `ClassResponse`, or similar. Also check inline records in the service files.

**Step 2: Add coordinatorUserId and coordinatorName to existing DTOs**

So that GET `/api/classes` and GET `/api/classes/{id}` responses include coordinator info.

**Step 3: Verify existing list/detail APIs return coordinator info**

Run: `curl http://localhost:8080/api/classes -H "Authorization: Bearer <token>" -H "X-Client-Id: <tenant>"`
Expected: Response includes `coordinatorUserId` and `coordinatorName` fields.

**Step 4: Commit**

```bash
git commit -am "feat: include coordinator info in class and batch API responses"
```

---

### Task 7: Frontend — Coordinator Assignment UI (Admin Dashboard)

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/` — add coordinator methods to existing Classes/Batches API or create new methods
- Modify: Admin dashboard class detail / batch detail pages to show coordinator and allow assignment
- Modify: `frontend/apps/admin-dashboard/src/lib/api.ts` — if new API class needed

**Step 1: Add API methods in shared-utils**

In the relevant API class (ClassesApi or similar), add:
```typescript
async assignClassCoordinator(classId: string, coordinatorUserId: string): Promise<CoordinatorResponse> {
  return this.apiClient.put(`/api/classes/${classId}/coordinator`, { coordinatorUserId });
}

async removeClassCoordinator(classId: string): Promise<void> {
  return this.apiClient.delete(`/api/classes/${classId}/coordinator`);
}

async getClassCoordinator(classId: string): Promise<CoordinatorResponse | null> {
  return this.apiClient.get(`/api/classes/${classId}/coordinator`);
}
```

Same for batches.

**Step 2: Build shared-utils**

Run: `cd frontend/packages/shared-utils && npm run build`

**Step 3: Add coordinator section to class/batch detail pages**

Show current coordinator (name + email) with a "Change" button. Clicking opens a dropdown/search of instructors in the tenant. Admin-only visibility.

**Step 4: Commit**

```bash
git commit -am "feat: add coordinator assignment UI to class and batch detail pages"
```

---

## Phase 2: Calendar Backend

### Task 8: Database Migration — Calendar Schema & Events Table

**Files:**
- Create: `student/src/main/resources/db/changelog/db.changelog-0029-calendar-events.yaml`
- Modify: `student/src/main/resources/db/changelog/student-master.yaml`
- Modify: `core-api/src/main/java/com/datagami/edudron/coreapi/config/LiquibaseConfig.java` — add calendar schema Liquibase bean

**Step 1: Create the calendar changelog**

```yaml
databaseChangeLog:
  - changeSet:
      id: 0029-create-calendar-schema
      author: edudron
      changes:
        - sql:
            sql: CREATE SCHEMA IF NOT EXISTS calendar

  - changeSet:
      id: 0029-create-calendar-events-table
      author: edudron
      changes:
        - createTable:
            schemaName: calendar
            tableName: calendar_events
            columns:
              - column:
                  name: id
                  type: varchar(255)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: client_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: title
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: text
              - column:
                  name: event_type
                  type: varchar(50)
                  constraints:
                    nullable: false
              - column:
                  name: custom_type_label
                  type: varchar(100)
              - column:
                  name: start_date_time
                  type: timestamptz
                  constraints:
                    nullable: false
              - column:
                  name: end_date_time
                  type: timestamptz
              - column:
                  name: all_day
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: audience
                  type: varchar(30)
                  constraints:
                    nullable: false
              - column:
                  name: class_id
                  type: varchar(255)
              - column:
                  name: batch_id
                  type: varchar(255)
              - column:
                  name: section_id
                  type: varchar(255)
              - column:
                  name: created_by_user_id
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: is_recurring
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: recurrence_rule
                  type: varchar(255)
              - column:
                  name: recurrence_parent_id
                  type: varchar(255)
              - column:
                  name: meeting_link
                  type: varchar(500)
              - column:
                  name: location
                  type: varchar(255)
              - column:
                  name: color
                  type: varchar(7)
              - column:
                  name: metadata
                  type: jsonb
              - column:
                  name: is_active
                  type: boolean
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamptz
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamptz
                  constraints:
                    nullable: false

  - changeSet:
      id: 0029-calendar-events-indexes
      author: edudron
      changes:
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_client_start
            columns:
              - column:
                  name: client_id
              - column:
                  name: start_date_time
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_client_audience
            columns:
              - column:
                  name: client_id
              - column:
                  name: audience
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_client_class
            columns:
              - column:
                  name: client_id
              - column:
                  name: class_id
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_client_batch
            columns:
              - column:
                  name: client_id
              - column:
                  name: batch_id
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_client_section
            columns:
              - column:
                  name: client_id
              - column:
                  name: section_id
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_personal
            columns:
              - column:
                  name: client_id
              - column:
                  name: created_by_user_id
              - column:
                  name: audience
        - createIndex:
            schemaName: calendar
            tableName: calendar_events
            indexName: idx_calendar_events_recurrence_parent
            columns:
              - column:
                  name: recurrence_parent_id
```

**Step 2: Add include to student-master.yaml**

```yaml
  - include:
      file: db/changelog/db.changelog-0029-calendar-events.yaml
```

**Step 3: Add calendar schema Liquibase bean to LiquibaseConfig.java**

In `core-api/src/main/java/com/datagami/edudron/coreapi/config/LiquibaseConfig.java`, add a new `SpringLiquibase` bean for the calendar schema, following the same pattern as the student schema bean. Set `@DependsOn` to depend on the student Liquibase bean.

**Step 4: Verify migration runs**

Run: `cd core-api && ../gradlew bootRun`
Expected: Calendar schema created, `calendar_events` table with all columns and indexes.

**Step 5: Commit**

```bash
git add student/src/main/resources/db/changelog/db.changelog-0029-calendar-events.yaml student/src/main/resources/db/changelog/student-master.yaml core-api/src/main/java/com/datagami/edudron/coreapi/config/LiquibaseConfig.java
git commit -m "feat: add calendar schema and calendar_events table migration"
```

---

### Task 9: CalendarEvent Entity & Enums

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/domain/CalendarEvent.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/EventType.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/EventAudience.java`

**Step 1: Create EventType enum**

```java
package com.datagami.edudron.student.domain;

public enum EventType {
    HOLIDAY,
    EXAM,
    SUBMISSION_DEADLINE,
    FACULTY_MEETING,
    REVIEW,
    GENERAL,
    CUSTOM,
    PERSONAL
}
```

**Step 2: Create EventAudience enum**

```java
package com.datagami.edudron.student.domain;

public enum EventAudience {
    TENANT_WIDE,
    CLASS,
    BATCH,
    SECTION,
    FACULTY_ONLY,
    PERSONAL
}
```

**Step 3: Create CalendarEvent entity**

```java
package com.datagami.edudron.student.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.datagami.edudron.common.util.UlidGenerator;

@Entity
@Table(name = "calendar_events", schema = "calendar")
public class CalendarEvent {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "custom_type_label")
    private String customTypeLabel;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time")
    private OffsetDateTime endDateTime;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private EventAudience audience;

    @Column(name = "class_id")
    private String classId;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "section_id")
    private String sectionId;

    @Column(name = "created_by_user_id", nullable = false)
    private String createdByUserId;

    @Column(name = "is_recurring", nullable = false)
    private boolean isRecurring = false;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Column(name = "recurrence_parent_id")
    private String recurrenceParentId;

    @Column(name = "meeting_link")
    private String meetingLink;

    @Column(name = "location")
    private String location;

    @Column(name = "color")
    private String color;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public CalendarEvent() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.generate();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and setters for all fields
    // (generate standard getters/setters for every field above)
}
```

**Step 4: Verify compilation**

Run: `cd student && ../gradlew compileJava`

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/domain/CalendarEvent.java student/src/main/java/com/datagami/edudron/student/domain/EventType.java student/src/main/java/com/datagami/edudron/student/domain/EventAudience.java
git commit -m "feat: add CalendarEvent entity with EventType and EventAudience enums"
```

---

### Task 10: Calendar Repository

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/repo/CalendarEventRepository.java`

**Step 1: Create repository**

```java
package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.CalendarEvent;
import com.datagami.edudron.student.domain.EventAudience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, String>, JpaSpecificationExecutor<CalendarEvent> {

    Optional<CalendarEvent> findByIdAndClientId(String id, UUID clientId);

    List<CalendarEvent> findByClientIdAndIsActiveTrue(UUID clientId);

    List<CalendarEvent> findByRecurrenceParentIdAndIsActiveTrue(String parentId);

    @Modifying
    @Query("UPDATE CalendarEvent e SET e.isActive = false WHERE e.recurrenceParentId = :parentId OR e.id = :parentId")
    int softDeleteSeries(@Param("parentId") String parentId);

    @Modifying
    @Query("UPDATE CalendarEvent e SET e.isActive = false WHERE (e.recurrenceParentId = :parentId OR e.id = :parentId) AND e.startDateTime >= :fromDate")
    int softDeleteFutureOccurrences(@Param("parentId") String parentId, @Param("fromDate") OffsetDateTime fromDate);
}
```

**Step 2: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/repo/CalendarEventRepository.java
git commit -m "feat: add CalendarEventRepository with series operations"
```

---

### Task 11: Calendar DTOs

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CreateCalendarEventRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/UpdateCalendarEventRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CalendarEventResponse.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CalendarEventImportResult.java`

**Step 1: Create request DTO**

```java
package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

public record CreateCalendarEventRequest(
    @NotBlank(message = "Title is required") String title,
    String description,
    @NotNull(message = "Event type is required") EventType eventType,
    String customTypeLabel,
    @NotNull(message = "Start date/time is required") OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    boolean allDay,
    @NotNull(message = "Audience is required") EventAudience audience,
    String classId,
    String batchId,
    String sectionId,
    boolean isRecurring,
    String recurrenceRule,
    String meetingLink,
    String location,
    String color,
    Map<String, Object> metadata
) {}
```

**Step 2: Create update DTO**

Same fields as create but all optional (no @NotNull/@NotBlank). Use a class instead of record so fields can be null to indicate "no change."

**Step 3: Create response DTO**

```java
package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.EventAudience;
import com.datagami.edudron.student.domain.EventType;

import java.time.OffsetDateTime;
import java.util.Map;

public record CalendarEventResponse(
    String id,
    String title,
    String description,
    EventType eventType,
    String customTypeLabel,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    boolean allDay,
    EventAudience audience,
    String classId,
    String className,
    String batchId,
    String batchName,
    String sectionId,
    String sectionName,
    String createdByUserId,
    String createdByName,
    boolean isRecurring,
    String recurrenceRule,
    String recurrenceParentId,
    String meetingLink,
    String location,
    String color,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
```

**Step 4: Create import result DTO**

```java
package com.datagami.edudron.student.dto;

import java.util.List;

public record CalendarEventImportResult(
    int created,
    int errors,
    List<ImportError> errorDetails
) {
    public record ImportError(int row, String message) {}
}
```

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/CreateCalendarEventRequest.java student/src/main/java/com/datagami/edudron/student/dto/UpdateCalendarEventRequest.java student/src/main/java/com/datagami/edudron/student/dto/CalendarEventResponse.java student/src/main/java/com/datagami/edudron/student/dto/CalendarEventImportResult.java
git commit -m "feat: add calendar event DTOs for create, update, response, and import"
```

---

### Task 12: Calendar Event Specification (Visibility Query)

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/CalendarEventSpecification.java`

This is the core "who sees what" logic.

**Step 1: Create specification builder**

```java
package com.datagami.edudron.student.service;

import com.datagami.edudron.student.domain.CalendarEvent;
import com.datagami.edudron.student.domain.EventAudience;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CalendarEventSpecification {

    public static Specification<CalendarEvent> forStudent(
            UUID clientId, String userId,
            Set<String> classIds, Set<String> batchIds, Set<String> sectionIds,
            OffsetDateTime startDate, OffsetDateTime endDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), startDate));
            predicates.add(cb.lessThanOrEqualTo(root.get("startDateTime"), endDate));

            // Audience visibility
            List<Predicate> audiencePredicates = new ArrayList<>();
            audiencePredicates.add(cb.equal(root.get("audience"), EventAudience.TENANT_WIDE));

            if (!classIds.isEmpty()) {
                audiencePredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.CLASS),
                    root.get("classId").in(classIds)
                ));
            }
            if (!batchIds.isEmpty()) {
                audiencePredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.BATCH),
                    root.get("batchId").in(batchIds)
                ));
            }
            if (!sectionIds.isEmpty()) {
                audiencePredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.SECTION),
                    root.get("sectionId").in(sectionIds)
                ));
            }

            // Personal events for this user
            audiencePredicates.add(cb.and(
                cb.equal(root.get("audience"), EventAudience.PERSONAL),
                cb.equal(root.get("createdByUserId"), userId)
            ));

            // Exclude FACULTY_ONLY
            predicates.add(cb.or(audiencePredicates.toArray(new Predicate[0])));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<CalendarEvent> forInstructor(
            UUID clientId, String userId,
            Set<String> classIds, Set<String> sectionIds, Set<String> batchIds,
            OffsetDateTime startDate, OffsetDateTime endDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), startDate));
            predicates.add(cb.lessThanOrEqualTo(root.get("startDateTime"), endDate));

            List<Predicate> audiencePredicates = new ArrayList<>();
            audiencePredicates.add(cb.equal(root.get("audience"), EventAudience.TENANT_WIDE));
            audiencePredicates.add(cb.equal(root.get("audience"), EventAudience.FACULTY_ONLY));

            if (!classIds.isEmpty()) {
                audiencePredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.CLASS),
                    root.get("classId").in(classIds)
                ));
            }
            if (!batchIds.isEmpty()) {
                audiencePredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.BATCH),
                    root.get("batchId").in(batchIds)
                ));
            }
            if (!sectionIds.isEmpty()) {
                audiencePredicates.add(cb.and(
                    cb.equal(root.get("audience"), EventAudience.SECTION),
                    root.get("sectionId").in(sectionIds)
                ));
            }

            audiencePredicates.add(cb.and(
                cb.equal(root.get("audience"), EventAudience.PERSONAL),
                cb.equal(root.get("createdByUserId"), userId)
            ));

            predicates.add(cb.or(audiencePredicates.toArray(new Predicate[0])));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<CalendarEvent> forAdmin(
            UUID clientId, String userId,
            OffsetDateTime startDate, OffsetDateTime endDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), startDate));
            predicates.add(cb.lessThanOrEqualTo(root.get("startDateTime"), endDate));

            // Admins see all institutional events + own personal
            predicates.add(cb.or(
                cb.notEqual(root.get("audience"), EventAudience.PERSONAL),
                cb.and(
                    cb.equal(root.get("audience"), EventAudience.PERSONAL),
                    cb.equal(root.get("createdByUserId"), userId)
                )
            ));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

**Step 2: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/CalendarEventSpecification.java
git commit -m "feat: add CalendarEventSpecification for role-based visibility queries"
```

---

### Task 13: Recurrence Generator Utility

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/util/RecurrenceGenerator.java`

**Step 1: Create utility**

Parses iCal RRULE strings and generates materialized occurrences. Supports `FREQ=DAILY`, `FREQ=WEEKLY` (with BYDAY), `FREQ=MONTHLY`. Limits: `COUNT` or `UNTIL`, max horizon 6 months.

```java
package com.datagami.edudron.student.util;

import com.datagami.edudron.student.domain.CalendarEvent;
import com.datagami.edudron.common.util.UlidGenerator;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class RecurrenceGenerator {

    private static final int MAX_OCCURRENCES = 200;
    private static final int DEFAULT_HORIZON_MONTHS = 6;

    public static List<CalendarEvent> generateOccurrences(CalendarEvent parent) {
        if (!parent.isRecurring() || parent.getRecurrenceRule() == null) {
            return Collections.emptyList();
        }

        Map<String, String> ruleParts = parseRRule(parent.getRecurrenceRule());
        String freq = ruleParts.getOrDefault("FREQ", "WEEKLY");
        int count = Integer.parseInt(ruleParts.getOrDefault("COUNT", "0"));
        String until = ruleParts.get("UNTIL");
        String byDay = ruleParts.get("BYDAY");
        int interval = Integer.parseInt(ruleParts.getOrDefault("INTERVAL", "1"));

        OffsetDateTime maxEnd = parent.getStartDateTime().plusMonths(DEFAULT_HORIZON_MONTHS);
        if (until != null) {
            maxEnd = parseUntil(until, parent.getStartDateTime().getOffset());
        }

        List<CalendarEvent> occurrences = new ArrayList<>();
        OffsetDateTime current = parent.getStartDateTime();
        Duration duration = parent.getEndDateTime() != null
            ? Duration.between(parent.getStartDateTime(), parent.getEndDateTime())
            : Duration.ZERO;
        int generated = 0;

        while (current.isBefore(maxEnd) && (count == 0 || generated < count) && generated < MAX_OCCURRENCES) {
            current = nextOccurrence(current, freq, interval, byDay, generated == 0);
            if (current.isAfter(maxEnd) || (count > 0 && generated >= count)) break;

            CalendarEvent occurrence = copyAsOccurrence(parent, current, duration);
            occurrences.add(occurrence);
            generated++;
        }

        return occurrences;
    }

    // Helper: parse RRULE string like "FREQ=WEEKLY;BYDAY=MO,WE;COUNT=12"
    // Helper: nextOccurrence based on frequency
    // Helper: copyAsOccurrence — copies all fields, sets new id, startDateTime, endDateTime, recurrenceParentId
    // ... implement these private methods
}
```

**Step 2: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/util/RecurrenceGenerator.java
git commit -m "feat: add RecurrenceGenerator for materializing recurring events"
```

---

### Task 14: CalendarEventService — Core CRUD + Recurrence

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/CalendarEventService.java`

**Step 1: Create service**

Key methods:
- `createEvent(CreateCalendarEventRequest, userId, userEmail, userRole)` — validates audience/scope, creates event, generates occurrences if recurring, audit logs
- `getEvents(startDate, endDate, filters, userId, userRole)` — uses CalendarEventSpecification based on role
- `getEventById(id, userId, userRole)` — single event with visibility check
- `updateEvent(id, UpdateCalendarEventRequest, userId, userEmail)` — single occurrence update
- `updateSeries(id, UpdateCalendarEventRequest, userId, userEmail)` — update all in series
- `deleteEvent(id, userId, userEmail)` — soft-delete single
- `deleteSeries(id, userId, userEmail)` — soft-delete entire series
- `createPersonalEvent(CreateCalendarEventRequest, userId, userEmail)` — auto-sets audience=PERSONAL

Important: Coordinator permission check — when a coordinator creates an event, validate that the target classId/batchId matches one they're assigned to. Query `Class.coordinatorUserId` or `Batch.coordinatorUserId`.

**Step 2: Verify compilation**

Run: `cd core-api && ../gradlew compileJava`

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/CalendarEventService.java
git commit -m "feat: add CalendarEventService with CRUD, recurrence, and permissions"
```

---

### Task 15: CSV Import/Export Service

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/CalendarImportExportService.java`

**Step 1: Create service**

Dependencies: Add `org.apache.commons:commons-csv` to student `build.gradle` (or use OpenCSV).

Key methods:
- `importEvents(MultipartFile file, userId, userEmail)` → `CalendarEventImportResult`
  - Parse CSV rows
  - Resolve classCode → classId, batchName → batchId, sectionName → sectionId
  - Validate each row (required fields, valid enums, date formats)
  - Create events for valid rows, collect errors for invalid rows
  - Audit log the import operation
- `exportEvents(startDate, endDate, filters, clientId)` → `byte[]` (CSV content)
- `getImportTemplate()` → `byte[]` (CSV with headers only)

**Step 2: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/CalendarImportExportService.java student/build.gradle
git commit -m "feat: add CalendarImportExportService for CSV import/export"
```

---

### Task 16: CalendarEventController

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/web/CalendarEventController.java`

**Step 1: Create controller**

```java
package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.service.CalendarEventService;
import com.datagami.edudron.student.service.CalendarImportExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;
    private final CalendarImportExportService importExportService;

    // Constructor injection

    @PostMapping("/events")
    public ResponseEntity<CalendarEventResponse> createEvent(
            @Valid @RequestBody CreateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        return ResponseEntity.ok(calendarEventService.createEvent(request, userId, userEmail, userRole));
    }

    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventResponse>> getEvents(
            @RequestParam OffsetDateTime startDate,
            @RequestParam OffsetDateTime endDate,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String audience,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        return ResponseEntity.ok(calendarEventService.getEvents(startDate, endDate, classId, batchId, sectionId, eventType, audience, userId, userRole));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<CalendarEventResponse> getEvent(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        return ResponseEntity.ok(calendarEventService.getEventById(id, userId, userRole));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<CalendarEventResponse> updateEvent(
            @PathVariable String id,
            @Valid @RequestBody UpdateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        return ResponseEntity.ok(calendarEventService.updateEvent(id, request, userId, userEmail));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        calendarEventService.deleteEvent(id, userId, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/events/{id}/series")
    public ResponseEntity<Void> updateSeries(
            @PathVariable String id,
            @Valid @RequestBody UpdateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        calendarEventService.updateSeries(id, request, userId, userEmail);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/events/{id}/series")
    public ResponseEntity<Void> deleteSeries(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        calendarEventService.deleteSeries(id, userId, userEmail);
        return ResponseEntity.noContent().build();
    }

    // Personal events
    @PostMapping("/events/personal")
    public ResponseEntity<CalendarEventResponse> createPersonalEvent(
            @Valid @RequestBody CreateCalendarEventRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        return ResponseEntity.ok(calendarEventService.createPersonalEvent(request, userId, userEmail));
    }

    // Import/Export
    @PostMapping("/events/import")
    public ResponseEntity<CalendarEventImportResult> importEvents(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userEmail", required = false) String userEmail) {
        return ResponseEntity.ok(importExportService.importEvents(file, userId, userEmail));
    }

    @GetMapping("/events/export")
    public ResponseEntity<byte[]> exportEvents(
            @RequestParam OffsetDateTime startDate,
            @RequestParam OffsetDateTime endDate,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String batchId) {
        byte[] csv = importExportService.exportEvents(startDate, endDate, classId, batchId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calendar-events.csv")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(csv);
    }

    @GetMapping("/events/import/template")
    public ResponseEntity<byte[]> getImportTemplate() {
        byte[] template = importExportService.getImportTemplate();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calendar-import-template.csv")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(template);
    }
}
```

**Step 2: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/web/CalendarEventController.java
git commit -m "feat: add CalendarEventController with CRUD, import/export, personal events"
```

---

### Task 17: Gateway Route for Calendar

**Files:**
- Modify: `gateway/src/main/resources/application.yml`

**Step 1: Add calendar route**

Add BEFORE the catch-all `student-service-api` route (before line 74):

```yaml
        - id: calendar-events
          uri: ${CORE_API_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/calendar/**
```

**Step 2: Verify routing**

Restart gateway, test: `curl http://localhost:8080/api/calendar/events?startDate=...&endDate=...`
Expected: Request reaches calendar controller (may return 401 if no token, which means routing works).

**Step 3: Commit**

```bash
git add gateway/src/main/resources/application.yml
git commit -m "feat: add gateway route for calendar API endpoints"
```

---

### Task 18: End-to-End Backend Test

**Step 1: Start services**

```bash
./scripts/edudron.sh start
```

**Step 2: Test CRUD flow**

```bash
# Create event
curl -X POST http://localhost:8080/api/calendar/events \
  -H "Authorization: Bearer <admin-token>" \
  -H "X-Client-Id: <tenant-id>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Mid-term Exam",
    "eventType": "EXAM",
    "startDateTime": "2026-04-15T10:00:00Z",
    "endDateTime": "2026-04-15T13:00:00Z",
    "allDay": false,
    "audience": "BATCH",
    "batchId": "<batch-id>",
    "isRecurring": false
  }'

# List events
curl "http://localhost:8080/api/calendar/events?startDate=2026-04-01T00:00:00Z&endDate=2026-04-30T23:59:59Z" \
  -H "Authorization: Bearer <admin-token>" \
  -H "X-Client-Id: <tenant-id>"

# Create recurring event
curl -X POST http://localhost:8080/api/calendar/events \
  -H "Authorization: Bearer <admin-token>" \
  -H "X-Client-Id: <tenant-id>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Weekly Faculty Meeting",
    "eventType": "FACULTY_MEETING",
    "startDateTime": "2026-04-06T10:00:00Z",
    "endDateTime": "2026-04-06T11:00:00Z",
    "audience": "FACULTY_ONLY",
    "isRecurring": true,
    "recurrenceRule": "FREQ=WEEKLY;BYDAY=MO;COUNT=12",
    "meetingLink": "https://meet.google.com/abc-def"
  }'

# Test personal event as student
curl -X POST http://localhost:8080/api/calendar/events/personal \
  -H "Authorization: Bearer <student-token>" \
  -H "X-Client-Id: <tenant-id>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Study session - Data Structures",
    "startDateTime": "2026-04-10T18:00:00Z",
    "endDateTime": "2026-04-10T20:00:00Z",
    "eventType": "PERSONAL",
    "audience": "PERSONAL"
  }'
```

**Step 3: Verify visibility**

- Query as student → should see TENANT_WIDE + their batch/class/section events + own personal. Should NOT see FACULTY_ONLY.
- Query as instructor → should see TENANT_WIDE + FACULTY_ONLY + assigned scope events + own personal.
- Query as admin → should see everything except other users' personal events.

**Step 4: Commit any fixes**

```bash
git commit -am "fix: calendar backend adjustments from e2e testing"
```

---

## Phase 3: Calendar Frontend

### Task 19: CalendarEventsApi in Shared Utils

**Files:**
- Create: `frontend/packages/shared-utils/src/api/calendarEvents.ts`
- Modify: `frontend/packages/shared-utils/src/api/index.ts` — export new API class
- Modify: `frontend/packages/shared-utils/src/index.ts` — re-export if needed

**Step 1: Create API class**

```typescript
import { ApiClient } from './apiClient';

export enum EventType {
  HOLIDAY = 'HOLIDAY',
  EXAM = 'EXAM',
  SUBMISSION_DEADLINE = 'SUBMISSION_DEADLINE',
  FACULTY_MEETING = 'FACULTY_MEETING',
  REVIEW = 'REVIEW',
  GENERAL = 'GENERAL',
  CUSTOM = 'CUSTOM',
  PERSONAL = 'PERSONAL',
}

export enum EventAudience {
  TENANT_WIDE = 'TENANT_WIDE',
  CLASS = 'CLASS',
  BATCH = 'BATCH',
  SECTION = 'SECTION',
  FACULTY_ONLY = 'FACULTY_ONLY',
  PERSONAL = 'PERSONAL',
}

export interface CalendarEvent {
  id: string;
  title: string;
  description?: string;
  eventType: EventType;
  customTypeLabel?: string;
  startDateTime: string;
  endDateTime?: string;
  allDay: boolean;
  audience: EventAudience;
  classId?: string;
  className?: string;
  batchId?: string;
  batchName?: string;
  sectionId?: string;
  sectionName?: string;
  createdByUserId: string;
  createdByName?: string;
  isRecurring: boolean;
  recurrenceRule?: string;
  recurrenceParentId?: string;
  meetingLink?: string;
  location?: string;
  color?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCalendarEventInput {
  title: string;
  description?: string;
  eventType: EventType;
  customTypeLabel?: string;
  startDateTime: string;
  endDateTime?: string;
  allDay?: boolean;
  audience: EventAudience;
  classId?: string;
  batchId?: string;
  sectionId?: string;
  isRecurring?: boolean;
  recurrenceRule?: string;
  meetingLink?: string;
  location?: string;
  color?: string;
  metadata?: Record<string, unknown>;
}

export interface CalendarEventImportResult {
  created: number;
  errors: number;
  errorDetails: { row: number; message: string }[];
}

export class CalendarEventsApi {
  constructor(private apiClient: ApiClient) {}

  async getEvents(params: {
    startDate: string;
    endDate: string;
    classId?: string;
    batchId?: string;
    sectionId?: string;
    eventType?: string;
    audience?: string;
  }): Promise<CalendarEvent[]> {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v) query.set(k, v); });
    return this.apiClient.get(`/api/calendar/events?${query.toString()}`);
  }

  async getEvent(id: string): Promise<CalendarEvent> {
    return this.apiClient.get(`/api/calendar/events/${id}`);
  }

  async createEvent(data: CreateCalendarEventInput): Promise<CalendarEvent> {
    return this.apiClient.post('/api/calendar/events', data);
  }

  async updateEvent(id: string, data: Partial<CreateCalendarEventInput>): Promise<CalendarEvent> {
    return this.apiClient.put(`/api/calendar/events/${id}`, data);
  }

  async deleteEvent(id: string): Promise<void> {
    return this.apiClient.delete(`/api/calendar/events/${id}`);
  }

  async updateSeries(id: string, data: Partial<CreateCalendarEventInput>): Promise<void> {
    return this.apiClient.put(`/api/calendar/events/${id}/series`, data);
  }

  async deleteSeries(id: string): Promise<void> {
    return this.apiClient.delete(`/api/calendar/events/${id}/series`);
  }

  async createPersonalEvent(data: CreateCalendarEventInput): Promise<CalendarEvent> {
    return this.apiClient.post('/api/calendar/events/personal', data);
  }

  async importEvents(file: File): Promise<CalendarEventImportResult> {
    return this.apiClient.postForm('/api/calendar/events/import', { file });
  }

  async exportEvents(startDate: string, endDate: string, classId?: string, batchId?: string): Promise<Blob> {
    const query = new URLSearchParams({ startDate, endDate });
    if (classId) query.set('classId', classId);
    if (batchId) query.set('batchId', batchId);
    return this.apiClient.downloadFile(`/api/calendar/events/export?${query.toString()}`);
  }

  async getImportTemplate(): Promise<Blob> {
    return this.apiClient.downloadFile('/api/calendar/events/import/template');
  }
}
```

**Step 2: Export from index files**

Add exports to `frontend/packages/shared-utils/src/api/index.ts` and `src/index.ts`.

**Step 3: Build shared-utils**

Run: `cd frontend/packages/shared-utils && npm run build`

**Step 4: Add to app api.ts files**

In `frontend/apps/admin-dashboard/src/lib/api.ts`:
```typescript
import { CalendarEventsApi } from '@kunal-ak23/edudron-shared-utils';
export const calendarEventsApi = new CalendarEventsApi(apiClient);
```

Same in `frontend/apps/student-portal/src/lib/api.ts`.

**Step 5: Commit**

```bash
git commit -am "feat: add CalendarEventsApi to shared-utils and wire up in both apps"
```

---

### Task 20: Calendar Page — Admin Dashboard (Grid + List View)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/calendar/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/calendar/CalendarGrid.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/calendar/EventList.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/calendar/EventCard.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/calendar/EventDetailModal.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/calendar/EventFormDrawer.tsx`

**Step 1: Build the page shell**

`/calendar` page with:
- Header: "Academic Calendar" title
- View toggle (grid/list) — use shadcn Tabs or ToggleGroup
- Filter bar: event type, class, batch, section dropdowns
- "+ Create Event" button (visible to admin and coordinators)
- Import/Export buttons (admin only)
- Month navigation (prev/next month, "Today" button)

**Step 2: Build CalendarGrid component**

Monthly grid showing days with colored event dots. Click a day to show events in a side panel or popover.

**Step 3: Build EventList component**

Chronological agenda view with date headers and EventCard items.

**Step 4: Build EventCard component**

Single event row: colored dot, title, time range, audience badge, meeting link icon.

**Step 5: Build EventDetailModal**

Full event details on click: all fields, edit/delete buttons for authorized users, series options for recurring events.

**Step 6: Build EventFormDrawer**

Create/edit form: title, description, event type dropdown, date/time pickers, all-day toggle, audience selector, conditional scope dropdowns, recurring toggle with RRULE builder (frequency, days, end condition), meeting link, location.

**Step 7: Wire up data fetching**

Use `useState` + `useEffect` pattern (matching existing dashboard patterns) to fetch events via `calendarEventsApi.getEvents()` for the visible date range.

**Step 8: Commit**

```bash
git commit -am "feat: add calendar page with grid/list views to admin dashboard"
```

---

### Task 21: Calendar Page — Student Portal

**Files:**
- Create: `frontend/apps/student-portal/src/app/calendar/page.tsx`
- Create: `frontend/apps/student-portal/src/components/calendar/CalendarGrid.tsx`
- Create: `frontend/apps/student-portal/src/components/calendar/EventList.tsx`
- Create: `frontend/apps/student-portal/src/components/calendar/EventCard.tsx`
- Create: `frontend/apps/student-portal/src/components/calendar/EventDetailModal.tsx`
- Create: `frontend/apps/student-portal/src/components/calendar/PersonalEventForm.tsx`

**Step 1: Build student calendar page**

Similar to admin but:
- No "Create Event" button for institutional events
- "+ Personal Event" button instead
- No import/export buttons
- Filter by event type only (no class/batch/section since already scoped)
- Read-only institutional events, editable personal events

**Step 2: Build PersonalEventForm**

Simplified form: title, description, date/time, recurring toggle, all-day toggle. No audience/scope fields (auto-set to PERSONAL).

**Step 3: Commit**

```bash
git commit -am "feat: add calendar page with personal events to student portal"
```

---

### Task 22: Dashboard Widgets (Both Apps)

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/dashboard/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/calendar/CalendarWidget.tsx`
- Modify: `frontend/apps/student-portal/src/app/page.tsx` (or wherever the student dashboard is)
- Create: `frontend/apps/student-portal/src/components/calendar/CalendarWidget.tsx`

**Step 1: Create CalendarWidget component**

Card showing "Upcoming Events" — next 5-7 events. Each shows colored dot, title, relative date ("Tomorrow", "In 3 days"), time. "View All" link to `/calendar`.

Fetch events for next 30 days, take first 7.

**Step 2: Add widget to admin dashboard**

Add the CalendarWidget card to the dashboard grid alongside existing stat cards.

**Step 3: Add widget to student portal**

Add to student dashboard/home page.

**Step 4: Commit**

```bash
git commit -am "feat: add upcoming events dashboard widget to both apps"
```

---

### Task 23: Event Import Modal (Admin Dashboard)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/components/calendar/EventImportModal.tsx`

**Step 1: Build import modal**

- "Download Template" button → calls `calendarEventsApi.getImportTemplate()`
- File upload dropzone (accept .csv, .xlsx)
- Upload button → calls `calendarEventsApi.importEvents(file)`
- Results display: success count + error table (row number, error message)
- "Close & Refresh" button to refresh calendar view

**Step 2: Commit**

```bash
git commit -am "feat: add CSV import modal for calendar events"
```

---

### Task 24: Calendar Color Settings (Admin Dashboard)

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/settings/page.tsx` — add Calendar Colors section

**Step 1: Add color configuration section**

Below existing feature toggles, add a "Calendar Event Colors" section:
- Grid of event types, each with a color picker
- "Reset to Defaults" button
- Save button
- Uses tenant features/settings API to persist

**Step 2: Commit**

```bash
git commit -am "feat: add tenant-level calendar color settings to admin settings page"
```

---

### Task 25: Navigation Updates

**Files:**
- Check: Admin dashboard sidebar/nav component — add "Calendar" link
- Check: Student portal sidebar/nav — add "Calendar" link

**Step 1: Find navigation components**

Look in `frontend/apps/admin-dashboard/src/components/` for sidebar, nav, or layout components.

**Step 2: Add Calendar nav item**

Add a calendar icon + "Calendar" label linking to `/calendar` in both apps.

**Step 3: Commit**

```bash
git commit -am "feat: add calendar link to navigation in both apps"
```

---

### Task 26: Final Integration Test

**Step 1: Start full stack**

```bash
# Backend
./scripts/edudron.sh start

# Frontend
cd frontend/apps/admin-dashboard && npm run dev &
cd frontend/apps/student-portal && npm run dev &
```

**Step 2: Test as admin**

- Navigate to `/calendar` in admin dashboard
- Create a tenant-wide holiday event
- Create a batch-scoped exam event
- Create a faculty-only meeting with Zoom link
- Create a recurring weekly event
- Toggle between grid and list view
- Filter by event type
- Import events from CSV
- Export events
- Assign a coordinator to a class and batch

**Step 3: Test as student**

- Navigate to `/calendar` in student portal
- Verify only visible events show (tenant-wide + own scope)
- Verify FACULTY_ONLY events are hidden
- Create a personal recurring study event
- Delete a single occurrence of the recurring event

**Step 4: Test as instructor coordinator**

- Login as instructor who is a coordinator
- Verify can create events scoped to their assigned class/batch
- Verify cannot create tenant-wide or other-scope events

**Step 5: Fix any issues found, commit**

```bash
git commit -am "fix: calendar integration test fixes"
```
