# Course Action Event Logging

## Overview
This document describes the event logging implementation for course-related actions in the content service. All course actions (create, edit, delete, publish, unpublish) are now automatically logged to the database.

## Actions Logged

The following course actions are now automatically logged:

1. **COURSE_CREATED** - When a course is created
2. **COURSE_EDITED** - When a course is updated/edited
3. **COURSE_DELETED** - When a course is deleted
4. **COURSE_PUBLISHED** - When a course is published
5. **COURSE_UNPUBLISHED** - When a course is unpublished

## Implementation Details

### Components Added

1. **Event Entity** (`content/domain/Event.java`)
   - Stores events in the `content.events` table
   - Same structure as the student service events table

2. **EventRepository** (`content/repo/EventRepository.java`)
   - JPA repository for querying events
   - Supports filtering by client, type, user, time range, etc.

3. **EventService** (`content/service/EventService.java`)
   - Service for logging events asynchronously
   - Methods: `logUserAction()`, `logSystemEvent()`, `logError()`, `logEvent()`

4. **AsyncConfig** (`content/config/AsyncConfig.java`)
   - Configures async processing for event logging
   - Thread pool: 2-5 threads, queue capacity 100

5. **Database Migration** (`db/changelog/db.changelog-0016-events.yaml`)
   - Creates `events` table in `content` schema
   - Includes indexes for efficient querying

### Integration in CourseService

Event logging has been added to the following methods:

- `createCourse()` - Logs `COURSE_CREATED` event
- `updateCourse()` - Logs `COURSE_EDITED` event
- `deleteCourse()` - Logs `COURSE_DELETED` event
- `publishCourse()` - Logs `COURSE_PUBLISHED` event
- `unpublishCourse()` - Logs `COURSE_UNPUBLISHED` event

### Event Data Captured

Each course action event includes:

- **Action Type**: The type of action (COURSE_CREATED, COURSE_EDITED, etc.)
- **User Information**: User ID and email (if available)
- **Course Information**: Course ID, title, and relevant metadata
- **Endpoint**: The API endpoint that triggered the action
- **Timestamp**: Automatic timestamp when the event occurred

### Example Event Data

**COURSE_CREATED Event:**
```json
{
  "actionType": "COURSE_CREATED",
  "data": {
    "courseId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
    "courseTitle": "Introduction to Java",
    "isPublished": false,
    "isFree": true
  }
}
```

**COURSE_PUBLISHED Event:**
```json
{
  "actionType": "COURSE_PUBLISHED",
  "data": {
    "courseId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
    "courseTitle": "Introduction to Java",
    "wasPublished": false,
    "totalLectures": 15,
    "totalDurationSeconds": 7200
  }
}
```

## Querying Events

Events can be queried using the EventRepository or by creating a REST API endpoint similar to the student service. The events are stored in the `content.events` table.

### Example Queries

```java
// Get all course creation events
Page<Event> events = eventRepository.findByClientIdAndEventTypeOrderByCreatedAtDesc(
    clientId, "USER_ACTION", pageable);

// Filter events by action type in eventData (requires custom query)
// Note: This would require a custom repository method using JSON queries
```

## Performance Considerations

- **Async Logging**: All events are logged asynchronously to avoid impacting request performance
- **Non-blocking**: Event logging failures don't break the course operations
- **Thread Pool**: Dedicated thread pool for event logging (2-5 threads)
- **Database Indexes**: Indexes on frequently queried fields for fast lookups

## Future Enhancements

1. **REST API**: Create an EventController similar to the student service for querying events
2. **Event Filtering**: Add configuration to filter which events to log
3. **Event Aggregation**: Create aggregated views for analytics
4. **Real-time Monitoring**: Add WebSocket support for real-time event streaming
5. **Additional Actions**: Log other course-related actions (lecture added, section created, etc.)

## Notes

- Events are logged asynchronously, so there may be a slight delay before they appear in the database
- Event logging failures are logged to the standard logger but don't affect course operations
- User information is extracted from the security context and identity service
- All events are scoped to the current tenant (client_id)
