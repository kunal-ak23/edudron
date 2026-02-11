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

/** Throttle interval for QUESTION_NAVIGATED to avoid flooding the API (ms). */
const QUESTION_NAV_THROTTLE_MS = 2500

let questionNavPending: {
  examId: string
  submissionId: string
  metadata: Record<string, unknown>
} | null = null
let questionNavTimer: ReturnType<typeof setTimeout> | null = null

/**
 * Log QUESTION_NAVIGATED at most once per QUESTION_NAV_THROTTLE_MS.
 * Rapid prev/next clicks send a single event with the latest metadata after the throttle window.
 */
export function logJourneyEventQuestionNavThrottled(
  examId: string,
  submissionId: string,
  metadata: Record<string, unknown>
): void {
  const payload = { eventType: 'QUESTION_NAVIGATED', severity: 'INFO' as const, metadata }
  questionNavPending = { examId, submissionId, metadata }

  const flush = () => {
    if (questionNavPending) {
      logJourneyEvent(examId, submissionId, {
        eventType: 'QUESTION_NAVIGATED',
        severity: 'INFO',
        metadata: questionNavPending.metadata
      })
      questionNavPending = null
    }
    questionNavTimer = null
  }

  if (questionNavTimer !== null) {
    return // already scheduled
  }
  questionNavTimer = setTimeout(flush, QUESTION_NAV_THROTTLE_MS)
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
