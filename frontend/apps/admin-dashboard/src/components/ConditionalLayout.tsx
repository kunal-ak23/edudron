'use client'

import { useEffect, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { AppLayout } from './AppLayout'

interface ConditionalLayoutProps {
  children: React.ReactNode
}

export function ConditionalLayout({ children }: ConditionalLayoutProps) {
  const pathname = usePathname()
  const router = useRouter()
  const { isAuthenticated, isLoading, user } = useAuth()
  const [isHydrated, setIsHydrated] = useState(false)

  useEffect(() => {
    setIsHydrated(true)
  }, [])

  // Pages that should not have the common layout (auth pages)
  const authPages = ['/login', '/register', '/forgot-password', '/reset-password', '/unauthorized', '/select-tenant']
  
  const isAuthPage = authPages.some(page => pathname === page || pathname === page + '/')

  if (!isHydrated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center space-y-4">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          <p className="text-sm text-gray-600">Initializing application...</p>
        </div>
      </div>
    )
  }

  // Show loading spinner while checking authentication
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center space-y-4">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          <p className="text-sm text-gray-600">Loading...</p>
        </div>
      </div>
    )
  }

  // If it's an auth page, render without layout (no AppLayout wrapper)
  if (isAuthPage) {
    return <>{children}</>
  }

  // If user is not authenticated and not on auth page, redirect to login
  if (!isAuthenticated() && !isLoading) {
    // Check localStorage as fallback before redirecting
    const storedUser = typeof window !== 'undefined' ? localStorage.getItem('user') : null
    const storedToken = typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null
    
    if (!storedUser || !storedToken) {
      // Redirect to login
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50">
          <div className="flex flex-col items-center space-y-4">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            <p className="text-sm text-gray-600">Redirecting to login...</p>
          </div>
        </div>
      )
    }
  }

  // For authenticated users on non-auth pages, render with full layout
  return (
    <AppLayout>{children}</AppLayout>
  )
}


