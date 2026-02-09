import { useState, useEffect } from 'react'
import { TenantFeaturesApi, TenantFeatureType } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'

export function usePsychometricTestFeature() {
  const [enabled, setEnabled] = useState<boolean>(false)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<Error | null>(null)
  const { needsTenantSelection, tenantId } = useAuth()

  useEffect(() => {
    async function checkFeature() {
      try {
        if (needsTenantSelection || !tenantId) {
          console.info('[StudentPortal][PsychTestFeature] skipping check (tenant not selected)', {
            needsTenantSelection,
            tenantId,
          })
          setEnabled(false)
          setError(null)
          setLoading(false)
          return
        }

        setLoading(true)
        setError(null)
        const tenantFeaturesApi = new TenantFeaturesApi(getApiClient())
        const isEnabled = await tenantFeaturesApi.isFeatureEnabled(TenantFeatureType.PSYCHOMETRIC_TEST)
        setEnabled(isEnabled)
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Unknown error'))
        setEnabled(false)
      } finally {
        setLoading(false)
      }
    }

    checkFeature()
  }, [needsTenantSelection, tenantId])

  return { enabled, loading, error }
}
