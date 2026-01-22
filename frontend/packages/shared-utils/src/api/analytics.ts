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
}
