'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Loader2, ArrowLeft } from 'lucide-react'
import { tenantsApi } from '@/lib/api'
import type { Tenant, CreateTenantRequest } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

export default function TenantEditPage() {
  const router = useRouter()
  const params = useParams()
  const tenantId = params.id as string
  const { toast } = useToast()
  const { user } = useAuth()

  const [tenant, setTenant] = useState<Tenant | null>(null)
  const [formData, setFormData] = useState<CreateTenantRequest>({
    name: '',
    slug: '',
    gstin: '',
    isActive: true
  })
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  
  const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
  
  // Redirect if not SYSTEM_ADMIN
  useEffect(() => {
    if (user && !isSystemAdmin) {
      router.push('/unauthorized')
    }
  }, [user, isSystemAdmin, router])

  const loadTenant = useCallback(async () => {
    try {
      setLoading(true)
      setError('')
      const tenantData = await tenantsApi.getTenant(tenantId)
      setTenant(tenantData)
      setFormData({
        name: tenantData.name || '',
        slug: tenantData.slug || '',
        gstin: tenantData.gstin || '',
        isActive: tenantData.isActive ?? true
      })
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      setError(errorMessage)
      toast({
        variant: 'destructive',
        title: 'Failed to load tenant',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }, [tenantId, toast])

  useEffect(() => {
    if (tenantId) {
      loadTenant()
    }
  }, [tenantId, loadTenant])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSaving(true)

    try {
      const updatedTenant = await tenantsApi.updateTenant(tenantId, formData)
      toast({
        title: 'Success',
        description: 'Tenant updated successfully',
      })
      // Redirect back to tenants list
      router.push('/tenants')
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      setError(errorMessage)
      toast({
        variant: 'destructive',
        title: 'Failed to update tenant',
        description: errorMessage,
      })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    try {
      setSaving(true)
      await tenantsApi.deleteTenant(tenantId)
      toast({
        title: 'Success',
        description: 'Tenant deleted successfully',
      })
      router.push('/tenants')
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      setError(errorMessage)
      toast({
        variant: 'destructive',
        title: 'Failed to delete tenant',
        description: errorMessage,
      })
    } finally {
      setSaving(false)
      setShowDeleteDialog(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (error && !tenant) {
    return (
      <div>
        <Card>
          <CardContent className="pt-6">
            <div className="text-center">
                  <p className="text-destructive mb-4">{error}</p>
                  <Button onClick={() => router.push('/tenants')} variant="outline">
                    <ArrowLeft className="h-4 w-4 mr-2" />
                    Back to Tenants
                  </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div>
      <div className="mb-6">
        <Button
          variant="ghost"
          onClick={() => router.push('/tenants')}
          className="mb-4"
        >
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Tenants
          </Button>
            {tenant && (
              <p className="text-muted-foreground mt-2">
                Tenant ID: {tenant.id}
              </p>
            )}
          </div>

          {error && (
            <Card className="mb-6 border-destructive">
              <CardContent className="pt-6">
                <p className="text-destructive">{error}</p>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Tenant Details</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit}>
                <div className="space-y-6">
                  <div className="space-y-2">
                    <Label htmlFor="name">Name *</Label>
                    <Input
                      id="name"
                      value={formData.name}
                      onChange={(e) =>
                        setFormData({ ...formData, name: e.target.value })
                      }
                      required
                      placeholder="Enter tenant name"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="slug">Slug *</Label>
                    <Input
                      id="slug"
                      value={formData.slug}
                      onChange={(e) =>
                        setFormData({ ...formData, slug: e.target.value })
                      }
                      required
                      placeholder="Enter tenant slug (e.g., acme-corp)"
                    />
                    <p className="text-sm text-muted-foreground">
                      A unique identifier for the tenant (lowercase, hyphens only)
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="gstin">GSTIN</Label>
                    <Input
                      id="gstin"
                      value={formData.gstin || ''}
                      onChange={(e) =>
                        setFormData({ ...formData, gstin: e.target.value })
                      }
                      placeholder="Enter GSTIN (optional)"
                    />
                  </div>

                  <div className="flex items-center space-x-2">
                    <Checkbox
                      id="isActive"
                      checked={formData.isActive}
                      onCheckedChange={(checked) =>
                        setFormData({ ...formData, isActive: checked as boolean })
                      }
                    />
                    <Label htmlFor="isActive" className="font-normal cursor-pointer">
                      Active
                    </Label>
                  </div>

                  {tenant && (
                    <div className="pt-4 border-t">
                      <p className="text-sm text-muted-foreground">
                        Created: {tenant.createdAt 
                          ? new Date(tenant.createdAt).toLocaleString()
                          : 'N/A'}
                      </p>
                    </div>
                  )}

                  <div className="flex justify-between pt-4">
                    {isSystemAdmin && (
                      <Button
                        type="button"
                        variant="destructive"
                        onClick={() => setShowDeleteDialog(true)}
                        disabled={saving}
                      >
                        Delete Tenant
                      </Button>
                    )}
                    <div className="flex gap-3 ml-auto">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => router.push('/tenants')}
                        disabled={saving}
                      >
                        Cancel
                      </Button>
                      {isSystemAdmin && (
                        <Button type="submit" disabled={saving}>
                          {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                          Save Changes
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              </form>
            </CardContent>
          </Card>

          {tenant && (
            <Card className="mt-6">
              <CardHeader>
                <CardTitle>Quick Actions</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex gap-3">
                  <Button
                    variant="outline"
                    onClick={() => router.push(`/tenants/${tenantId}/branding`)}
                  >
                    Manage Branding
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        onConfirm={handleDelete}
        title="Delete Tenant"
        description="Are you sure you want to delete this tenant? This will deactivate it."
        confirmText="Delete"
        variant="destructive"
        isLoading={saving}
      />
    </div>
  )
}

