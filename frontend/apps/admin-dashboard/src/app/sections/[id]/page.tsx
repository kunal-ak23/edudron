'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, ArrowLeft, Save } from 'lucide-react'
import { sectionsApi, classesApi, institutesApi } from '@/lib/api'
import type { Section, CreateSectionRequest, Class, Institute } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

export default function SectionDetailPage() {
  const router = useRouter()
  const params = useParams()
  const sectionId = params.id as string
  const { toast } = useToast()
  const [section, setSection] = useState<Section | null>(null)
  const [classItem, setClassItem] = useState<Class | null>(null)
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [formData, setFormData] = useState<CreateSectionRequest>({
    name: '',
    description: '',
    classId: '',
    startDate: '',
    endDate: '',
    maxStudents: undefined
  })

  const loadSection = useCallback(async () => {
    try {
      setLoading(true)
      const sectionData = await sectionsApi.getSection(sectionId)
      setSection(sectionData)
      setFormData({
        name: sectionData.name,
        description: sectionData.description || '',
        classId: sectionData.classId,
        startDate: sectionData.startDate || '',
        endDate: sectionData.endDate || '',
        maxStudents: sectionData.maxStudents
      })
      
      // Load class and institute for breadcrumb
      const classData = await classesApi.getClass(sectionData.classId)
      setClassItem(classData)
      
      const instituteData = await institutesApi.getInstitute(classData.instituteId)
      setInstitute(instituteData)
    } catch (err: any) {
      console.error('Error loading section:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load section',
        description: errorMessage,
      })
      router.push('/institutes')
    } finally {
      setLoading(false)
    }
  }, [sectionId, toast, router])

  useEffect(() => {
    if (sectionId) {
      loadSection()
    }
  }, [sectionId, loadSection])

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)

    try {
      const updated = await sectionsApi.updateSection(sectionId, formData)
      setSection(updated)
      toast({
        title: 'Section updated',
        description: `${updated.name} has been updated successfully.`,
      })
    } catch (err: any) {
      console.error('Error updating section:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to update section',
        description: errorMessage,
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    try {
      await sectionsApi.deleteSection(sectionId)
      toast({
        title: 'Section deactivated',
        description: 'The section has been deactivated successfully.',
      })
      router.push(`/classes/${section?.classId}/sections`)
    } catch (err: any) {
      console.error('Error deleting section:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to deactivate section',
        description: errorMessage,
      })
    } finally {
      setShowDeleteDialog(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!section || !classItem || !institute) {
    return null
  }

  return (
    <div>
      <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
        <Link href="/institutes" className="hover:text-gray-900">Institutes</Link>
        <span>/</span>
        <Link href={`/institutes/${institute.id}/classes`} className="hover:text-gray-900">{institute.name}</Link>
        <span>/</span>
        <Link href={`/classes/${classItem.id}`} className="hover:text-gray-900">{classItem.name}</Link>
            <span>/</span>
            <Link href={`/classes/${classItem.id}/sections`} className="hover:text-gray-900">Sections</Link>
            <span>/</span>
            <span className="text-gray-900">{section.name}</span>
          </div>

          <Link href={`/classes/${classItem.id}/sections`}>
            <Button variant="ghost" className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Sections
            </Button>
          </Link>


          <Card>
            <CardHeader>
              <CardTitle>Section Details</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleUpdate} className="space-y-6">
                <div className="grid gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="name">Section Name</Label>
                    <Input
                      id="name"
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="description">Description</Label>
                    <Input
                      id="description"
                      value={formData.description || ''}
                      onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="startDate">Start Date</Label>
                    <Input
                      id="startDate"
                      type="date"
                      value={formData.startDate || ''}
                      onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="endDate">End Date</Label>
                    <Input
                      id="endDate"
                      type="date"
                      value={formData.endDate || ''}
                      onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="maxStudents">Maximum Students</Label>
                    <Input
                      id="maxStudents"
                      type="number"
                      min="1"
                      value={formData.maxStudents || ''}
                      onChange={(e) => setFormData({ ...formData, maxStudents: e.target.value ? parseInt(e.target.value) : undefined })}
                      placeholder="Leave empty for unlimited"
                    />
                  </div>
                </div>
                <div className="flex justify-between">
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={() => setShowDeleteDialog(true)}
                  >
                    Deactivate Section
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
            <CardContent>
              <Link href={`/sections/${sectionId}/enroll`}>
                <Button variant="outline" className="w-full">
                  Enroll to Course
                </Button>
              </Link>
            </CardContent>
          </Card>

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        onConfirm={handleDelete}
        title="Deactivate Section"
        description="Are you sure you want to deactivate this section?"
        confirmText="Deactivate"
        variant="destructive"
      />
    </div>
  )
}


