import { ApiClient } from './ApiClient'

export interface LectureViewSession {
  id: string
  clientId: string
  enrollmentId: string
  studentId: string
  courseId: string
  lectureId: string
  sessionStartedAt: string
  sessionEndedAt?: string
  durationSeconds: number
  progressAtStart: number
  progressAtEnd?: number
  isCompletedInSession: boolean
  createdAt: string
  updatedAt: string
}

export interface LectureAnalytics {
  lectureId: string
  lectureTitle: string
  totalViews: number
  uniqueViewers: number
  averageSessionDurationSeconds: number
  completionRate: number
  skipRate: number
  firstViewAt?: string
  lastViewAt?: string
  studentEngagements: StudentLectureEngagement[]
  recentSessions: LectureViewSession[]
}

export interface CourseAnalytics {
  courseId: string
  courseTitle: string
  totalViewingSessions: number
  uniqueStudentsEngaged: number
  averageTimePerLectureSeconds: number
  overallCompletionRate: number
  lectureEngagements: LectureEngagementSummary[]
  skippedLectures: SkippedLecture[]
  activityTimeline: ActivityTimelinePoint[]
}

export interface StudentLectureEngagement {
  studentId: string
  studentEmail?: string
  totalSessions: number
  totalDurationSeconds: number
  averageSessionDurationSeconds: number
  firstViewAt?: string
  lastViewAt?: string
  isCompleted: boolean
  completionPercentage?: number
}

export interface LectureEngagementSummary {
  lectureId: string
  lectureTitle: string
  totalViews: number
  uniqueViewers: number
  averageDurationSeconds: number
  completionRate: number
  skipRate: number
}

export interface SkippedLecture {
  lectureId: string
  lectureTitle: string
  lectureDurationSeconds: number
  totalSessions: number
  skippedSessions: number
  skipRate: number
  averageDurationSeconds: number
  skipReason: 'DURATION_THRESHOLD' | 'QUICK_COMPLETION'
}

export interface ActivityTimelinePoint {
  timestamp: string
  sessionCount: number
  uniqueStudents: number
}

export interface SectionAnalytics {
  sectionId: string
  sectionName: string
  classId?: string
  className?: string
  totalCourses: number // Number of courses in section
  totalViewingSessions: number // Across all courses
  uniqueStudentsEngaged: number
  averageTimePerLectureSeconds: number
  overallCompletionRate: number
  lectureEngagements: LectureEngagementSummary[] // Lectures from all courses
  skippedLectures: SkippedLecture[] // Across all courses
  activityTimeline: ActivityTimelinePoint[] // Across all courses
  courseBreakdown: CourseBreakdown[] // Per-course metrics
}

export interface ClassAnalytics {
  classId: string
  className: string
  instituteId: string
  totalSections: number
  totalCourses: number // Number of unique courses across all sections
  totalViewingSessions: number // Across all sections and courses
  uniqueStudentsEngaged: number
  averageTimePerLectureSeconds: number
  overallCompletionRate: number
  lectureEngagements: LectureEngagementSummary[] // Lectures from all courses
  skippedLectures: SkippedLecture[] // Across all courses and sections
  activityTimeline: ActivityTimelinePoint[] // Across all courses and sections
  sectionComparison: SectionComparison[] // Each section's aggregate metrics
  courseBreakdown: CourseBreakdown[] // Per-course metrics
}

export interface SectionComparison {
  sectionId: string
  sectionName: string
  totalStudents: number
  activeStudents: number
  averageCompletionRate: number // Across all courses
  averageTimeSpentSeconds: number // Across all courses
}

export interface CourseBreakdown {
  courseId: string
  courseTitle: string
  totalSessions: number
  uniqueStudents: number
  completionRate: number
  averageTimeSpentSeconds: number
}

export interface StartSessionRequest {
  courseId: string
  lectureId: string
  progressAtStart?: number
}

export interface EndSessionRequest {
  progressAtEnd?: number
  isCompleted?: boolean
}

export class AnalyticsApi {
  constructor(private apiClient: ApiClient) {}

