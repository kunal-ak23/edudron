'use client'

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { AuthService } from './AuthService'
import type { User, LoginCredentials, AuthResponse, TenantInfo } from '../types/auth'

interface AuthContextType {
  user: User | null
  token: string | null
  tenantId: string | null
  isLoading: boolean
  needsTenantSelection: boolean
  availableTenants: TenantInfo[] | null
  login: (credentials: LoginCredentials) => Promise<AuthResponse>
  logout: () => Promise<void>
  selectTenant: (tenantId: string) => Promise<void>
  isAuthenticated: () => boolean
  updateToken: (token: string) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

interface AuthProviderProps {
  children: ReactNode
  baseUrl?: string
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children, baseUrl = '' }) => {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [tenantId, setTenantId] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [needsTenantSelection, setNeedsTenantSelection] = useState(false)
  const [availableTenants, setAvailableTenants] = useState<TenantInfo[] | null>(null)

  const authService = new AuthService(baseUrl)

  useEffect(() => {
    const initializeAuth = () => {
      try {
        // Check localStorage for stored auth data
        const storedUser = localStorage.getItem('user')
        const storedToken = localStorage.getItem('auth_token')
        const storedTenantId = localStorage.getItem('tenant_id')
        const storedAvailableTenants = localStorage.getItem('available_tenants')

        if (storedUser && storedToken) {
          const userData = JSON.parse(storedUser)
          setUser(userData)
          setToken(storedToken)
          
          // Check if tenant is selected (exclude "PENDING_TENANT_SELECTION" and "SYSTEM")
          const currentTenantId = storedTenantId || userData.tenantId
          if (currentTenantId && 
              currentTenantId !== 'PENDING_TENANT_SELECTION' && 
              currentTenantId !== 'SYSTEM' &&
              currentTenantId !== 'null' &&
              currentTenantId !== '') {
            setTenantId(currentTenantId)
            setNeedsTenantSelection(false)
          } else {
            // User is logged in but no tenant selected or pending selection
            setNeedsTenantSelection(true)
            if (storedAvailableTenants) {
              setAvailableTenants(JSON.parse(storedAvailableTenants))
            }
          }
        }
      } catch (error) {
        console.error('Failed to initialize auth:', error)
      } finally {
        setIsLoading(false)
      }
    }

    initializeAuth()
  }, [])

  const login = async (credentials: LoginCredentials): Promise<AuthResponse> => {
    try {
      setIsLoading(true)
      const response = await authService.login(credentials)
      
      setUser(response.user)
      setToken(response.token)
      
      // Store available tenants if provided
      if (response.availableTenants) {
        setAvailableTenants(response.availableTenants)
        localStorage.setItem('available_tenants', JSON.stringify(response.availableTenants))
      }
      
      // Check if tenant selection is needed
      // Backend sets tenantId to "PENDING_TENANT_SELECTION" when selection is needed
      const tenantId = response.user.tenantId
      const isPendingSelection = tenantId === 'PENDING_TENANT_SELECTION' || 
                                 tenantId === 'SYSTEM' ||
                                 !tenantId ||
                                 tenantId === 'null' ||
                                 tenantId === ''
      
      if (response.needsTenantSelection || isPendingSelection) {
        setNeedsTenantSelection(true)
        setTenantId(null)
      } else {
        setNeedsTenantSelection(false)
        setTenantId(tenantId)
        setAvailableTenants(null)
      }
      
      return response
    } catch (error) {
      throw error
    } finally {
      setIsLoading(false)
    }
  }

  const logout = async () => {
    try {
      await authService.logout()
    } catch (error) {
      console.error('Logout failed:', error)
    } finally {
      // Clear React state
      setUser(null)
      setToken(null)
      setTenantId(null)
      setNeedsTenantSelection(false)
      setAvailableTenants(null)
      
      // Clear localStorage
      localStorage.removeItem('auth_token')
      localStorage.removeItem('refresh_token')
      localStorage.removeItem('tenant_id')
      localStorage.removeItem('user')
      localStorage.removeItem('available_tenants')
      localStorage.removeItem('clientId')
      localStorage.removeItem('selectedTenantId')
      
      // Redirect to login page
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
    }
  }

  const selectTenant = async (selectedTenantId: string) => {
    try {
      setIsLoading(true)
      await authService.selectTenant(selectedTenantId)
      
      // Reload user from localStorage (AuthService.selectTenant refreshes /idp/users/me)
      try {
        const userStr = localStorage.getItem('user')
        if (userStr) {
          const updatedUser = JSON.parse(userStr)
          setUser(updatedUser)
        } else if (user) {
          const updatedUser = { ...user, tenantId: selectedTenantId }
          setUser(updatedUser)
          localStorage.setItem('user', JSON.stringify(updatedUser))
        }
      } catch (e) {
        if (user) {
          const updatedUser = { ...user, tenantId: selectedTenantId }
          setUser(updatedUser)
          localStorage.setItem('user', JSON.stringify(updatedUser))
        }
      }
      
      // Store tenant ID in all possible keys for compatibility
      if (typeof window !== 'undefined') {
        localStorage.setItem('tenant_id', selectedTenantId)
        localStorage.setItem('clientId', selectedTenantId)
        localStorage.setItem('selectedTenantId', selectedTenantId)
      }
      
      setTenantId(selectedTenantId)
      setNeedsTenantSelection(false)
      setAvailableTenants(null)
      localStorage.removeItem('available_tenants')
    } catch (error) {
      throw error
    } finally {
      setIsLoading(false)
    }
  }

  const isAuthenticated = (): boolean => {
    return !!token && !!user
  }

  const updateToken = (newToken: string) => {
    setToken(newToken)
    if (typeof window !== 'undefined') {
      localStorage.setItem('auth_token', newToken)
    }
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        tenantId,
        isLoading,
        needsTenantSelection,
        availableTenants,
        login,
        logout,
        selectTenant,
        isAuthenticated,
        updateToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

