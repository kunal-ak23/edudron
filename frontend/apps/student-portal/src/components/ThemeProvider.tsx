'use client'

import { useEffect } from 'react'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { TenantBrandingApi } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'
import { applyTenantBranding } from '@/utils/colorUtils'

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const { tenantId } = useAuth()

  useEffect(() => {
    const loadBranding = async () => {
      if (!tenantId || tenantId === 'PENDING_TENANT_SELECTION' || tenantId === 'SYSTEM') {
        return
      }

      try {
        const apiClient = getApiClient()
        const brandingApi = new TenantBrandingApi(apiClient)
        const branding = await brandingApi.getBranding()
        
        if (branding.primaryColor) {
          applyTenantBranding(branding.primaryColor)
        }
      } catch (error) {
        console.warn('[ThemeProvider] Failed to load tenant branding, using defaults:', error)
      }
    }

    loadBranding()
  }, [tenantId])

  return <>{children}</>
}

