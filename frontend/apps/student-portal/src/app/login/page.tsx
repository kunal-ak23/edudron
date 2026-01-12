'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button, Input } from '@edudron/ui-components'
import { authService } from '@/lib/auth'
import type { LoginCredentials, RegisterCredentials } from '@edudron/shared-utils'

export default function LoginPage() {
  const router = useRouter()
  const [isLogin, setIsLogin] = useState(true)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const credentials: LoginCredentials = { email, password }
      const response = await authService.login(credentials)

      if (response.needsTenantSelection && response.availableTenants) {
        router.push('/select-tenant')
      } else {
        router.push('/courses')
      }
    } catch (err: any) {
      // Show user-friendly error message
      const errorMessage = err.message || 'Login failed. Please try again.'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const credentials: RegisterCredentials = {
        name,
        email,
        password,
        role: 'STUDENT'
      }
      await authService.register(credentials)
      router.push('/courses')
    } catch (err: any) {
      // Show user-friendly error message
      const errorMessage = err.message || 'Registration failed. Please try again.'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 via-white to-primary-100">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-xl shadow-lg border border-primary-100">
        <div>
          <h2 className="mt-6 text-center text-3xl font-extrabold text-primary-600">
            EduDron
          </h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            {isLogin ? 'Sign in to your account' : 'Create a new account'}
          </p>
        </div>
        <form
          className="mt-8 space-y-6"
          onSubmit={isLogin ? handleLogin : handleRegister}
        >
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
              {error}
            </div>
          )}
          <div className="space-y-4">
            {!isLogin && (
              <Input
                label="Name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoComplete="name"
              />
            )}
            <Input
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
            <Input
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete={isLogin ? 'current-password' : 'new-password'}
            />
          </div>
          <div>
            <Button 
              type="submit" 
              variant="primary"
              className="w-full font-semibold" 
              loading={loading}
            >
              {isLogin ? 'Sign in' : 'Sign up'}
            </Button>
          </div>
          <div className="text-center">
            <button
              type="button"
              onClick={() => {
                setIsLogin(!isLogin)
                setError('')
              }}
              className="text-sm font-medium text-primary-600 hover:text-primary-700 transition-colors"
            >
              {isLogin
                ? "Don't have an account? Sign up"
                : 'Already have an account? Sign in'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

