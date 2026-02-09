'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute, Button } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { getApiClient } from '@/lib/api'
import { ThinkingPlaceholder } from '@/components/psych-test/ThinkingPlaceholder'
import { TypewriterText, usePrefersReducedMotion } from '@/components/psych-test/TypewriterText'

export const dynamic = 'force-dynamic'

type QuestionType = 'LIKERT' | 'SCENARIO_MCQ' | 'OPEN_ENDED'

interface NextQuestion {
  sessionId: string
  questionId: string | null
  type: QuestionType | null
  prompt: string | null
  options: Array<{ id: string; label: string }>
  currentQuestionNumber: number
  totalQuestions: number
  canStopEarly: boolean
  personalizationSource?: 'RAW' | 'TEMPLATE' | 'AI' | string
  askedId?: string | null
}

const DISCLAIMER = 'This guidance is for educational and career planning purposes only. It is guidance, not diagnosis.'

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

function Reveal({
  children,
  disabled
}: {
  children: ReactNode
  disabled?: boolean
}) {
  const [shown, setShown] = useState(Boolean(disabled))

  useEffect(() => {
    if (disabled) {
      setShown(true)
      return
    }
    // allow initial paint, then animate in
    const t = window.setTimeout(() => setShown(true), 10)
    return () => window.clearTimeout(t)
  }, [disabled])

  return (
    <div className={`transition-all duration-300 ${shown ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'}`}>
      {children}
    </div>
  )
}

