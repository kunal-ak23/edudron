'use client'

import { useEffect, useState } from 'react'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Loader2, Save, Settings } from 'lucide-react'
import { TenantFeatureType, type TenantFeatureDto } from '@kunal-ak23/edudron-shared-utils'
import { tenantFeaturesApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'

export const dynamic = 'force-dynamic'

export default function SettingsPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  const [features, setFeatures] = useState<TenantFeatureDto[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    loadFeatures()
  }, [])

  const loadFeatures = async () => {
    try {
      setLoading(true)
      const featuresData = await tenantFeaturesApi.getAllFeatures()
      setFeatures(featuresData)
    } catch (error) {
      console.error('Failed to load features:', error)
      toast({
        title: 'Error',
        description: 'Failed to load feature settings',
        variant: 'destructive'
      })
    } finally {
      setLoading(false)
    }
  }

  const handleToggleFeature = async (feature: TenantFeatureType, enabled: boolean) => {
    try {
      setSaving(true)
      await tenantFeaturesApi.updateFeature(feature, enabled)
      
      // Update local state
      setFeatures(prev => prev.map(f => 
        f.feature === feature 
          ? { ...f, enabled, isOverridden: true }
          : f
      ))
      
      toast({
        title: 'Success',
        description: 'Feature setting updated successfully'
      })
    } catch (error) {
      console.error('Failed to update feature:', error)
      toast({
        title: 'Error',
        description: 'Failed to update feature setting',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleResetFeature = async (feature: TenantFeatureType) => {
    try {
      setSaving(true)
      await tenantFeaturesApi.resetFeature(feature)
      
      // Reload features to get updated defaults
      await loadFeatures()
      
      toast({
        title: 'Success',
        description: 'Feature reset to default value'
      })
    } catch (error) {
      console.error('Failed to reset feature:', error)
      toast({
        title: 'Error',
        description: 'Failed to reset feature setting',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const getFeatureDisplayName = (feature: TenantFeatureType): string => {
    switch (feature) {
      case TenantFeatureType.STUDENT_SELF_ENROLLMENT:
        return 'Student Self-Enrollment'
      default:
        return feature
    }
  }

  const getFeatureDescription = (feature: TenantFeatureType): string => {
    switch (feature) {
      case TenantFeatureType.STUDENT_SELF_ENROLLMENT:
        return 'Allow students to enroll themselves in courses. When disabled, only admins and instructors can enroll students.'
      default:
        return ''
    }
  }

  if (loading) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="flex items-center justify-center min-h-[400px]">
          <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
          <Settings className="h-8 w-8" />
          Settings
        </h1>
        <p className="text-gray-600 mt-2">Manage your tenant settings and preferences</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Feature Settings</CardTitle>
          <CardDescription>
            Configure tenant-level feature flags. These settings control what features are available to users in your tenant.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {features.map((feature) => (
            <div key={feature.feature} className="flex items-start justify-between p-4 border rounded-lg">
              <div className="flex-1">
                <div className="flex items-center gap-3 mb-2">
                  <Label htmlFor={`feature-${feature.feature}`} className="text-base font-semibold cursor-pointer">
                    {getFeatureDisplayName(feature.feature)}
                  </Label>
                  {feature.isOverridden && (
                    <span className="text-xs px-2 py-1 bg-blue-100 text-blue-800 rounded">
                      Custom
                    </span>
                  )}
                  {!feature.isOverridden && (
                    <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
                      Default
                    </span>
                  )}
                </div>
                <p className="text-sm text-gray-600 mb-3">
                  {getFeatureDescription(feature.feature)}
                </p>
                <p className="text-xs text-gray-500">
                  Default value: <span className="font-medium">{feature.defaultValue ? 'Enabled' : 'Disabled'}</span>
                </p>
              </div>
              <div className="flex items-center gap-4">
                <Switch
                  id={`feature-${feature.feature}`}
                  checked={feature.enabled}
                  onCheckedChange={(checked) => handleToggleFeature(feature.feature, checked)}
                  disabled={saving}
                />
                {feature.isOverridden && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleResetFeature(feature.feature)}
                    disabled={saving}
                  >
                    Reset
                  </Button>
                )}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
