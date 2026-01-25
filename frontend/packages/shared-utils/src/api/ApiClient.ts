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
    this.onTokenRefresh = callback
  }

  /**
   * Set callback to be called when logout is needed
   */
  setOnLogout(callback: () => void) {
    this.onLogout = callback
  }

  private setupInterceptors() {
    // Request interceptor to add auth token and tenant ID
    this.client.interceptors.request.use(
      (config) => {
        // Don't add Authorization header for auth endpoints (login, register, refresh)
        // This prevents stale tokens from causing issues
        const isAuthEndpoint = config.url?.includes('/auth/login') || 
                               config.url?.includes('/auth/register') || 
                               config.url?.includes('/auth/refresh')
        
        // Add auth token (skip for auth endpoints)
        if (!isAuthEndpoint) {
          const token = this.tokenManager.getToken()
          if (token) {
            config.headers.Authorization = `Bearer ${token}`
          }
        }
        
        // Add tenant ID from localStorage (check multiple possible keys)
        // Read dynamically on each request to ensure we get the latest value
        // Skip for auth endpoints as well
        if (!isAuthEndpoint) {
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
              console.info('[ApiClient] set X-Client-Id', { url: config.url, tenantId })
            } else {
              console.warn('[ApiClient] skipping X-Client-Id (placeholder/invalid)', { url: config.url, tenantId })
            }
          } else {
            console.warn('[ApiClient] no tenantId found in localStorage', { url: config.url })
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
          originalRequest._retry = true

          try {
            const refreshToken = this.tokenManager.getRefreshToken()
            
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
              
              // Try refresh endpoint - send refreshToken in request body
              const response = await refreshClient.post(
                '/auth/refresh',
                { refreshToken }
              )

              const newToken = response.data.token || response.data.data?.token || (response.data as any)?.token
              if (newToken) {
                this.tokenManager.setToken(newToken)
                if (response.data.refreshToken || response.data.data?.refreshToken || (response.data as any)?.refreshToken) {
                  const newRefreshToken = response.data.refreshToken || response.data.data?.refreshToken || (response.data as any)?.refreshToken
                  this.tokenManager.setRefreshToken(newRefreshToken)
                }
                
                // Notify AuthContext about token refresh
                if (this.onTokenRefresh) {
                  this.onTokenRefresh(newToken)
                }
                
                originalRequest.headers.Authorization = `Bearer ${newToken}`
                return this.client(originalRequest)
              }
            }
          } catch (refreshError: any) {
            // Refresh failed, trigger logout
            this.handleLogout()
            return Promise.reject(refreshError)
          }
        }

        // If refresh didn't work or other 401/403 error, trigger logout
        if (status === 401 || status === 403) {
          this.handleLogout()
        }

        return Promise.reject(error)
      }
    )
  }

  async get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<any> = await this.client.get(url, config)
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (array, object, etc.), wrap it in ApiResponse format
    return { data: responseData } as ApiResponse<T>
  }

  async post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<any> = await this.client.post(url, data, config)
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (object, etc.), wrap it in ApiResponse format
    return { data: responseData } as ApiResponse<T>
  }

  async postForm<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig & { onUploadProgress?: (progressEvent: { loaded: number; total: number }) => void }): Promise<ApiResponse<T>> {
    const response: AxiosResponse<any> = await this.client.post(url, formData, {
      ...config,
      // Use config timeout if provided, otherwise use default (30000ms)
      timeout: config?.timeout ?? this.client.defaults.timeout,
      headers: {
        ...config?.headers,
        'Content-Type': 'multipart/form-data',
      },
      // Ensure progress tracking works for large files
      maxContentLength: Infinity,
      maxBodyLength: Infinity,
      onUploadProgress: config?.onUploadProgress ? (progressEvent) => {
        // Ensure we always have valid progress values
        const loaded: number = progressEvent.loaded || 0
        
        // Handle undefined total - ensure we always have a number
        // If total is undefined or 0, use loaded as fallback (shows progress but not percentage)
        const totalValue = progressEvent.total
        const total: number = (typeof totalValue === 'number' && totalValue > 0)
          ? totalValue
          : loaded // Fallback to loaded if total is not available
        
        // Call the progress callback with normalized values (both are guaranteed to be numbers)
        if (config?.onUploadProgress) {
          config.onUploadProgress({
            loaded,
            total
          } as { loaded: number; total: number })
        }
      } : undefined,
    })
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (object, etc.), wrap it in ApiResponse format
    return { data: responseData } as ApiResponse<T>
  }

  async put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<any> = await this.client.put(url, data, config)
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (object, etc.), wrap it in ApiResponse format
    return { data: responseData } as ApiResponse<T>
  }

  async patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<any> = await this.client.patch(url, data, config)
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (object, etc.), wrap it in ApiResponse format
    return { data: responseData } as ApiResponse<T>
  }

  async delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response: AxiosResponse<any> = await this.client.delete(url, config)
    const responseData = response.data
    
    // If the response is already in ApiResponse format { data: ... }, return it
    if (responseData && typeof responseData === 'object' && 'data' in responseData && !Array.isArray(responseData)) {
      return responseData as ApiResponse<T>
    }
    
    // If the response is a direct value (object, etc.), wrap it in ApiResponse format
    return { data: responseData } as ApiResponse<T>
  }

  setBaseURL(baseURL: string) {
    this.client.defaults.baseURL = baseURL
  }

  getBaseURL(): string {
    return this.client.defaults.baseURL || ''
  }

  getToken(): string | null {
    return this.tokenManager.getToken()
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
    }
    
    // Notify AuthContext to update state
    if (this.onLogout) {
      this.onLogout()
    } else {
      // Fallback: redirect to login if no callback is set
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
    }
  }
}

export default ApiClient

