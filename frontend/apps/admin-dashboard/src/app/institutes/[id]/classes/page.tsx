'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Plus, Loader2, ArrowLeft, Users, Edit, FolderOpen, Network, Layers, FolderPlus, Eye } from 'lucide-react'
import { institutesApi, classesApi, apiClient } from '@/lib/api'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import type { Institute, Class } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { BatchCreateClassesDialog } from '@/components/BatchCreateClassesDialog'
import { BatchCreateClassWithSectionsDialog } from '@/components/BatchCreateClassWithSectionsDialog'

interface InstructorAccess {
  allowedClassIds: string[]
  allowedSectionIds: string[]
  allowedCourseIds: string[]
}

export default function InstituteClassesPage() {
  const router = useRouter()
  const params = useParams()
  const instituteId = params.id as string
  const { toast } = useToast()
  const { user } = useAuth()
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [classes, setClasses] = useState<Class[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [batchClassesDialogOpen, setBatchClassesDialogOpen] = useState(false)
  const [batchClassWithSectionsDialogOpen, setBatchClassWithSectionsDialogOpen] = useState(false)
  
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const isViewOnly = isInstructor || isSupportStaff

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      
      // For instructors, fetch their allowed access first
      let allowedClassIds: Set<string> | null = null
      
      if (isViewOnly && user?.id) {
        try {
          const accessResponse = await apiClient.get<InstructorAccess>(`/api/instructor-assignments/instructor/${user.id}/access`)
          allowedClassIds = new Set(accessResponse.data.allowedClassIds || [])
        } catch (err) {
          allowedClassIds = new Set()
        }
      }
      
      const [instituteData, classesData] = await Promise.all([
        institutesApi.getInstitute(instituteId),
        classesApi.listClassesByInstitute(instituteId)
      ])
      setInstitute(instituteData)
      
      // Filter classes for instructors
      let filteredClasses = classesData || []
      if (allowedClassIds !== null) {
        filteredClasses = filteredClasses.filter(cls => allowedClassIds!.has(cls.id))
      }
      setClasses(filteredClasses)
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      setError(errorMessage)
      toast({
        variant: 'destructive',
        title: 'Failed to load data',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }, [instituteId, toast, isViewOnly, user?.id])

  useEffect(() => {
    if (instituteId) {
      loadData()
    }
  }, [instituteId, loadData])

  const handleEdit = (classItem: Class) => {
    router.push(`/classes/${classItem.id}`)
  }

  const handleViewSections = (classItem: Class) => {
    router.push(`/classes/${classItem.id}/sections`)
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (error && !institute) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Link href="/institutes">
            <Button variant="ghost" className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Institutes
            </Button>
          </Link>
          <Card>
            <CardContent className="pt-6">
              <div className="text-center py-12">
                <h3 className="text-lg font-semibold text-gray-900 mb-2">Failed to load data</h3>
                <p className="text-sm text-gray-600 mb-4">{error}</p>
                <Button onClick={loadData} variant="outline">
                  Try Again
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    )
  }

  if (!institute) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-gray-600">No institute data available</p>
          <Link href="/institutes">
            <Button variant="outline" className="mt-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Institutes
            </Button>
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Link href="/institutes">
            <Button variant="ghost" className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Institutes
            </Button>
          </Link>

          <div className="flex justify-between items-center mb-8">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Classes - {institute.name}</h1>
              <p className="mt-2 text-sm text-gray-600">Manage classes for this institute</p>
            </div>
            <div className="flex gap-2">
              <Link href={`/institutes/${instituteId}/tree`}>
                <Button variant="outline">
                  <Network className="h-4 w-4 mr-2" />
                  Tree View
                </Button>
              </Link>
              {!isViewOnly && (
                <>
                  <Button 
                    variant="outline"
                    onClick={() => setBatchClassesDialogOpen(true)}
                  >
                    <Layers className="h-4 w-4 mr-2" />
                    Batch Create
                  </Button>
                  <Button 
                    variant="outline"
                    onClick={() => setBatchClassWithSectionsDialogOpen(true)}
                  >
                    <FolderPlus className="h-4 w-4 mr-2" />
                    Class + Sections
                  </Button>
                  <Link href={`/institutes/${instituteId}/classes/new`}>
                    <Button>
                      <Plus className="h-4 w-4 mr-2" />
                      Create New Class
                    </Button>
                  </Link>
                </>
              )}
            </div>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>All Classes</CardTitle>
            </CardHeader>
            <CardContent>
              {classes.length === 0 ? (
                <div className="text-center py-12">
                  <Users className="mx-auto h-12 w-12 text-gray-400" />
                  <h3 className="mt-2 text-sm font-semibold text-gray-900">
                    {isViewOnly ? 'No classes assigned to you' : 'No classes yet'}
                  </h3>
                  <p className="mt-1 text-sm text-gray-500">
                    {isViewOnly 
                      ? 'Contact your administrator to get access to classes.' 
                      : 'Create a class to get started.'}
                  </p>
                  {!isViewOnly && (
                    <Link href={`/institutes/${instituteId}/classes/new`}>
                      <Button className="mt-4">
                        <Plus className="h-4 w-4 mr-2" />
                        Create First Class
                      </Button>
                    </Link>
                  )}
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Code</TableHead>
                      <TableHead>Academic Year</TableHead>
                      <TableHead>Grade/Level</TableHead>
                      <TableHead>Sections</TableHead>
                      <TableHead>Students</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {classes.map((classItem) => (
                      <TableRow key={classItem.id}>
                        <TableCell className="font-medium">{classItem.name}</TableCell>
                        <TableCell>{classItem.code}</TableCell>
                        <TableCell>{classItem.academicYear || '-'}</TableCell>
                        <TableCell>{classItem.grade || classItem.level || '-'}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            <FolderOpen className="h-4 w-4 text-gray-400" />
                            {classItem.sectionCount || 0}
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            <Users className="h-4 w-4 text-gray-400" />
                            {classItem.studentCount || 0}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={classItem.isActive ? 'default' : 'secondary'}>
                            {classItem.isActive ? 'Active' : 'Inactive'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleViewSections(classItem)}
                            >
                              View Sections
                            </Button>
                            {!isViewOnly && (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleEdit(classItem)}
                              >
                                <Edit className="h-4 w-4 mr-1" />
                                Edit
                              </Button>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Batch Dialogs */}
        <BatchCreateClassesDialog
          open={batchClassesDialogOpen}
          onOpenChange={setBatchClassesDialogOpen}
          instituteId={instituteId}
          onSuccess={loadData}
        />
        <BatchCreateClassWithSectionsDialog
          open={batchClassWithSectionsDialogOpen}
          onOpenChange={setBatchClassWithSectionsDialogOpen}
          instituteId={instituteId}
          onSuccess={loadData}
        />
      </div>
  )
}

