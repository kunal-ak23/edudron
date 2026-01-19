'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute, Button } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { getApiClient } from '@/lib/api'
import { RiasecBarChart } from '@/components/psych-test/RiasecBarChart'
import { RecommendedCourses, RecommendedCourse } from '@/components/psych-test/RecommendedCourses'

export const dynamic = 'force-dynamic'

interface ResultResponse {
  id: string
  sessionId: string
  overallConfidence: string
  domainScores: Record<string, { score?: number; confidence?: number; answered_primary?: number }>
  topDomains: string[] | any
  streamSuggestion?: string
  careerFields?: string[] | any
  recommendedCourses?: RecommendedCourse[] | any
  reportText?: string | null
  answerBreakdown?: AnswerBreakdownItem[]
  domainNarratives?: Record<string, string> | any
  suggestions?: SuggestionsExplanation | null
  createdAt?: string
}

interface AnswerBreakdownItem {
  index: number
  questionId?: string | null
  questionType?: string | null
  prompt?: string | null
  selectedOptionId?: string | null
  selectedLabel?: string | null
  optionValue?: number | null
  reverseScored?: boolean | null
  weight?: number | null
  impactedDomains?: string[] | null
  scoreDelta0To100?: Record<string, number> | null
  scoreAfter0To100?: Record<string, number> | null
  text?: string | null
  affectsRiasecScores?: boolean | null
  meaning?: string | null
}

interface SuggestionsExplanation {
  streamSuggestion?: string | null
  streamReason?: string | null
  primaryCareerPaths?: { title: string; reason?: string | null }[] | null
  alternateCareerPaths?: { title: string; reason?: string | null }[] | null
  roleModelsInAlignedFields?: string[] | null
}

