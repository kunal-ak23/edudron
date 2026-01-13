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
    console.log('[ApiClientSetup] Setting up ApiClient callbacks...')
    const apiClient = getApiClient()

    // Set up token refresh callback
    apiClient.setOnTokenRefresh((newToken: string) => {
      console.log('[ApiClientSetup] Token refreshed successfully, updating AuthContext...')
      // Update AuthContext state with new token
      updateToken(newToken)
    })

    // Set up logout callback
    apiClient.setOnLogout(() => {
      console.log('[ApiClientSetup] Token expired or invalid, logging out...')
      logout().catch((error) => {
        console.error('[ApiClientSetup] Logout error:', error)
        // Force redirect even if logout fails
        if (typeof window !== 'undefined') {
          console.log('[ApiClientSetup] Forcing redirect to login...')
          window.location.href = '/login'
        }
      })
    })

    console.log('[ApiClientSetup] ApiClient callbacks set up successfully')

    return () => {
      // Cleanup
      console.log('[ApiClientSetup] Cleaning up callbacks...')
      apiClient.setOnTokenRefresh(undefined as any)
      apiClient.setOnLogout(undefined as any)
    }
  }, [logout, updateToken])

  return null
}

