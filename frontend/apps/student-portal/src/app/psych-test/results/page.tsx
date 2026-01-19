'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, Button } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { getApiClient } from '@/lib/api'

type PastResult = {
  sessionId: string
  completedAt?: string | null
  overallConfidence?: string | null
  topDomains?: string[] | null
  streamSuggestion?: string | null
  createdAt?: string | null
}

function formatDate(value?: string | null) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString()
}

export default function PsychTestPastResultsPage() {
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [items, setItems] = useState<PastResult[]>([])

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true)
        setError(null)
        const apiClient = getApiClient()
        const resp = await apiClient.get(`/api/psych-test/results?limit=50`)
        const data = (resp && typeof resp === 'object' && 'data' in (resp as any)) ? (resp as any).data : resp
        setItems(Array.isArray(data) ? (data as PastResult[]) : [])
      } catch (e: any) {
        console.error(e)
        setError('Failed to load past results.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const empty = useMemo(() => !loading && !error && items.length === 0, [loading, error, items.length])

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="max-w-4xl mx-auto">
            <div className="mb-6 flex items-center justify-between gap-3">
              <div>
                <h1 className="text-3xl font-bold text-gray-900 mb-1">Past Results</h1>
                <p className="text-gray-600">Your completed Psych Test (v2) sessions.</p>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => router.push('/psych-test')}>
                  Take Test
                </Button>
              </div>
            </div>

            {loading && (
              <div className="bg-white rounded-lg shadow-md p-8 text-center text-gray-600">Loading…</div>
            )}

            {!loading && error && (
              <div className="bg-white rounded-lg shadow-md p-8 text-center">
                <div className="text-gray-700 mb-4">{error}</div>
                <Button onClick={() => window.location.reload()}>Retry</Button>
              </div>
            )}

            {empty && (
              <div className="bg-white rounded-lg shadow-md p-8 text-center">
                <div className="text-gray-700 mb-2">No past results yet.</div>
                <div className="text-sm text-gray-500 mb-4">Complete a test to see results here.</div>
                <Button onClick={() => router.push('/psych-test')}>Take Test</Button>
              </div>
            )}

            {!loading && !error && items.length > 0 && (
              <div className="space-y-3">
                {items.map((r) => {
                  const when = r.completedAt || r.createdAt
                  const top = Array.isArray(r.topDomains) ? r.topDomains.join('') : ''
                  return (
                    <button
                      key={r.sessionId}
                      onClick={() => router.push(`/psych-test/results/${r.sessionId}`)}
                      className="w-full text-left bg-white rounded-lg shadow-md border border-gray-100 p-5 hover:border-primary-200 hover:shadow-lg transition"
                    >
                      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                        <div>
                          <div className="text-sm text-gray-500">Session</div>
                          <div className="font-semibold text-gray-900">{r.sessionId}</div>
                          {when && (
                            <div className="text-sm text-gray-600 mt-1">
                              Completed: {formatDate(when)}
                            </div>
                          )}
                        </div>

                        <div className="sm:text-right">
                          {top && (
                            <div className="text-sm text-gray-600">
                              Top domains: <span className="font-semibold text-gray-900">{top}</span>
                            </div>
                          )}
                          {r.overallConfidence && (
                            <div className="text-sm text-gray-600 mt-1">
                              Confidence: <span className="font-semibold text-gray-900">{r.overallConfidence}</span>
                            </div>
                          )}
                          {r.streamSuggestion && (
                            <div className="text-sm text-gray-600 mt-1">
                              Stream: <span className="font-semibold text-primary-700">{r.streamSuggestion}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}

