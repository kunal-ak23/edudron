import { apiClient } from './api'

export interface ProctoringEvent {
  id: string
  eventType: string
  severity: 'INFO' | 'WARNING' | 'VIOLATION'
  metadata: Record<string, any>
  createdAt: string
}

export interface ProctoringReport {
  submissionId: string
  proctoringStatus: 'CLEAR' | 'FLAGGED' | 'SUSPICIOUS' | 'VIOLATION'
  tabSwitchCount: number
  copyAttemptCount: number
  identityVerified: boolean
  identityVerificationPhotoUrl?: string
  proctoringData?: {
    photos: Array<{
      url: string
      capturedAt: string
    }>
  }
  eventCounts: {
    info: number
    warning: number
    violation: number
  }
  events: ProctoringEvent[]
}

export const proctoringApi = {
  /**
   * Get proctoring report for a submission
   */
  getReport: async (
    examId: string,
    submissionId: string
  ): Promise<ProctoringReport> => {
    return apiClient.get(
      `/api/student/exams/${examId}/submissions/${submissionId}/proctoring/report`
    ) as unknown as Promise<ProctoringReport>
  },

  /**
   * Get proctoring events filtered by severity
   */
  getEvents: async (
    examId: string,
    submissionId: string,
    severity?: 'INFO' | 'WARNING' | 'VIOLATION'
  ): Promise<ProctoringEvent[]> => {
    const url = `/api/student/exams/${examId}/submissions/${submissionId}/proctoring/events`
    const params = severity ? `?severity=${severity}` : ''
    return apiClient.get(url + params) as unknown as Promise<ProctoringEvent[]>
  }
}
