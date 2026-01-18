'use client'

import { useEffect } from 'react'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'

/**
 * Component to setup ApiClient with AuthContext callbacks
 * This ensures token refresh and logout are properly handled
 */
export function ApiClientSetup() {
  const { logout, updateToken } = useAuth()

  useEffect(() => {
    const apiClient = getApiClient()

    // Set up token refresh callback
    apiClient.setOnTokenRefresh((newToken: string) => {
      // Update AuthContext state with new token
      updateToken(newToken)
    })

    // Set up logout callback
    apiClient.setOnLogout(() => {
      logout().catch((error) => {
        console.error('[ApiClientSetup] Logout error:', error)
        // Force redirect even if logout fails
        if (typeof window !== 'undefined') {
          window.location.href = '/login'
        }
      })
    })

    return () => {
      // Cleanup
      apiClient.setOnTokenRefresh(undefined as any)
      apiClient.setOnLogout(undefined as any)
    }
  }, [logout, updateToken])

  return null
}

