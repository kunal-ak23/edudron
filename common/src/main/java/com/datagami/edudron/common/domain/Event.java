package com.datagami.edudron.common.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Unified Event entity stored in common.events table.
 * All services use this entity to log events to a centralized location.
 */
@Entity
@Table(name = "events", schema = "common", indexes = {
    @Index(name = "idx_events_client_id", columnList = "clientId"),
    @Index(name = "idx_events_event_type", columnList = "eventType"),
    @Index(name = "idx_events_created_at", columnList = "createdAt"),
    @Index(name = "idx_events_user_id", columnList = "userId"),
    @Index(name = "idx_events_trace_id", columnList = "traceId"),
    @Index(name = "idx_events_service_name", columnList = "serviceName"),
    @Index(name = "idx_events_client_type_created", columnList = "clientId,eventType,createdAt"),
    @Index(name = "idx_events_user_created", columnList = "userId,createdAt"),
    @Index(name = "idx_events_service_type_created", columnList = "serviceName,eventType,createdAt")
})
public class Event {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 50)
    private String eventType; // HTTP_REQUEST, USER_ACTION, SYSTEM_EVENT, ERROR, LOGIN, LOGOUT, etc.

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // HTTP Request fields
    @Column(length = 10)
    private String httpMethod; // GET, POST, PUT, DELETE, etc.

    @Column(length = 500)
    private String httpPath; // Request path

    private Integer httpStatus; // Response status code

    private Long durationMs; // Request duration in milliseconds

    // User context
    @Column(length = 26)
    private String userId; // User ID if available

    @Column(length = 100)
    private String userEmail; // User email if available

    // Request context
    @Column(length = 100)
    private String traceId; // Request trace ID for correlation

    @Column(length = 100)
    private String requestId; // Request ID

    @Column(length = 500)
    private String userAgent; // User agent string

    @Column(length = 50)
    private String ipAddress; // Client IP address

    // Event details
    @Column(columnDefinition = "TEXT")
    private String eventData; // JSON string with additional event data

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // Error message if event is an error

    @Column(columnDefinition = "TEXT")
    private String errorStackTrace; // Stack trace if event is an error

    // Service context
    @Column(nullable = false, length = 50)
    private String serviceName; // Service that generated the event (gateway, student, content, identity)

    @Column(length = 100)
    private String endpoint; // API endpoint

    // Additional context fields
    @Column(length = 100)
    private String sessionId; // Session ID for tracking user sessions

    @Column(length = 50)
    private String deviceType; // mobile, desktop, tablet

    @Column(length = 100)
    private String browser; // Chrome, Firefox, Safari, etc.

    @Column(length = 100)
    private String os; // Windows, macOS, Linux, iOS, Android

    // Constructors
    public Event() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getHttpPath() { return httpPath; }
    public void setHttpPath(String httpPath) { this.httpPath = httpPath; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getEventData() { return eventData; }
    public void setEventData(String eventData) { this.eventData = eventData; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorStackTrace() { return errorStackTrace; }
    public void setErrorStackTrace(String errorStackTrace) { this.errorStackTrace = errorStackTrace; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
