'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Plus, Loader2, Users, Filter, X, Trash2, ChevronLeft, ChevronRight, Search } from 'lucide-react'
import { enrollmentsApi, coursesApi, institutesApi, classesApi, sectionsApi, apiClient } from '@/lib/api'
import type { Enrollment, Course, Institute, Class, Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export default function EnrollmentsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [filteredEnrollments, setFilteredEnrollments] = useState<Enrollment[]>([])
  const [courses, setCourses] = useState<Record<string, Course>>({})
  const [allCourses, setAllCourses] = useState<Course[]>([]) // All courses for filter dropdown
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [classes, setClasses] = useState<Class[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedCourseId, setSelectedCourseId] = useState<string>('all')
  const [selectedInstituteId, setSelectedInstituteId] = useState<string>('all')
  const [selectedClassId, setSelectedClassId] = useState<string>('all')
  const [selectedSectionId, setSelectedSectionId] = useState<string>('all')
  const [searchEmail, setSearchEmail] = useState<string>('')
  const [unenrollingId, setUnenrollingId] = useState<string | null>(null)
  const [showUnenrollDialog, setShowUnenrollDialog] = useState(false)
  const [enrollmentToUnenroll, setEnrollmentToUnenroll] = useState<Enrollment | null>(null)
  const [showAddEnrollmentDialog, setShowAddEnrollmentDialog] = useState(false)
  const [students, setStudents] = useState<Array<{ id: string; email: string; name?: string }>>([])
  const [selectedStudentId, setSelectedStudentId] = useState<string>('')
  const [selectedEnrollCourseId, setSelectedEnrollCourseId] = useState<string>('')
  const [selectedEnrollInstituteId, setSelectedEnrollInstituteId] = useState<string>('')
  const [selectedEnrollClassId, setSelectedEnrollClassId] = useState<string>('')
  const [selectedEnrollSectionId, setSelectedEnrollSectionId] = useState<string>('')
  const [enrolling, setEnrolling] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const [enrollmentsResponse, institutesData, allCoursesData] = await Promise.all([
        enrollmentsApi.listAllEnrollmentsPaginated(currentPage, pageSize),
        institutesApi.listInstitutes(),
        coursesApi.listCourses().catch(() => []) // Load all courses for filter
      ])
      
      setEnrollments(enrollmentsResponse.content)
      setTotalElements(enrollmentsResponse.totalElements)
      setTotalPages(enrollmentsResponse.totalPages)
      setInstitutes(institutesData)
      setAllCourses(allCoursesData)

      // Load courses for current page (for display)
      const courseIds = Array.from(new Set(enrollmentsResponse.content.map(e => e.courseId)))
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

      // Load students for enrollment dropdown
      try {
        const studentsResponse = await apiClient.get<Array<{ id: string; email: string; name?: string }>>('/idp/users/role/STUDENT')
        setStudents(studentsResponse.data || [])
      } catch (err) {
        console.error('Error loading students:', err)
        // Continue without students - will show empty list
      }
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
  }, [currentPage, pageSize, toast])

  const filterEnrollments = useCallback(() => {
    let filtered = [...enrollments]

    // Filter by email search (case-insensitive)
    if (searchEmail.trim()) {
      const searchLower = searchEmail.toLowerCase().trim()
      filtered = filtered.filter(e => {
        const email = e.studentEmail?.toLowerCase() || ''
        const studentId = e.studentId?.toLowerCase() || ''
        return email.includes(searchLower) || studentId.includes(searchLower)
      })
    }

    // Filter by course
    if (selectedCourseId && selectedCourseId !== 'all') {
      filtered = filtered.filter(e => e.courseId === selectedCourseId)
    }

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
  }, [enrollments, searchEmail, selectedCourseId, selectedInstituteId, selectedClassId, selectedSectionId])

  useEffect(() => {
    loadData()
  }, [loadData, currentPage, pageSize])

  useEffect(() => {
    filterEnrollments()
  }, [filterEnrollments])

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
    setSelectedCourseId('all')
    setSelectedInstituteId('all')
    setSelectedClassId('all')
    setSelectedSectionId('all')
    setSearchEmail('')
    setCurrentPage(0) // Reset to first page when clearing filters
  }

  const handleUnenrollClick = (enrollment: Enrollment) => {
    setEnrollmentToUnenroll(enrollment)
    setShowUnenrollDialog(true)
  }

  const handleUnenrollConfirm = async () => {
    if (!enrollmentToUnenroll) return

    setUnenrollingId(enrollmentToUnenroll.id)
    try {
      // Use the admin endpoint to delete enrollment by ID
      await enrollmentsApi.deleteEnrollment(enrollmentToUnenroll.id)
      
      // Reload current page to refresh data
      await loadData()
      
      toast({
        title: 'Success',
        description: 'Student has been unenrolled from the course',
      })
      
      setShowUnenrollDialog(false)
      setEnrollmentToUnenroll(null)
    } catch (err: any) {
      console.error('Error unenrolling:', err)
      toast({
        variant: 'destructive',
        title: 'Failed to unenroll',
        description: extractErrorMessage(err),
      })
    } finally {
      setUnenrollingId(null)
    }
  }

  const handleAddEnrollment = async () => {
    if (!selectedStudentId || !selectedEnrollCourseId) {
      toast({
        variant: 'destructive',
        title: 'Missing required fields',
        description: 'Please select both student and course',
      })
      return
    }

    setEnrolling(true)
    try {
      const options: any = {}
      if (selectedEnrollClassId && selectedEnrollClassId !== 'all') {
        options.classId = selectedEnrollClassId
      }
      if (selectedEnrollSectionId && selectedEnrollSectionId !== 'all') {
        options.sectionId = selectedEnrollSectionId
      }
      if (selectedEnrollInstituteId && selectedEnrollInstituteId !== 'all') {
        options.instituteId = selectedEnrollInstituteId
      }

      await enrollmentsApi.enrollStudentInCourse(selectedStudentId, selectedEnrollCourseId, options)
      
      toast({
        title: 'Success',
        description: 'Student has been enrolled in the course',
      })
      
      // Reset form
      setSelectedStudentId('')
      setSelectedEnrollCourseId('')
      setSelectedEnrollInstituteId('')
      setSelectedEnrollClassId('')
      setSelectedEnrollSectionId('')
      setShowAddEnrollmentDialog(false)
      
      // Reload data to show new enrollment
      await loadData()
    } catch (err: any) {
      console.error('Error enrolling student:', err)
      toast({
        variant: 'destructive',
        title: 'Failed to enroll student',
        description: extractErrorMessage(err),
      })
    } finally {
      setEnrolling(false)
    }
  }

  if (loading) {
    return (
      <div className="container mx-auto py-8 px-4">
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 px-4">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Enrollments</h1>
          <p className="text-gray-600 mt-2">Manage student course enrollments</p>
        </div>
        <Button onClick={() => setShowAddEnrollmentDialog(true)}>
          <Plus className="h-4 w-4 mr-2" />
          Add Enrollment
        </Button>
      </div>

      <Card className="mb-6">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Filter className="h-5 w-5" />
                Filters
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-6 gap-4 mb-4">
                <div className="space-y-2 md:col-span-1">
                  <Label>Search Email</Label>
                  <div className="relative">
                    <Search className="absolute left-2 top-2.5 h-4 w-4 text-gray-400" />
                    <Input
                      placeholder="Search by email..."
                      value={searchEmail}
                      onChange={(e) => {
                        setSearchEmail(e.target.value)
                        setCurrentPage(0) // Reset to first page when searching
                      }}
                      className="pl-8"
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label>Course</Label>
                  <Select 
                    value={selectedCourseId} 
                    onValueChange={(value) => {
                      setSelectedCourseId(value)
                      setCurrentPage(0) // Reset to first page when filtering
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="All Courses" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Courses</SelectItem>
                      {allCourses
                        .filter(c => c.isPublished && c.status !== 'ARCHIVED')
                        .map(course => (
                          <SelectItem key={course.id} value={course.id}>
                            {course.title}
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                </div>
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
                    disabled={selectedCourseId === 'all' && selectedInstituteId === 'all' && selectedClassId === 'all' && selectedSectionId === 'all' && !searchEmail.trim()}
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
              <CardTitle>
                All Enrollments ({totalElements.toLocaleString()})
                {filteredEnrollments.length < enrollments.length && (
                  <span className="text-sm font-normal text-gray-500 ml-2">
                    (Showing {filteredEnrollments.length} of {enrollments.length} on this page)
                  </span>
                )}
              </CardTitle>
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
                      <TableHead>Student Email</TableHead>
                      <TableHead>Course</TableHead>
                      <TableHead>Hierarchy</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Enrolled At</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredEnrollments.map((enrollment) => (
                      <TableRow key={enrollment.id}>
                        <TableCell className="font-medium">
                          {enrollment.studentEmail || enrollment.studentId}
                        </TableCell>
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
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleUnenrollClick(enrollment)}
                            disabled={unenrollingId === enrollment.id}
                          >
                            {unenrollingId === enrollment.id ? (
                              <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                              <Trash2 className="h-4 w-4 text-red-500" />
                            )}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              
              {/* Pagination Controls */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between mt-4 pt-4 border-t">
                  <div className="flex items-center gap-2">
                    <Label className="text-sm">Page size:</Label>
                    <Select
                      value={pageSize.toString()}
                      onValueChange={(value) => {
                        setPageSize(Number(value))
                        setCurrentPage(0) // Reset to first page when changing page size
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
                      Showing {currentPage * pageSize + 1} to {Math.min((currentPage + 1) * pageSize, totalElements)} of {totalElements.toLocaleString()}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(0)}
                      disabled={currentPage === 0 || loading}
                    >
                      First
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                      disabled={currentPage === 0 || loading}
                    >
                      <ChevronLeft className="h-4 w-4" />
                      Previous
                    </Button>
                    <span className="text-sm text-gray-600 px-2">
                      Page {currentPage + 1} of {totalPages}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
                      disabled={currentPage >= totalPages - 1 || loading}
                    >
                      Next
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage(totalPages - 1)}
                      disabled={currentPage >= totalPages - 1 || loading}
                    >
                      Last
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

      {/* Unenroll Confirmation Dialog */}
      <Dialog open={showUnenrollDialog} onOpenChange={setShowUnenrollDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm Unenrollment</DialogTitle>
            <DialogDescription>
              Are you sure you want to unenroll student {enrollmentToUnenroll?.studentId} from{' '}
              {enrollmentToUnenroll ? courses[enrollmentToUnenroll.courseId]?.title || enrollmentToUnenroll.courseId : 'this course'}?
              This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowUnenrollDialog(false)
                setEnrollmentToUnenroll(null)
              }}
              disabled={unenrollingId !== null}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleUnenrollConfirm}
              disabled={unenrollingId !== null}
            >
              {unenrollingId ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Unenrolling...
                </>
              ) : (
                'Confirm Unenroll'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add Enrollment Dialog */}
      <Dialog open={showAddEnrollmentDialog} onOpenChange={setShowAddEnrollmentDialog}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Add New Enrollment</DialogTitle>
            <DialogDescription>
              Enroll a student in a course. You can optionally specify class and section.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="student">Student *</Label>
              <Select value={selectedStudentId} onValueChange={setSelectedStudentId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a student" />
                </SelectTrigger>
                <SelectContent>
                  {students.map((student) => (
                    <SelectItem key={student.id} value={student.id}>
                      {student.email} {student.name && `(${student.name})`}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="course">Course *</Label>
              <Select value={selectedEnrollCourseId} onValueChange={setSelectedEnrollCourseId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a course" />
                </SelectTrigger>
                <SelectContent>
                  {allCourses
                    .filter(c => c.isPublished && c.status !== 'ARCHIVED')
                    .map((course) => (
                      <SelectItem key={course.id} value={course.id}>
                        {course.title}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="institute">Institute (Optional)</Label>
              <Select 
                value={selectedEnrollInstituteId} 
                onValueChange={(value) => {
                  setSelectedEnrollInstituteId(value)
                  setSelectedEnrollClassId('all')
                  setSelectedEnrollSectionId('all')
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select an institute" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">None</SelectItem>
                  {institutes.map((inst) => (
                    <SelectItem key={inst.id} value={inst.id}>{inst.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="class">Class (Optional)</Label>
              <Select 
                value={selectedEnrollClassId} 
                onValueChange={(value) => {
                  setSelectedEnrollClassId(value)
                  setSelectedEnrollSectionId('all')
                }}
                disabled={!selectedEnrollInstituteId || selectedEnrollInstituteId === 'all'}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a class" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">None</SelectItem>
                  {(() => {
                    const filtered = selectedEnrollInstituteId && selectedEnrollInstituteId !== 'all'
                      ? classes.filter(c => c.instituteId === selectedEnrollInstituteId)
                      : classes
                    return filtered.map((classItem) => (
                      <SelectItem key={classItem.id} value={classItem.id}>{classItem.name}</SelectItem>
                    ))
                  })()}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="section">Section (Optional)</Label>
              <Select 
                value={selectedEnrollSectionId} 
                onValueChange={setSelectedEnrollSectionId}
                disabled={!selectedEnrollClassId || selectedEnrollClassId === 'all'}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a section" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">None</SelectItem>
                  {(() => {
                    const filtered = selectedEnrollClassId && selectedEnrollClassId !== 'all'
                      ? sections.filter(s => s.classId === selectedEnrollClassId)
                      : sections
                    return filtered.map((section) => (
                      <SelectItem key={section.id} value={section.id}>{section.name}</SelectItem>
                    ))
                  })()}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowAddEnrollmentDialog(false)
                setSelectedStudentId('')
                setSelectedEnrollCourseId('')
                setSelectedEnrollInstituteId('')
                setSelectedEnrollClassId('')
                setSelectedEnrollSectionId('')
              }}
              disabled={enrolling}
            >
              Cancel
            </Button>
            <Button
              onClick={handleAddEnrollment}
              disabled={enrolling || !selectedStudentId || !selectedEnrollCourseId}
            >
              {enrolling ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Enrolling...
                </>
              ) : (
                'Enroll Student'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}


