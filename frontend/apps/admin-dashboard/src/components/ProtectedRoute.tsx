'use client'

import React, { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'

interface ProtectedRouteProps {
  children: React.ReactNode
  requiredRoles?: string[]
  redirectTo?: string
}

export default function ProtectedRoute({ 
  children, 
  requiredRoles,
  redirectTo = '/login' 
}: ProtectedRouteProps) {
  const router = useRouter()
  const { user, isLoading, isAuthenticated, tenantId } = useAuth()
  const [isAuthorized, setIsAuthorized] = useState(false)
  const [mounted, setMounted] = useState(false)

  // Ensure we're on client side
  useEffect(() => {
    setMounted(true)
  }, [])

  useEffect(() => {
    if (!mounted || isLoading) {
      return
    }

    // Check authentication
    if (!isAuthenticated() || !user) {
      router.push(redirectTo)
      return
    }

    // Check if tenant is selected (required for all authenticated users except SYSTEM_ADMIN)
    if (!tenantId && user.role !== 'SYSTEM_ADMIN') {
      const availableTenants = localStorage.getItem('available_tenants')
      if (availableTenants) {
        // User is logged in but needs to select tenant - redirect to login page
        router.push('/login')
      } else {
        router.push(redirectTo)
      }
      return
    }

    // Check role requirements
    if (requiredRoles && requiredRoles.length > 0) {
      if (!requiredRoles.includes(user.role)) {
        router.push('/unauthorized')
        return
      }
    }

    setIsAuthorized(true)
  }, [mounted, isLoading, user, tenantId, requiredRoles, redirectTo, router, isAuthenticated])

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