export default function PsychTestRunnerPage() {
  const params = useParams()
  const router = useRouter()
  const sessionId = params.sessionId as string

  const [q, setQ] = useState<NextQuestion | null>(null)
  const [displayQ, setDisplayQ] = useState<NextQuestion | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedOptionId, setSelectedOptionId] = useState<string>('')
  const [text, setText] = useState<string>('')
  const [thinkingMode, setThinkingMode] = useState<'ai' | 'saving' | 'finalizing' | null>(null)

  const prefersReducedMotion = usePrefersReducedMotion()
  const [contentStage, setContentStage] = useState<'visible' | 'entering' | 'exiting'>('visible')
  const [promptDone, setPromptDone] = useState(false)
  const [visibleOptionCount, setVisibleOptionCount] = useState(0)
  const [typedOptionCount, setTypedOptionCount] = useState(0)
  const [showTextArea, setShowTextArea] = useState(false)
  const revealTimersRef = useRef<number[]>([])

  const progressPct = useMemo(() => {
    const base = displayQ ?? q
    if (!base) return 0
    return Math.min(100, Math.max(0, Math.round((base.currentQuestionNumber / (base.totalQuestions || 30)) * 100)))
  }, [displayQ, q])

  const loadNext = async () => {
    try {
      setLoading(true)
      setThinkingMode('ai')
      setError(null)
      const apiClient = getApiClient()
      const resp = await apiClient.get(`/api/psych-test/sessions/${sessionId}/next-question`)
      const data = (resp && typeof resp === 'object' && 'data' in (resp as any)) ? (resp as any).data : resp
      setQ(data)
      setDisplayQ(data)
      setSelectedOptionId('')
      setText('')
    } catch (e: any) {
      setError('Failed to load next question.')
    } finally {
      setLoading(false)
      setThinkingMode(null)
    }
  }

  useEffect(() => {
    if (sessionId) loadNext()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId])

  // Reset typing/reveal state whenever the displayed question changes.
  useEffect(() => {
    // clear scheduled reveal timers
    for (const t of revealTimersRef.current) window.clearTimeout(t)
    revealTimersRef.current = []

    if (!displayQ?.questionId) {
      setPromptDone(true)
      setVisibleOptionCount(0)
      setTypedOptionCount(0)
      setShowTextArea(false)
      return
    }

    setPromptDone(Boolean(prefersReducedMotion))
    setVisibleOptionCount(0)
    setTypedOptionCount(0)
    setShowTextArea(Boolean(prefersReducedMotion))

    if (!prefersReducedMotion) {
      setContentStage('entering')
      const t = window.setTimeout(() => setContentStage('visible'), 20)
      revealTimersRef.current.push(t)
    } else {
      setContentStage('visible')
    }
  }, [displayQ?.questionId, prefersReducedMotion])

  // Reveal options/textarea after prompt finishes typing.
  useEffect(() => {
    if (!displayQ?.questionId) return
    if (!promptDone) return

    if (prefersReducedMotion) {
      if (displayQ.type === 'OPEN_ENDED') setShowTextArea(true)
      else {
        const len = displayQ.options?.length ?? 0
        setVisibleOptionCount(len)
        setTypedOptionCount(len)
      }
      return
    }

    if (displayQ.type === 'OPEN_ENDED') {
      const t = window.setTimeout(() => setShowTextArea(true), 80)
      revealTimersRef.current.push(t)
      return
    }

    // Strict sequential: show option 1, then only show the next after the previous finishes typing.
    setVisibleOptionCount(1)
    setTypedOptionCount(0)
  }, [displayQ?.questionId, displayQ?.type, displayQ?.options?.length, promptDone, prefersReducedMotion])

  // When an option finishes typing, reveal the next option.
  useEffect(() => {
    if (!displayQ?.questionId) return
    if (prefersReducedMotion) return
    if (displayQ.type === 'OPEN_ENDED') return
    if (!promptDone) return

    const total = displayQ.options?.length ?? 0
    const nextVisible = Math.min(total, typedOptionCount + 1)
    if (nextVisible > visibleOptionCount) setVisibleOptionCount(nextVisible)
  }, [displayQ?.questionId, displayQ?.type, displayQ?.options?.length, prefersReducedMotion, promptDone, typedOptionCount, visibleOptionCount])

  const disableInputs = loading || contentStage === 'exiting'
  // Allow interaction once prompt is done (and textarea is visible for open-ended).
  const disableWhileTyping =
    !prefersReducedMotion &&
    displayQ?.questionId != null &&
    (!promptDone || (displayQ.type === 'OPEN_ENDED' && !showTextArea))
  const isDisabled = disableInputs || disableWhileTyping

  const submit = async () => {
    if (!displayQ || !displayQ.questionId) return
    if ((displayQ.type === 'OPEN_ENDED' && !text.trim()) || (displayQ.type !== 'OPEN_ENDED' && !selectedOptionId)) {
      setError('Please answer before continuing.')
      return
    }

    try {
      // Remove the answered question and show an in-place loader while we save + fetch next.
      if (!prefersReducedMotion) {
        setContentStage('exiting')
        await sleep(180)
      }
      setDisplayQ(null)
      setLoading(true)
      setThinkingMode('saving')
      setError(null)
      const apiClient = getApiClient()
      await apiClient.post(`/api/psych-test/sessions/${sessionId}/answers`, {
        questionId: displayQ.questionId,
        selectedOptionId: displayQ.type === 'OPEN_ENDED' ? null : selectedOptionId,
        text: displayQ.type === 'OPEN_ENDED' ? text.trim() : null,
        timeSpentMs: null
      })
      await loadNext()
    } catch (e: any) {
      setError('Failed to submit answer.')
      // Bring the question back so the student can retry.
      setDisplayQ(q)
      setContentStage('visible')
    } finally {
      setLoading(false)
      setThinkingMode(null)
    }
  }

  const complete = async () => {
    try {
      setLoading(true)
      setThinkingMode('finalizing')
      setError(null)
      const apiClient = getApiClient()
      // Completing can take longer because it may generate narratives/explanations.
      await apiClient.post(
        `/api/psych-test/sessions/${sessionId}/complete`,
        undefined,
        { timeout: 120000 }
      )
      router.push(`/psych-test/results/${sessionId}`)
    } catch (e: any) {
      // If completion took longer than the HTTP timeout, it may still succeed server-side.
      // Redirect to results page and let it poll until ready.
      const status = e?.response?.status
      const code = e?.code
      const msg = String(e?.message || '')
      const isTimeout = code === 'ECONNABORTED' || msg.toLowerCase().includes('timeout')
      if (isTimeout || status === 504) {
        setError('Finalizing is taking longer than usual. Redirecting to results…')
        router.push(`/psych-test/results/${sessionId}`)
        return
      }

      setError('Failed to complete the test.')
    } finally {
      setLoading(false)
      setThinkingMode(null)
    }
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="max-w-3xl mx-auto">
            <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg text-yellow-800 text-sm">
              <div className="font-semibold mb-1">Disclaimer</div>
              <div>{DISCLAIMER}</div>
            </div>

            {error && (
              <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
                {error}
              </div>
            )}

            {!displayQ ? (
              <div className="bg-white rounded-lg shadow-md p-8">
                {loading ? (
                  <>
                    {q && (
                      <div className="mb-6">
                        <div className="flex items-center justify-between text-sm text-gray-600 mb-2">
                          <div>Question {q.currentQuestionNumber} of {q.totalQuestions}</div>
                          <div>{progressPct}%</div>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div className="bg-primary-600 h-2 rounded-full" style={{ width: `${progressPct}%` }} />
                        </div>
                      </div>
                    )}
                    <ThinkingPlaceholder mode={thinkingMode ?? 'ai'} />
                  </>
                ) : (
                  <div className="text-center space-y-4">
                    <div className="text-gray-600">No question loaded.</div>
                    <div className="flex justify-center">
                      <Button onClick={loadNext} disabled={loading}>
                        Retry
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            ) : displayQ.questionId == null ? (
              <div className="bg-white rounded-lg shadow-md p-8">
                <h1 className="text-xl font-bold text-gray-900 mb-2">You can finish now</h1>
                <p className="text-gray-600 mb-4">
                  {displayQ.canStopEarly ? 'Your results look stable enough to stop early.' : 'You have reached the end of the question set.'}
                </p>
                <div className="flex justify-end">
                  <Button onClick={complete} disabled={loading}>Complete & View Results</Button>
                </div>
              </div>
            ) : (
              <div className="bg-white rounded-lg shadow-md p-8">
                <div className="mb-6">
                  <div className="flex items-center justify-between text-sm text-gray-600 mb-2">
                    <div>Question {displayQ.currentQuestionNumber} of {displayQ.totalQuestions}</div>
                    <div>{progressPct}%</div>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div className="bg-primary-600 h-2 rounded-full" style={{ width: `${progressPct}%` }} />
                  </div>
                </div>

                <div
                  className={`transition-all duration-200 ${
                    contentStage === 'entering'
                      ? 'opacity-0 translate-y-2 scale-[0.99]'
                      : contentStage === 'exiting'
                        ? 'opacity-0 -translate-y-1 scale-[0.99]'
                        : 'opacity-100 translate-y-0 scale-100'
                  }`}
                >
                  <TypewriterText
                    key={displayQ.questionId}
                    as="div"
                    className="text-xl font-semibold text-gray-900 mb-2"
                    text={displayQ.prompt || ''}
                    onDone={() => setPromptDone(true)}
                    speedMs={18}
                    startDelayMs={60}
                    cursor
                  />

                  {promptDone && displayQ.personalizationSource && displayQ.personalizationSource !== 'RAW' && (
                    <Reveal disabled={prefersReducedMotion}>
                      <div className="text-xs text-gray-500 mb-4">
                        Personalized{displayQ.personalizationSource === 'AI' ? ' with AI' : ''}.
                      </div>
                    </Reveal>
                  )}

                  {displayQ.type !== 'OPEN_ENDED' && promptDone && (
                    <div className="space-y-2 mb-6">
                      {(displayQ.options || []).slice(0, visibleOptionCount).map((o, idx) => {
                        const shownLabel = o.label
                        return (
                        <Reveal key={o.id} disabled={prefersReducedMotion}>
                          <button
                            onClick={() => setSelectedOptionId(o.id)}
                            disabled={isDisabled}
                            className={`w-full text-left p-4 rounded-lg border transition-colors ${
                              selectedOptionId === o.id
                                ? 'border-primary-600 bg-primary-50'
                                : 'border-gray-200 hover:bg-gray-50'
                            } ${isDisabled ? 'opacity-80 cursor-not-allowed' : ''}`}
                          >
                            <TypewriterText
                              key={`${displayQ.questionId}-${o.id}`}
                              text={shownLabel}
                              speedMs={10}
                              startDelayMs={0}
                              cursor={false}
                              onDone={() => setTypedOptionCount((c) => Math.max(c, idx + 1))}
                            />
                          </button>
                        </Reveal>
                        )
                      })}
                    </div>
                  )}

                  {displayQ.type === 'OPEN_ENDED' && (
                    <div className="mb-6">
                      {showTextArea && (
                        <Reveal disabled={prefersReducedMotion}>
                          <textarea
                            value={text}
                            onChange={(e) => setText(e.target.value)}
                            disabled={isDisabled}
                            rows={4}
                            maxLength={500}
                            className="w-full border border-gray-300 rounded-lg p-3 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:bg-gray-50 disabled:cursor-not-allowed"
                            placeholder="Write a short answer…"
                          />
                          <div className="text-xs text-gray-500 mt-1">{text.length}/500</div>
                        </Reveal>
                      )}
                    </div>
                  )}

                  <div className="flex items-center justify-between">
                    <div className="text-sm text-gray-600">
                      {displayQ.canStopEarly ? 'You may be able to stop early soon.' : 'Keep going.'}
                    </div>
                    <div className="flex gap-2">
                      <Button variant="outline" onClick={complete} disabled={loading || !displayQ.canStopEarly}>
                        Stop Early
                      </Button>
                      <Button onClick={submit} disabled={isDisabled}>
                        Next
                      </Button>
                    </div>
                  </div>

                  {(loading || thinkingMode) && displayQ.questionId && (
                    <div className="mt-4">
                      <Reveal disabled>
                        <div className="text-xs text-gray-500">
                          Tip: you can answer quickly—this adapts as you go.
                        </div>
                      </Reveal>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}

