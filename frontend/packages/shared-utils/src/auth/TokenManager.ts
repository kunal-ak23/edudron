export class TokenManager {
  private tokenKey = 'auth_token'
  private refreshTokenKey = 'refresh_token'

  getToken(): string | null {
    if (typeof window === 'undefined') return null
    return localStorage.getItem(this.tokenKey)
  }

  getRefreshToken(): string | null {
    if (typeof window === 'undefined') return null
    return localStorage.getItem(this.refreshTokenKey)
  }

  setToken(token: string): void {
    if (typeof window === 'undefined') return
    localStorage.setItem(this.tokenKey, token)
  }

  setRefreshToken(refreshToken: string): void {
    if (typeof window === 'undefined') return
    localStorage.setItem(this.refreshTokenKey, refreshToken)
  }

  clearToken(): void {
    if (typeof window === 'undefined') return
    localStorage.removeItem(this.tokenKey)
    localStorage.removeItem(this.refreshTokenKey)
  }
}

export default TokenManager


