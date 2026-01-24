'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { Loader2 } from 'lucide-react'
import { enrollmentsApi, coursesApi, apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import type { Course } from '@kunal-ak23/edudron-shared-utils'

interface AddStudentToClassDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  classId: string
  onSuccess?: () => void
}

export function AddStudentToClassDialog({
  open,
  onOpenChange,
  classId,
  onSuccess,
}: AddStudentToClassDialogProps) {
  const { toast } = useToast()
  const [students, setStudents] = useState<Array<{ id: string; email: string; name?: string }>>([])
  const [studentsLoading, setStudentsLoading] = useState(false)
  const [studentsSearchQuery, setStudentsSearchQuery] = useState('')
  const [selectedStudentId, setSelectedStudentId] = useState<string>('')
  const [selectedCourseId, setSelectedCourseId] = useState<string>('')
  const [courses, setCourses] = useState<Course[]>([])
  const [coursesLoading, setCoursesLoading] = useState(false)
  const [enrolling, setEnrolling] = useState(false)

  // Load students with pagination and backend filtering
  const loadStudents = useCallback(async (searchQuery: string = '', page: number = 0, size: number = 100) => {
    try {
      setStudentsLoading(true)
      
      const params = new URLSearchParams()
      params.append('page', page.toString())
      params.append('size', size.toString())
      
      if (searchQuery.trim()) {
        params.append('search', searchQuery.trim())
      }
      
      const apiUrl = `/idp/users/role/STUDENT/paginated?${params.toString()}`
      
      try {
        const studentsResponse = await apiClient.get<{
          content: Array<{ id: string; email: string; name?: string }>
          totalElements: number
          totalPages: number
        }>(apiUrl)
        
        const loadedStudents = studentsResponse.data?.content || []
        setStudents(loadedStudents)
      } catch (paginatedError) {
        console.warn('Paginated endpoint failed, falling back to non-paginated:', paginatedError)
        const studentsResponse = await apiClient.get<Array<{ id: string; email: string; name?: string }>>('/idp/users/role/STUDENT')
        const allStudents = studentsResponse.data || []
        
        if (searchQuery.trim()) {
          const query = searchQuery.toLowerCase().trim()
          const filtered = allStudents.filter(s => {
            const emailMatch = s.email?.toLowerCase().includes(query)
            const nameMatch = s.name?.toLowerCase().includes(query)
            return emailMatch || nameMatch
          })
          setStudents(filtered.slice(0, size))
        } else {
          setStudents(allStudents.slice(0, size))
        }
      }
    } catch (err) {
      console.error('Error loading students:', err)
    } finally {
      setStudentsLoading(false)
    }
  }, [])

  // Load courses
  const loadCourses = useCallback(async () => {
    try {
      setCoursesLoading(true)
      const coursesData = await coursesApi.listCourses()
      setCourses(coursesData.filter(c => c.isPublished && c.status !== 'ARCHIVED'))
    } catch (err) {
      console.error('Error loading courses:', err)
    } finally {
      setCoursesLoading(false)
    }
  }, [])

  // Load students when dialog opens
  useEffect(() => {
    if (open && students.length === 0 && !studentsSearchQuery.trim()) {
      loadStudents('', 0, 100)
    }
  }, [open, loadStudents, students.length, studentsSearchQuery])

  // Load courses when dialog opens
  useEffect(() => {
    if (open) {
      loadCourses()
    }
  }, [open, loadCourses])

  // Handle student search
  const handleStudentSearch = useCallback((query: string) => {
    setStudentsSearchQuery(query)
  }, [])

  // Debounced effect to search students
  useEffect(() => {
    if (!open) {
      setStudentsSearchQuery('')
      return
    }
    
    if (students.length === 0 && !studentsSearchQuery.trim()) {
      return
    }
    
    const timeoutId = setTimeout(() => {
      const trimmedQuery = studentsSearchQuery.trim()
      loadStudents(trimmedQuery, 0, 100)
    }, 300)
    
    return () => clearTimeout(timeoutId)
  }, [studentsSearchQuery, open, loadStudents, students.length])

  const handleAddStudent = async () => {
    if (!selectedStudentId || !selectedCourseId) {
      toast({
        variant: 'destructive',
        title: 'Missing required fields',
        description: 'Please select both student and course',
      })
      return
    }

    setEnrolling(true)
    try {
      await enrollmentsApi.enrollStudentInCourse(selectedStudentId, selectedCourseId, {
        classId,
      })
      
      toast({
        title: 'Success',
        description: 'Student has been enrolled in the course and associated with the class',
      })
      
      // Reset form
      setSelectedStudentId('')
      setSelectedCourseId('')
      onOpenChange(false)
      
      // Call success callback
      if (onSuccess) {
        onSuccess()
      }
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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Add Student to Class</DialogTitle>
          <DialogDescription>
            Enroll a student in a course and associate them with this class.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="student">Student *</Label>
            <SearchableSelect
              options={students.map((student) => ({
                value: student.id,
                label: student.name ? `${student.email} (${student.name})` : student.email,
                searchText: `${student.email} ${student.name || ''}`.toLowerCase(),
              }))}
              value={selectedStudentId}
              onValueChange={setSelectedStudentId}
              placeholder="Select a student"
              emptyMessage="No students found"
              onSearch={handleStudentSearch}
              loading={studentsLoading}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="course">Course *</Label>
            <SearchableSelect
              options={courses.map((course) => ({
                value: course.id,
                label: course.title,
                searchText: course.title.toLowerCase(),
              }))}
              value={selectedCourseId}
              onValueChange={setSelectedCourseId}
              placeholder="Select a course"
              emptyMessage="No courses found"
              loading={coursesLoading}
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              onOpenChange(false)
              setSelectedStudentId('')
              setSelectedCourseId('')
            }}
            disabled={enrolling}
          >
            Cancel
          </Button>
          <Button
            onClick={handleAddStudent}
            disabled={enrolling || !selectedStudentId || !selectedCourseId}
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
  )
}
