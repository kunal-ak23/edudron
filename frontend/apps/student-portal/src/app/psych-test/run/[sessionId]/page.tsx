'use client'

import { useEffect, useMemo, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute, Button } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { getApiClient } from '@/lib/api'

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
}

const DISCLAIMER = 'This guidance is for educational and career planning purposes only. It is guidance, not diagnosis.'

export default function PsychTestRunnerPage() {
  const params = useParams()
  const router = useRouter()
  const sessionId = params.sessionId as string

  const [q, setQ] = useState<NextQuestion | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedOptionId, setSelectedOptionId] = useState<string>('')
  const [text, setText] = useState<string>('')

  const progressPct = useMemo(() => {
    if (!q) return 0
    return Math.min(100, Math.max(0, Math.round((q.currentQuestionNumber / (q.totalQuestions || 30)) * 100)))
  }, [q])

  const loadNext = async () => {
    try {
      setLoading(true)
      setError(null)
      const apiClient = getApiClient()
      const resp = await apiClient.get(`/api/psych-test/sessions/${sessionId}/next-question`)
      const data = (resp && typeof resp === 'object' && 'data' in (resp as any)) ? (resp as any).data : resp
      setQ(data)
      setSelectedOptionId('')
      setText('')
    } catch (e: any) {
      console.error(e)
      setError('Failed to load next question.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (sessionId) loadNext()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId])

  const submit = async () => {
    if (!q || !q.questionId) return
    if ((q.type === 'OPEN_ENDED' && !text.trim()) || (q.type !== 'OPEN_ENDED' && !selectedOptionId)) {
      setError('Please answer before continuing.')
      return
    }

    try {
      setLoading(true)
      setError(null)
      const apiClient = getApiClient()
      await apiClient.post(`/api/psych-test/sessions/${sessionId}/answers`, {
        questionId: q.questionId,
        selectedOptionId: q.type === 'OPEN_ENDED' ? null : selectedOptionId,
        text: q.type === 'OPEN_ENDED' ? text.trim() : null,
        timeSpentMs: null
      })
      await loadNext()
    } catch (e: any) {
      console.error(e)
      setError('Failed to submit answer.')
    } finally {
      setLoading(false)
    }
  }

  const complete = async () => {
    try {
      setLoading(true)
      setError(null)
      const apiClient = getApiClient()
      await apiClient.post(`/api/psych-test/sessions/${sessionId}/complete`)
      router.push(`/psych-test/results/${sessionId}`)
    } catch (e: any) {
      console.error(e)
      setError('Failed to complete the test.')
    } finally {
      setLoading(false)
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

            {!q ? (
              <div className="bg-white rounded-lg shadow-md p-8 text-center">
                <div className="text-gray-600">{loading ? 'Loading…' : 'No question loaded.'}</div>
              </div>
            ) : q.questionId == null ? (
              <div className="bg-white rounded-lg shadow-md p-8">
                <h1 className="text-xl font-bold text-gray-900 mb-2">You can finish now</h1>
                <p className="text-gray-600 mb-4">
                  {q.canStopEarly ? 'Your results look stable enough to stop early.' : 'You have reached the end of the question set.'}
                </p>
                <div className="flex justify-end">
                  <Button onClick={complete} disabled={loading}>Complete & View Results</Button>
                </div>
              </div>
            ) : (
              <div className="bg-white rounded-lg shadow-md p-8">
                <div className="mb-6">
                  <div className="flex items-center justify-between text-sm text-gray-600 mb-2">
                    <div>Question {q.currentQuestionNumber} of {q.totalQuestions}</div>
                    <div>{progressPct}%</div>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div className="bg-primary-600 h-2 rounded-full" style={{ width: `${progressPct}%` }} />
                  </div>
                </div>

                <div className="text-xl font-semibold text-gray-900 mb-4">{q.prompt}</div>

                {q.type !== 'OPEN_ENDED' && (
                  <div className="space-y-2 mb-6">
                    {q.options?.map((o) => (
                      <button
                        key={o.id}
                        onClick={() => setSelectedOptionId(o.id)}
                        disabled={loading}
                        className={`w-full text-left p-4 rounded-lg border ${
                          selectedOptionId === o.id ? 'border-primary-600 bg-primary-50' : 'border-gray-200 hover:bg-gray-50'
                        }`}
                      >
                        {o.label}
                      </button>
                    ))}
                  </div>
                )}

                {q.type === 'OPEN_ENDED' && (
                  <div className="mb-6">
                    <textarea
                      value={text}
                      onChange={(e) => setText(e.target.value)}
                      disabled={loading}
                      rows={4}
                      maxLength={500}
                      className="w-full border border-gray-300 rounded-lg p-3 focus:outline-none focus:ring-2 focus:ring-primary-500"
                      placeholder="Write a short answer…"
                    />
                    <div className="text-xs text-gray-500 mt-1">{text.length}/500</div>
                  </div>
                )}

                <div className="flex items-center justify-between">
                  <div className="text-sm text-gray-600">
                    {q.canStopEarly ? 'You may be able to stop early soon.' : 'Keep going.'}
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" onClick={complete} disabled={loading || !q.canStopEarly}>
                      Stop Early
                    </Button>
                    <Button onClick={submit} disabled={loading}>
                      Next
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}

