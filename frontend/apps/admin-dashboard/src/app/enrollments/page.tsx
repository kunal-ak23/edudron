'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@edudron/shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Loader2, Users, Filter, X } from 'lucide-react'
import { enrollmentsApi, coursesApi, institutesApi, classesApi, sectionsApi } from '@/lib/api'
import type { Enrollment, Course, Institute, Class, Section } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export default function EnrollmentsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [filteredEnrollments, setFilteredEnrollments] = useState<Enrollment[]>([])
  const [courses, setCourses] = useState<Record<string, Course>>({})
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [classes, setClasses] = useState<Class[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedInstituteId, setSelectedInstituteId] = useState<string>('all')
  const [selectedClassId, setSelectedClassId] = useState<string>('all')
  const [selectedSectionId, setSelectedSectionId] = useState<string>('all')

  useEffect(() => {
    loadData()
  }, [])

  useEffect(() => {
    filterEnrollments()
  }, [enrollments, selectedInstituteId, selectedClassId, selectedSectionId])

  const loadData = async () => {
    try {
      setLoading(true)
      const [enrollmentsData, institutesData] = await Promise.all([
        enrollmentsApi.listEnrollments(),
        institutesApi.listInstitutes()
      ])
      setEnrollments(enrollmentsData)
      setInstitutes(institutesData)

      // Load courses
      const courseIds = Array.from(new Set(enrollmentsData.map(e => e.courseId)))
      const coursePromises = courseIds.map(id => coursesApi.getCourse(id).catch(() => null))
      const coursesData = await Promise.all(coursePromises)
      const coursesMap: Record<string, Course> = {}
      coursesData.forEach((course, index) => {
        if (course) {
          coursesMap[courseIds[index]] = course
        }
      })
      setCourses(coursesMap)

      // Load classes and sections
      const allClasses: Class[] = []
      const allSections: Section[] = []
      for (const inst of institutesData) {
        const instClasses = await classesApi.listClassesByInstitute(inst.id)
        allClasses.push(...instClasses)
        for (const classItem of instClasses) {
          const classSections = await sectionsApi.listSectionsByClass(classItem.id)
          allSections.push(...classSections)
        }
      }
      setClasses(allClasses)
      setSections(allSections)
    } catch (err: any) {
      console.error('Error loading data:', err)
      toast({
        variant: 'destructive',
        title: 'Failed to load enrollments',
        description: extractErrorMessage(err),
      })
    } finally {
      setLoading(false)
    }
  }

  const filterEnrollments = () => {
    let filtered = [...enrollments]

    if (selectedInstituteId && selectedInstituteId !== 'all') {
      filtered = filtered.filter(e => e.instituteId === selectedInstituteId)
    }
    if (selectedClassId && selectedClassId !== 'all') {
      filtered = filtered.filter(e => e.classId === selectedClassId)
    }
    if (selectedSectionId && selectedSectionId !== 'all') {
      filtered = filtered.filter(e => e.batchId === selectedSectionId)
    }

    setFilteredEnrollments(filtered)
  }

  const getHierarchyPath = (enrollment: Enrollment) => {
    const parts: string[] = []
    if (enrollment.instituteId) {
      const institute = institutes.find(i => i.id === enrollment.instituteId)
      if (institute) parts.push(institute.name)
    }
    if (enrollment.classId) {
      const classItem = classes.find(c => c.id === enrollment.classId)
      if (classItem) parts.push(classItem.name)
    }
    if (enrollment.batchId) {
      const section = sections.find(s => s.id === enrollment.batchId)
      if (section) parts.push(section.name)
    }
    return parts.length > 0 ? parts.join(' → ') : '-'
  }

  const getFilteredClasses = () => {
    if (!selectedInstituteId || selectedInstituteId === 'all') return classes
    return classes.filter(c => c.instituteId === selectedInstituteId)
  }

  const getFilteredSections = () => {
    if (!selectedClassId || selectedClassId === 'all') return sections
    return sections.filter(s => s.classId === selectedClassId)
  }

  const clearFilters = () => {
    setSelectedInstituteId('all')
    setSelectedClassId('all')
    setSelectedSectionId('all')
  }

  if (loading) {
    return (
      
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
  )
}

  return (
    <div>

          <Card className="mb-6">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Filter className="h-5 w-5" />
                Filters
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="space-y-2">
                  <Label>Institute</Label>
                  <Select value={selectedInstituteId} onValueChange={(value) => {
                    setSelectedInstituteId(value)
                    setSelectedClassId('all')
                    setSelectedSectionId('all')
                  }}>
                    <SelectTrigger>
                      <SelectValue placeholder="All Institutes" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Institutes</SelectItem>
                      {institutes.map(inst => (
                        <SelectItem key={inst.id} value={inst.id}>{inst.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Class</Label>
                  <Select 
                    value={selectedClassId} 
                    onValueChange={(value) => {
                      setSelectedClassId(value)
                      setSelectedSectionId('all')
                    }}
                    disabled={!selectedInstituteId || selectedInstituteId === 'all'}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="All Classes" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Classes</SelectItem>
                      {getFilteredClasses().map(classItem => (
                        <SelectItem key={classItem.id} value={classItem.id}>{classItem.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Section</Label>
                  <Select 
                    value={selectedSectionId} 
                    onValueChange={setSelectedSectionId}
                    disabled={!selectedClassId || selectedClassId === 'all'}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="All Sections" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Sections</SelectItem>
                      {getFilteredSections().map(section => (
                        <SelectItem key={section.id} value={section.id}>{section.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex items-end">
                  <Button 
                    variant="outline" 
                    onClick={clearFilters}
                    className="w-full"
                    disabled={selectedInstituteId === 'all' && selectedClassId === 'all' && selectedSectionId === 'all'}
                  >
                    <X className="h-4 w-4 mr-2" />
                    Clear Filters
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>All Enrollments ({filteredEnrollments.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {filteredEnrollments.length === 0 ? (
                <div className="text-center py-12">
                  <Users className="mx-auto h-12 w-12 text-gray-400" />
                  <h3 className="mt-2 text-sm font-semibold text-gray-900">No enrollments found</h3>
                  <p className="mt-1 text-sm text-gray-500">
                    {enrollments.length === 0 
                      ? 'No enrollments in the system yet.'
                      : 'Try adjusting your filters.'}
                  </p>
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Student ID</TableHead>
                      <TableHead>Course</TableHead>
                      <TableHead>Hierarchy</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Enrolled At</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredEnrollments.map((enrollment) => (
                      <TableRow key={enrollment.id}>
                        <TableCell className="font-medium">{enrollment.studentId}</TableCell>
                        <TableCell>{courses[enrollment.courseId]?.title || enrollment.courseId}</TableCell>
                        <TableCell>
                          <div className="text-sm text-gray-600">
                            {getHierarchyPath(enrollment)}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={enrollment.status === 'ACTIVE' ? 'default' : 'secondary'}>
                            {enrollment.status}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {new Date(enrollment.enrolledAt).toLocaleDateString()}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
      </div>
  )
}


