'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { tenantsApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator
} from '@/components/ui/dropdown-menu'
import { Badge } from '@/components/ui/badge'
import {
  Building2,
  ChevronDown,
  Check,
  Loader2
} from 'lucide-react'
import type { Tenant } from '@kunal-ak23/edudron-shared-utils'

export function TenantSelector() {
  const router = useRouter()
  const { user, tenantId, selectTenant } = useAuth()
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [selectedTenant, setSelectedTenant] = useState<Tenant | null>(null)
  const [loading, setLoading] = useState(true)
  const [isOpen, setIsOpen] = useState(false)

  const handleTenantSelect = async (tenant: Tenant) => {
    try {
      await selectTenant(tenant.id)
      setSelectedTenant(tenant)
      setIsOpen(false)
      // Navigate to dashboard so all tenant-scoped data is loaded fresh
      router.push('/dashboard')
    } catch (error) {
    }
  }

  useEffect(() => {
    const loadTenants = async () => {
      try {
        setLoading(true)
        const allTenants = await tenantsApi.listTenants()
        setTenants(allTenants || [])

        // Find selected tenant from localStorage
        const currentTenantId = tenantId ||
          localStorage.getItem('tenant_id') ||
          localStorage.getItem('clientId') ||
          localStorage.getItem('selectedTenantId')

        // Validate tenant ID is not a placeholder
        const isValidTenantId = currentTenantId &&
          currentTenantId !== 'PENDING_TENANT_SELECTION' &&
          currentTenantId !== 'SYSTEM' &&
          currentTenantId !== 'null' &&
          currentTenantId !== ''

        if (isValidTenantId && allTenants) {
          const found = allTenants.find(t => t.id === currentTenantId)
          if (found) {
            setSelectedTenant(found)
          } else {
            // Tenant ID exists but not found in list - clear it
            setSelectedTenant(null)
          }
        } else if (allTenants && allTenants.length > 0) {
          // No valid tenant selected - auto-select first tenant if only one exists
          if (allTenants.length === 1) {
            // Auto-select and navigate to dashboard
            selectTenant(allTenants[0].id).then(() => {
              setSelectedTenant(allTenants[0])
              router.push('/dashboard')
            }).catch(error => {
            })
          } else {
            setSelectedTenant(null)
          }
        }
      } catch (error) {
      } finally {
        setLoading(false)
      }
    }

    if (user?.role === 'SYSTEM_ADMIN') {
      loadTenants()
    } else {
      setLoading(false)
    }
  }, [tenantId, selectTenant, user?.role])

  // Only show for SYSTEM_ADMIN users
  if (user?.role !== 'SYSTEM_ADMIN') {
    return null
  }

  if (loading) {
    return (
      <Button variant="outline" disabled className="flex items-center space-x-2">
        <Loader2 className="h-4 w-4 animate-spin" />
        <span>Loading...</span>
      </Button>
    )
  }

  if (tenants.length === 0) {
    return null
  }

  return (
    <DropdownMenu open={isOpen} onOpenChange={setIsOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          className="flex items-center space-x-2 min-w-[200px] justify-between"
        >
          <div className="flex items-center space-x-2">
            <Building2 className="h-4 w-4" />
            <span className="truncate">
              {selectedTenant?.name || 'Select Tenant'}
            </span>
          </div>
          <ChevronDown className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-64" align="start">
        {tenants.map((tenant) => (
          <DropdownMenuItem
            key={tenant.id}
            onClick={() => handleTenantSelect(tenant)}
            className="flex items-center justify-between cursor-pointer"
          >
            <div className="flex items-center space-x-2">
              <Building2 className="h-4 w-4" />
              <div className="flex flex-col">
                <span className="font-medium">{tenant.name}</span>
                <span className="text-xs text-muted-foreground">{tenant.slug}</span>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              {tenant.isActive && (
                <Badge variant="secondary" className="text-xs">
                  Active
                </Badge>
              )}
              {selectedTenant?.id === tenant.id && (
                <Check className="h-4 w-4 text-primary" />
              )}
            </div>
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          className="text-muted-foreground cursor-pointer"
          onClick={() => router.push('/tenants')}
        >
          <Building2 className="h-4 w-4 mr-2" />
          Manage Tenants
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

