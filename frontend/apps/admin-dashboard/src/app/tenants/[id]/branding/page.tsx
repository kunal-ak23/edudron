'use client'

import { useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, FileUpload } from '@edudron/ui-components'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, ArrowLeft } from 'lucide-react'
import { tenantBrandingApi, mediaApi } from '@/lib/api'
import type { TenantBranding } from '@edudron/shared-utils'

export default function TenantBrandingPage() {
  const router = useRouter()
  const params = useParams()
  const tenantId = params.id as string

  const [branding, setBranding] = useState<TenantBranding>({
    clientId: tenantId,
    primaryColor: '#3b82f6',
    secondaryColor: '#64748b',
    accentColor: '#f59e0b',
    backgroundColor: '#ffffff',
    surfaceColor: '#f8fafc',
    textPrimaryColor: '#0f172a',
    textSecondaryColor: '#64748b',
    fontFamily: 'Inter',
    borderRadius: '0.5rem',
    isActive: true
  })
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    loadBranding()
  }, [tenantId])

  const loadBranding = async () => {
    try {
      setLoading(true)
      // Set tenant context before calling API
      const originalTenantId = localStorage.getItem('tenant_id')
      localStorage.setItem('tenant_id', tenantId)
      localStorage.setItem('clientId', tenantId)
      
      try {
        const currentBranding = await tenantBrandingApi.getBranding()
        setBranding({ ...currentBranding, clientId: tenantId })
      } catch (err: any) {
        // If branding doesn't exist, use defaults
        console.log('No branding found, using defaults')
        setBranding(prev => ({ ...prev, clientId: tenantId }))
      } finally {
        // Restore original tenant context if it existed
        if (originalTenantId) {
          localStorage.setItem('tenant_id', originalTenantId)
          localStorage.setItem('clientId', originalTenantId)
        }
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load branding')
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSaving(true)

    try {
      // Set tenant context before calling API
      const originalTenantId = localStorage.getItem('tenant_id')
      localStorage.setItem('clientId', tenantId)
      localStorage.setItem('tenant_id', tenantId)
      
      await tenantBrandingApi.updateBranding({ ...branding, clientId: tenantId })
      setSuccess('Branding updated successfully!')
      setTimeout(() => setSuccess(''), 3000)
      
      // Restore original tenant context if it existed
      if (originalTenantId) {
        localStorage.setItem('tenant_id', originalTenantId)
        localStorage.setItem('clientId', originalTenantId)
      }
    } catch (err: any) {
      setError(err.message || 'Failed to update branding')
    } finally {
      setSaving(false)
    }
  }

  const handleColorChange = (field: keyof TenantBranding, value: string) => {
    setBranding(prev => ({ ...prev, [field]: value }))
  }

  if (loading) {
    return (
      <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN']}>
        <div className="min-h-screen flex items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN']}>
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="mb-8">
            <Button variant="outline" onClick={() => router.push('/tenants')} className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Tenants
            </Button>
            <h1 className="text-3xl font-bold">Tenant Branding</h1>
            <p className="mt-2 text-sm text-muted-foreground">Customize the appearance and branding for this tenant</p>
          </div>

          {error && (
            <div className="bg-destructive/10 border border-destructive/20 text-destructive px-4 py-3 rounded-md mb-4">
              {error}
            </div>
          )}

          {success && (
            <div className="bg-green-500/10 border border-green-500/20 text-green-700 dark:text-green-400 px-4 py-3 rounded-md mb-4">
              {success}
            </div>
          )}

          <form onSubmit={handleSave} className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>Color Scheme</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  <div className="space-y-2">
                    <Label>Primary Color</Label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="color"
                        value={branding.primaryColor}
                        onChange={(e) => handleColorChange('primaryColor', e.target.value)}
                        className="h-10 w-20 border border-input rounded-md cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={branding.primaryColor}
                        onChange={(e) => handleColorChange('primaryColor', e.target.value)}
                        pattern="^#[0-9A-Fa-f]{6}$"
                        className="flex-1"
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>Secondary Color</Label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="color"
                        value={branding.secondaryColor}
                        onChange={(e) => handleColorChange('secondaryColor', e.target.value)}
                        className="h-10 w-20 border border-input rounded-md cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={branding.secondaryColor}
                        onChange={(e) => handleColorChange('secondaryColor', e.target.value)}
                        pattern="^#[0-9A-Fa-f]{6}$"
                        className="flex-1"
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>Accent Color</Label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="color"
                        value={branding.accentColor || '#f59e0b'}
                        onChange={(e) => handleColorChange('accentColor', e.target.value)}
                        className="h-10 w-20 border border-input rounded-md cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={branding.accentColor || ''}
                        onChange={(e) => handleColorChange('accentColor', e.target.value)}
                        pattern="^#[0-9A-Fa-f]{6}$"
                        placeholder="#f59e0b"
                        className="flex-1"
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>Background Color</Label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="color"
                        value={branding.backgroundColor || '#ffffff'}
                        onChange={(e) => handleColorChange('backgroundColor', e.target.value)}
                        className="h-10 w-20 border border-input rounded-md cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={branding.backgroundColor || ''}
                        onChange={(e) => handleColorChange('backgroundColor', e.target.value)}
                        pattern="^#[0-9A-Fa-f]{6}$"
                        className="flex-1"
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>Surface Color</Label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="color"
                        value={branding.surfaceColor || '#f8fafc'}
                        onChange={(e) => handleColorChange('surfaceColor', e.target.value)}
                        className="h-10 w-20 border border-input rounded-md cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={branding.surfaceColor || ''}
                        onChange={(e) => handleColorChange('surfaceColor', e.target.value)}
                        pattern="^#[0-9A-Fa-f]{6}$"
                        className="flex-1"
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label>Text Primary</Label>
                    <div className="flex items-center space-x-2">
                      <input
                        type="color"
                        value={branding.textPrimaryColor || '#0f172a'}
                        onChange={(e) => handleColorChange('textPrimaryColor', e.target.value)}
                        className="h-10 w-20 border border-input rounded-md cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={branding.textPrimaryColor || ''}
                        onChange={(e) => handleColorChange('textPrimaryColor', e.target.value)}
                        pattern="^#[0-9A-Fa-f]{6}$"
                        className="flex-1"
                      />
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Typography</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Font Family</Label>
                    <Input
                      type="text"
                      value={branding.fontFamily || 'Inter'}
                      onChange={(e) => handleColorChange('fontFamily', e.target.value)}
                      placeholder="Inter"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Heading Font</Label>
                    <Input
                      type="text"
                      value={branding.fontHeading || ''}
                      onChange={(e) => handleColorChange('fontHeading', e.target.value)}
                      placeholder="Inter, system-ui, sans-serif"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Border Radius</Label>
                    <Input
                      type="text"
                      value={branding.borderRadius || '0.5rem'}
                      onChange={(e) => handleColorChange('borderRadius', e.target.value)}
                      placeholder="0.5rem"
                    />
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Assets</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <FileUpload
                    label="Logo"
                    accept="image/*"
                    maxSize={5 * 1024 * 1024} // 5MB
                    value={branding.logoUrl || ''}
                    onChange={(url) => handleColorChange('logoUrl', url)}
                    onUpload={async (file) => await mediaApi.uploadImage(file, 'logos')}
                    helperText="Upload a logo image (PNG, JPG, GIF up to 5MB)"
                  />
                  <FileUpload
                    label="Favicon"
                    accept="image/*"
                    maxSize={1 * 1024 * 1024} // 1MB
                    value={branding.faviconUrl || ''}
                    onChange={(url) => handleColorChange('faviconUrl', url)}
                    onUpload={async (file) => await mediaApi.uploadImage(file, 'favicons')}
                    helperText="Upload a favicon image (ICO, PNG up to 1MB)"
                  />
                </div>
              </CardContent>
            </Card>

            <div className="flex justify-end space-x-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => router.push('/tenants')}
                disabled={saving}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={saving}>
                {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                Save Branding
              </Button>
            </div>
          </form>
        </div>
      </div>
    </ProtectedRoute>
  )
}