export default function PsychTestResultsPage() {
  const params = useParams()
  const router = useRouter()
  const sessionId = params.sessionId as string

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<ResultResponse | null>(null)
  const [isPolling, setIsPolling] = useState(false)
  const retryTimerRef = useRef<number | null>(null)

  const parsedReport = useMemo(() => {
    if (!result?.reportText) return null
    try {
      return JSON.parse(result.reportText)
    } catch {
      return null
    }
  }, [result?.reportText])

  const topDomainsLabel = useMemo(() => {
    const t = result?.topDomains
    if (Array.isArray(t)) return t.join('')
    try {
      if (t && typeof t === 'object' && 'length' in t) return String(t)
    } catch {}
    return ''
  }, [result?.topDomains])

  useEffect(() => {
    let cancelled = false

    const clearRetry = () => {
      if (retryTimerRef.current != null) {
        window.clearTimeout(retryTimerRef.current)
        retryTimerRef.current = null
      }
    }

    const isResultNotReadyError = (e: any) => {
      const status = e?.response?.status
      const message =
        e?.response?.data?.message ||
        e?.response?.data?.error ||
        e?.message ||
        ''

      // Depending on backend exception mapping, "Result not found" may surface as 400/404.
      if (status === 404) return true
      if (status === 400 && String(message).toLowerCase().includes('result')) return true
      return false
    }

    const load = async (attempt: number) => {
      try {
        if (attempt === 0) {
          setLoading(true)
          setError(null)
          setIsPolling(false)
        }
        const apiClient = getApiClient()
        const resp = await apiClient.get(`/api/psych-test/sessions/${sessionId}/result`, { timeout: 60000 })
        const data = (resp && typeof resp === 'object' && 'data' in (resp as any)) ? (resp as any).data : resp
        if (!cancelled) {
          setResult(data)
          setIsPolling(false)
        }
      } catch (e: any) {
        console.error(e)
        if (cancelled) return

        // If results are still being generated, poll briefly instead of failing.
        const maxAttempts = 20 // ~60s at 3s intervals
        if (isResultNotReadyError(e) && attempt < maxAttempts) {
          setIsPolling(true)
          clearRetry()
          retryTimerRef.current = window.setTimeout(() => {
            load(attempt + 1)
          }, 3000)
          return
        }

        setError(isResultNotReadyError(e) ? 'Results are taking longer than usual. Please try again.' : 'Failed to load results.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    if (sessionId) load(0)
    return () => {
      cancelled = true
      clearRetry()
    }
  }, [sessionId])

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="max-w-4xl mx-auto">
            <div className="mb-6">
              <h1 className="text-3xl font-bold text-gray-900 mb-2">Your Results</h1>
              <p className="text-gray-600">Adaptive RIASEC summary and curated course recommendations.</p>
            </div>

            {loading && (
              <div className="bg-white rounded-lg shadow-md p-8 text-center text-gray-600">Loading…</div>
            )}

            {!loading && isPolling && (
              <div className="bg-white rounded-lg shadow-md p-8 text-center">
                <div className="flex items-center justify-center gap-3 text-gray-700">
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary-600" />
                  <div className="font-medium">Generating your results…</div>
                </div>
                <div className="text-sm text-gray-500 mt-2">This can take up to a minute.</div>
              </div>
            )}

            {!loading && (error || !result) && (
              <div className="bg-white rounded-lg shadow-md p-8 text-center">
                <div className="text-gray-700 mb-4">{error || 'Results not found.'}</div>
                <div className="flex justify-center gap-3">
                  <Button variant="outline" onClick={() => router.push('/psych-test')}>Back</Button>
                  <Button onClick={() => router.push('/psych-test/results')}>Past Results</Button>
                </div>
              </div>
            )}

            {!loading && result && (
              <div className="space-y-6">
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-yellow-800 text-sm">
                  <div className="font-semibold mb-1">Disclaimer</div>
                  <div>{parsedReport?.disclaimer || 'guidance, not diagnosis'}</div>
                </div>

                <div className="bg-white rounded-lg shadow-md p-6">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="text-sm text-gray-600">Overall confidence</div>
                      <div className="text-2xl font-bold text-gray-900">{result.overallConfidence}</div>
                    </div>
                    {result.streamSuggestion && (
                      <div className="text-right">
                        <div className="text-sm text-gray-600">Suggested stream</div>
                        <div className="text-2xl font-bold text-primary-700">{result.streamSuggestion}</div>
                      </div>
                    )}
                  </div>
                  {topDomainsLabel && (
                    <div className="mt-3 text-sm text-gray-600">
                      Top domains: <span className="font-semibold text-gray-900">{topDomainsLabel}</span>
                    </div>
                  )}
                </div>

                <div className="bg-white rounded-lg shadow-md p-6">
                  <h2 className="text-xl font-semibold text-gray-900 mb-4">RIASEC scores</h2>
                  <RiasecBarChart domainScores={result.domainScores || null} />
                </div>

                {(parsedReport?.summaryLong || parsedReport?.summary) && (
                  <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold text-gray-900 mb-2">Summary</h2>
                    <p className="text-gray-700 whitespace-pre-wrap">{parsedReport?.summaryLong || parsedReport?.summary}</p>
                  </div>
                )}

                {result.suggestions && (
                  <div className="bg-white rounded-lg shadow-md p-6 space-y-4">
                    <div>
                      <h2 className="text-xl font-semibold text-gray-900 mb-2">Why these suggestions</h2>
                      {result.suggestions.streamReason && (
                        <p className="text-gray-700 whitespace-pre-wrap">{result.suggestions.streamReason}</p>
                      )}
                    </div>

                    {Array.isArray(result.suggestions.primaryCareerPaths) && result.suggestions.primaryCareerPaths.length > 0 && (
                      <div>
                        <h3 className="text-lg font-semibold text-gray-900 mb-2">Primary career paths</h3>
                        <div className="space-y-2">
                          {result.suggestions.primaryCareerPaths.map((c, idx) => (
                            <div key={idx} className="border border-gray-200 rounded-lg p-3">
                              <div className="font-semibold text-gray-900">{c.title}</div>
                              {c.reason && <div className="text-sm text-gray-700 mt-1">{c.reason}</div>}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {Array.isArray(result.suggestions.alternateCareerPaths) && result.suggestions.alternateCareerPaths.length > 0 && (
                      <div>
                        <h3 className="text-lg font-semibold text-gray-900 mb-2">Alternate paths to explore</h3>
                        <div className="space-y-2">
                          {result.suggestions.alternateCareerPaths.map((c, idx) => (
                            <div key={idx} className="border border-gray-200 rounded-lg p-3">
                              <div className="font-semibold text-gray-900">{c.title}</div>
                              {c.reason && <div className="text-sm text-gray-700 mt-1">{c.reason}</div>}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {Array.isArray(result.suggestions.roleModelsInAlignedFields) && result.suggestions.roleModelsInAlignedFields.length > 0 && (
                      <div>
                        <h3 className="text-lg font-semibold text-gray-900 mb-2">Role models (in aligned fields)</h3>
                        <ul className="list-disc pl-5 space-y-1 text-gray-700">
                          {result.suggestions.roleModelsInAlignedFields.map((p, idx) => (
                            <li key={idx}>{p}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}

                {Array.isArray(result.careerFields) && result.careerFields.length > 0 && (
                  <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold text-gray-900 mb-4">Suggested career fields</h2>
                    <div className="flex flex-wrap gap-2">
                      {(result.careerFields as any[]).map((c, idx) => (
                        <span key={idx} className="px-3 py-1 rounded-full bg-gray-100 text-gray-700 text-sm">
                          {String(c)}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {Array.isArray(result.recommendedCourses) && result.recommendedCourses.length > 0 && (
                  <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold text-gray-900 mb-4">Recommended courses</h2>
                    <RecommendedCourses courses={result.recommendedCourses as any} />
                  </div>
                )}

                {Array.isArray(result.answerBreakdown) && result.answerBreakdown.length > 0 && (
                  <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold text-gray-900 mb-2">Your answers and how they affected your RIASEC scores</h2>
                    <p className="text-sm text-gray-600 mb-4">
                      We compute RIASEC deterministically. Likert + scenario answers affect scores; open-ended answers are stored as context and do not change scores.
                    </p>

                    <div className="space-y-3">
                      {result.answerBreakdown.map((a) => {
                        const impacted = Array.isArray(a.impactedDomains) ? a.impactedDomains : []
                        const delta = a.scoreDelta0To100 || {}
                        const deltaText = impacted
                          .map((d) => {
                            const v = Number(delta[d] ?? 0)
                            const sign = v >= 0 ? '+' : ''
                            return `${d} ${sign}${v.toFixed(1)}`
                          })
                          .join(' · ')

                        const selection =
                          a.questionType === 'OPEN_ENDED'
                            ? (a.text ? `"${a.text}"` : '(no text)')
                            : (a.selectedLabel || a.selectedOptionId || '(no selection)')

                        return (
                          <div key={`${a.questionId ?? 'q'}-${a.index}`} className="border border-gray-200 rounded-lg p-4">
                            <div className="text-xs text-gray-500 mb-1">Q{a.index}{a.questionType ? ` · ${a.questionType}` : ''}</div>
                            <div className="font-medium text-gray-900">{a.prompt || a.questionId}</div>
                            <div className="mt-2 text-sm text-gray-700">
                              <span className="font-semibold">Your answer:</span> {selection}
                            </div>
                            {a.meaning && (
                              <div className="mt-2 text-sm text-gray-700">
                                <span className="font-semibold">What this indicates:</span> {a.meaning}
                              </div>
                            )}
                            {impacted.length > 0 && (
                              <div className="mt-2 text-sm text-gray-700">
                                <span className="font-semibold">Impacted domains:</span> {impacted.join(', ')}
                              </div>
                            )}
                            {deltaText && impacted.length > 0 && (
                              <div className="mt-2 text-sm text-gray-600">
                                <span className="font-semibold">Score change:</span> {deltaText}
                              </div>
                            )}
                            {(a.affectsRiasecScores !== false) && (typeof a.weight === 'number' || typeof a.reverseScored === 'boolean') && (
                              <div className="mt-2 text-xs text-gray-500">
                                {typeof a.weight === 'number' ? `Weight ${a.weight}` : ''}
                                {typeof a.reverseScored === 'boolean' ? `${typeof a.weight === 'number' ? ' · ' : ''}${a.reverseScored ? 'reverse-scored' : 'direct-scored'}` : ''}
                              </div>
                            )}
                            {a.affectsRiasecScores === false && (
                              <div className="mt-2 text-xs text-gray-500">Not scored (does not change RIASEC scores)</div>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}

                {result.domainNarratives && (
                  <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold text-gray-900 mb-2">What each RIASEC domain means for you</h2>
                    <p className="text-sm text-gray-600 mb-4">
                      These explanations are generated once at completion and stored with your result (not re-generated on every page load).
                    </p>

                    {typeof result.domainNarratives?.overall_summary === 'string' && (
                      <div className="border border-gray-200 rounded-lg p-4 mb-4">
                        <div className="font-semibold text-gray-900 mb-1">Overall summary</div>
                        <div className="text-sm text-gray-700 whitespace-pre-wrap">{result.domainNarratives.overall_summary}</div>
                      </div>
                    )}

                    <div className="space-y-3">
                      {(['R', 'I', 'A', 'S', 'E', 'C'] as const).map((k) => {
                        const text = result.domainNarratives?.[k]
                        if (!text) return null
                        return (
                          <div key={k} className="border border-gray-200 rounded-lg p-4">
                            <div className="font-semibold text-gray-900 mb-1">{k}</div>
                            <div className="text-sm text-gray-700 whitespace-pre-wrap">{String(text)}</div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}

                <div className="flex justify-center gap-3">
                  <Button variant="outline" onClick={() => router.push('/psych-test/results')}>Past Results</Button>
                  <Button variant="outline" onClick={() => router.push('/psych-test')}>Take Again</Button>
                  <Button onClick={() => router.push('/')}>Go Home</Button>
                </div>
              </div>
            )}
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}

