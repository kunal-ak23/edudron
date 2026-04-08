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
export { TenantFeaturesApi, TenantFeatureType } from './api/tenantFeatures'
export type { TenantFeatureDto } from './api/tenantFeatures'
export { InstitutesApi, InstituteType } from './api/institutes'
export type { Institute, CreateInstituteRequest } from './api/institutes'
export { ClassesApi } from './api/classes'
export type { Class, CreateClassRequest, BatchCreateClassesRequest, BatchCreateClassesResponse, CreateClassWithSectionsRequest, ClassWithSections, CreateSectionForClassRequest, CoordinatorResponse } from './api/classes'
export { SectionsApi } from './api/sections'
export type { Section, CreateSectionRequest, BatchCreateSectionsRequest, BatchCreateSectionsResponse } from './api/sections'
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
export { AnalyticsApi } from './api/analytics'
export { SimulationsApi } from './api/simulations'
export { ProjectsApi, ProjectQuestionsApi } from './api/projects'
export { CalendarEventsApi, EventType, EventAudience } from './api/calendarEvents'
export type { CalendarEvent, CreateCalendarEventInput, CalendarEventImportResult } from './api/calendarEvents'
export { ResultsApi } from './api/results'
export { CertificatesApi } from './api/certificates'
export type {
  CertificateTemplate,
  Certificate,
  CertificateVisibility,
  CertificateGenerateRequest,
  CertificateVerification
} from './api/certificates'
export type {
  ProjectDTO, ProjectGroupDTO, ProjectEventDTO, ProjectQuestionDTO, ProjectAttachmentDTO,
  CreateProjectRequest, GenerateGroupsRequest, SubmitProjectRequest, AttachmentInfo,
  AttendanceEntry, GradeEntry,
  ProjectEventSubmissionDTO, ProjectEventFeedbackDTO, SubmitEventRequest, EventFeedbackRequest,
  ProjectTemplateDTO
} from './api/projects'
export type {
  GenerateSimulationRequest, SimulationDTO, SimulationStateDTO,
  SimulationDecisionDTO, SimulationPlayDTO, DecisionInput,
  ChoiceDTO, DebriefDTO, YearEndReviewDTO,
  SimulationExportDTO, MentorGuidance, MetricImpact
} from './api/simulations'
export type {
  LectureViewSession,
  LectureAnalytics,
  CourseAnalytics,
  StudentLectureEngagement,
  LectureEngagementSummary,
  SkippedLecture,
  ActivityTimelinePoint,
  StartSessionRequest,
  EndSessionRequest,
  SectionAnalytics,
  ClassAnalytics,
  SectionComparison,
  CourseBreakdown
} from './api/analytics'

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
  AssessmentQuestion,
  AIGenerationJobDTO,
  CourseCopyRequest,
  CourseCopyResult
} from './api/courses'

export type {
  Enrollment,
  Batch,
  Progress,
  LectureProgress,
  SectionProgress,
  ClassStudentDTO,
  SectionStudentDTO,
  TransferEnrollmentRequest,
  BulkTransferEnrollmentRequest,
  TransferEnrollmentError,
  BulkTransferEnrollmentResponse,
  AddToSectionRequest,
  BulkAddToSectionRequest
} from './api/enrollments'
// Section and CreateSectionRequest are already exported above on line 17

export type {
  SubscriptionPlan,
  Subscription,
  Payment
} from './api/payments'

// Preferences
export { getFontSize, setFontSize, resetFontSize, applyFontSize } from './preferences/fontSize'
export { FontSizeControl } from './components/FontSizeControl'
export { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './components/Tooltip'

// TipTap Extensions
export { ResizableImage } from './tiptap/ResizableImage'
export type { ImageAlignment } from './tiptap/ResizableImage'
export { ImageBubbleMenu } from './tiptap/ImageBubbleMenu'
export { HighlightMark } from './tiptap/HighlightMark'

