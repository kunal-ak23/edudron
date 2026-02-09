'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, Button } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { getApiClient } from '@/lib/api'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

export default function PsychTestStartPage() {
  const router = useRouter()
  const { needsTenantSelection } = useAuth()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (needsTenantSelection) {
      router.replace('/select-tenant')
      setLoading(false)
      return
    }
    startOrResume()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const startOrResume = async () => {
    try {
      setLoading(true)
      setError(null)

      const apiClient = getApiClient()
      const resp = await apiClient.post('/api/psych-test/sessions', { grade: 10, locale: 'en', maxQuestions: 30 })

      const data = (resp && typeof resp === 'object' && 'data' in (resp as any)) ? (resp as any).data : resp
      const sessionId = data?.sessionId || data?.id

      if (!sessionId) {
        setError('Failed to start the test.')
        return
      }

      router.push(`/psych-test/run/${sessionId}`)
    } catch (e: any) {
      setError('Failed to start the test. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="max-w-2xl mx-auto bg-white rounded-lg shadow-md p-8">
            <h1 className="text-2xl font-bold text-gray-900 mb-2">Psychometric Test (v2)</h1>
            <p className="text-gray-600 mb-6">
              Starting your adaptive RIASEC test…
            </p>

            {error && (
              <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
                {error}
              </div>
            )}

            <div className="flex items-center justify-between">
              <div className="text-sm text-gray-500">
                {loading ? 'Loading…' : 'Ready'}
              </div>
              <Button onClick={startOrResume} disabled={loading}>
                {loading ? 'Starting…' : 'Retry'}
              </Button>
            </div>
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}

