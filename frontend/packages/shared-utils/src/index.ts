// API Client
export { ApiClient } from './api/ApiClient'
export type { ApiResponse, PaginatedResponse } from './api/ApiClient'
export { CoursesApi, CourseGenerationIndexApi } from './api/courses'
export type { GenerateCourseRequest, CourseGenerationIndex } from './api/courses'
export { EnrollmentsApi } from './api/enrollments'
export { PaymentsApi } from './api/payments'
export { MediaApi } from './api/media'
export type { UploadResponse } from './api/media'
export { TenantsApi, TenantBrandingApi } from './api/tenants'
export type { Tenant, CreateTenantRequest, TenantBranding } from './api/tenants'
export { InstitutesApi, InstituteType } from './api/institutes'
export type { Institute, CreateInstituteRequest } from './api/institutes'
export { ClassesApi } from './api/classes'
export type { Class, CreateClassRequest } from './api/classes'
export { SectionsApi } from './api/sections'
export type { Section, CreateSectionRequest } from './api/sections'
export { LecturesApi } from './api/lectures'
export type { CreateLectureRequest, UpdateLectureRequest } from './api/lectures'
export type { LectureContent } from './api/courses'
export { FeedbackApi } from './api/feedback'
export type { Feedback, CreateFeedbackRequest, FeedbackType } from './api/feedback'
export { NotesApi } from './api/notes'
export type { Note, CreateNoteRequest } from './api/notes'
export { IssuesApi } from './api/issues'
export type { IssueReport, CreateIssueReportRequest, IssueType, IssueStatus } from './api/issues'
export { StudentsApi } from './api/students'
export type { BulkStudentImportResult, StudentImportRowResult, BulkEnrollmentResult } from './api/students'
export type { BulkEnrollmentResult as EnrollmentBulkEnrollmentResult } from './api/enrollments'

// Auth
export { AuthService } from './auth/AuthService'
export { TokenManager } from './auth/TokenManager'
export { AuthProvider, useAuth } from './auth/AuthContext'

// Types
export type {
  User,
  UserRole,
  LoginCredentials,
  RegisterCredentials,
  AuthResponse,
  TenantInfo,
  AuthError
} from './types/auth'

export type {
  Course,
  CourseInstructor,
  LearningObjective,
  Section as CourseSection,
  Chapter,
  Lecture,
  Assessment,
  AssessmentQuestion
} from './api/courses'

export type {
  Enrollment,
  Batch,
  Progress,
  LectureProgress,
  SectionProgress
} from './api/enrollments'
// Section and CreateSectionRequest are already exported above on line 17

export type {
  SubscriptionPlan,
  Subscription,
  Payment
} from './api/payments'

