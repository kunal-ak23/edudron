# CRUD Audit Log Coverage

This document lists all entities and operations covered by the CRUD audit log system, and those explicitly skipped. All services write to `common.audit_logs`; the paged read API is exposed by identity at `GET /idp/audit-logs`.

## Table 1 — Entities and actions covered

| Service   | Entity           | CREATE | UPDATE | DELETE | Other actions        | Notes |
|-----------|------------------|--------|--------|--------|----------------------|-------|
| identity  | User             | Yes    | Yes    | No     | —                    | Password reset audited as UPDATE. |
| identity  | Client           | Yes    | Yes    | No     | —                    | |
| identity  | TenantBranding   | Yes    | Yes    | No     | —                    | |
| identity  | TenantFeature    | Yes    | Yes    | Yes    | —                    | resetFeatureToDefault = DELETE. |
| identity  | UserInstitute    | Yes    | Yes    | Yes    | —                    | On user update / institute assign. |
| identity  | User (register)  | Yes    | No     | No     | —                    | AuthService register. |
| content   | Course           | Yes    | Yes    | Yes    | PUBLISH, UNPUBLISH   | |
| content   | Section          | Yes    | Yes    | Yes    | —                    | Content schema (course sections). |
| content   | Lecture          | Yes    | Yes    | Yes    | —                    | |
| content   | Assessment       | Yes    | Yes    | Yes    | —                    | Exam/assessment. |
| content   | QuestionBank     | Yes    | Yes    | Yes    | —                    | |
| student   | Enrollment       | Yes    | No     | Yes    | TRANSFER             | Unenroll = DELETE or dedicated action. |
| student   | Institute        | Yes    | Yes    | No     | —                    | |
| student   | Batch            | Yes    | Yes    | No     | —                    | |
| student   | Section          | Yes    | Yes    | No     | —                    | Student schema (institute sections). |
| student   | Class            | Yes    | Yes    | No     | —                    | |
| payment   | Payment          | Yes    | Yes    | No     | —                    | Status updates; actor = "system" when no user. |
| payment   | SubscriptionPlan | Yes    | Yes    | No     | —                    | Deactivate = UPDATE. |
| payment   | Subscription     | Yes    | Yes    | No     | —                    | Cancel = UPDATE. |
| payment   | Payment (webhook)| No     | Yes    | No     | —                    | actor = "webhook". |

## Table 2 — Entities and actions skipped

| Service  | Entity / area              | Actions skipped     | Reason |
|----------|----------------------------|---------------------|--------|
| identity | User (token refresh / internal) | UPDATE (lastLogin, refreshToken) | High volume; not business CRUD. |
| content  | CourseAnnouncement         | CREATE, UPDATE, DELETE | Out of scope for initial rollout; add later if needed. |
| content  | CourseCategory             | CREATE, UPDATE, DELETE | Same. |
| content  | CourseInstructor           | CREATE, UPDATE, DELETE | Same. |
| content  | CoursePrerequisite         | CREATE, UPDATE, DELETE | Same. |
| content  | LearningObjective          | CREATE, UPDATE, DELETE | Same. |
| content  | MediaAsset                 | CREATE, UPDATE, DELETE | Same. |
| content  | CourseTag                  | CREATE, UPDATE, DELETE | Same. |
| content  | SubLesson                  | CREATE, UPDATE, DELETE | Same. |
| content  | CourseResource             | CREATE, UPDATE, DELETE | Same; includes PDF storage. |
| content  | LectureContent             | CREATE, UPDATE, DELETE | Same; media content. |
| content  | CourseGenerationIndex     | CREATE, UPDATE, DELETE | Internal index; not user-facing CRUD. |
| content  | QuizOption / QuestionBankOption | CREATE, UPDATE, DELETE | Child of Quiz/QuestionBank; optional to avoid noise. |
| content  | ExamQuestion (per-question)| CREATE, UPDATE, DELETE | Assessment-level audit is enough; skip per-question. |
| content  | CourseCopy / MediaCopy     | Bulk saves          | Copy operations; can add single "COURSE_COPIED" audit if desired. |
| content  | PsychTestSession          | CREATE, UPDATE      | Psych test runtime; high volume. |
| content  | PsychTestResult            | CREATE             | Same. |
| content  | PsychTestQuestion / Option / Answer / Asked | All | Same. |
| content  | Event (common.events)      | —                  | Event is the log target; not audited as entity. |
| student  | InstructorAssignment      | CREATE, UPDATE, DELETE | Out of scope for initial rollout. |
| student  | AssessmentSubmission       | CREATE, UPDATE      | Exam submissions; high volume; optional later. |
| student  | ProctoringEvent            | CREATE             | Proctoring stream; high volume. |
| student  | LectureViewSession         | CREATE, UPDATE      | View progress; high volume. |
| student  | Progress                   | CREATE, UPDATE, DELETE | Progress records; high volume. |
| student  | Note                       | CREATE, UPDATE, DELETE | Out of scope for initial rollout. |
| student  | Feedback                   | CREATE, DELETE     | Same. |
| student  | IssueReport                | CREATE             | Same. |
| student  | Enrollment (placeholder)   | CREATE, DELETE     | Bulk/placeholder enrollments; optional or actor = "system". |
| student  | Event (common.events)      | —                  | Event is the log target; not audited as entity. |
| payment  | PaymentWebhook             | CREATE, UPDATE     | Internal webhook log; optional with actor = "webhook". |

---

Keep these tables in sync when adding or skipping entities during implementation.
