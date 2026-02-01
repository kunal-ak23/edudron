'use client'

import { useEffect, useState, useCallback } from 'react'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Plus, Loader2, Trash2, Users, BookOpen, GraduationCap } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

interface InstructorAssignment {
  id: string
  clientId: string
  instructorUserId: string
  instructorName?: string
  instructorEmail?: string
  assignmentType: 'CLASS' | 'SECTION' | 'COURSE'
  classId?: string
  className?: string
  sectionId?: string
  sectionName?: string
  courseId?: string
  courseName?: string
  scopedClassIds?: string[]
  scopedSectionIds?: string[]
  createdAt: string
}

interface User {
  id: string
  name: string
  email: string
  role: string
}

interface Class {
  id: string
  name: string
  code: string
}

interface Section {
  id: string
  name: string
  classId: string
}

interface Course {
  id: string
  title: string
}

export default function InstructorAssignmentsPage() {
  const { toast } = useToast()
  const { user } = useAuth()
  const [assignments, setAssignments] = useState<InstructorAssignment[]>([])
  const [instructors, setInstructors] = useState<User[]>([])
  const [classes, setClasses] = useState<Class[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // Form state
  const [selectedInstructor, setSelectedInstructor] = useState('')
  const [assignmentType, setAssignmentType] = useState<'CLASS' | 'SECTION' | 'COURSE'>('CLASS')
  const [selectedClass, setSelectedClass] = useState('')
  const [selectedSection, setSelectedSection] = useState('')
  const [selectedCourse, setSelectedCourse] = useState('')

  const canManageAssignments = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      
      // Load all data in parallel
      const [assignmentsRes, instructorsRes, classesRes, sectionsRes, coursesRes] = await Promise.all([
        apiClient.get<InstructorAssignment[]>('/api/instructor-assignments'),
        apiClient.get<{ content: User[] }>('/idp/users/paginated?role=INSTRUCTOR&size=100'),
        apiClient.get<Class[]>('/api/classes'),
        apiClient.get<Section[]>('/api/sections'),
        apiClient.get<{ content: Course[] }>('/content/courses?size=100'),
      ])

      setAssignments(assignmentsRes.data || [])
      setInstructors(instructorsRes.data?.content || [])
      setClasses(classesRes.data || [])
      setSections(sectionsRes.data || [])
      setCourses(coursesRes.data?.content || [])
    } catch (error: any) {
      console.error('Failed to load data:', error)
      toast({
        title: 'Error',
        description: extractErrorMessage(error),
        variant: 'destructive',
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleCreateAssignment = async () => {
    if (!selectedInstructor) {
      toast({
        title: 'Error',
        description: 'Please select an instructor',
        variant: 'destructive',
      })
      return
    }

    try {
      setSubmitting(true)

      const payload: any = {
        instructorUserId: selectedInstructor,
        assignmentType,
      }

      if (assignmentType === 'CLASS') {
        if (!selectedClass) {
          toast({
            title: 'Error',
            description: 'Please select a class',
            variant: 'destructive',
          })
          return
        }
        payload.classId = selectedClass
      } else if (assignmentType === 'SECTION') {
        if (!selectedSection) {
          toast({
            title: 'Error',
            description: 'Please select a section',
            variant: 'destructive',
          })
          return
        }
        payload.sectionId = selectedSection
      } else if (assignmentType === 'COURSE') {
        if (!selectedCourse) {
          toast({
            title: 'Error',
            description: 'Please select a course',
            variant: 'destructive',
          })
          return
        }
        payload.courseId = selectedCourse
      }

      await apiClient.post('/api/instructor-assignments', payload)

      toast({
        title: 'Success',
        description: 'Assignment created successfully',
      })

      setDialogOpen(false)
      resetForm()
      loadData()
    } catch (error: any) {
      console.error('Failed to create assignment:', error)
      toast({
        title: 'Error',
        description: extractErrorMessage(error),
        variant: 'destructive',
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDeleteAssignment = async (assignmentId: string) => {
    if (!confirm('Are you sure you want to remove this assignment?')) {
      return
    }

    try {
      await apiClient.delete(`/api/instructor-assignments/${assignmentId}`)

      toast({
        title: 'Success',
        description: 'Assignment removed successfully',
      })

      loadData()
    } catch (error: any) {
      console.error('Failed to delete assignment:', error)
      toast({
        title: 'Error',
        description: extractErrorMessage(error),
        variant: 'destructive',
      })
    }
  }

  const resetForm = () => {
    setSelectedInstructor('')
    setAssignmentType('CLASS')
    setSelectedClass('')
    setSelectedSection('')
    setSelectedCourse('')
  }

  const getAssignmentTypeBadge = (type: string) => {
    switch (type) {
      case 'CLASS':
        return <Badge variant="default"><Users className="w-3 h-3 mr-1" />Class</Badge>
      case 'SECTION':
        return <Badge variant="secondary"><GraduationCap className="w-3 h-3 mr-1" />Section</Badge>
      case 'COURSE':
        return <Badge variant="outline"><BookOpen className="w-3 h-3 mr-1" />Course</Badge>
      default:
        return <Badge variant="outline">{type}</Badge>
    }
  }

  const getAssignmentTarget = (assignment: InstructorAssignment) => {
    switch (assignment.assignmentType) {
      case 'CLASS':
        return assignment.className || assignment.classId || 'Unknown Class'
      case 'SECTION':
        return assignment.sectionName || assignment.sectionId || 'Unknown Section'
      case 'COURSE':
        return assignment.courseName || assignment.courseId || 'Unknown Course'
      default:
        return 'Unknown'
    }
  }

  // Group assignments by instructor
  const groupedAssignments = assignments.reduce((acc, assignment) => {
    const key = assignment.instructorUserId
    if (!acc[key]) {
      acc[key] = {
        instructor: instructors.find(i => i.id === assignment.instructorUserId),
        assignments: [],
      }
    }
    acc[key].assignments.push(assignment)
    return acc
  }, {} as Record<string, { instructor?: User; assignments: InstructorAssignment[] }>)

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="w-8 h-8 animate-spin" />
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 px-4">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">Instructor Assignments</h1>
          <p className="text-muted-foreground">
            Manage which classes, sections, and courses instructors can access
          </p>
        </div>
        {canManageAssignments && (
          <Button onClick={() => setDialogOpen(true)}>
            <Plus className="w-4 h-4 mr-2" />
            Add Assignment
          </Button>
        )}
      </div>

      {Object.keys(groupedAssignments).length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center">
            <Users className="w-12 h-12 mx-auto mb-4 text-muted-foreground" />
            <h3 className="text-lg font-medium mb-2">No assignments yet</h3>
            <p className="text-muted-foreground mb-4">
              Assign instructors to specific classes, sections, or courses to control their access.
            </p>
            {canManageAssignments && (
              <Button onClick={() => setDialogOpen(true)}>
                <Plus className="w-4 h-4 mr-2" />
                Create First Assignment
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          {Object.entries(groupedAssignments).map(([instructorId, data]) => (
            <Card key={instructorId}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Users className="w-5 h-5" />
                  {data.instructor?.name || 'Unknown Instructor'}
                  <span className="text-sm font-normal text-muted-foreground">
                    ({data.instructor?.email || instructorId})
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Type</TableHead>
                      <TableHead>Target</TableHead>
                      <TableHead>Created</TableHead>
                      {canManageAssignments && <TableHead className="w-[80px]">Actions</TableHead>}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.assignments.map((assignment) => (
                      <TableRow key={assignment.id}>
                        <TableCell>{getAssignmentTypeBadge(assignment.assignmentType)}</TableCell>
                        <TableCell className="font-medium">
                          {getAssignmentTarget(assignment)}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {new Date(assignment.createdAt).toLocaleDateString()}
                        </TableCell>
                        {canManageAssignments && (
                          <TableCell>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleDeleteAssignment(assignment.id)}
                            >
                              <Trash2 className="w-4 h-4 text-destructive" />
                            </Button>
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Create Assignment Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>Create Assignment</DialogTitle>
            <DialogDescription>
              Assign an instructor to a class, section, or course.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="instructor">Instructor</Label>
              <Select value={selectedInstructor} onValueChange={setSelectedInstructor}>
                <SelectTrigger>
                  <SelectValue placeholder="Select an instructor" />
                </SelectTrigger>
                <SelectContent>
                  {instructors.map((instructor) => (
                    <SelectItem key={instructor.id} value={instructor.id}>
                      {instructor.name} ({instructor.email})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="type">Assignment Type</Label>
              <Select value={assignmentType} onValueChange={(v) => setAssignmentType(v as any)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CLASS">Class - Access all sections in class</SelectItem>
                  <SelectItem value="SECTION">Section - Access specific section only</SelectItem>
                  <SelectItem value="COURSE">Course - Access specific course</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {assignmentType === 'CLASS' && (
              <div className="grid gap-2">
                <Label htmlFor="class">Class</Label>
                <Select value={selectedClass} onValueChange={setSelectedClass}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select a class" />
                  </SelectTrigger>
                  <SelectContent>
                    {classes.map((cls) => (
                      <SelectItem key={cls.id} value={cls.id}>
                        {cls.name} ({cls.code})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {assignmentType === 'SECTION' && (
              <div className="grid gap-2">
                <Label htmlFor="section">Section</Label>
                <Select value={selectedSection} onValueChange={setSelectedSection}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select a section" />
                  </SelectTrigger>
                  <SelectContent>
                    {sections.map((section) => (
                      <SelectItem key={section.id} value={section.id}>
                        {section.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {assignmentType === 'COURSE' && (
              <div className="grid gap-2">
                <Label htmlFor="course">Course</Label>
                <Select value={selectedCourse} onValueChange={setSelectedCourse}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select a course" />
                  </SelectTrigger>
                  <SelectContent>
                    {courses.map((course) => (
                      <SelectItem key={course.id} value={course.id}>
                        {course.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleCreateAssignment} disabled={submitting}>
              {submitting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
              Create Assignment
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
