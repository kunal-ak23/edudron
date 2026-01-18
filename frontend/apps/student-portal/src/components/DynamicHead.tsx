'use client'

import { useEffect } from 'react'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { TenantBrandingApi, TenantsApi } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'

export function DynamicHead() {
  const { tenantId } = useAuth()

  useEffect(() => {
    const updateHead = async () => {
      // Default values
      let tenantName = 'EduDron'
      let faviconUrl: string | null = null

      // Fetch tenant info if tenantId is available
      if (tenantId && tenantId !== 'PENDING_TENANT_SELECTION' && tenantId !== 'SYSTEM') {
        try {
          const apiClient = getApiClient()
          const brandingApi = new TenantBrandingApi(apiClient)
          const tenantsApi = new TenantsApi(apiClient)

          // Fetch branding and tenant info in parallel
          const [branding, tenant] = await Promise.all([
            brandingApi.getBranding().catch(() => null),
            tenantsApi.getTenant(tenantId).catch(() => null)
          ])

          if (tenant?.name) {
            tenantName = tenant.name
          }

          if (branding?.faviconUrl) {
            faviconUrl = branding.faviconUrl
          }
        } catch (error) {
        }
      }

      // Update document title
      document.title = `${tenantName} | EduDron`

      // Update favicon
      const updateFavicon = (url: string) => {
        // Remove existing favicon links
        const existingLinks = document.querySelectorAll("link[rel*='icon']")
        existingLinks.forEach((link) => link.remove())

        // Determine image type from URL extension
        const getImageType = (url: string): string => {
          const extension = url.toLowerCase().split('.').pop()
          switch (extension) {
            case 'png':
              return 'image/png'
            case 'svg':
              return 'image/svg+xml'
            case 'jpg':
            case 'jpeg':
              return 'image/jpeg'
            case 'ico':
            default:
              return 'image/x-icon'
          }
        }

        // Create new favicon link
        const link = document.createElement('link')
        link.rel = 'icon'
        link.type = getImageType(url)
        link.href = url
        document.head.appendChild(link)
      }

      if (faviconUrl) {
        updateFavicon(faviconUrl)
      } else {
        // Use default favicon or remove custom one
        const existingLinks = document.querySelectorAll("link[rel*='icon']")
        existingLinks.forEach((link) => {
          // Only remove if it's a custom one (not the default)
          const href = link.getAttribute('href')
          if (href && !href.includes('/favicon.ico')) {
            link.remove()
          }
        })
      }
    }

    updateHead()
  }, [tenantId])

  return null // This component doesn't render anything
}
