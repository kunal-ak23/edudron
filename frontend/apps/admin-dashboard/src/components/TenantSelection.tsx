'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { CheckCircle, Building2 } from 'lucide-react'
import type { TenantInfo } from '@edudron/shared-utils'

interface TenantSelectionProps {
  tenants: TenantInfo[]
  onTenantSelect: (tenantId: string) => void
  userEmail: string
  title?: string
  subtitle?: string
}

export function TenantSelection({ tenants, onTenantSelect, userEmail, title, subtitle }: TenantSelectionProps) {
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [autoSelecting, setAutoSelecting] = useState(false)

  // Auto-select if only one tenant (using useEffect to avoid render-time state updates)
  useEffect(() => {
    if (tenants.length === 1 && !autoSelecting) {
      setAutoSelecting(true)
      setIsLoading(true)
      onTenantSelect(tenants[0].id).catch((error) => {
        console.error('Error auto-selecting tenant:', error)
        setAutoSelecting(false)
        setIsLoading(false)
      })
    }
  }, [tenants, onTenantSelect, autoSelecting])

  const handleContinue = async () => {
    if (!selectedTenantId) return
    
    setIsLoading(true)
    try {
      await onTenantSelect(selectedTenantId)
    } finally {
      setIsLoading(false)
    }
  }

  // If only one tenant, show loading state while auto-selecting
  if (tenants.length === 1) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Setting up your workspace...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-2xl w-full space-y-8">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-blue-100 mb-4">
            <Building2 className="h-8 w-8 text-blue-600" />
          </div>
          <h2 className="text-3xl font-extrabold text-gray-900">
            {title || 'Select Your Workspace'}
          </h2>
          <p className="mt-2 text-sm text-gray-600">
            {subtitle || `Choose which tenant you want to access as ${userEmail}`}
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          {tenants.map((tenant) => (
            <Card 
              key={tenant.id}
              className={`cursor-pointer transition-all duration-200 hover:shadow-lg ${
                selectedTenantId === tenant.id 
                  ? 'ring-2 ring-blue-500 bg-blue-50' 
                  : 'hover:shadow-md'
              }`}
              onClick={() => setSelectedTenantId(tenant.id)}
            >
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg">{tenant.name}</CardTitle>
                  {selectedTenantId === tenant.id && (
                    <CheckCircle className="h-5 w-5 text-blue-600" />
                  )}
                </div>
                <CardDescription className="text-sm text-gray-500">
                  {tenant.slug}
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="flex items-center justify-between">
                  <Badge variant={tenant.isActive ? "default" : "secondary"}>
                    {tenant.isActive ? 'Active' : 'Inactive'}
                  </Badge>
                  <span className="text-xs text-gray-400">
                    ID: {tenant.id.slice(0, 8)}...
                  </span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        <div className="flex justify-center">
          <Button 
            onClick={handleContinue}
            disabled={!selectedTenantId || isLoading}
            className="px-8 py-2"
          >
            {isLoading ? 'Setting up workspace...' : 'Continue'}
          </Button>
        </div>
      </div>
    </div>
  )
}

