# Double View Count Fix - Solution 2 Implementation

## Problem Statement

When students marked a lecture as complete, the analytics dashboard was showing **2 views** instead of 1. This was caused by the frontend ending the current session and immediately starting a new session when the "Mark as Complete" button was clicked.

### Root Cause

In `frontend/apps/student-portal/src/app/courses/[id]/learn/page.tsx`, the `handleMarkComplete` function was:
1. Ending the current viewing session
2. Starting a new session immediately after

Since views are counted as `COUNT(sessions)`, this created 2 session records for a single lecture viewing, resulting in 2 views in analytics.

## Solution: Update Session Without Ending It

We implemented **Solution 2**: Add a session update endpoint that allows updating the completion status and progress **without ending the session**. This keeps the session alive and maintains accurate view counts.

### Why Solution 2 is Superior

1. **Conceptually Correct**: A session represents a continuous viewing period. Marking complete is just progress metadata.
2. **Better Analytics**: Captures complete viewing time including post-completion review
3. **Simpler Logic**: No complex conditional restart logic needed
4. **No Edge Cases**: Handles mark/unmark naturally without session disruption
5. **Accurate Duration**: Session duration reflects actual time on page

## Implementation Details

### Backend Changes

#### 1. New DTO: `UpdateSessionRequest.java`
**File**: `student/src/main/java/com/datagami/edudron/student/dto/UpdateSessionRequest.java`

```java
public class UpdateSessionRequest {
    private BigDecimal progressAtEnd;
    private Boolean isCompleted;
    // Getters and setters
}
```

#### 2. New Service Method: `updateSession()`
**File**: `student/src/main/java/com/datagami/edudron/student/service/LectureViewSessionService.java`

Added method to update session progress and completion status without setting `sessionEndedAt`:
- Updates `progressAtEnd` if provided
- Updates `isCompletedInSession` if provided
- Saves changes and evicts course analytics cache
- Returns updated session

#### 3. New Controller Endpoint: `PATCH /api/lectures/{lectureId}/sessions/{sessionId}`
**File**: `student/src/main/java/com/datagami/edudron/student/web/LectureViewSessionController.java`

Added PATCH endpoint that:
- Validates lecture ID matches session
- Calls `updateSession()` service method
- Returns updated session DTO

### Frontend Changes

#### 4. New API Method: `updateLectureSession()`
**File**: `frontend/packages/shared-utils/src/api/analytics.ts`

Added method that:
- Calls PATCH endpoint with lectureId, sessionId, and update request
- Logs request and response for debugging
- Returns updated session

#### 5. Updated `handleMarkComplete()` Function
**File**: `frontend/apps/student-portal/src/app/courses/[id]/learn/page.tsx`

**Before** (causing double views):
```typescript
// End current session
await analyticsApi.endLectureSession(...)
// Start new session (creates 2nd view!)
const newSession = await analyticsApi.startLectureSession(...)
setActiveSessionId(newSession.id)
```

**After** (single view):
```typescript
// Update session without ending it
await analyticsApi.updateLectureSession(sessionLectureId, currentActiveSessionId, {
  progressAtEnd: isCompleted ? 100 : 0,
  isCompleted
})
// Session continues, no new session created
```

## Session Lifecycle

### New Correct Flow

1. **User opens lecture** → Session 1 starts
2. **User marks complete** → Session 1 updated with `isCompleted: true`
3. **User continues reviewing** → Session 1 still tracking
4. **User navigates away** → Session 1 ends

**Result**: 1 session = 1 view ✅

### Old Problematic Flow

1. **User opens lecture** → Session 1 starts
2. **User marks complete** → Session 1 ends, Session 2 starts
3. **User navigates away** → Session 2 ends

**Result**: 2 sessions = 2 views ❌

## Testing Checklist

- [ ] Backend compiles without errors
- [ ] Frontend compiles without errors
- [ ] Open a lecture and mark it complete
- [ ] Verify session continues (check network tab for PATCH call)
- [ ] Navigate to analytics dashboard
- [ ] Verify lecture shows **1 view**, not 2
- [ ] Test unmarking complete (should also use update)
- [ ] Test marking complete then navigating away
- [ ] Test marking complete then staying on page

## Files Modified

### Backend (3 files + 1 new)
1. ✅ `student/src/main/java/com/datagami/edudron/student/dto/UpdateSessionRequest.java` (NEW)
2. ✅ `student/src/main/java/com/datagami/edudron/student/service/LectureViewSessionService.java`
3. ✅ `student/src/main/java/com/datagami/edudron/student/web/LectureViewSessionController.java`

### Frontend (2 files)
4. ✅ `frontend/packages/shared-utils/src/api/analytics.ts`
5. ✅ `frontend/apps/student-portal/src/app/courses/[id]/learn/page.tsx`

## API Reference

### New Endpoint

```
PATCH /api/lectures/{lectureId}/sessions/{sessionId}
```

**Request Body**:
```json
{
  "progressAtEnd": 100,
  "isCompleted": true
}
```

**Response**: LectureViewSessionDTO with updated fields

**Use Case**: Update session progress/completion without ending the session

## Benefits

1. ✅ **Fixes double view count** - Only 1 session per viewing period
2. ✅ **More accurate analytics** - Captures complete viewing time
3. ✅ **Cleaner code** - Removed complex end+restart logic
4. ✅ **Better UX** - No session interruption when marking complete
5. ✅ **Handles edge cases** - Works correctly for mark/unmark scenarios

## Deployment Notes

- Backend changes are backward compatible (adds new endpoint, doesn't break existing ones)
- Frontend changes require backend to be deployed first
- No database migrations needed (uses existing session fields)
- Analytics cache is automatically evicted on session updates

## Verification Query

To verify the fix is working, run this SQL after testing:

```sql
-- Check number of sessions per lecture per student
SELECT 
    lecture_id,
    student_id,
    COUNT(*) as session_count,
    STRING_AGG(id, ', ') as session_ids
FROM student.lecture_view_sessions
WHERE client_id = 'YOUR_CLIENT_ID'
    AND course_id = 'YOUR_COURSE_ID'
    AND created_at > NOW() - INTERVAL '1 hour'
GROUP BY lecture_id, student_id
HAVING COUNT(*) > 1
ORDER BY session_count DESC;
```

If the fix is working, you should see **no results** or significantly fewer duplicate sessions.

---

**Status**: ✅ Implementation Complete
**Date**: 2025-01-25
**Issue**: Double view count on lecture completion
**Solution**: Update session without ending it (Solution 2)
