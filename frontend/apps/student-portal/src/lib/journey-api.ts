import { apiClient } from './api'

export interface JourneyEventPayload {
  eventType: string
  severity?: 'INFO' | 'WARNING' | 'VIOLATION'
  metadata?: Record<string, unknown>
}

/**
 * Log an assessment journey event (with submission - normal flow).
 */
export async function logJourneyEvent(
  examId: string,
  submissionId: string,
  payload: JourneyEventPayload
): Promise<void> {
  try {
    await apiClient.post(
      `/api/student/exams/${examId}/submissions/${submissionId}/journey/events`,
      {
        eventType: payload.eventType,
        severity: payload.severity ?? 'INFO',
        metadata: payload.metadata ?? {}
      }
    )
  } catch (_) {
    // Fire-and-forget; do not block UI
  }
}

/**
 * Log an early journey event (no submission yet, e.g. EXAM_TAKE_CLICKED).
 */
export async function logJourneyEventWithoutSubmission(
  examId: string,
  payload: JourneyEventPayload
): Promise<void> {
  try {
    await apiClient.post(`/api/student/exams/${examId}/journey/events`, {
      eventType: payload.eventType,
      severity: payload.severity ?? 'INFO',
      metadata: payload.metadata ?? {}
    })
  } catch (_) {
    // Fire-and-forget
  }
}

/**
 * Send a journey event via sendBeacon (for page unload / visibility hidden).
 * Use when the page may be closing and fetch might be cancelled.
 */
export function sendJourneyEventBeacon(
  examId: string,
  submissionId: string,
  payload: JourneyEventPayload
): void {
  try {
    const base =
      typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_API_GATEWAY_URL
        ? process.env.NEXT_PUBLIC_API_GATEWAY_URL
        : (typeof window !== 'undefined' && (window as any).__NEXT_PUBLIC_API_GATEWAY_URL) || 'http://localhost:8080'
    const url = `${base}/api/student/exams/${examId}/submissions/${submissionId}/journey/events`
    const body = JSON.stringify({
      eventType: payload.eventType,
      severity: payload.severity ?? 'INFO',
      metadata: payload.metadata ?? {}
    })
    if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
      navigator.sendBeacon(url, new Blob([body], { type: 'application/json' }))
    }
  } catch (_) {
    // Ignore
  }
}
