import { useState, useEffect } from 'react'
import { TenantFeaturesApi, TenantFeatureType } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'

export function usePsychometricTestFeature() {
  const [enabled, setEnabled] = useState<boolean>(false)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    async function checkFeature() {
      try {
        setLoading(true)
        setError(null)
        const tenantFeaturesApi = new TenantFeaturesApi(getApiClient())
        const isEnabled = await tenantFeaturesApi.isFeatureEnabled(TenantFeatureType.PSYCHOMETRIC_TEST)
        setEnabled(isEnabled)
      } catch (err) {
        console.error('Failed to check psychometric test feature:', err)
        setError(err instanceof Error ? err : new Error('Unknown error'))
        setEnabled(false)
      } finally {
        setLoading(false)
      }
    }

    checkFeature()
  }, [])

  return { enabled, loading, error }
}
