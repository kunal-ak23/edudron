'use client'

import React, { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, Save, Plus, Users, Mail, Phone, ChevronLeft, ChevronRight, BarChart3 } from 'lucide-react'
import { classesApi, institutesApi, enrollmentsApi, sectionsApi } from '@/lib/api'
import type { Class, CreateClassRequest, Institute, ClassStudentDTO, Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'
import { AddStudentToClassDialog } from '@/components/AddStudentToClassDialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

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
  const [members, setMembers] = useState<ClassStudentDTO[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [sections, setSections] = useState<Section[]>([])
  const [showAddStudentDialog, setShowAddStudentDialog] = useState(false)
  const [membersCurrentPage, setMembersCurrentPage] = useState(0)
  const [membersPageSize, setMembersPageSize] = useState(20)
  const [membersTotalElements, setMembersTotalElements] = useState(0)
  const [membersTotalPages, setMembersTotalPages] = useState(0)
  const [studentSectionsMap, setStudentSectionsMap] = useState<Map<string, string[]>>(new Map())
  const [formData, setFormData] = useState<CreateClassRequest>({
    name: '',
    code: '',
    instituteId: '',
    academicYear: '',
    grade: '',
    level: '',
    isActive: true
  })

  const loadClass = useCallback(async () => {
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
  }, [classId, toast, router])

  const loadMembers = useCallback(async () => {
    try {
      setMembersLoading(true)
      const response = await enrollmentsApi.getStudentsByClassPaginated(
        classId,
        membersCurrentPage,
        membersPageSize
      )
      setMembers(response.content)
      setMembersTotalElements(response.totalElements)
      setMembersTotalPages(response.totalPages)
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to load members',
        description: extractErrorMessage(err),
      })
    } finally {
      setMembersLoading(false)
    }
  }, [classId, membersCurrentPage, membersPageSize, toast])

  const loadSections = useCallback(async () => {
    try {
      const sectionsData = await sectionsApi.listSectionsByClass(classId)
      setSections(sectionsData)
    } catch (err: any) {
    }
  }, [classId])

  // Load enrollments to map students to their sections
  const loadStudentSectionsMap = useCallback(async () => {
    try {
      // Load all enrollments for this class (we need to check sectionId/batchId)
      const enrollments = await enrollmentsApi.listAllEnrollmentsPaginated(0, 1000, {
        classId: classId
      })
      
      // Build a map: studentId -> array of sectionIds
      const map = new Map<string, string[]>()
      
      enrollments.content.forEach(enrollment => {
        const studentId = enrollment.studentId
        const sectionId = enrollment.batchId // batchId is sectionId
        
        if (sectionId) {
          if (!map.has(studentId)) {
            map.set(studentId, [])
          }
          const sections = map.get(studentId)!
          if (!sections.includes(sectionId)) {
            sections.push(sectionId)
          }
        }
      })
      
      setStudentSectionsMap(map)
    } catch (err: any) {
      // Continue without the map - sections column will be empty
    }
  }, [classId])

  useEffect(() => {
    if (classId) {
      loadMembers()
      loadSections()
      loadStudentSectionsMap()
    }
  }, [classId, loadMembers, loadSections, loadStudentSectionsMap])

  // Reset to first page when page size changes
  useEffect(() => {
    setMembersCurrentPage(0)
  }, [membersPageSize])

  useEffect(() => {
    if (classId) {
      loadClass()
    }
  }, [classId, loadClass])

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
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!classItem || !institute) {
    return null
  }

  return (
    <>
      <div>
        <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
          <Link href="/institutes" className="hover:text-gray-900">Institutes</Link>
          <span>/</span>
          <Link href={`/institutes/${institute.id}/classes`} className="hover:text-gray-900">{institute.name}</Link>
          <span>/</span>
          <span className="text-gray-900">{classItem.name}</span>
        </div>

        <div className="flex items-center justify-between mb-4">
          <Link href={`/institutes/${institute.id}/classes`}>
            <Button variant="ghost">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Classes
            </Button>
          </Link>
          <Button 
            variant="outline"
            onClick={() => router.push(`/analytics/classes/${classId}`)}
          >
            <BarChart3 className="h-4 w-4 mr-2" />
            View Analytics
          </Button>
        </div>

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

        <Card className="mt-6">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Members ({membersTotalElements.toLocaleString()})</CardTitle>
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
                  Add students to this class by enrolling them in a course.
                </p>
              </div>
            ) : (
              <>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Email</TableHead>
                      <TableHead>Phone</TableHead>
                      <TableHead>Sections</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {members.map((member) => {
                      // Find sections this student is actually enrolled in
                      const studentSectionIds = studentSectionsMap.get(member.id) || []
                      const studentSections = sections.filter(section => 
                        studentSectionIds.includes(section.id)
                      )
                      
                      return (
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
                          <TableCell>
                            <div className="flex flex-wrap gap-1">
                              {studentSections.length > 0 ? (
                                studentSections.slice(0, 2).map((section) => (
                                  <Badge key={section.id} variant="outline" className="text-xs">
                                    {section.name}
                                  </Badge>
                                ))
                              ) : (
                                <span className="text-gray-400 text-sm">—</span>
                              )}
                              {studentSections.length > 2 && (
                                <Badge variant="outline" className="text-xs">
                                  +{studentSections.length - 2}
                                </Badge>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
                
                {/* Pagination Controls */}
                {membersTotalPages > 1 && (
                  <div className="flex items-center justify-between mt-4 pt-4 border-t">
                    <div className="flex items-center gap-2">
                      <Label className="text-sm">Page size:</Label>
                      <Select
                        value={membersPageSize.toString()}
                        onValueChange={(value) => {
                          setMembersPageSize(Number(value))
                          setMembersCurrentPage(0)
                        }}
                      >
                        <SelectTrigger className="w-20">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="10">10</SelectItem>
                          <SelectItem value="20">20</SelectItem>
                          <SelectItem value="50">50</SelectItem>
                          <SelectItem value="100">100</SelectItem>
                        </SelectContent>
                      </Select>
                      <span className="text-sm text-gray-600">
                        Showing {membersCurrentPage * membersPageSize + 1} to {Math.min((membersCurrentPage + 1) * membersPageSize, membersTotalElements)} of {membersTotalElements.toLocaleString()}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setMembersCurrentPage(0)}
                        disabled={membersCurrentPage === 0 || membersLoading}
                      >
                        First
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setMembersCurrentPage(prev => Math.max(0, prev - 1))}
                        disabled={membersCurrentPage === 0 || membersLoading}
                      >
                        <ChevronLeft className="h-4 w-4" />
                        Previous
                      </Button>
                      <span className="text-sm text-gray-600 px-2">
                        Page {membersCurrentPage + 1} of {membersTotalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setMembersCurrentPage(prev => Math.min(membersTotalPages - 1, prev + 1))}
                        disabled={membersCurrentPage >= membersTotalPages - 1 || membersLoading}
                      >
                        Next
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setMembersCurrentPage(membersTotalPages - 1)}
                        disabled={membersCurrentPage >= membersTotalPages - 1 || membersLoading}
                      >
                        Last
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <AddStudentToClassDialog
        open={showAddStudentDialog}
        onOpenChange={setShowAddStudentDialog}
        classId={classId}
        onSuccess={() => {
          loadMembers()
          loadStudentSectionsMap()
        }}
      />

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        onConfirm={handleDelete}
        title="Deactivate Class"
        description="Are you sure you want to deactivate this class?"
        confirmText="Deactivate"
        variant="destructive"
      />
    </>
  )
}


