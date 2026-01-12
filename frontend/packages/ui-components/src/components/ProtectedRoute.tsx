'use client'

import React, { useEffect, useState } from 'react'

export interface ProtectedRouteProps {
  children: React.ReactNode
  requiredRoles?: string[]
  redirectTo?: string
}

function ProtectedRoute({ 
  children, 
  requiredRoles,
  redirectTo = '/login' 
}: ProtectedRouteProps) {
  const [isAuthorized, setIsAuthorized] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [mounted, setMounted] = useState(false)

  // Ensure we're on client side
  useEffect(() => {
    setMounted(true)
  }, [])

  useEffect(() => {
    if (!mounted || typeof window === 'undefined') {
      return
    }

    const checkAuth = () => {
      const token = localStorage.getItem('auth_token')
      const userStr = localStorage.getItem('user')
      const tenantId = localStorage.getItem('tenant_id')

      if (!token || !userStr) {
        window.location.href = redirectTo
        setIsLoading(false)
        return
      }

      // Check if tenant is selected (required for all authenticated users)
      if (!tenantId) {
        // Check if user needs tenant selection
        const availableTenants = localStorage.getItem('available_tenants')
        if (availableTenants) {
          // User is logged in but needs to select tenant - redirect to login page
          // which will show tenant selection UI
          window.location.href = '/login'
        } else {
          // No tenant selected and no available tenants - redirect to login
          window.location.href = redirectTo
        }
        setIsLoading(false)
        return
      }

      if (requiredRoles && requiredRoles.length > 0) {
        try {
          const user = JSON.parse(userStr)
          if (!requiredRoles.includes(user.role)) {
            window.location.href = '/unauthorized'
            setIsLoading(false)
            return
          }
        } catch {
          window.location.href = redirectTo
          setIsLoading(false)
          return
        }
      }

      setIsAuthorized(true)
      setIsLoading(false)
    }

    checkAuth()
  }, [mounted, requiredRoles, redirectTo])

  if (!mounted || isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  if (!isAuthorized) {
    return null
  }

  return <>{children}</>
}

ProtectedRoute.displayName = 'ProtectedRoute'

export default ProtectedRoute
