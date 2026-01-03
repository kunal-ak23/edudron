'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Button, Card } from '@edudron/ui-components'
import { tenantsApi } from '@/lib/api'
import { authService } from '@/lib/auth'
import type { Tenant, TenantInfo } from '@edudron/shared-utils'

export default function SelectTenantPage() {
  const router = useRouter()
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [user, setUser] = useState<any>(null)

  useEffect(() => {
    // Get user from localStorage
    const userStr = localStorage.getItem('user')
    if (userStr) {
      setUser(JSON.parse(userStr))
    }

    // Load available tenants
    loadTenants()
  }, [])

  const loadTenants = async () => {
    try {
      setLoading(true)
      const activeTenants = await tenantsApi.getActiveTenants()
      setTenants(activeTenants)
      
      // Auto-select if only one tenant
      if (activeTenants.length === 1) {
        handleTenantSelect(activeTenants[0].id)
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load tenants')
    } finally {
      setLoading(false)
    }
  }

  const handleTenantSelect = async (tenantId: string) => {
    if (submitting) return

    try {
      setSubmitting(true)
      setError('')

      await authService.selectTenant(tenantId)

      // Redirect to dashboard
      router.push('/dashboard')
    } catch (err: any) {
      setError(err.message || 'Failed to select tenant')
      setSubmitting(false)
    }
  }

  const handleNoTenants = () => {
    // For SYSTEM_ADMIN with no tenants, go to tenant management
    if (user?.role === 'SYSTEM_ADMIN') {
      router.push('/tenants')
    } else {
      router.push('/login')
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading tenants...</p>
        </div>
      </div>
    )
  }

  if (tenants.length === 0) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-md w-full">
          <div className="bg-white py-8 px-6 shadow-xl rounded-lg border-l-4 border-blue-500">
            <div className="text-center space-y-4">
              <h2 className="text-2xl font-bold text-gray-900">No Tenants Available</h2>
              <p className="text-gray-600">
                {user?.role === 'SYSTEM_ADMIN' 
                  ? 'You can create a new tenant to get started.'
                  : 'Please contact your administrator to set up a tenant for you.'}
              </p>
              {user?.role === 'SYSTEM_ADMIN' && (
                <Button onClick={handleNoTenants} className="w-full">
                  Create New Tenant
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-2xl w-full space-y-8">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-blue-100 mb-4">
            <svg className="h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
          </div>
          <h2 className="text-3xl font-extrabold text-gray-900">
            Select Your Workspace
          </h2>
          <p className="mt-2 text-sm text-gray-600">
            {user?.email ? `Choose which tenant you want to access as ${user.email}` : 'Choose which tenant you want to access'}
          </p>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
            {error}
          </div>
        )}

        <div className="grid gap-4 md:grid-cols-2">
          {tenants.map((tenant) => (
            <Card
              key={tenant.id}
              className={`cursor-pointer transition-all duration-200 hover:shadow-lg ${
                selectedTenantId === tenant.id 
                  ? 'ring-2 ring-blue-500 bg-blue-50' 
                  : ''
              }`}
              onClick={() => setSelectedTenantId(tenant.id)}
            >
              <div className="p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-lg font-semibold text-gray-900">{tenant.name}</h3>
                  {selectedTenantId === tenant.id && (
                    <svg className="h-5 w-5 text-blue-600" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                  )}
                </div>
                <p className="text-sm text-gray-500 mb-4">{tenant.slug}</p>
                <div className="flex items-center justify-between">
                  <span className={`px-2 py-1 text-xs rounded ${
                    tenant.isActive 
                      ? 'bg-green-100 text-green-800' 
                      : 'bg-gray-100 text-gray-800'
                  }`}>
                    {tenant.isActive ? 'Active' : 'Inactive'}
                  </span>
                  <span className="text-xs text-gray-400">
                    ID: {tenant.id.slice(0, 8)}...
                  </span>
                </div>
              </div>
            </Card>
          ))}
        </div>

        <div className="flex justify-center">
          <Button 
            onClick={() => selectedTenantId && handleTenantSelect(selectedTenantId)}
            disabled={!selectedTenantId || submitting}
            className="px-8 py-2"
            loading={submitting}
          >
            Continue
          </Button>
        </div>
      </div>
    </div>
  )
}

