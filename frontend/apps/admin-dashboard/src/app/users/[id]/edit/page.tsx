'use client'

import { useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { Loader2 } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

// Platform-side roles that only SYSTEM_ADMIN can modify
const PLATFORM_SIDE_ROLES = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER']
// University-side roles that TENANT_ADMIN can modify
const UNIVERSITY_SIDE_ROLES = ['INSTRUCTOR', 'SUPPORT_STAFF', 'STUDENT']

interface UpdateUserRequest {
  email: string
  name: string
  phone?: string
  role: string
  instituteIds?: string[]
  active?: boolean
}

interface User {
  id: string
  email: string
  name: string
  phone?: string
  role: string
  instituteIds?: string[]
  active: boolean
}

export default function EditUserPage() {
  const router = useRouter()
  const params = useParams()
  const userId = params?.id as string
  const { toast } = useToast()
  const { user } = useAuth()
  const [loading, setLoading] = useState(false)
  const [loadingUser, setLoadingUser] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loadingInstitutes, setLoadingInstitutes] = useState(true)
  const [selectedInstituteIds, setSelectedInstituteIds] = useState<string[]>([])
  const [formData, setFormData] = useState<UpdateUserRequest>({
    email: '',
    name: '',
    phone: '',
    role: 'STUDENT',
    instituteIds: [],
    active: true
  })
  
  const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
  const isTenantAdmin = user?.role === 'TENANT_ADMIN'
  const canManageUsers = isSystemAdmin || isTenantAdmin
  const showInstituteSelection = formData.role !== 'SYSTEM_ADMIN'
  
  // Check if the user being edited is a platform-side role
  const isEditingPlatformSideUser = PLATFORM_SIDE_ROLES.includes(formData.role)
  
  // Check permissions
  useEffect(() => {
    if (!canManageUsers) {
      router.push('/unauthorized')
      return
    }
  }, [canManageUsers, router])
  
  // Load user data
  useEffect(() => {
    const loadUser = async () => {
      if (!userId) return
      
      try {
        setLoadingUser(true)
        const response = await apiClient.get<User>(`/idp/users/${userId}`)
        const userData = response.data
        
        // Check if TENANT_ADMIN is trying to edit a platform-side user
        if (isTenantAdmin && PLATFORM_SIDE_ROLES.includes(userData.role)) {
          toast({
            variant: 'destructive',
            title: 'Access Denied',
            description: 'You can only edit university-side users (Student, Instructor, Support Staff)',
          })
          router.push('/users')
          return
        }
        
        setFormData({
          email: userData.email || '',
          name: userData.name || '',
          phone: userData.phone || '',
          role: userData.role || 'STUDENT',
          instituteIds: userData.instituteIds || [],
          active: userData.active !== undefined ? userData.active : true
        })
        
        setSelectedInstituteIds(userData.instituteIds || [])
      } catch (err: any) {
        const errorMessage = extractErrorMessage(err)
        toast({
          variant: 'destructive',
          title: 'Failed to load user',
          description: errorMessage,
        })
        router.push('/users')
      } finally {
        setLoadingUser(false)
      }
    }
    
    loadUser()
  }, [userId, router, toast, isTenantAdmin])
  
  // Load institutes
  useEffect(() => {
    const loadInstitutes = async () => {
      try {
        const data = await institutesApi.listInstitutes()
        setInstitutes(data)
      } catch (err) {
      } finally {
        setLoadingInstitutes(false)
      }
    }
    
    if (showInstituteSelection) {
      loadInstitutes()
    } else {
      setLoadingInstitutes(false)
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
      await apiClient.put(`/idp/users/${userId}`, submitData)
      toast({
        title: 'User updated',
        description: 'User has been successfully updated.',
      })
      router.push('/users')
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      setError(errorMessage)
      toast({
        variant: 'destructive',
        title: 'Failed to update user',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }

  if (loadingUser) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto py-8">
      <div className="mb-6">
        <Button variant="ghost" onClick={() => router.push('/users')}>
          ← Back to Users
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Edit User</CardTitle>
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
              <Select 
                value={formData.role} 
                onValueChange={handleRoleChange}
                disabled={isEditingPlatformSideUser && !isSystemAdmin}
              >
                <SelectTrigger id="role">
                  <SelectValue placeholder="Select a role" />
                </SelectTrigger>
                <SelectContent>
                  {/* University-side roles - available to both SYSTEM_ADMIN and TENANT_ADMIN */}
                  <SelectItem value="STUDENT">Student</SelectItem>
                  <SelectItem value="INSTRUCTOR">Instructor</SelectItem>
                  <SelectItem value="SUPPORT_STAFF">Support Staff</SelectItem>
                  
                  {/* Platform-side roles - only available to SYSTEM_ADMIN */}
                  {isSystemAdmin && (
                    <>
                      <SelectItem value="CONTENT_MANAGER">Content Manager</SelectItem>
                      <SelectItem value="TENANT_ADMIN">Tenant Admin</SelectItem>
                      <SelectItem value="SYSTEM_ADMIN">System Admin</SelectItem>
                    </>
                  )}
                </SelectContent>
              </Select>
              {isTenantAdmin && isEditingPlatformSideUser && (
                <p className="text-sm text-destructive">
                  You cannot modify platform-side users. Only SYSTEM_ADMIN can modify these roles.
                </p>
              )}
              {isTenantAdmin && !isEditingPlatformSideUser && (
                <p className="text-sm text-muted-foreground">
                  You can only assign university-side roles (Student, Instructor, Support Staff)
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
                onClick={() => router.push('/users')}
                disabled={loading}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={loading}>
                {loading ? 'Updating...' : 'Update User'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
