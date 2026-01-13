'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Button, Input } from '@kunal-ak23/edudron-ui-components'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { TenantSelection } from '@/components/TenantSelection'
import type { LoginCredentials } from '@kunal-ak23/edudron-shared-utils'

export default function LoginPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const { login, selectTenant, user, needsTenantSelection, availableTenants, tenantId, isLoading: authLoading } = useAuth()

  // If authenticated and no tenant selection needed, redirect
  // Use useEffect to avoid calling router.push during render
  // MUST be called before any conditional returns to follow Rules of Hooks
  useEffect(() => {
    // Don't redirect if we're still loading or if tenant selection is needed
    if (authLoading || needsTenantSelection) {
      return
    }

    if (user && !needsTenantSelection) {
      // Check if password reset is required - redirect to profile page
      if (user.passwordResetRequired) {
        router.push('/profile')
        return
      }
      
      // Check if tenant is actually selected (not PENDING_TENANT_SELECTION)
      const tenantId = localStorage.getItem('tenant_id') || user.tenantId
      const isPending = tenantId === 'PENDING_TENANT_SELECTION' || tenantId === 'SYSTEM' || !tenantId
      
      // Redirect STUDENT users to student portal
      if (user.role === 'STUDENT') {
        const studentPortalUrl = typeof window !== 'undefined' 
          ? (window.location.origin.includes('localhost') 
              ? 'http://localhost:3001' 
              : window.location.origin.replace('admin', 'student').replace('dashboard', 'portal'))
          : 'http://localhost:3001'
        window.location.href = studentPortalUrl
        return
      }
      
      // For SYSTEM_ADMIN, allow access even without tenant (they can select from top bar)
      if (user.role === 'SYSTEM_ADMIN') {
        // Only redirect if we're on login page and have a valid tenant
        if (!isPending && window.location.pathname === '/login') {
          router.push('/tenants')
        }
      } else if (!isPending) {
        // For other users, require tenant
        router.push('/dashboard')
      }
    }
  }, [user, needsTenantSelection, authLoading, router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)

    try {
      const credentials: LoginCredentials = { email, password }
      const response = await login(credentials)
      // The AuthContext will handle setting needsTenantSelection
      // If password reset is required, redirect will happen in useEffect
    } catch (err: any) {
      const errorMessage = err.message || 'Login failed. Please try again.'
      setError(errorMessage)
    } finally {
      setIsLoading(false)
    }
  }

  const handleTenantSelect = async (tenantId: string) => {
    if (!user) return

    try {
      await selectTenant(tenantId)
      
      // Redirect based on user role
      if (user.role === 'SYSTEM_ADMIN') {
        router.push('/tenants')
      } else {
        router.push('/dashboard')
      }
    } catch (error) {
      console.error('Error selecting tenant:', error)
      setError('Failed to select tenant')
    }
  }

  const handleNoTenants = () => {
    if (!user) return

    // For SYSTEM_ADMIN with no tenants, go directly to tenant management
    if (user.role === 'SYSTEM_ADMIN') {
      router.push('/tenants')
    }
  }

  // Show tenant selection if we have user that needs tenant selection
  // Check this FIRST before any redirects
  // For SYSTEM_ADMIN, show tenant selection if availableTenants exist and no tenant is selected
  const currentTenantId = tenantId || localStorage.getItem('tenant_id')
  const hasValidTenant = currentTenantId && 
                         currentTenantId !== 'PENDING_TENANT_SELECTION' && 
                         currentTenantId !== 'SYSTEM' &&
                         currentTenantId !== 'null' &&
                         currentTenantId !== ''
  
  if (user && (needsTenantSelection || (user.role === 'SYSTEM_ADMIN' && availableTenants && availableTenants.length > 0 && !hasValidTenant))) {
    // If no tenants available, show option to proceed to system management
    if (!availableTenants || availableTenants.length === 0) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-red-50 to-orange-100 py-12 px-4 sm:px-6 lg:px-8">
          <div className="max-w-md w-full space-y-8">
            <div className="text-center">
              <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-red-100 mb-4">
                <span className="text-2xl font-bold text-red-600">⚡</span>
              </div>
              <h2 className="text-3xl font-extrabold text-gray-900">
                No Tenants Available
              </h2>
              <p className="mt-2 text-sm text-gray-600">
                No tenants are currently available in the system.
              </p>
            </div>

            <div className="bg-white py-8 px-6 shadow-xl rounded-lg border-l-4 border-red-500">
              <div className="text-center space-y-4">
                <div className="text-gray-600">
                  <p className="mb-4">As a System Administrator, you can:</p>
                  <ul className="text-left space-y-2 text-sm">
                    <li>• Create new tenants</li>
                    <li>• Manage system users</li>
                    <li>• Configure system settings</li>
                    <li>• Monitor system health</li>
                  </ul>
                </div>

                <button
                  onClick={handleNoTenants}
                  className="w-full bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded-md transition-colors"
                >
                  Proceed to System Management
                </button>
              </div>
            </div>
          </div>
        </div>
      )
    }

    return (
      <TenantSelection
        tenants={availableTenants}
        onTenantSelect={handleTenantSelect}
        userEmail={user.email || ''}
        title="Select Working Tenant"
        subtitle="Choose which tenant you want to work with as a System Administrator"
      />
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-blue-100 mb-4">
            <span className="text-2xl font-bold text-blue-600">E</span>
          </div>
          <h2 className="text-3xl font-extrabold text-gray-900">
            Admin Sign In
          </h2>
          <p className="mt-2 text-sm text-gray-600">
            Access your EduDron admin dashboard
          </p>
        </div>

        <div className="bg-white py-8 px-6 shadow-xl rounded-lg">
          <form className="space-y-6" onSubmit={handleSubmit}>
            <div className="space-y-4">
              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                  Email Address
                </label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="Enter your email"
                />
              </div>

              <div>
                <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                  Password
                </label>
                <input
                  id="password"
                  name="password"
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="Enter your password"
                />
              </div>
            </div>

            {error && (
              <div className="text-red-600 text-sm text-center">
                {error}
              </div>
            )}

            <div>
              <button
                type="submit"
                disabled={isLoading}
                className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
              >
                {isLoading ? 'Signing in...' : 'Sign in'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

