import { ApiClient } from './ApiClient'

export interface Tenant {
  id: string
  slug: string
  name: string
  gstin?: string
  isActive: boolean
  createdAt: string
}

export interface CreateTenantRequest {
  name: string
  slug: string
  gstin?: string
  isActive?: boolean
}

export interface TenantBranding {
  id?: string
  clientId: string
  primaryColor: string
  secondaryColor: string
  accentColor?: string
  backgroundColor?: string
  surfaceColor?: string
  textPrimaryColor?: string
  textSecondaryColor?: string
  logoUrl?: string
  faviconUrl?: string
  fontFamily?: string
  fontHeading?: string
  borderRadius?: string
  isActive?: boolean
}

export class TenantsApi {
  constructor(private apiClient: ApiClient) {}

  async createTenant(request: CreateTenantRequest): Promise<Tenant> {
    console.log('[TenantsApi.createTenant] Starting with request:', request)
    const response = await this.apiClient.post<Tenant>('/api/tenant', request)
    console.log('[TenantsApi.createTenant] Raw response from apiClient.post:', response)
    console.log('[TenantsApi.createTenant] Response type:', typeof response)
    console.log('[TenantsApi.createTenant] Response is array?:', Array.isArray(response))
    console.log('[TenantsApi.createTenant] Response keys:', response && typeof response === 'object' ? Object.keys(response) : 'N/A')
    console.log('[TenantsApi.createTenant] Response stringified:', JSON.stringify(response, null, 2))
    
    // Check if response has data property
    if (response && typeof response === 'object' && 'data' in response) {
      console.log('[TenantsApi.createTenant] Response has data property')
      console.log('[TenantsApi.createTenant] response.data:', response.data)
      console.log('[TenantsApi.createTenant] response.data type:', typeof response.data)
      console.log('[TenantsApi.createTenant] response.data keys:', response.data && typeof response.data === 'object' ? Object.keys(response.data) : 'N/A')
      
      const tenant = response.data as Tenant
      console.log('[TenantsApi.createTenant] Extracted tenant from response.data:', tenant)
      console.log('[TenantsApi.createTenant] tenant.id:', tenant?.id)
      console.log('[TenantsApi.createTenant] tenant.name:', tenant?.name)
      
      if (!tenant || !tenant.id || !tenant.name) {
        console.error('[TenantsApi.createTenant] Validation failed - tenant:', tenant)
        console.error('[TenantsApi.createTenant] Validation failed - has tenant:', !!tenant)
        console.error('[TenantsApi.createTenant] Validation failed - has id:', !!tenant?.id)
        console.error('[TenantsApi.createTenant] Validation failed - has name:', !!tenant?.name)
        throw new Error(`Invalid tenant response from server: missing required fields. Received: ${JSON.stringify(tenant)}`)
      }
      return tenant
    }
    
    // Check if response itself is the tenant object
    if (response && typeof response === 'object' && 'id' in response && 'name' in response) {
      console.log('[TenantsApi.createTenant] Response is tenant object directly')
      const tenant = response as any as Tenant
      console.log('[TenantsApi.createTenant] Using response as tenant:', tenant)
      
      if (!tenant || !tenant.id || !tenant.name) {
        console.error('[TenantsApi.createTenant] Validation failed - tenant:', tenant)
        throw new Error(`Invalid tenant response from server: missing required fields. Received: ${JSON.stringify(tenant)}`)
      }
      return tenant
    }
    
    // Unknown structure
    console.error('[TenantsApi.createTenant] Unknown response structure:', response)
    throw new Error(`Invalid tenant response from server: unexpected response structure. Received: ${JSON.stringify(response)}`)
  }

  async listTenants(): Promise<Tenant[]> {
    try {
      const response = await this.apiClient.get<Tenant[]>('/api/tenant')
      
      // The API returns the array directly: [{...}]
      // ApiClient.get returns response.data from axios
      // So if API returns [{...}], axios puts it in response.data = [{...}]
      // And ApiClient.get returns response.data which is [{...}]
      
      // Check if response is already an array (most likely case)
      if (Array.isArray(response)) {
        console.log('[TenantsApi.listTenants] ✓ Response is array, returning directly. Length:', response.length)
        return response
      }
      
      // Check if response has a data property that is an array
      if (response && typeof response === 'object' && response !== null) {
        const responseAny = response as any
        if ('data' in responseAny) {
          const data = responseAny.data
          console.log('[TenantsApi.listTenants] Found data property:', data)
          if (Array.isArray(data)) {
            console.log('[TenantsApi.listTenants] ✓ response.data is array, returning it. Length:', data.length)
            return data
          }
        }
      }
      
      console.error('[TenantsApi.listTenants] ✗ No valid array found in response')
      console.error('[TenantsApi.listTenants] Response structure:', {
        isArray: Array.isArray(response),
        type: typeof response,
        hasData: response && typeof response === 'object' && 'data' in response,
        value: response
      })
      return []
    } catch (error) {
      console.error('[TenantsApi.listTenants] ✗ Error occurred:', error)
      throw error
    }
  }

  async getActiveTenants(): Promise<Tenant[]> {
    const response = await this.apiClient.get<Tenant[]>('/api/tenant/active')
    // Handle both wrapped { data: [...] } and direct array responses
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getTenant(id: string): Promise<Tenant> {
    const response = await this.apiClient.get<Tenant>(`/api/tenant/${id}`)
    return response.data
  }

  async updateTenant(id: string, request: CreateTenantRequest): Promise<Tenant> {
    const response = await this.apiClient.put<Tenant>(`/api/tenant/${id}`, request)
    return response.data
  }

  async deleteTenant(id: string): Promise<void> {
    await this.apiClient.delete(`/api/tenant/${id}`)
  }
}

export class TenantBrandingApi {
  constructor(private apiClient: ApiClient) {}

  async getBranding(): Promise<TenantBranding> {
    const response = await this.apiClient.get<TenantBranding>('/api/tenant/branding')
    return response.data
  }

  async updateBranding(branding: TenantBranding): Promise<TenantBranding> {
    const response = await this.apiClient.put<TenantBranding>('/api/tenant/branding', branding)
    return response.data
  }
}

