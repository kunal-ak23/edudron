# Unenrollment Concurrency Issue Fix

## Error Description

When trying to unenroll a section from a course, the following error occurred:

```
An unexpected error occurred: Row was updated or deleted by another transaction 
(or unsaved-value mapping was incorrect) : 
[com.datagami.edudron.student.domain.Enrollment#01KFTCPE6GAB1A1C684B44529C]
```

This is a Hibernate optimistic locking/stale entity exception.

## Root Cause

The original implementation in `BulkEnrollmentService` was:

1. Fetching all enrollments for a class/section
2. Filtering them to find enrollments for a specific course
3. Deleting each enrollment one-by-one in a loop

**Problem:**
- When deleting entities one-by-one, Hibernate manages each entity in the persistence context
- If multiple requests happen concurrently, or if there are entity state changes between fetch and delete, Hibernate throws optimistic locking exceptions
- Even though the `Enrollment` entity doesn't have a `@Version` field, Hibernate can still detect concurrent modifications
- This approach is also inefficient - N queries for N enrollments

### Original Code (Section Unenrollment - Lines 282-327)

```java
public BulkEnrollmentResult unenrollSectionFromCourse(String sectionId, String courseId) {
    // ... validation ...
    
    // Fetch all enrollments
    List<Enrollment> sectionEnrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
    List<Enrollment> enrollmentsToDelete = sectionEnrollments.stream()
        .filter(e -> courseId.equals(e.getCourseId()))
        .collect(Collectors.toList());
    
    // Delete one-by-one
    for (Enrollment enrollment : enrollmentsToDelete) {
        try {
            enrollmentRepository.delete(enrollment);  // ❌ Stale entity risk
            // ...
        } catch (Exception e) {
            // ...
        }
    }
}
```

### Original Code (Class Unenrollment - Lines 229-277)

Same pattern - fetch and delete one-by-one.

## Solution

Implemented bulk delete queries that execute a single DELETE statement directly against the database, bypassing the persistence context entirely.

### 1. Added Bulk Delete Methods to Repository

**File:** `student/src/main/java/com/datagami/edudron/student/repo/EnrollmentRepository.java`

```java
// Bulk delete methods for efficient unenrollment
@Modifying
@Query("DELETE FROM Enrollment e WHERE e.clientId = :clientId AND e.batchId = :batchId AND e.courseId = :courseId")
int deleteByClientIdAndBatchIdAndCourseId(
    @Param("clientId") UUID clientId,
    @Param("batchId") String batchId,
    @Param("courseId") String courseId
);

@Modifying
@Query("DELETE FROM Enrollment e WHERE e.clientId = :clientId AND e.classId = :classId AND e.courseId = :courseId")
int deleteByClientIdAndClassIdAndCourseId(
    @Param("clientId") UUID clientId,
    @Param("classId") String classId,
    @Param("courseId") String courseId
);
```

**Key Features:**
- `@Modifying` - Indicates this is an update/delete query
- `@Query` with DELETE - Executes directly against the database
- Returns `int` - Number of rows deleted
- Single database operation - No entity fetching or loop

### 2. Updated Service to Use Bulk Delete

**File:** `student/src/main/java/com/datagami/edudron/student/service/BulkEnrollmentService.java`

**Section Unenrollment (Updated):**

```java
public BulkEnrollmentResult unenrollSectionFromCourse(String sectionId, String courseId) {
    // ... validation ...
    
    // Count enrollments before deletion (for reporting only)
    List<Enrollment> sectionEnrollments = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
    long enrollmentCount = sectionEnrollments.stream()
        .filter(e -> courseId.equals(e.getCourseId()))
        .count();
    
    log.info("Unenrolling {} students from section {} in course {}", 
        enrollmentCount, sectionId, courseId);
    
    try {
        // ✅ Use bulk delete - single database operation
        int deletedCount = enrollmentRepository.deleteByClientIdAndBatchIdAndCourseId(
            clientId, sectionId, courseId);
        result.setEnrolledStudents((long) deletedCount);
        
        log.info("Unenrollment completed: {} enrollments deleted successfully", deletedCount);
    } catch (Exception e) {
        log.error("Failed to delete enrollments: {}", e.getMessage());
        result.setFailedStudents(enrollmentCount);
        // ...
    }
    
    return result;
}
```

**Class Unenrollment (Updated):**

Same pattern using `deleteByClientIdAndClassIdAndCourseId()`.

## Benefits

1. ✅ **No Concurrency Issues** - Single atomic DELETE operation
2. ✅ **Better Performance** - One query instead of N queries
3. ✅ **Simpler Code** - No loop, no entity management
4. ✅ **Transactional Safety** - Entire operation is atomic
5. ✅ **No Stale Entity Problems** - Bypasses persistence context

## Technical Details

### How Bulk Delete Works

```sql
-- Single SQL statement executed by the bulk delete method
DELETE FROM student.enrollments 
WHERE client_id = ? 
  AND batch_id = ? 
  AND course_id = ?
```

This is executed directly against the database, so:
- No entities are loaded into memory
- No persistence context management
- No optimistic locking checks
- Single atomic operation

### Transaction Scope

The `BulkEnrollmentService` is annotated with `@Transactional` at the class level (line 39), so all bulk delete operations are automatically wrapped in a transaction and will be rolled back on error.

## Testing

To verify the fix:

1. Create a section with multiple students
2. Enroll the section in a course
3. Try to unenroll the section from the course
4. The operation should complete successfully without the concurrency error
5. Verify all enrollments were deleted

## Alternative Approaches Considered

1. **Add @Version field to Enrollment** - Would add optimistic locking, but doesn't solve the root inefficiency
2. **Use @Lock annotations** - Would add pessimistic locking, but increases contention
3. **Flush and clear EntityManager** - Complex and error-prone
4. **Bulk delete (chosen)** - Simplest, most efficient, and safest solution

## Migration Notes

- This fix is backward compatible
- No database schema changes required
- No API changes required
- Existing enrollments are unaffected
