# Unified Event Logging System

## Overview
All events are now logged to a **unified `common.events` table** that all services write to. This provides a centralized location for all application events, making it easy to query, analyze, and audit events across all services.

## Architecture

### Unified Events Table
- **Location**: `common.events` table in PostgreSQL
- **Schema**: All services write to the same table
- **Benefits**: 
  - Single source of truth for all events
  - Easy cross-service event correlation
  - Simplified analytics and reporting
  - Unified querying interface

### Service Implementation
Each service has:
- `CommonEventRepository` - JPA repository pointing to `common.events`
- `CommonEventService` - Extends base `EventService` from common module
- All services use the same event structure and logging methods

## Event Types Logged

### Authentication Events
1. **LOGIN** - User login events
   - Captures: user ID, email, IP address, user agent, session ID, device info
   - Logged in: `AuthService.login()`

2. **LOGOUT** - User logout events (when implemented)
   - Captures: user ID, email, session ID

3. **USER_REGISTERED** - New user registration
   - Captures: user ID, email, role, tenant info

### User Management Events
4. **USER_CREATED** - User account creation
   - Captures: user ID, email, name, role, active status, institute IDs

5. **USER_UPDATED** - User account updates
   - Captures: user ID, email, name, old/new role, active status, institute IDs

### Course Management Events
6. **COURSE_CREATED** - Course creation
   - Captures: course ID, title, published status, pricing info

7. **COURSE_EDITED** - Course updates
   - Captures: course ID, title, published status, assignment info

8. **COURSE_DELETED** - Course deletion
   - Captures: course ID, title, was published status

9. **COURSE_PUBLISHED** - Course publication
   - Captures: course ID, title, lecture count, duration

10. **COURSE_UNPUBLISHED** - Course unpublishing
    - Captures: course ID, title

### Enrollment Events
11. **COURSE_ENROLLED** - Student enrollment
    - Captures: enrollment ID, student ID, course ID, institute/class/section IDs

12. **COURSE_UNENROLLED** - Student unenrollment
    - Captures: enrollment ID, student ID, course ID, institute/class/section IDs

13. **ENROLLMENT_DELETED** - Enrollment deletion (admin)
    - Captures: enrollment ID, student ID, course ID

### Learning Activity Events
14. **VIDEO_WATCH_PROGRESS** - Video watch progress updates
    - Captures: student ID, course ID, lecture ID, progress %, watch duration
    - Logged when: Lecture view session ends

15. **LECTURE_COMPLETED** - Lecture completion
    - Captures: student ID, course ID, lecture ID, total duration
    - Logged when: Student completes a lecture (100% progress)

16. **ASSESSMENT_SUBMITTED** - Assessment/exam submission
    - Captures: student ID, assessment ID, course ID, score, max score, passed status
    - Logged when: Student submits an assessment

### Search Events
17. **SEARCH_QUERY** - Search queries
    - Captures: user ID, query text, search type, result count, duration
    - Logged when: User searches for courses

### System Events
18. **HTTP_REQUEST** - All HTTP requests (automatic)
    - Captures: method, path, status, duration, user info, trace ID

19. **ERROR** - Error events (automatic)
    - Captures: error type, message, stack trace, endpoint, user ID

## Event Data Structure

All events include:
- **Common Fields**: id, clientId, eventType, createdAt, serviceName
- **User Context**: userId, userEmail
- **Request Context**: traceId, requestId, userAgent, ipAddress
- **Device Info**: deviceType, browser, os (parsed from user agent)
- **Event-Specific Data**: Stored as JSON in `eventData` field

## Additional Fields

The unified events table includes additional fields for better analytics:
- `sessionId` - For tracking user sessions
- `deviceType` - mobile, desktop, tablet
- `browser` - Chrome, Firefox, Safari, etc.
- `os` - Windows, macOS, Linux, iOS, Android

## Querying Events

### Single Query Across All Services
```sql
-- Get all events for a client
SELECT * FROM common.events 
WHERE client_id = '...' 
ORDER BY created_at DESC;

-- Get all login events
SELECT * FROM common.events 
WHERE event_type = 'LOGIN' 
ORDER BY created_at DESC;

-- Get all events for a user across all services
SELECT * FROM common.events 
WHERE user_id = '...' 
ORDER BY created_at DESC;

-- Get events by service
SELECT * FROM common.events 
WHERE service_name = 'student' 
ORDER BY created_at DESC;
```

### Event Correlation
Events can be correlated using:
- `traceId` - For request correlation
- `sessionId` - For user session tracking
- `userId` - For user activity tracking

## Performance Considerations

- **Async Logging**: All events are logged asynchronously
- **Indexes**: Comprehensive indexes for fast queries
- **Non-blocking**: Event logging failures don't break operations
- **Thread Pool**: Dedicated thread pool (2-5 threads) per service

## Future Event Types (Ready to Add)

The `EventService` base class includes methods for:
- `logFileUpload()` - File upload events
- `logSearchQuery()` - Search query events (partially implemented)
- Additional custom events via `logEvent()`

## Migration Notes

- Old service-specific event tables (`student.events`, `content.events`, `idp.events`) can be kept for historical data
- New events go to `common.events`
- Consider data migration script if historical events need to be moved

## Benefits

1. **Unified Analytics**: Query all events in one place
2. **Cross-Service Insights**: Understand user journeys across services
3. **Simplified Auditing**: Single audit trail for all actions
4. **Better Debugging**: Correlate events across services using trace IDs
5. **Scalable**: Easy to add new event types and services
