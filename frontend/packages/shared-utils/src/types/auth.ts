export type UserRole = 
  | 'SYSTEM_ADMIN'
  | 'TENANT_ADMIN'
  | 'CONTENT_MANAGER'
  | 'INSTRUCTOR'
  | 'STUDENT'
  | 'SUPPORT_STAFF'

export interface User {
  id: string
  name: string
  email: string
  phone?: string
  role: UserRole
  tenantId: string
  tenantName?: string
  tenantSlug?: string
  createdAt: string
  passwordResetRequired?: boolean
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface RegisterCredentials {
  name: string
  email: string
  password: string
  phone?: string
  role: 'TENANT_ADMIN' | 'CONTENT_MANAGER' | 'INSTRUCTOR' | 'STUDENT' | 'SUPPORT_STAFF'
}

export interface AuthResponse {
  token: string
  refreshToken: string
  type: string
  expiresIn: number
  user: User
  needsTenantSelection: boolean
  availableTenants: TenantInfo[] | null
}

export interface TenantInfo {
  id: string
  name: string
  slug: string
  isActive: boolean
}

export interface AuthError {
  code: string
  message: string
  details?: any
}


