import { ApiClient } from '../api/ApiClient'
import { 
  User, 
  LoginCredentials, 
  RegisterCredentials, 
  AuthResponse, 
  AuthError 
} from '../types/auth'

export class AuthService {
  private apiClient: ApiClient
  private baseUrl: string

  constructor(baseUrl: string = '') {
    this.baseUrl = baseUrl
    this.apiClient = new ApiClient(baseUrl)
  }

  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    try {
      const response = await this.apiClient.post<AuthResponse>('/auth/login', {
        email: credentials.email,
        password: credentials.password
      })

      const authData = response.data || response
      
      // Store tokens
      if (authData.token) {
        this.apiClient.setToken(authData.token)
        if (typeof window !== 'undefined') {
          localStorage.setItem('auth_token', authData.token)
          if (authData.refreshToken) {
            localStorage.setItem('refresh_token', authData.refreshToken)
          }
          if (authData.availableTenants) {
            localStorage.setItem('available_tenants', JSON.stringify(authData.availableTenants))
          }
          if (authData.user) {
            localStorage.setItem('user', JSON.stringify(authData.user))
            if (authData.user.tenantId) {
              localStorage.setItem('tenant_id', authData.user.tenantId)
            }
          }
        }
      }

      return authData
    } catch (error: any) {
      // Extract error message from response
      let errorMessage = 'Login failed. Please check your credentials and try again.'
      
      if (error.response?.data) {
        const errorData = error.response.data
        // Check for specific error messages
        if (errorData.error) {
          errorMessage = errorData.error
        } else if (errorData.message) {
          errorMessage = errorData.message
        }
        // Check for error code
        if (errorData.code === 'INVALID_CREDENTIALS') {
          errorMessage = 'Invalid email or password. Please try again.'
        }
      } else if (error.message) {
        // Use error message if available
        if (error.message.includes('Invalid') || error.message.includes('credentials')) {
          errorMessage = 'Invalid email or password. Please try again.'
        } else {
          errorMessage = error.message
        }
      }
      
      throw new Error(errorMessage)
    }
  }

  async register(credentials: RegisterCredentials): Promise<AuthResponse> {
    try {
      const response = await this.apiClient.post<AuthResponse>('/auth/register', {
        name: credentials.name,
        email: credentials.email,
        password: credentials.password,
        phone: credentials.phone,
        role: credentials.role
      })

      const authData = response.data || response
      
      // Store tokens
      if (authData.token) {
        this.apiClient.setToken(authData.token)
        if (typeof window !== 'undefined') {
          localStorage.setItem('auth_token', authData.token)
          if (authData.refreshToken) {
            localStorage.setItem('refresh_token', authData.refreshToken)
          }
          if (authData.user) {
            localStorage.setItem('user', JSON.stringify(authData.user))
            if (authData.user.tenantId) {
              localStorage.setItem('tenant_id', authData.user.tenantId)
            }
          }
        }
      }

      return authData
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || error.message || 'Registration failed'
      throw new Error(errorMessage)
    }
  }

  async refreshToken(): Promise<string> {
    try {
      const refreshToken = typeof window !== 'undefined' 
        ? localStorage.getItem('refresh_token') 
        : null
      
      if (!refreshToken) {
        throw new Error('No refresh token available')
      }

      const response = await this.apiClient.post<{ token: string; refreshToken?: string }>(
        '/auth/refresh',
        null,
        {
          params: { refreshToken }
        }
      )

      const newToken = response.data?.token || (response as any).token
      if (newToken) {
        this.apiClient.setToken(newToken)
        if (typeof window !== 'undefined') {
          localStorage.setItem('auth_token', newToken)
          if (response.data?.refreshToken || (response as any).refreshToken) {
            localStorage.setItem('refresh_token', response.data?.refreshToken || (response as any).refreshToken)
          }
        }
        return newToken
      }

      throw new Error('Token refresh failed')
    } catch (error: any) {
      this.logout()
      throw new Error('Token refresh failed')
    }
  }

  async logout(): Promise<void> {
    try {
      // Call logout endpoint if available
      await this.apiClient.post('/auth/logout')
    } catch (error) {
      // Continue with local logout even if API call fails
      console.error('Logout API call failed:', error)
    } finally {
      // Clear local storage
      this.apiClient.clearToken()
      if (typeof window !== 'undefined') {
        localStorage.removeItem('auth_token')
        localStorage.removeItem('refresh_token')
        localStorage.removeItem('user')
        localStorage.removeItem('tenant_id')
      }
    }
  }

  getCurrentUser(): User | null {
    if (typeof window === 'undefined') return null
    
    try {
      const userStr = localStorage.getItem('user')
      if (userStr) {
        return JSON.parse(userStr)
      }
    } catch (error) {
      console.error('Failed to parse user from localStorage:', error)
    }
    
    return null
  }

  isAuthenticated(): boolean {
    if (typeof window === 'undefined') return false
    const token = localStorage.getItem('auth_token')
    return !!token
  }

  async selectTenant(tenantId: string): Promise<void> {
    // Store tenant ID in localStorage for API client to use (store in multiple keys for compatibility)
    if (typeof window !== 'undefined') {
      localStorage.setItem('tenant_id', tenantId)
      localStorage.setItem('clientId', tenantId)
      localStorage.setItem('selectedTenantId', tenantId)
      localStorage.removeItem('available_tenants')
      
      // Update user object with tenant ID
      const userStr = localStorage.getItem('user')
      if (userStr) {
        try {
          const user = JSON.parse(userStr)
          user.tenantId = tenantId
          localStorage.setItem('user', JSON.stringify(user))
        } catch (e) {
          console.error('Failed to update user with tenant ID:', e)
        }
      }
    }
    
    // Refresh user info under the selected tenant (keeps flags like passwordResetRequired accurate)
    try {
      const meResponse = await this.apiClient.get<any>('/idp/users/me')
      const me = (meResponse as any)?.data ?? meResponse
      if (typeof window !== 'undefined' && me && typeof me === 'object') {
        const existingUserStr = localStorage.getItem('user')
        const existingUser = existingUserStr ? JSON.parse(existingUserStr) : {}
        const mergedUser = { ...existingUser, ...me, tenantId }
        localStorage.setItem('user', JSON.stringify(mergedUser))
      }
    } catch (e) {
      // If /me fails, continue; tenant header will still scope subsequent API calls.
    }
  }
}

export default AuthService

