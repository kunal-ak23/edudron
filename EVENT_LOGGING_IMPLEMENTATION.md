# Event Logging Implementation

## Overview
This document describes the event logging system that stores all application events in the database for auditing, analytics, and debugging purposes.

## Components Created

### 1. Event Entity (`Event.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/domain/Event.java`
- **Purpose**: JPA entity to store events in the database
- **Key Fields**:
  - `eventType`: Type of event (HTTP_REQUEST, USER_ACTION, SYSTEM_EVENT, ERROR)
  - `httpMethod`, `httpPath`, `httpStatus`, `durationMs`: HTTP request details
  - `userId`, `userEmail`: User context
  - `traceId`, `requestId`: Request correlation
  - `eventData`: JSON string with additional event data
  - `errorMessage`, `errorStackTrace`: Error details
  - `serviceName`, `endpoint`: Service context

### 2. EventRepository (`EventRepository.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/repo/EventRepository.java`
- **Purpose**: JPA repository for querying events
- **Key Methods**:
  - Find events by client, type, user, trace ID
  - Find events in time range
  - Find error events
  - Count events by type

### 3. EventService (`EventService.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/service/EventService.java`
- **Purpose**: Service for logging events asynchronously
- **Key Methods**:
  - `logHttpRequest()`: Log HTTP requests
  - `logUserAction()`: Log user actions
  - `logSystemEvent()`: Log system events
  - `logError()`: Log errors
  - `logEvent()`: Generic event logging

### 4. EventLoggingInterceptor (`EventLoggingInterceptor.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/config/EventLoggingInterceptor.java`
- **Purpose**: Automatically intercepts and logs all HTTP requests
- **Features**:
  - Captures request method, path, status, duration
  - Extracts user information, trace IDs, IP addresses
  - Logs errors automatically
  - Non-blocking (async logging)

### 5. AsyncConfig (`AsyncConfig.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/config/AsyncConfig.java`
- **Purpose**: Configures async processing for event logging
- **Configuration**: Thread pool with 2-5 threads, queue capacity of 100

### 6. WebConfig (`WebConfig.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/config/WebConfig.java`
- **Purpose**: Registers the EventLoggingInterceptor
- **Exclusions**: Actuator endpoints, Swagger UI, error pages

### 7. EventController (`EventController.java`)
- **Location**: `student/src/main/java/com/datagami/edudron/student/web/EventController.java`
- **Purpose**: REST API for querying events
- **Endpoints**:
  - `GET /api/events`: Get all events (paginated)
  - `GET /api/events/type/{eventType}`: Get events by type
  - `GET /api/events/user/{userId}`: Get events by user
  - `GET /api/events/trace/{traceId}`: Get events by trace ID
  - `GET /api/events/range`: Get events in time range
  - `GET /api/events/errors`: Get error events

### 8. Database Migration (`db.changelog-0014-events.yaml`)
- **Location**: `student/src/main/resources/db/changelog/db.changelog-0014-events.yaml`
- **Purpose**: Creates the `events` table in the `student` schema
- **Indexes**: Created on `client_id`, `event_type`, `created_at`, `user_id`, `trace_id`

### 9. Updated Components
- **GlobalExceptionHandler**: Now logs errors to the event service
- **JwtAuthenticationFilter**: Sets user information as request attributes for event logging

## Event Types

1. **HTTP_REQUEST**: All HTTP requests are automatically logged
2. **USER_ACTION**: User-initiated actions (can be logged manually)
3. **SYSTEM_EVENT**: System-level events (can be logged manually)
4. **ERROR**: Errors and exceptions are automatically logged

## Usage Examples

### Automatic Logging
All HTTP requests are automatically logged via the interceptor. No code changes needed.

### Manual Event Logging

```java
@Autowired
private EventService eventService;

// Log a user action
eventService.logUserAction(
    "COURSE_ENROLLED",
    userId,
    userEmail,
    "/api/enrollments",
    Map.of("courseId", courseId, "enrollmentId", enrollmentId)
);

// Log a system event
eventService.logSystemEvent(
    "BATCH_PROCESSING_COMPLETED",
    "Processed 1000 records",
    Map.of("recordCount", 1000, "duration", 5000)
);

// Log an error
eventService.logError(
    "ValidationException",
    "Invalid input data",
    stackTrace,
    "/api/students",
    userId,
    traceId
);
```

### Querying Events

```bash
# Get all events
GET /api/events?page=0&size=50

# Get HTTP request events
GET /api/events/type/HTTP_REQUEST?page=0&size=50

# Get events for a specific user
GET /api/events/user/{userId}?page=0&size=50

# Get events by trace ID (for request correlation)
GET /api/events/trace/{traceId}

# Get error events
GET /api/events/errors?page=0&size=50

# Get events in time range
GET /api/events/range?startTime=2024-01-01T00:00:00Z&endTime=2024-01-31T23:59:59Z
```

## Performance Considerations

1. **Async Logging**: All event logging is done asynchronously to avoid impacting request performance
2. **Thread Pool**: Dedicated thread pool (2-5 threads) for event logging
3. **Error Handling**: Event logging failures don't break the application
4. **Indexes**: Database indexes on frequently queried fields for fast lookups

## Database Schema

The `events` table is created in the `student` schema with the following structure:
- Primary key: `id` (ULID, varchar(26))
- Foreign key: `client_id` (UUID)
- Indexes on: `client_id`, `event_type`, `created_at`, `user_id`, `trace_id`
- Composite index on: `client_id`, `event_type`, `created_at`

## Future Enhancements

1. **Gateway Integration**: Add event logging to the gateway service (requires R2DBC for reactive database access)
2. **Event Retention**: Implement automatic cleanup of old events
3. **Event Aggregation**: Create aggregated views for analytics
4. **Real-time Monitoring**: Add WebSocket support for real-time event streaming
5. **Event Filtering**: Add configuration to filter which events to log
6. **Multi-service Support**: Extend to other services (content, identity, payment)

## Notes

- Events are logged asynchronously, so there may be a slight delay before they appear in the database
- Event logging failures are logged to the standard logger but don't affect the application
- The system automatically extracts user information from JWT tokens when available
- All events are scoped to the current tenant (client_id)
