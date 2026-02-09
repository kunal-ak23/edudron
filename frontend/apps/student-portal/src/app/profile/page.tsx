'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button, Input, PasswordInput, Label, Card, CardHeader, CardTitle, CardDescription, CardContent, ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { apiClient } from '@/lib/api'
import { StudentLayout } from '@/components/StudentLayout'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

interface UserProfile {
  id: string
  name: string
  email: string
  phone?: string
  role: string
  active: boolean
  passwordResetRequired?: boolean
  instituteIds?: string[]
  createdAt: string
  lastLoginAt?: string
  // Student-specific fields
  classId?: string
  className?: string
  sectionId?: string
  sectionName?: string
}

export default function ProfilePage() {
  const router = useRouter()
  const { user: authUser, needsTenantSelection } = useAuth()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [changingPassword, setChangingPassword] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  
  // Password change form state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  useEffect(() => {
    if (needsTenantSelection) {
      router.replace('/select-tenant')
      setLoading(false)
      return
    }
    loadProfile()
  }, [])

  const loadProfile = async () => {
    try {
      setError('')
      const response = await apiClient.get<UserProfile>('/idp/users/me')
      const profileData = 'data' in response ? response.data : response
      setProfile(profileData)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || 'Failed to load profile'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    
    if (newPassword !== confirmPassword) {
      setError('New password and confirm password do not match')
      return
    }

    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long')
      return
    }

    setChangingPassword(true)
    try {
      await apiClient.put('/idp/users/me/password', {
        currentPassword,
        newPassword,
      })
      
      setSuccess('Your password has been successfully changed')
      
      // Clear form
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      
      // Fetch updated profile to get the cleared passwordResetRequired flag
      let updatedProfileData: UserProfile | null = null
      try {
        const updatedProfile = await apiClient.get<UserProfile>('/idp/users/me')
        updatedProfileData = 'data' in updatedProfile ? updatedProfile.data : updatedProfile
        setProfile(updatedProfileData)
      } catch (err) {
        // Still proceed with updating user data
      }
      
      // Update user in localStorage to sync with AuthContext
      // The backend clears passwordResetRequired after password change
      if (typeof window !== 'undefined') {
        const storedUser = localStorage.getItem('user')
        if (storedUser) {
          try {
            const userData = JSON.parse(storedUser)
            // Use the updated profile data or assume flag is cleared
            const passwordResetCleared = updatedProfileData?.passwordResetRequired === false || 
                                       (updatedProfileData === null && profile?.passwordResetRequired)
            
            // Update user object with cleared passwordResetRequired flag
            const updatedUser = {
              ...userData,
              passwordResetRequired: false // Backend always clears this after password change
            }
            localStorage.setItem('user', JSON.stringify(updatedUser))
            
            // If password reset was required, redirect after updating localStorage
            // Use window.location.href to force full page reload and refresh AuthContext
            if (profile?.passwordResetRequired) {
              setTimeout(() => {
                window.location.href = '/courses'
              }, 1000)
              return // Exit early since we're reloading
            }
          } catch (err) {
            // Still redirect even if update fails
            if (profile?.passwordResetRequired) {
              setTimeout(() => {
                window.location.href = '/courses'
              }, 1500)
            }
          }
        }
      }
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || 'Failed to change password'
      setError(errorMessage)
    } finally {
      setChangingPassword(false)
    }
  }

  const formatDate = (dateString?: string) => {
    if (!dateString) return 'Never'
    try {
      return new Date(dateString).toLocaleString()
    } catch {
      return dateString
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="container mx-auto py-8 px-4">
        <Card>
          <CardContent>
            <p className="text-gray-600">{error || 'Failed to load profile'}</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-8 space-y-6">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Profile</h1>
            <p className="text-gray-600 mt-1">View and manage your profile information</p>
          </div>

      {profile.passwordResetRequired && (
        <Card className="border-orange-500 bg-orange-50">
          <CardHeader>
            <CardTitle className="text-orange-700">Password Reset Required</CardTitle>
            <CardDescription className="text-orange-600">
              You must reset your password before continuing. Please set a new password below.
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {success && (
        <div className="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded">
          {success}
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-2">
        {/* Profile Information */}
        <Card>
          <CardHeader>
            <CardTitle>Profile Information</CardTitle>
            <CardDescription>Your account details</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label className="text-gray-600">Name</Label>
              <p className="text-sm font-medium mt-1 text-gray-900">{profile.name}</p>
            </div>
            <div>
              <Label className="text-gray-600">Email</Label>
              <p className="text-sm font-medium mt-1 text-gray-900">{profile.email}</p>
            </div>
            {profile.phone && (
              <div>
                <Label className="text-gray-600">Phone</Label>
                <p className="text-sm font-medium mt-1 text-gray-900">{profile.phone}</p>
              </div>
            )}
            <div>
              <Label className="text-gray-600">Role</Label>
              <p className="text-sm font-medium mt-1 text-gray-900">{profile.role}</p>
            </div>
            {profile.role?.toUpperCase() === 'STUDENT' && (
              <>
                {profile.className && (
                  <div>
                    <Label className="text-gray-600">Class</Label>
                    <p className="text-sm font-medium mt-1 text-gray-900">{profile.className}</p>
                  </div>
                )}
                {profile.sectionName && (
                  <div>
                    <Label className="text-gray-600">Section</Label>
                    <p className="text-sm font-medium mt-1 text-gray-900">{profile.sectionName}</p>
                  </div>
                )}
                {!profile.className && !profile.sectionName && (
                  <div>
                    <Label className="text-gray-600">Class/Section</Label>
                    <p className="text-sm font-medium mt-1 text-gray-500 italic">Not assigned</p>
                  </div>
                )}
              </>
            )}
            <div>
              <Label className="text-gray-600">Account Created</Label>
              <p className="text-sm font-medium mt-1 text-gray-900">{formatDate(profile.createdAt)}</p>
            </div>
            {profile.lastLoginAt && (
              <div>
                <Label className="text-gray-600">Last Login</Label>
                <p className="text-sm font-medium mt-1 text-gray-900">{formatDate(profile.lastLoginAt)}</p>
              </div>
            )}
            <div>
              <Label className="text-gray-600">Status</Label>
              <p className="text-sm font-medium mt-1">
                <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${
                  profile.active 
                    ? 'bg-green-100 text-green-800' 
                    : 'bg-red-100 text-red-800'
                }`}>
                  {profile.active ? 'Active' : 'Inactive'}
                </span>
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Change Password */}
        <Card>
          <CardHeader>
            <CardTitle>Change Password</CardTitle>
            <CardDescription>Update your account password</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handlePasswordChange} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="currentPassword">Current Password</Label>
                <PasswordInput
                  id="currentPassword"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required
                  placeholder="Enter current password"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="newPassword">New Password</Label>
                <PasswordInput
                  id="newPassword"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  minLength={8}
                  placeholder="Enter new password (min 8 characters)"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="confirmPassword">Confirm New Password</Label>
                <PasswordInput
                  id="confirmPassword"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  minLength={8}
                  placeholder="Confirm new password"
                />
              </div>

              <Button 
                type="submit" 
                variant="primary"
                className="w-full" 
                loading={changingPassword}
              >
                Change Password
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
