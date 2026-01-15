'use client'

import { useState, useEffect } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { useAuth, FontSizeControl, TenantBrandingApi, TenantsApi } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'

interface StudentLayoutProps {
  children: React.ReactNode
}

export function StudentLayout({ children }: StudentLayoutProps) {
  const router = useRouter()
  const pathname = usePathname()
  const { user, logout, tenantId } = useAuth()
  const [tenantName, setTenantName] = useState<string>('EduDron')
  const [tenantLogo, setTenantLogo] = useState<string | null>(null)
  const [logoError, setLogoError] = useState(false)

  useEffect(() => {
    const loadTenantInfo = async () => {
      if (!tenantId || tenantId === 'PENDING_TENANT_SELECTION' || tenantId === 'SYSTEM') {
        setTenantName('EduDron')
        setTenantLogo(null)
        setLogoError(false)
        return
      }

      try {
        setLogoError(false) // Reset error state when loading new tenant
        const apiClient = getApiClient()
        const brandingApi = new TenantBrandingApi(apiClient)
        const tenantsApi = new TenantsApi(apiClient)

        // Fetch branding and tenant info in parallel
        const [branding, tenant] = await Promise.all([
          brandingApi.getBranding().catch(() => null),
          tenantsApi.getTenant(tenantId).catch(() => null)
        ])

        if (tenant?.name) {
          setTenantName(tenant.name)
        } else {
          setTenantName('EduDron')
        }

        if (branding?.logoUrl) {
          setTenantLogo(branding.logoUrl)
        } else {
          setTenantLogo(null)
        }
      } catch (error) {
        console.warn('[StudentLayout] Failed to load tenant info:', error)
        setTenantName('EduDron')
        setTenantLogo(null)
      }
    }

    loadTenantInfo()
  }, [tenantId])

  // Check if password reset is required (except on profile page)
  useEffect(() => {
    if (user?.passwordResetRequired && typeof window !== 'undefined' && pathname !== '/profile') {
      router.push('/profile')
    }
  }, [user, pathname, router])

  const handleLogout = async () => {
    try {
      await logout()
      router.push('/login')
    } catch (error) {
      console.error('Logout error:', error)
      // Force redirect even if logout fails
      localStorage.clear()
      router.push('/login')
    }
  }

  // Don't show header on login page
  if (pathname === '/login') {
    return <>{children}</>
  }

  return (
    <div className="h-screen bg-gray-50 flex flex-col overflow-hidden">
      {/* Header */}
      <header className="bg-white shadow-sm z-50 flex-shrink-0">
        <div className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12">
          <div className="flex justify-between items-center h-20">
            <div className="flex items-center space-x-6">
              <div
                className="flex items-center space-x-3 cursor-pointer"
                onClick={() => router.push('/courses')}
              >
                {tenantLogo && !logoError ? (
                  <div className="relative h-16 flex items-center">
                    <img
                      src={tenantLogo}
                      alt={tenantName}
                      className="h-16 w-auto max-w-[300px] object-contain"
                      onError={() => setLogoError(true)}
                    />
                  </div>
                ) : (
                  <h1 className="text-3xl font-bold text-primary-600">
                    {tenantName}
                  </h1>
                )}
              </div>
              <nav className="hidden md:flex space-x-6">
                <button
                  onClick={() => router.push('/courses')}
                  className={`font-medium transition-colors ${
                    pathname === '/courses'
                      ? 'text-primary-600'
                      : 'text-gray-700 hover:text-primary-600'
                  }`}
                >
                  Browse
                </button>
                <button
                  onClick={() => router.push('/my-courses')}
                  className={`font-medium transition-colors ${
                    pathname === '/my-courses'
                      ? 'text-primary-600'
                      : 'text-gray-700 hover:text-primary-600'
                  }`}
                >
                  My Courses
                </button>
                <button
                  onClick={() => router.push('/exams')}
                  className={`font-medium transition-colors ${
                    pathname?.startsWith('/exams')
                      ? 'text-primary-600'
                      : 'text-gray-700 hover:text-primary-600'
                  }`}
                >
                  Exams
                </button>
              </nav>
            </div>
            <div className="flex items-center space-x-4">
              {/* Font Size Control */}
              <FontSizeControl className="hidden sm:flex" />
              
              {user && (
                <div className="flex items-center space-x-3">
                  <div className="text-right hidden sm:block">
                    <p className="text-sm font-medium text-gray-900">
                      {user.name || user.email?.split('@')[0] || 'User'}
                    </p>
                    {user.email && (
                      <p className="text-xs text-gray-500">{user.email}</p>
                    )}
                  </div>
                  <button
                    onClick={() => router.push('/profile')}
                    className="w-8 h-8 rounded-full bg-primary-600 text-white flex items-center justify-center font-semibold cursor-pointer hover:bg-primary-700 transition-colors"
                    title="View Profile"
                  >
                    {user.name?.charAt(0).toUpperCase() || user.email?.charAt(0).toUpperCase() || 'U'}
                  </button>
                </div>
              )}
              <button
                onClick={handleLogout}
                className="px-4 py-2 text-sm font-medium text-gray-700 hover:text-primary-600 transition-colors"
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 overflow-y-auto">{children}</main>
    </div>
  )
}

