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
  proctoringStatus: 'CLEAR' | 'FLAGGED' | 'SUSPICIOUS' | 'VIOLATION' | null
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
    const response = await apiClient.get(
      `/api/student/exams/${examId}/submissions/${submissionId}/proctoring/report`
    )
    return (response as any)?.data || response
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
    const response = await apiClient.get(url + params)
    return (response as any)?.data || response
  }
}
