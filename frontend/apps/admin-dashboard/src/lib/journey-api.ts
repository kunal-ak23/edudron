import { apiClient } from './api'

export interface JourneyEvent {
  id: string
  eventType: string
  severity?: string
  metadata?: Record<string, unknown>
  createdAt: string
}

/**
 * Get assessment journey events for a submission (teachers/admins).
 */
export async function getJourneyEvents(
  examId: string,
  submissionId: string
): Promise<JourneyEvent[]> {
  const data = await apiClient.get(
    `/api/student/exams/${examId}/submissions/${submissionId}/journey/events`
  )
  return (Array.isArray(data) ? data : (data as any)?.data ?? []) as JourneyEvent[]
}
