---
name: Fix Exam Concurrency Issues
overview: Fix concurrency issues causing errors when 200-300 students take exams simultaneously by adding optimistic locking, database indexes, increasing connection pool, and implementing retry logic for concurrent updates.
todos:
  - id: add-version-field
    content: Add @Version field to AssessmentSubmission entity with getter/setter
    status: pending
  - id: create-migration
    content: Create database migration for version column and composite index (client_id, student_id, assessment_id)
    status: pending
  - id: update-master-changelog
    content: Add new migration to db.changelog-master.yaml
    status: pending
  - id: add-retry-logic-saveprogress
    content: Update saveProgress() method with retry logic for OptimisticLockingFailureException
    status: pending
  - id: add-retry-logic-submitexam
    content: Update submitExam() method with retry logic for consistency
    status: pending
  - id: increase-connection-pool
    content: Increase HikariCP maximum-pool-size from 35 to 100 in application.yml
    status: pending
isProject: false
---

# Fix Exam Concurrency Issues for High Load

## Problem Analysis

When 200-300 students take exams simultaneously, the system experiences errors due to:

1. **High Auto-Save Load**: Frontend auto-saves every 15 seconds → 13-20 requests/second
2. **Insufficient Connection Pool**: Max 35 connections cannot handle concurrent load
3. **Race Conditions**: No optimistic locking causes lost updates when multiple auto-saves occur simultaneously
4. **Missing Database Index**: Frequent query `findByClientIdAndStudentIdAndAssessmentId` lacks composite index
5. **No Retry Logic**: Concurrent update conflicts cause failures instead of graceful retries

## Solution Overview

### 1. Add Optimistic Locking to AssessmentSubmission Entity

**File**: `student/src/main/java/com/datagami/edudron/student/domain/AssessmentSubmission.java`

- Add `@Version` field after the `@Id` field
- Add getter/setter for version field
- This prevents lost updates when multiple auto-saves occur simultaneously
```java
@Version
@Column(name = "version")
private Long version;
```


### 2. Create Database Migration

**File**: `student/src/main/resources/db/changelog/db.changelog-0020-optimistic-locking-and-indexes.yaml` (new file)

- Add `version` column (BIGINT, default 0) to `assessment_submissions` table
- Create composite index on `(client_id, student_id, assessment_id)` for the frequent query pattern
- Update `db.changelog-master.yaml` to include this new changelog

The index will optimize:

- `findByClientIdAndStudentIdAndAssessmentId()` - used in `startExam()` and `getSubmissionByExamId()`
- `countByClientIdAndStudentIdAndAssessmentId()` - used in exam listing

### 3. Update ExamSubmissionService with Retry Logic

**File**: `student/src/main/java/com/datagami/edudron/student/service/ExamSubmissionService.java`

- Update `saveProgress()` method to handle `OptimisticLockingFailureException`
- Implement retry logic (max 3 attempts with exponential backoff)
- On retry, reload the entity to get latest version and merge changes
- Preserve the latest `answersJson` and `timeRemainingSeconds` values

Key changes:

- Wrap save operation in retry loop
- Catch `OptimisticLockingFailureException` or `StaleObjectStateException`
- Reload entity before retry
- Merge answers JSON (prefer newer answers, but merge if needed)

### 4. Increase Database Connection Pool

**File**: `student/src/main/resources/application.yml`

- Increase `maximum-pool-size` from 35 to 100 (or make it configurable via `HIKARI_MAX_POOL_SIZE` env var)
- This provides sufficient connections for 200-300 concurrent students
- Keep `minimum-idle` at 5 for efficiency

### 5. Update submitExam() for Consistency

**File**: `student/src/main/java/com/datagami/edudron/student/service/ExamSubmissionService.java`

- Add similar retry logic to `submitExam()` method
- Ensure final submission is atomic and handles concurrent submission attempts

## Implementation Details

### Optimistic Locking Flow

```
Student A Auto-Save → Load (version=5) → Modify → Save (version=5) ✓
Student A Auto-Save → Load (version=5) → Modify → Save (version=5) ✗ OptimisticLockException
                                    ↓ Retry
                         Load (version=6) → Modify → Save (version=6) ✓
```

### Retry Strategy

- Max 3 retry attempts
- Exponential backoff: 50ms, 100ms, 200ms
- On retry: reload entity, merge changes, save again
- If all retries fail, log error and throw exception (should be rare)

### Database Index Impact

The composite index `(client_id, student_id, assessment_id)` will:

- Speed up `findByClientIdAndStudentIdAndAssessmentId()` queries
- Reduce database load during concurrent exam starts
- Improve performance of `countByClientIdAndStudentIdAndAssessmentId()`

## Testing Considerations

- Test concurrent auto-saves from same student (should retry gracefully)
- Test concurrent exam starts (should handle race conditions)
- Test with 200-300 simulated concurrent requests
- Monitor connection pool usage under load
- Verify no lost updates occur

## Additional Recommendations (Not in Plan)

- Consider reducing frontend auto-save frequency from 15s to 30s
- Add monitoring/alerting for optimistic lock failures
- Consider implementing request queuing if load exceeds capacity