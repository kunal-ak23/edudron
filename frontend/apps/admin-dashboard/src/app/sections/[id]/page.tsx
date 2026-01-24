'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, ArrowLeft, Save, Plus, Users, Mail, Phone } from 'lucide-react'
import { sectionsApi, classesApi, institutesApi, enrollmentsApi } from '@/lib/api'
import type { Section, CreateSectionRequest, Class, Institute, SectionStudentDTO } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'
import { AddStudentToSectionDialog } from '@/components/AddStudentToSectionDialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

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
  const [members, setMembers] = useState<SectionStudentDTO[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [showAddStudentDialog, setShowAddStudentDialog] = useState(false)
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

  const loadMembers = useCallback(async () => {
    try {
      setMembersLoading(true)
      const students = await enrollmentsApi.getStudentsBySection(sectionId)
      setMembers(students)
    } catch (err: any) {
      console.error('Error loading members:', err)
      toast({
        variant: 'destructive',
        title: 'Failed to load members',
        description: extractErrorMessage(err),
      })
    } finally {
      setMembersLoading(false)
    }
  }, [sectionId, toast])

  useEffect(() => {
    if (sectionId) {
      loadSection()
      loadMembers()
    }
  }, [sectionId, loadSection, loadMembers])

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

          <Card className="mt-6">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Members ({members.length})</CardTitle>
                <Button onClick={() => setShowAddStudentDialog(true)}>
                  <Plus className="h-4 w-4 mr-2" />
                  Add Student
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {membersLoading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-primary" />
                </div>
              ) : members.length === 0 ? (
                <div className="text-center py-12">
                  <Users className="mx-auto h-12 w-12 text-gray-400" />
                  <h3 className="mt-2 text-sm font-semibold text-gray-900">No members found</h3>
                  <p className="mt-1 text-sm text-gray-500">
                    Add students to this section by enrolling them in a course.
                  </p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Email</TableHead>
                      <TableHead>Phone</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {members.map((member) => (
                      <TableRow key={member.id}>
                        <TableCell className="font-medium">{member.name || '-'}</TableCell>
                        <TableCell>
                          <div className="flex items-center">
                            <Mail className="h-4 w-4 mr-2 text-gray-400" />
                            {member.email}
                          </div>
                        </TableCell>
                        <TableCell>
                          {member.phone ? (
                            <div className="flex items-center">
                              <Phone className="h-4 w-4 mr-2 text-gray-400" />
                              {member.phone}
                            </div>
                          ) : (
                            <span className="text-gray-400">—</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

      <AddStudentToSectionDialog
        open={showAddStudentDialog}
        onOpenChange={setShowAddStudentDialog}
        sectionId={sectionId}
        onSuccess={loadMembers}
      />

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


