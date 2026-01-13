'use client'

import { useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
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
import { Plus, Loader2, ArrowLeft, Users, Edit } from 'lucide-react'
import { classesApi, sectionsApi, institutesApi } from '@/lib/api'
import type { Class, Section, Institute } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'

export default function ClassSectionsPage() {
  const router = useRouter()
  const params = useParams()
  const classId = params.id as string
  const { toast } = useToast()
  const [classItem, setClassItem] = useState<Class | null>(null)
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (classId) {
      loadData()
    }
  }, [classId])

  const loadData = async () => {
    try {
      setLoading(true)
      const classData = await classesApi.getClass(classId)
      setClassItem(classData)
      
      const [instituteData, sectionsData] = await Promise.all([
        institutesApi.getInstitute(classData.instituteId),
        sectionsApi.listSectionsByClass(classId)
      ])
      setInstitute(instituteData)
      setSections(sectionsData || [])
    } catch (err: any) {
      console.error('Error loading data:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load data',
        description: errorMessage,
      })
      router.push('/institutes')
    } finally {
      setLoading(false)
    }
  }

  const handleEdit = (section: Section) => {
    router.push(`/sections/${section.id}`)
  }

  if (loading) {
    return (
      
        <div className="min-h-screen flex items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
  )
}

  if (!classItem || !institute) {
    return null
  }

  return (
    
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
            <Link href="/institutes" className="hover:text-gray-900">Institutes</Link>
            <span>/</span>
            <Link href={`/institutes/${institute.id}/classes`} className="hover:text-gray-900">{institute.name}</Link>
            <span>/</span>
            <Link href={`/classes/${classId}`} className="hover:text-gray-900">{classItem.name}</Link>
            <span>/</span>
            <span className="text-gray-900">Sections</span>
          </div>

          <Link href={`/classes/${classId}`}>
            <Button variant="ghost" className="mb-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Class
            </Button>
          </Link>

          <div className="flex justify-between items-center mb-8">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Sections - {classItem.name}</h1>
              <p className="mt-2 text-sm text-gray-600">Manage sections for this class</p>
            </div>
            <Link href={`/classes/${classId}/sections/new`}>
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                Create New Section
              </Button>
            </Link>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>All Sections</CardTitle>
            </CardHeader>
            <CardContent>
              {sections.length === 0 ? (
                <div className="text-center py-12">
                  <Users className="mx-auto h-12 w-12 text-gray-400" />
                  <h3 className="mt-2 text-sm font-semibold text-gray-900">No sections yet</h3>
                  <p className="mt-1 text-sm text-gray-500">Create a section to get started.</p>
                  <Link href={`/classes/${classId}/sections/new`}>
                    <Button className="mt-4">
                      <Plus className="h-4 w-4 mr-2" />
                      Create First Section
                    </Button>
                  </Link>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead>Start Date</TableHead>
                      <TableHead>End Date</TableHead>
                      <TableHead>Capacity</TableHead>
                      <TableHead>Students</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sections.map((section) => (
                      <TableRow key={section.id}>
                        <TableCell className="font-medium">{section.name}</TableCell>
                        <TableCell>{section.description || '-'}</TableCell>
                        <TableCell>{section.startDate || '-'}</TableCell>
                        <TableCell>{section.endDate || '-'}</TableCell>
                        <TableCell>{section.maxStudents ? `${section.maxStudents}` : 'Unlimited'}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            <Users className="h-4 w-4 text-gray-400" />
                            {section.studentCount || 0}
                            {section.maxStudents && ` / ${section.maxStudents}`}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={section.isActive ? 'default' : 'secondary'}>
                            {section.isActive ? 'Active' : 'Inactive'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleEdit(section)}
                          >
                            <Edit className="h-4 w-4 mr-1" />
                            Edit
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
  )
}


