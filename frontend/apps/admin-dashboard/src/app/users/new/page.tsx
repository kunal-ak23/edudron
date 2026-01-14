'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PasswordInput } from '@/components/ui/password-input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { apiClient, institutesApi } from '@/lib/api'
import type { Institute } from '@kunal-ak23/edudron-shared-utils'
import { Checkbox } from '@/components/ui/checkbox'

export const dynamic = 'force-dynamic'

interface CreateUserRequest {
  email: string
  password: string
  name: string
  phone?: string
  role: string
  instituteIds?: string[]
  active?: boolean
}

export default function NewUserPage() {
  const router = useRouter()
  const { user } = useAuth()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loadingInstitutes, setLoadingInstitutes] = useState(true)
  const [selectedInstituteIds, setSelectedInstituteIds] = useState<string[]>([])
  const [formData, setFormData] = useState<CreateUserRequest>({
    email: '',
    password: '',
    name: '',
    phone: '',
    role: 'STUDENT',
    instituteIds: [],
    active: true
  })
  
  const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
  const showInstituteSelection = formData.role !== 'SYSTEM_ADMIN'
  
  useEffect(() => {
    const loadInstitutes = async () => {
      try {
        const data = await institutesApi.listInstitutes()
        setInstitutes(data)
      } catch (err) {
        console.error('Failed to load institutes:', err)
      } finally {
        setLoadingInstitutes(false)
      }
    }
    
    if (showInstituteSelection) {
      loadInstitutes()
    }
  }, [showInstituteSelection])
  
  useEffect(() => {
    setFormData(prev => ({
      ...prev,
      instituteIds: selectedInstituteIds
    }))
  }, [selectedInstituteIds])

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: value
    }))
  }

  const handleRoleChange = (value: string) => {
    setFormData(prev => ({
      ...prev,
      role: value
    }))
    // Clear institute selection if switching to SYSTEM_ADMIN
    if (value === 'SYSTEM_ADMIN') {
      setSelectedInstituteIds([])
    }
  }
  
  const handleInstituteToggle = (instituteId: string) => {
    setSelectedInstituteIds(prev => {
      if (prev.includes(instituteId)) {
        return prev.filter(id => id !== instituteId)
      } else {
        return [...prev, instituteId]
      }
    })
  }

  const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, checked } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: checked
    }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    
    // Validate institute selection for non-SYSTEM_ADMIN users
    if (showInstituteSelection && selectedInstituteIds.length === 0) {
      setError('Please select at least one institute')
      return
    }
    
    setLoading(true)

    try {
      const submitData = {
        ...formData,
        instituteIds: showInstituteSelection ? selectedInstituteIds : undefined
      }
      await apiClient.post('/idp/users', submitData)
      router.push('/users')
    } catch (err: any) {
      console.error('Failed to create user:', err)
      setError(err.response?.data?.message || err.message || 'Failed to create user')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="bg-white shadow-sm border-b border-gray-200">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <div className="flex items-center space-x-8">
                <h1
                  className="text-2xl font-bold text-blue-600 cursor-pointer"
                  onClick={() => router.push('/dashboard')}
                >
                  EduDron Admin
                </h1>
                <nav className="hidden md:flex space-x-6">
                  <button
                    onClick={() => router.push('/dashboard')}
                    className="text-gray-700 hover:text-blue-600"
                  >
                    Dashboard
                  </button>
                  <button
                    onClick={() => router.push('/users')}
                    className="text-gray-700 hover:text-blue-600 font-medium"
                  >
                    Users
                  </button>
                </nav>
              </div>
            </div>
          </div>
        </header>

        <main className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="mb-6">
            <Button variant="ghost" onClick={() => router.back()}>
              ← Back to Users
            </Button>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Create New User</CardTitle>
            </CardHeader>
            <CardContent>
              {error && (
                <div className="mb-4 p-4 bg-destructive/10 border border-destructive/20 rounded-md">
                  <p className="text-sm text-destructive">{error}</p>
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-6">
                <div className="space-y-2">
                  <Label htmlFor="name">
                    Full Name <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="name"
                    name="name"
                    type="text"
                    value={formData.name}
                    onChange={handleInputChange}
                    required
                    placeholder="John Doe"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="email">
                    Email <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    value={formData.email}
                    onChange={handleInputChange}
                    required
                    placeholder="john.doe@example.com"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="password">
                    Password <span className="text-destructive">*</span>
                  </Label>
                  <PasswordInput
                    id="password"
                    name="password"
                    value={formData.password}
                    onChange={handleInputChange}
                    required
                    minLength={8}
                    placeholder="At least 8 characters"
                  />
                  <p className="text-sm text-muted-foreground">
                    Password must be at least 8 characters long
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="phone">Phone (Optional)</Label>
                  <Input
                    id="phone"
                    name="phone"
                    type="tel"
                    value={formData.phone}
                    onChange={handleInputChange}
                    placeholder="+1234567890"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="role">
                    Role <span className="text-destructive">*</span>
                  </Label>
                  <Select value={formData.role} onValueChange={handleRoleChange}>
                    <SelectTrigger id="role">
                      <SelectValue placeholder="Select a role" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="STUDENT">Student</SelectItem>
                      <SelectItem value="INSTRUCTOR">Instructor</SelectItem>
                      <SelectItem value="CONTENT_MANAGER">Content Manager</SelectItem>
                      <SelectItem value="TENANT_ADMIN">Tenant Admin</SelectItem>
                      <SelectItem value="SUPPORT_STAFF">Support Staff</SelectItem>
                      {isSystemAdmin && (
                        <SelectItem value="SYSTEM_ADMIN">System Admin</SelectItem>
                      )}
                    </SelectContent>
                  </Select>
                  {!isSystemAdmin && (
                    <p className="text-sm text-muted-foreground">
                      Note: SYSTEM_ADMIN can only be created by existing SYSTEM_ADMIN users
                    </p>
                  )}
                  {isSystemAdmin && formData.role === 'SYSTEM_ADMIN' && (
                    <p className="text-sm text-muted-foreground">
                      System Admin users have access to all tenants and institutes
                    </p>
                  )}
                </div>

                {showInstituteSelection && (
                  <div className="space-y-2">
                    <Label>
                      Institutes <span className="text-destructive">*</span>
                    </Label>
                    {loadingInstitutes ? (
                      <p className="text-sm text-muted-foreground">Loading institutes...</p>
                    ) : institutes.length === 0 ? (
                      <p className="text-sm text-muted-foreground">No institutes available. Please create an institute first.</p>
                    ) : (
                      <div className="border rounded-md p-4 max-h-60 overflow-y-auto space-y-2">
                        {institutes.map((institute) => (
                          <div key={institute.id} className="flex items-center space-x-2">
                            <Checkbox
                              id={`institute-${institute.id}`}
                              checked={selectedInstituteIds.includes(institute.id)}
                              onCheckedChange={() => handleInstituteToggle(institute.id)}
                            />
                            <Label
                              htmlFor={`institute-${institute.id}`}
                              className="font-normal cursor-pointer flex-1"
                            >
                              {institute.name} ({institute.code})
                            </Label>
                          </div>
                        ))}
                      </div>
                    )}
                    <p className="text-sm text-muted-foreground">
                      Select at least one institute for this user
                    </p>
                  </div>
                )}

                <div className="flex items-center space-x-2">
                  <input
                    id="active"
                    name="active"
                    type="checkbox"
                    checked={formData.active}
                    onChange={handleCheckboxChange}
                    className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                  />
                  <Label htmlFor="active" className="font-normal cursor-pointer">
                    Active (User can login)
                  </Label>
                </div>

                <div className="flex justify-end space-x-4 pt-4">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => router.back()}
                    disabled={loading}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" disabled={loading}>
                    {loading ? 'Creating...' : 'Create User'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </main>
      </div>
  )
}

