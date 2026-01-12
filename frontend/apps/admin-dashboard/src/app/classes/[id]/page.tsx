'use client'

import { useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute } from '@edudron/ui-components'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, Save } from 'lucide-react'
import { classesApi, institutesApi } from '@/lib/api'
import type { Class, CreateClassRequest, Institute } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

export default function ClassDetailPage() {
  const router = useRouter()
  const params = useParams()
  const classId = params.id as string
  const { toast } = useToast()
  const [classItem, setClassItem] = useState<Class | null>(null)
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [formData, setFormData] = useState<CreateClassRequest>({
    name: '',
    code: '',
    instituteId: '',
    academicYear: '',
    grade: '',
    level: '',
    isActive: true
  })

  useEffect(() => {
    if (classId) {
      loadClass()
    }
  }, [classId])

  const loadClass = async () => {
    try {
      setLoading(true)
      const classData = await classesApi.getClass(classId)
      setClassItem(classData)
      setFormData({
        name: classData.name,
        code: classData.code,
        instituteId: classData.instituteId,
        academicYear: classData.academicYear || '',
        grade: classData.grade || '',
        level: classData.level || '',
        isActive: classData.isActive
      })
      
      // Load institute for breadcrumb
      const instituteData = await institutesApi.getInstitute(classData.instituteId)
      setInstitute(instituteData)
    } catch (err: any) {
      console.error('Error loading class:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load class',
        description: errorMessage,
      })
      router.push('/institutes')
    } finally {
      setLoading(false)
    }
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)

    try {
      const updated = await classesApi.updateClass(classId, formData)
      setClassItem(updated)
      toast({
        title: 'Class updated',
        description: `${updated.name} has been updated successfully.`,
      })
    } catch (err: any) {
      console.error('Error updating class:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to update class',
        description: errorMessage,
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    try {
      await classesApi.deleteClass(classId)
      toast({
        title: 'Class deactivated',
        description: 'The class has been deactivated successfully.',
      })
      router.push(`/institutes/${classItem?.instituteId}/classes`)
    } catch (err: any) {
      console.error('Error deleting class:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to deactivate class',
        description: errorMessage,
      })
    } finally {
      setShowDeleteDialog(false)
    }
  }

  if (loading) {
    return (
      <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN']}>
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      </ProtectedRoute>
    )
  }

  if (!classItem || !institute) {
    return null
  }

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN']}>
      <div>
          <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
            <Link href="/institutes" className="hover:text-gray-900">Institutes</Link>
            <span>/</span>
            <Link href={`/institutes/${institute.id}/classes`} className="hover:text-gray-900">{institute.name}</Link>
            <span>/</span>
            <span className="text-gray-900">{classItem.name}</span>
          </div>

          <Link href={`/institutes/${institute.id}/classes`}>
            <Button variant="ghost" className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Classes
            </Button>
          </Link>


          <Card>
            <CardHeader>
              <CardTitle>Class Details</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleUpdate} className="space-y-6">
                <div className="grid gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="name">Class Name</Label>
                    <Input
                      id="name"
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="code">Class Code</Label>
                    <Input
                      id="code"
                      value={formData.code}
                      onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="academicYear">Academic Year</Label>
                    <Input
                      id="academicYear"
                      value={formData.academicYear || ''}
                      onChange={(e) => setFormData({ ...formData, academicYear: e.target.value })}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="grade">Grade</Label>
                    <Input
                      id="grade"
                      value={formData.grade || ''}
                      onChange={(e) => setFormData({ ...formData, grade: e.target.value })}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="level">Level</Label>
                    <Input
                      id="level"
                      value={formData.level || ''}
                      onChange={(e) => setFormData({ ...formData, level: e.target.value })}
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id="isActive"
                      checked={formData.isActive}
                      onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                      className="rounded"
                    />
                    <Label htmlFor="isActive">Active</Label>
                  </div>
                </div>
                <div className="flex justify-between">
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={() => setShowDeleteDialog(true)}
                  >
                    Deactivate Class
                  </Button>
                  <Button type="submit" disabled={submitting}>
                    {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <Save className="mr-2 h-4 w-4" />
                    Save Changes
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card className="mt-6">
            <CardHeader>
              <CardTitle>Quick Actions</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <Link href={`/classes/${classId}/sections`}>
                <Button variant="outline" className="w-full">
                  View Sections ({classItem.sectionCount || 0})
                </Button>
              </Link>
              <Link href={`/classes/${classId}/enroll`}>
                <Button variant="outline" className="w-full">
                  Enroll to Course
                </Button>
              </Link>
            </CardContent>
          </Card>
        </div>
      </div>

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        onConfirm={handleDelete}
        title="Deactivate Class"
        description="Are you sure you want to deactivate this class?"
        confirmText="Deactivate"
        variant="destructive"
      />
    </ProtectedRoute>
  )
}


