'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PasswordInput } from '@/components/ui/password-input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'
import { apiClient } from '@/lib/api'
import { extractErrorMessage } from '@/lib/error-utils'
import { Loader2, User, Lock } from 'lucide-react'

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
}

export default function ProfilePage() {
  const router = useRouter()
  const { user: authUser, logout } = useAuth()
  const { toast } = useToast()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [changingPassword, setChangingPassword] = useState(false)
  
  // Password change form state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const loadProfile = useCallback(async () => {
    try {
      const response = await apiClient.get<UserProfile>('/idp/users/me')
      const profileData = 'data' in response ? response.data : response
      setProfile(profileData)
    } catch (error: any) {
      const errorMessage = extractErrorMessage(error)
      toast({
        variant: 'destructive',
        title: 'Failed to load profile',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (newPassword !== confirmPassword) {
      toast({
        variant: 'destructive',
        title: 'Password mismatch',
        description: 'New password and confirm password do not match',
      })
      return
    }

    if (newPassword.length < 8) {
      toast({
        variant: 'destructive',
        title: 'Invalid password',
        description: 'Password must be at least 8 characters long',
      })
      return
    }

    setChangingPassword(true)
    try {
      await apiClient.put('/idp/users/me/password', {
        currentPassword,
        newPassword,
      })
      
      toast({
        title: 'Password changed',
        description: 'Your password has been successfully changed',
      })
      
      // Clear form
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      
      // If password reset was required, reload profile to update the flag
      if (profile?.passwordResetRequired) {
        await loadProfile()
      }
    } catch (error: any) {
      const errorMessage = extractErrorMessage(error)
      toast({
        variant: 'destructive',
        title: 'Failed to change password',
        description: errorMessage,
      })
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
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="container mx-auto py-8">
        <Card>
          <CardContent className="pt-6">
            <p className="text-muted-foreground">Failed to load profile</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Profile</h1>
          <p className="text-muted-foreground mt-1">View and manage your profile information</p>
        </div>
      </div>

      {profile.passwordResetRequired && (
        <Card className="border-orange-500 bg-orange-50 dark:bg-orange-950">
          <CardHeader>
            <CardTitle className="text-orange-700 dark:text-orange-300">Password Reset Required</CardTitle>
            <CardDescription className="text-orange-600 dark:text-orange-400">
              You must reset your password before continuing. Please set a new password below.
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      <div className="grid gap-6 md:grid-cols-2">
        {/* Profile Information */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <User className="h-5 w-5" />
              <CardTitle>Profile Information</CardTitle>
            </div>
            <CardDescription>Your account details</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label className="text-muted-foreground">Name</Label>
              <p className="text-sm font-medium mt-1">{profile.name}</p>
            </div>
            <div>
              <Label className="text-muted-foreground">Email</Label>
              <p className="text-sm font-medium mt-1">{profile.email}</p>
            </div>
            {profile.phone && (
              <div>
                <Label className="text-muted-foreground">Phone</Label>
                <p className="text-sm font-medium mt-1">{profile.phone}</p>
              </div>
            )}
            <div>
              <Label className="text-muted-foreground">Role</Label>
              <p className="text-sm font-medium mt-1">{profile.role}</p>
            </div>
            <div>
              <Label className="text-muted-foreground">Account Created</Label>
              <p className="text-sm font-medium mt-1">{formatDate(profile.createdAt)}</p>
            </div>
            {profile.lastLoginAt && (
              <div>
                <Label className="text-muted-foreground">Last Login</Label>
                <p className="text-sm font-medium mt-1">{formatDate(profile.lastLoginAt)}</p>
              </div>
            )}
            <div>
              <Label className="text-muted-foreground">Status</Label>
              <p className="text-sm font-medium mt-1">
                <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${
                  profile.active 
                    ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' 
                    : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
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
            <div className="flex items-center gap-2">
              <Lock className="h-5 w-5" />
              <CardTitle>Change Password</CardTitle>
            </div>
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
                className="w-full" 
                disabled={changingPassword}
              >
                {changingPassword ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Changing Password...
                  </>
                ) : (
                  'Change Password'
                )}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
