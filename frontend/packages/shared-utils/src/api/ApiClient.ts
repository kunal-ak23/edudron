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

        // If 401 and not already retrying, try to refresh token
        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true

          try {
            const refreshToken = this.tokenManager.getRefreshToken()
            if (refreshToken) {
              const response = await axios.post(
                `${this.client.defaults.baseURL}/auth/refresh`,
                null,
                {
                  params: { refreshToken }
                }
              )

              const newToken = response.data.token || response.data.data?.token
              if (newToken) {
                this.tokenManager.setToken(newToken)
                if (response.data.refreshToken || response.data.data?.refreshToken) {
                  this.tokenManager.setRefreshToken(response.data.refreshToken || response.data.data.refreshToken)
                }
                
                originalRequest.headers.Authorization = `Bearer ${newToken}`
                return this.client(originalRequest)
              }
            }
          } catch (refreshError) {
            // Refresh failed, clear tokens and redirect to login
            this.tokenManager.clearToken()
            if (typeof window !== 'undefined') {
              window.location.href = '/login'
            }
            return Promise.reject(refreshError)
          }
        }

        // If refresh didn't work or other error, clear token and redirect
        if (error.response?.status === 401) {
          this.tokenManager.clearToken()
          if (typeof window !== 'undefined') {
            window.location.href = '/login'
          }
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
}

export default ApiClient

