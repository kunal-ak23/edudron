import { useState, useEffect } from 'react'
import { TenantFeatureType, useAuth } from '@kunal-ak23/edudron-shared-utils'
import { tenantFeaturesApi } from '@/lib/api'

export function useProjectsFeature() {
  const [enabled, setEnabled] = useState<boolean>(false)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<Error | null>(null)
  const { needsTenantSelection, tenantId } = useAuth()

  useEffect(() => {
    async function checkFeature() {
      try {
        if (needsTenantSelection || !tenantId) {
          setEnabled(false)
          setError(null)
          setLoading(false)
          return
        }

        setLoading(true)
        setError(null)
        // Use PROJECTS feature type - falls back to string if enum doesn't have it yet
        const featureType = (TenantFeatureType as any).PROJECTS || 'PROJECTS'
        const isEnabled = await tenantFeaturesApi.isFeatureEnabled(featureType)
        setEnabled(isEnabled)
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Unknown error'))
        // Default to enabled if the feature flag check fails (feature may not exist yet)
        setEnabled(true)
      } finally {
        setLoading(false)
      }
    }

    checkFeature()
  }, [needsTenantSelection, tenantId])

  return { enabled, loading, error }
}
