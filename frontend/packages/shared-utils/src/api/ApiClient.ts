import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { TokenManager } from '../auth/TokenManager'

export interface ApiResponse<T = any> {
  data: T
  message?: string
  status?: 'success' | 'error'
}

export interface PaginatedResponse<T = any> {
  data: T[]
  pagination: {
    page: number
    limit: number
    total: number
    totalPages: number
  }
}

export class ApiClient {
  private client: AxiosInstance
  private tokenManager: TokenManager
  private onTokenRefresh?: (token: string) => void
  private onLogout?: () => void

  constructor(baseURL: string, tokenManager?: TokenManager) {
    this.tokenManager = tokenManager || new TokenManager()
    
    this.client = axios.create({
      baseURL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
    })

    this.setupInterceptors()
  }

  /**
   * Set callback to be called when token is refreshed
   */
  setOnTokenRefresh(callback: (token: string) => void) {
    console.log('[ApiClient] Setting onTokenRefresh callback')
    this.onTokenRefresh = callback
  }

  /**
   * Set callback to be called when logout is needed
   */
  setOnLogout(callback: () => void) {
    console.log('[ApiClient] Setting onLogout callback')
    this.onLogout = callback
  }

  private setupInterceptors() {
    // Request interceptor to add auth token and tenant ID
    this.client.interceptors.request.use(
      (config) => {
        // Add auth token
        const token = this.tokenManager.getToken()
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
        
        // Add tenant ID from localStorage (check multiple possible keys)
        // Read dynamically on each request to ensure we get the latest value
        let tenantId: string | null = null
        if (typeof window !== 'undefined') {
          tenantId = localStorage.getItem('clientId') || 
                     localStorage.getItem('selectedTenantId') || 
                     localStorage.getItem('tenant_id') ||
                     null
        }
        
        if (tenantId) {
          // Validate tenant ID is not a placeholder value
          if (tenantId !== 'PENDING_TENANT_SELECTION' && 
              tenantId !== 'SYSTEM' && 
              tenantId !== 'null' && 
              tenantId !== '') {
            config.headers['X-Client-Id'] = tenantId
            // Log in development to help debug
            if (typeof window !== 'undefined' && process.env.NODE_ENV === 'development') {
              console.log('[API Client] Setting X-Client-Id header:', tenantId, 'for request:', config.url)
            }
          } else {
            // Log warning if tenant ID is a placeholder
            if (typeof window !== 'undefined' && process.env.NODE_ENV === 'development') {
              console.warn('[API Client] X-Client-Id header not set - tenant ID is placeholder value:', tenantId, 'for request:', config.url)
            }
          }
        } else {
          // Log warning if clientId is missing (but don't block the request)
          if (typeof window !== 'undefined' && process.env.NODE_ENV === 'development') {
            console.warn('[API Client] X-Client-Id header not set - tenant ID not found in localStorage. Checked: clientId, selectedTenantId, tenant_id. Request:', config.url)
          }
        }
        
        return config
      },
      (error) => Promise.reject(error)
    )

    // Response interceptor for error handling and token refresh
    this.client.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config
        const status = error.response?.status

        // Handle 401 (Unauthorized) and 403 (Forbidden) as potential token expiration
        // 403 can also mean token expired or invalid permissions
        if ((status === 401 || status === 403) && !originalRequest._retry) {
          console.log(`[ApiClient] Received ${status} error for ${originalRequest.url}, attempting token refresh...`)
          originalRequest._retry = true

          try {
            const refreshToken = this.tokenManager.getRefreshToken()
            console.log('[ApiClient] Refresh token available:', !!refreshToken)
            
            if (refreshToken) {
              // Create a new axios instance without interceptors to avoid infinite loop
              // The refresh endpoint should not require authentication
              const refreshClient = axios.create({
                baseURL: this.client.defaults.baseURL,
                timeout: 10000,
                headers: {
                  'Content-Type': 'application/json',
                },
              })
              
              console.log('[ApiClient] Attempting to refresh token...')
              
              // Try refresh endpoint - send refreshToken in request body
              const response = await refreshClient.post(
                '/auth/refresh',
                { refreshToken }
              )

              console.log('[ApiClient] Token refresh response:', response.status, response.data)

              const newToken = response.data.token || response.data.data?.token || (response.data as any)?.token
              if (newToken) {
                console.log('[ApiClient] Token refresh successful, updating token...')
                this.tokenManager.setToken(newToken)
                if (response.data.refreshToken || response.data.data?.refreshToken || (response.data as any)?.refreshToken) {
                  const newRefreshToken = response.data.refreshToken || response.data.data?.refreshToken || (response.data as any)?.refreshToken
                  this.tokenManager.setRefreshToken(newRefreshToken)
                }
                
                // Notify AuthContext about token refresh
                if (this.onTokenRefresh) {
                  console.log('[ApiClient] Notifying AuthContext about token refresh...')
                  this.onTokenRefresh(newToken)
                } else {
                  console.warn('[ApiClient] onTokenRefresh callback not set!')
                }
                
                originalRequest.headers.Authorization = `Bearer ${newToken}`
                console.log('[ApiClient] Retrying original request with new token...')
                return this.client(originalRequest)
              } else {
                console.error('[ApiClient] Token refresh response did not contain a new token')
              }
            } else {
              console.warn('[ApiClient] No refresh token available, cannot refresh')
            }
          } catch (refreshError: any) {
            console.error('[ApiClient] Token refresh failed:', refreshError?.message || refreshError)
            console.error('[ApiClient] Refresh error details:', {
              status: refreshError?.response?.status,
              data: refreshError?.response?.data,
              message: refreshError?.message
            })
            // Refresh failed, trigger logout
            console.log('[ApiClient] Triggering logout due to refresh failure...')
            this.handleLogout()
            return Promise.reject(refreshError)
          }
        }

        // If refresh didn't work or other 401/403 error, trigger logout
        if (status === 401 || status === 403) {
          console.log(`[ApiClient] ${status} error not handled by refresh, triggering logout...`)
          this.handleLogout()
        }

        return Promise.reject(error)
      }
    )
  }

  async get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    console.log('[ApiClient.get] Making request to:', url, 'with config:', config)
    const response: AxiosResponse<any> = await this.client.get(url, config)
    console.log('[ApiClient.get] Raw axios response:', response)
    console.log('[ApiClient.get] Response.data:', response.data)
    console.log('[ApiClient.get] Response.data type:', typeof response.data)
    console.log('[ApiClient.get] Response.data is array?:', Array.isArray(response.data))
    console.log('[ApiClient.get] Response.data keys:', response.data && typeof response.data === 'object' ? Object.keys(response.data) : 'N/A')
    
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      console.log('[ApiClient.get] Response already has data property, returning as-is')
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (array, object, etc.), wrap it in ApiResponse format
    console.log('[ApiClient.get] Wrapping response in ApiResponse format')
    const wrapped = { data: responseData } as ApiResponse<T>
    console.log('[ApiClient.get] Wrapped response:', wrapped)
    return wrapped
  }

  async post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<ApiResponse<T>> = await this.client.post(url, data, config)
    return response.data
  }

  async postForm<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<ApiResponse<T>> = await this.client.post(url, formData, {
      ...config,
      headers: {
        ...config?.headers,
        'Content-Type': 'multipart/form-data',
      },
    })
    return response.data
  }

  async put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<ApiResponse<T>> = await this.client.put(url, data, config)
    return response.data
  }

  async patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<ApiResponse<T>> = await this.client.patch(url, data, config)
    return response.data
  }

  async delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<ApiResponse<T>> = await this.client.delete(url, config)
    return response.data
  }

  setBaseURL(baseURL: string) {
    this.client.defaults.baseURL = baseURL
  }

  setToken(token: string) {
    this.tokenManager.setToken(token)
  }

  clearToken() {
    this.tokenManager.clearToken()
  }

  /**
   * Handle logout - clear tokens and notify AuthContext
   */
  private handleLogout() {
    console.log('[ApiClient] handleLogout called')
    this.tokenManager.clearToken()
    
    // Clear all auth-related localStorage
    if (typeof window !== 'undefined') {
      localStorage.removeItem('auth_token')
      localStorage.removeItem('refresh_token')
      localStorage.removeItem('user')
      localStorage.removeItem('tenant_id')
      localStorage.removeItem('clientId')
      localStorage.removeItem('selectedTenantId')
      localStorage.removeItem('available_tenants')
      console.log('[ApiClient] Cleared all auth data from localStorage')
    }
    
    // Notify AuthContext to update state
    if (this.onLogout) {
      console.log('[ApiClient] Calling onLogout callback...')
      this.onLogout()
    } else {
      console.warn('[ApiClient] onLogout callback not set! Redirecting to login...')
      // Fallback: redirect to login if no callback is set
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
    }
  }
}

export default ApiClient

