# User Management and Enrollment Event Logging

## Overview
This document describes the event logging implementation for user management actions in the identity service and enrollment/unenrollment actions in the student service.

## Actions Logged

### User Management (Identity Service)

1. **USER_CREATED** - When a user is created
2. **USER_UPDATED** - When a user is updated/edited

### Enrollment Management (Student Service)

1. **COURSE_ENROLLED** - When a student enrolls in a course
2. **COURSE_UNENROLLED** - When a student unenrolls from a course
3. **ENROLLMENT_DELETED** - When an enrollment is deleted (admin operation)

## Implementation Details

### Identity Service Components

1. **Event Entity** (`identity/domain/Event.java`)
   - Stores events in the `idp.events` table
   - Same structure as other services' events tables

2. **EventRepository** (`identity/repo/EventRepository.java`)
   - JPA repository for querying events
   - Supports filtering by client, type, user, time range, etc.

3. **EventService** (`identity/service/EventService.java`)
   - Service for logging events asynchronously
   - Handles SYSTEM_ADMIN users (who may not have a tenant context)

4. **AsyncConfig** (`identity/config/AsyncConfig.java`)
   - Configures async processing for event logging
   - Thread pool: 2-5 threads, queue capacity 100

5. **Database Migration** (`db/changelog/db.changelog-0008-events.yaml`)
   - Creates `events` table in `idp` schema
   - Includes indexes for efficient querying

### Integration in UserService

Event logging has been added to:
- `createUser()` - Logs `USER_CREATED` event
- `updateUser()` - Logs `USER_UPDATED` event

### Integration in EnrollmentService

Event logging has been added to:
- `enrollStudent()` - Logs `COURSE_ENROLLED` event
- `unenroll()` - Logs `COURSE_UNENROLLED` event
- `deleteEnrollment()` - Logs `ENROLLMENT_DELETED` event

### Event Data Captured

#### USER_CREATED Event:
```json
{
  "actionType": "USER_CREATED",
  "data": {
    "userId": "...",
    "userEmail": "user@example.com",
    "userName": "John Doe",
    "role": "STUDENT",
    "isActive": true,
    "instituteIds": ["inst-1", "inst-2"]
  }
}
```

#### USER_UPDATED Event:
```json
{
  "actionType": "USER_UPDATED",
  "data": {
    "userId": "...",
    "userEmail": "user@example.com",
    "userName": "John Doe",
    "oldRole": "STUDENT",
    "newRole": "INSTRUCTOR",
    "isActive": true,
    "instituteIds": ["inst-1"]
  }
}
```

#### COURSE_ENROLLED Event:
```json
{
  "actionType": "COURSE_ENROLLED",
  "data": {
    "enrollmentId": "...",
    "studentId": "...",
    "courseId": "...",
    "instituteId": "...",
    "classId": "...",
    "sectionId": "..."
  }
}
```

#### COURSE_UNENROLLED Event:
```json
{
  "actionType": "COURSE_UNENROLLED",
  "data": {
    "enrollmentId": "...",
    "studentId": "...",
    "courseId": "...",
    "instituteId": "...",
    "classId": "...",
    "sectionId": "..."
  }
}
```

#### ENROLLMENT_DELETED Event:
```json
{
  "actionType": "ENROLLMENT_DELETED",
  "data": {
    "enrollmentId": "...",
    "studentId": "...",
    "courseId": "...",
    "instituteId": "...",
    "classId": "...",
    "sectionId": "..."
  }
}
```

## Special Considerations

### SYSTEM_ADMIN Users

For SYSTEM_ADMIN users who don't have a tenant context, events are logged with a placeholder client ID (`00000000-0000-0000-0000-000000000000`) to ensure the events can still be stored (since `clientId` is required).

### User Context

- Events capture the user who performed the action (current user)
- For enrollment events, the student being enrolled is also captured in the event data
- User email is fetched from the identity service when available

## Performance Considerations

- **Async Logging**: All events are logged asynchronously to avoid impacting request performance
- **Non-blocking**: Event logging failures don't break the operations
- **Thread Pool**: Dedicated thread pool for event logging (2-5 threads)
- **Database Indexes**: Indexes on frequently queried fields for fast lookups

## Future Enhancements

1. **REST API**: Create EventController endpoints for querying events
2. **User Deletion**: Add event logging if user deletion is implemented
3. **Bulk Operations**: Log bulk enrollment/unenrollment operations
4. **Event Filtering**: Add configuration to filter which events to log
5. **Event Aggregation**: Create aggregated views for analytics

## Notes

- Events are logged asynchronously, so there may be a slight delay before they appear in the database
- Event logging failures are logged to the standard logger but don't affect operations
- User information is extracted from the security context and identity service
- All events are scoped to the current tenant (client_id) except for SYSTEM_ADMIN events
