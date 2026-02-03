# EventService Coverage

This document lists where and how **EventService** (common.events) is used across services. Events are behavioral/product-level: “what did the user do?” (analytics, funnels, notifications). They are **separate from** the CRUD audit log (who changed what; see `AUDIT_LOG_COVERAGE.md`).

---

## Services that use EventService

| Service   | Uses EventService? | Implementation           |
|-----------|--------------------|---------------------------|
| identity  | Yes                | CommonEventService        |
| content   | Yes                | CommonEventService        |
| student   | Yes                | CommonEventService        |
| payment   | **No**             | —                         |

---

## Table 1 — Event types and where they are emitted

### Identity

| Event type / method     | Where emitted                    | Notes |
|-------------------------|-----------------------------------|-------|
| **LOGIN**               | AuthService (password login, OTP login) | logLogin(userId, email, ip, userAgent, sessionId, data) |
| **USER_ACTION**         | AuthService                      | USER_REGISTERED (on register) |
| **USER_ACTION**         | UserService                      | USER_CREATED, USER_UPDATED, PASSWORD_RESET_BY_ADMIN |
| **LOGOUT**              | —                                 | logLogout exists in EventService/CommonEventService but **not called** anywhere |

### Content

| Event type / method     | Where emitted                    | Notes |
|-------------------------|-----------------------------------|-------|
| **USER_ACTION**         | CourseService                    | COURSE_CREATED, COURSE_EDITED, COURSE_DELETED, COURSE_PUBLISHED, COURSE_UNPUBLISHED |
| **SEARCH_QUERY**        | CourseService                    | logSearchQuery(userId, query, "COURSE", resultCount, durationMs, data) |

### Student

| Event type / method     | Where emitted                    | Notes |
|-------------------------|-----------------------------------|-------|
| **USER_ACTION**         | EnrollmentService                 | COURSE_ENROLLED, COURSE_UNENROLLED, ENROLLMENT_DELETED, ENROLLMENT_TRANSFERRED |
| **USER_ACTION**         | ExamSubmissionService            | — via logAssessmentSubmission (ASSESSMENT_SUBMITTED) |
| **VIDEO_WATCH_PROGRESS**| LectureViewSessionService        | logVideoWatchProgress(userId, courseId, lectureId, progress%, durationSec, data) |
| **LECTURE_COMPLETED**   | LectureViewSessionService        | logLectureCompletion(userId, courseId, lectureId, totalDurationSec, data) |
| **HTTP_REQUEST**        | EventLoggingInterceptor          | logHttpRequest(method, path, status, durationMs, traceId, requestId, userAgent, ip, userId, email, data) — request-level logging |
| **ERROR**               | GlobalExceptionHandler           | logError(errorType, message, stackTrace, endpoint, userId, traceId) on handled exceptions |
| **ERROR**               | EventLoggingInterceptor          | logError when request fails |

---

## Table 2 — EventService API: used vs unused

| Method                    | Used? | Where used |
|---------------------------|-------|------------|
| logUserAction             | Yes   | Identity (Auth, User), Content (Course), Student (Enrollment) |
| logLogin                  | Yes   | Identity (AuthService) |
| logLogout                 | **No**| Defined but never called |
| logHttpRequest            | Yes   | Student (EventLoggingInterceptor) |
| logError                  | Yes   | Student (GlobalExceptionHandler, EventLoggingInterceptor); Identity/Content expose it but call sites are in student |
| logVideoWatchProgress     | Yes   | Student (LectureViewSessionService) |
| logLectureCompletion      | Yes   | Student (LectureViewSessionService) |
| logAssessmentSubmission   | Yes   | Student (ExamSubmissionService) |
| logSearchQuery            | Yes   | Content (CourseService) |
| logFileUpload             | **No**| Defined in EventService but never called |
| logEvent (generic)        | Yes   | Via CommonEventService overrides; direct call sites not enumerated above |

---

## Table 3 — User-action event types (logUserAction) by service

| Service   | Action type              | Endpoint / context |
|-----------|---------------------------|---------------------|
| identity  | USER_REGISTERED           | /auth/register      |
| identity  | USER_CREATED              | /idp/users          |
| identity  | USER_UPDATED              | /idp/users/{id}     |
| identity  | PASSWORD_RESET_BY_ADMIN   | /idp/users/{id}/reset-password |
| content   | COURSE_CREATED            | /api/content/courses |
| content   | COURSE_EDITED             | /api/content/courses/{id} |
| content   | COURSE_DELETED            | /api/content/courses/{id} |
| content   | COURSE_PUBLISHED          | /api/content/courses/{id}/publish |
| content   | COURSE_UNPUBLISHED        | /api/content/courses/{id}/unpublish |
| student   | COURSE_ENROLLED           | /api/courses/{id}/enroll |
| student   | COURSE_UNENROLLED         | /api/courses/{id}/enroll |
| student   | ENROLLMENT_DELETED        | /api/enrollments/{id} |
| student   | ENROLLMENT_TRANSFERRED    | /api/enrollments/transfer |

---

## Gaps (available in API but not used)

1. **LOGOUT** — `logLogout` is implemented but never called (e.g. from AuthService logout).
2. **FILE_UPLOADED** — `logFileUpload` is implemented but no service calls it.
3. **Payment** — No EventService usage (e.g. no PAYMENT_INITIATED, PAYMENT_SUCCESS, SUBSCRIPTION_CREATED events). Add if product/analytics needs payment funnel events.

---

## Summary

- **Identity:** Login, register, user CRUD and password reset.
- **Content:** Course CRUD/publish/unpublish, course search.
- **Student:** Enrollment lifecycle, assessment submission, video progress and lecture completion, HTTP request and error logging.
- **Payment:** No event logging.

Update this document when adding or removing event calls or new event types.
