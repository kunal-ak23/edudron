'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button, Card, ProtectedRoute } from '@kunal-ak23/edudron-ui-components'

export const dynamic = 'force-dynamic'

export default function SelectTenantPage() {
  const router = useRouter()
  const { user, availableTenants, selectTenant, needsTenantSelection } = useAuth()
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const tenants = useMemo(() => availableTenants ?? [], [availableTenants])

  useEffect(() => {
    // If user doesn't need selection anymore, just continue.
    if (!needsTenantSelection) {
      router.push('/courses')
      return
    }
  }, [needsTenantSelection, router])

  useEffect(() => {
    // Auto-select if only one tenant option exists
    if (tenants.length === 1 && !submitting) {
      setSelectedTenantId(tenants[0].id)
    }
  }, [tenants, submitting])

  const handleContinue = async () => {
    if (!selectedTenantId || submitting) return
    setSubmitting(true)
    setError('')
    try {
      await selectTenant(selectedTenantId)
      router.push('/courses')
    } catch (e: any) {
      setError(e?.message || 'Failed to select tenant')
      setSubmitting(false)
    }
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 via-white to-primary-100 py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-2xl w-full space-y-6">
          <div className="text-center">
            <h2 className="text-3xl font-extrabold text-gray-900">Select your tenant</h2>
            <p className="mt-2 text-sm text-gray-600">
              {user?.email ? `Choose which tenant you want to study under as ${user.email}` : 'Choose which tenant you want to study under'}
            </p>
          </div>

          {tenants.length === 0 ? (
            <Card>
              <div className="p-6 text-center space-y-3">
                <p className="text-gray-700">No tenant options found for your account.</p>
                <Button onClick={() => router.push('/login')} className="w-full">
                  Back to login
                </Button>
              </div>
            </Card>
          ) : (
            <>
              {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl">
                  {error}
                </div>
              )}

              <div className="grid gap-4 md:grid-cols-2">
                {tenants.map((t) => (
                  <Card
                    key={t.id}
                    className={`cursor-pointer transition-all duration-200 hover:shadow-lg ${
                      selectedTenantId === t.id ? 'ring-2 ring-primary-500 bg-primary-50' : ''
                    }`}
                    onClick={() => setSelectedTenantId(t.id)}
                  >
                    <div className="p-6">
                      <div className="flex items-center justify-between mb-2">
                        <h3 className="text-lg font-semibold text-gray-900">{t.name}</h3>
                        {selectedTenantId === t.id && (
                          <span className="text-primary-600 text-sm font-semibold">Selected</span>
                        )}
                      </div>
                      <p className="text-sm text-gray-500">{t.slug}</p>
                      <div className="mt-4 flex items-center justify-between">
                        <span
                          className={`px-2 py-1 text-xs rounded ${
                            t.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                          }`}
                        >
                          {t.isActive ? 'Active' : 'Inactive'}
                        </span>
                        <span className="text-xs text-gray-400">ID: {t.id.slice(0, 8)}...</span>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>

              <div className="flex justify-center">
                <Button
                  onClick={handleContinue}
                  disabled={!selectedTenantId || submitting}
                  loading={submitting}
                  className="w-full md:w-auto px-10"
                >
                  Continue
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </ProtectedRoute>
  )
}

