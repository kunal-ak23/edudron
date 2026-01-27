import { apiClient } from './api'

export interface ProctoringEvent {
  eventType: string
  severity: 'INFO' | 'WARNING' | 'VIOLATION'
  metadata?: Record<string, any>
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
  events: Array<{
    id: string
    eventType: string
    severity: string
    metadata: Record<string, any>
    createdAt: string
  }>
}

export const proctoringApi = {
  /**
   * Log a proctoring event during exam
   */
  logEvent: async (
    examId: string,
    submissionId: string,
    event: ProctoringEvent
  ): Promise<any> => {
    return apiClient.post(
      `/api/student/exams/${examId}/submissions/${submissionId}/proctoring/log-event`,
      event
    ) as unknown as Promise<any>
  },

  /**
   * Upload identity verification photo
   */
  verifyIdentity: async (
    examId: string,
    submissionId: string,
    photoBase64: string
  ): Promise<{ message: string; submissionId: string }> => {
    return apiClient.post(
      `/api/student/exams/${examId}/submissions/${submissionId}/proctoring/verify-identity`,
      { photo: photoBase64 }
    ) as unknown as Promise<{ message: string; submissionId: string }>
  },

  /**
   * Upload periodic exam photo
   */
  capturePhoto: async (
    examId: string,
    submissionId: string,
    photoBase64: string
  ): Promise<{ message: string; submissionId: string; capturedAt: string }> => {
    return apiClient.post(
      `/api/student/exams/${examId}/submissions/${submissionId}/proctoring/capture-photo`,
      { photo: photoBase64 }
    ) as unknown as Promise<{ message: string; submissionId: string; capturedAt: string }>
  },

  /**
   * Get proctoring report for a submission
   */
  getReport: async (
    examId: string,
    submissionId: string
  ): Promise<ProctoringReport> => {
    return apiClient.get(
      `/api/exams/${examId}/submissions/${submissionId}/proctoring/report`
    ) as unknown as Promise<ProctoringReport>
  },

  /**
   * Get proctoring events filtered by severity
   */
  getEvents: async (
    examId: string,
    submissionId: string,
    severity?: 'INFO' | 'WARNING' | 'VIOLATION'
  ): Promise<any[]> => {
    const url = `/api/exams/${examId}/submissions/${submissionId}/proctoring/events`
    const params = severity ? `?severity=${severity}` : ''
    return apiClient.get(url + params) as unknown as Promise<any[]>
  }
}
