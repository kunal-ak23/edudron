'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@edudron/shared-utils'

export function useRequireAuth(allowedRoles?: string[]) {
  const router = useRouter()
  const { user, isAuthenticated } = useAuth()

  useEffect(() => {
    if (!isAuthenticated() || !user) {
      router.push('/login')
      return
    }

    if (allowedRoles && allowedRoles.length > 0) {
      if (!allowedRoles.includes(user.role)) {
        router.push('/unauthorized')
      }
    }
  }, [user, isAuthenticated, router, allowedRoles])

  return { user, isAuthenticated: isAuthenticated() }
}