  async startLectureSession(lectureId: string, request: StartSessionRequest): Promise<LectureViewSession> {
    console.log('[AnalyticsApi] startLectureSession called:', {
      lectureId,
      courseId: request.courseId,
      progressAtStart: request.progressAtStart,
      url: `/api/lectures/${lectureId}/sessions/start`
    })
    try {
      const response = await this.apiClient.post<LectureViewSession>(
        `/api/lectures/${lectureId}/sessions/start`,
        request
      )
      console.log('[AnalyticsApi] startLectureSession success:', {
        sessionId: response.data?.id,
        lectureId: response.data?.lectureId,
        startedAt: response.data?.sessionStartedAt
      })
      return response.data
    } catch (error) {
      console.error('[AnalyticsApi] startLectureSession error:', {
        lectureId,
        courseId: request.courseId,
        error: error instanceof Error ? error.message : String(error),
        errorResponse: (error as any)?.response?.data
      })
      throw error
    }
  }

  async endLectureSession(lectureId: string, sessionId: string, request: EndSessionRequest): Promise<LectureViewSession> {
    console.log('[AnalyticsApi] endLectureSession called:', {
      lectureId,
      sessionId,
      progressAtEnd: request.progressAtEnd,
      isCompleted: request.isCompleted,
      url: `/api/lectures/${lectureId}/sessions/${sessionId}/end`
    })
    try {
      const response = await this.apiClient.post<LectureViewSession>(
        `/api/lectures/${lectureId}/sessions/${sessionId}/end`,
        request
      )
      console.log('[AnalyticsApi] endLectureSession success:', {
        sessionId: response.data?.id,
        lectureId: response.data?.lectureId,
        duration: response.data?.durationSeconds,
        endedAt: response.data?.sessionEndedAt
      })
      return response.data
    } catch (error) {
      console.error('[AnalyticsApi] endLectureSession error:', {
        lectureId,
        sessionId,
        error: error instanceof Error ? error.message : String(error),
        errorResponse: (error as any)?.response?.data,
        statusCode: (error as any)?.response?.status
      })
      throw error
    }
  }

  async getLectureAnalytics(lectureId: string): Promise<LectureAnalytics> {
    const response = await this.apiClient.get<LectureAnalytics>(`/api/lectures/${lectureId}/analytics`)
    return response.data
  }

  async getCourseAnalytics(courseId: string): Promise<CourseAnalytics> {
    const response = await this.apiClient.get<CourseAnalytics>(`/api/courses/${courseId}/analytics`)
    return response.data
  }

  async getSkippedLectures(courseId: string): Promise<SkippedLecture[]> {
    const response = await this.apiClient.get<SkippedLecture[]>(`/api/courses/${courseId}/analytics/skipped`)
    return Array.isArray(response.data) ? response.data : []
  }

  // ==================== SECTION ANALYTICS ====================

  async getSectionAnalytics(sectionId: string): Promise<SectionAnalytics> {
    const response = await this.apiClient.get<SectionAnalytics>(`/api/sections/${sectionId}/analytics`)
    return response.data
  }

  async getSectionSkippedLectures(sectionId: string): Promise<SkippedLecture[]> {
    const response = await this.apiClient.get<SkippedLecture[]>(`/api/sections/${sectionId}/analytics/skipped`)
    return Array.isArray(response.data) ? response.data : []
  }

  async clearSectionAnalyticsCache(sectionId: string): Promise<{ message: string }> {
    const response = await this.apiClient.delete<{ message: string }>(`/api/sections/${sectionId}/analytics/cache`)
    return response.data
  }

  // ==================== CLASS ANALYTICS ====================

  async getClassAnalytics(classId: string): Promise<ClassAnalytics> {
    const response = await this.apiClient.get<ClassAnalytics>(`/api/classes/${classId}/analytics`)
    return response.data
  }

  async getClassSectionComparison(classId: string): Promise<SectionComparison[]> {
    const response = await this.apiClient.get<SectionComparison[]>(`/api/classes/${classId}/analytics/sections/compare`)
    return Array.isArray(response.data) ? response.data : []
  }

  async clearClassAnalyticsCache(classId: string): Promise<{ message: string }> {
    const response = await this.apiClient.delete<{ message: string }>(`/api/classes/${classId}/analytics/cache`)
    return response.data
  }
}
