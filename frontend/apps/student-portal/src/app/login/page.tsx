'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button, Input, PasswordInput } from '@kunal-ak23/edudron-ui-components'
import { authService } from '@/lib/auth'
import type { LoginCredentials, RegisterCredentials } from '@kunal-ak23/edudron-shared-utils'

function WelcomeIllustration({ className }: { className?: string }) {
  return (
    <img
      src="/login-rafiki.svg"
      alt=""
      aria-hidden="true"
      className={className ?? 'w-full h-auto'}
    />
  )
}

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

      // Check if password reset is required - redirect to profile page
      if (response.user?.passwordResetRequired) {
        router.push('/profile')
        return
      }

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
    <div className="min-h-screen flex flex-col bg-gradient-to-br from-primary-50 via-white to-primary-100">
      <div className="flex-1 flex items-center justify-center px-4 py-10 sm:px-6 lg:px-10">
        <div className="w-full max-w-6xl">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 lg:gap-14 items-center">
            {/* Left illustration panel (desktop) */}
            <div className="hidden lg:flex flex-col items-center text-center">
              <div className="text-2xl font-extrabold tracking-tight text-gray-900">
                EduDron
              </div>

              <div className="mt-6 w-full max-w-xl flex justify-center">
                <WelcomeIllustration className="w-full max-w-[520px]" />
              </div>

              <div className="mt-10">
                <h1 className="text-5xl font-extrabold tracking-tight text-gray-900">
                  Welcome!
                </h1>
              </div>
            </div>

            {/* Right form panel */}
            <div className="flex justify-center lg:justify-end">
              <div className="w-full max-w-md rounded-2xl bg-white shadow-lg border border-primary-100 p-8 sm:p-10">
                {/* Mobile header */}
                <div className="lg:hidden flex items-center justify-center mb-8 text-2xl font-extrabold tracking-tight text-gray-900">
                  EduDron
                </div>

                <div className="space-y-1">
                  <h2 className="text-2xl font-bold text-gray-900">
                    {isLogin ? 'Sign In' : 'Sign Up'}
                  </h2>
                  <p className="text-sm text-gray-600">
                    {isLogin ? 'to access Account' : 'to create your Account'}
                  </p>
                </div>

                <form
                  className="mt-8 space-y-6"
                  onSubmit={isLogin ? handleLogin : handleRegister}
                >
                  {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl">
                      {error}
                    </div>
                  )}

                  <div className="space-y-4">
                    {!isLogin && (
                      <div>
                        <label htmlFor="name" className="sr-only">
                          Full name
                        </label>
                        <Input
                          id="name"
                          value={name}
                          onChange={(e) => setName(e.target.value)}
                          required
                          autoComplete="name"
                          placeholder="Full name"
                          aria-label="Full name"
                          className="rounded-xl py-3"
                        />
                      </div>
                    )}

                    <div>
                      <label htmlFor="email" className="sr-only">
                        Email address
                      </label>
                      <Input
                        id="email"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        autoComplete="email"
                        placeholder="Email address"
                        aria-label="Email address"
                        className="rounded-xl py-3"
                      />
                    </div>

                    <div>
                      <label htmlFor="password" className="sr-only">
                        Password
                      </label>
                      <PasswordInput
                        id="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        autoComplete={isLogin ? 'current-password' : 'new-password'}
                        placeholder="Password"
                        aria-label="Password"
                        className="rounded-xl py-3"
                      />
                    </div>
                  </div>

                  <Button
                    type="submit"
                    variant="primary"
                    size="lg"
                    className="w-full"
                    loading={loading}
                  >
                    {isLogin ? 'Continue' : 'Create account'}
                  </Button>

                  <div className="text-center">
                    <button
                      type="button"
                      onClick={() => {
                        setIsLogin(!isLogin)
                        setError('')
                      }}
                      className="text-sm font-medium text-primary-700 hover:text-primary-800 transition-colors"
                    >
                      {isLogin
                        ? "New in EduDron? Create an account"
                        : 'Already have an account? Sign in'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>
      </div>

      <footer className="border-t border-primary-100 bg-white/70 backdrop-blur">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-10 py-3 flex items-center justify-between text-xs text-gray-600">
          <div>© 2026 Datagami Technology Services Private Limited</div>
          <div className="flex items-center gap-6">
            <span>Privacy</span>
            <span>Terms &amp; conditions</span>
          </div>
        </div>
      </footer>
    </div>
  )
}

