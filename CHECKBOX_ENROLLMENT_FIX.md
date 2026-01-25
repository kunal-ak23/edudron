# Course Enrollment Checkbox Fix

## Issue Description

When managing course enrollments for a section or class, the checkboxes were not showing as checked/enabled for courses that the section/class was already enrolled in.

## Root Cause

The frontend code was using the wrong API method to fetch existing enrollments:

### Section Enrollment Page (`frontend/apps/admin-dashboard/src/app/sections/[id]/enroll/page.tsx`)

**Before (Lines 119-128):**
```typescript
// Load existing enrollments to see which courses are already enrolled
try {
  const enrollments = await enrollmentsApi.listEnrollments()
  const sectionEnrollments = enrollments.filter(e => e.batchId === sectionId)
  const enrolledIds = new Set(sectionEnrollments.map(e => e.courseId))
  setEnrolledCourseIds(enrolledIds)
} catch (err) {
  console.error('Error loading existing enrollments:', err)
  // Continue without pre-populating enrolled courses
}
```

**Problem:** 
- `listEnrollments()` returns ALL enrollments without proper filtering
- Client-side filtering by `batchId === sectionId` was unreliable
- The API response might not include all necessary fields

### Class Enrollment Page (`frontend/apps/admin-dashboard/src/app/classes/[id]/enroll/page.tsx`)

**Before (Lines 132-141):**
```typescript
// Load existing enrollments to see which courses are already enrolled
try {
  const enrollments = await enrollmentsApi.listEnrollments()
  const classEnrollments = enrollments.filter(e => e.classId === classId)
  const enrolledIds = new Set(classEnrollments.map(e => e.courseId))
  setEnrolledCourseIds(enrolledIds)
} catch (err) {
  console.error('Error loading existing enrollments:', err)
  // Continue without pre-populating enrolled courses
}
```

**Problem:**
- Same issue - client-side filtering by `classId` was unreliable

## Solution

Use the `listAllEnrollmentsPaginated()` API method which supports server-side filtering by `sectionId` and `classId`:

### Section Enrollment Page

**After:**
```typescript
// Load existing enrollments to see which courses are already enrolled
try {
  // Use paginated API with sectionId filter to get enrollments for this section
  const enrollmentsResponse = await enrollmentsApi.listAllEnrollmentsPaginated(0, 1000, {
    sectionId: sectionId
  })
  const enrolledIds = new Set(enrollmentsResponse.content.map(e => e.courseId))
  setEnrolledCourseIds(enrolledIds)
} catch (err) {
  console.error('Error loading existing enrollments:', err)
  // Continue without pre-populating enrolled courses
}
```

### Class Enrollment Page

**After:**
```typescript
// Load existing enrollments to see which courses are already enrolled
try {
  // Use paginated API with classId filter to get enrollments for this class
  const enrollmentsResponse = await enrollmentsApi.listAllEnrollmentsPaginated(0, 1000, {
    classId: classId
  })
  const enrolledIds = new Set(enrollmentsResponse.content.map(e => e.courseId))
  setEnrolledCourseIds(enrolledIds)
} catch (err) {
  console.error('Error loading existing enrollments:', err)
  // Continue without pre-populating enrolled courses
}
```

## Backend Support

The backend already properly supports these filters:

1. **API Endpoint:** `/api/enrollments/all/paged` (`EnrollmentController.java` line 89)
2. **Supported Parameters:** `sectionId`, `classId`, `courseId`, `instituteId`, `email`
3. **Service Implementation:** `EnrollmentService.getAllEnrollments()` (line 506)
4. **Database Filtering:** Uses JPA Specification with proper field filtering:
   - `batchId` field represents `sectionId` (line 628-631)
   - `classId` field for class filtering (line 623-626)

## Benefits

1. ✅ Proper server-side filtering reduces unnecessary data transfer
2. ✅ More reliable - uses database-level filtering instead of client-side filtering
3. ✅ Checkboxes now correctly show as checked for already enrolled courses
4. ✅ Consistent with the backend's filtering capabilities

## Testing

To verify the fix:

1. Create a section with students
2. Enroll the section in a course
3. Go to "Manage Course Enrollments" for that section
4. The checkbox for the enrolled course should now be checked
5. The "Enrolled" badge should appear next to the course title

Repeat for class enrollments to verify that fix as well.
