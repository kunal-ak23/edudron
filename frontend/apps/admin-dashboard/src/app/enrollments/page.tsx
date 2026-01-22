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
import { SearchableSelect, type SearchableSelectOption } from '@/components/ui/searchable-select'
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
  const [allEnrollments, setAllEnrollments] = useState<Enrollment[]>([]) // Store all enrollments for client-side filtering
  const [filteredEnrollments, setFilteredEnrollments] = useState<Enrollment[]>([])
  const [courses, setCourses] = useState<Record<string, Course>>({})
  const [allCourses, setAllCourses] = useState<Course[]>([]) // All courses for filter dropdown
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [classes, setClasses] = useState<Class[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [loading, setLoading] = useState(true)
  const [tableLoading, setTableLoading] = useState(false) // Separate loading state for table only
  const [initialLoadDone, setInitialLoadDone] = useState(false)
  const [selectedCourseId, setSelectedCourseId] = useState<string>('all')
  const [selectedInstituteId, setSelectedInstituteId] = useState<string>('all')
  const [selectedClassId, setSelectedClassId] = useState<string>('all')
  const [selectedSectionId, setSelectedSectionId] = useState<string>('all')
  const [searchEmail, setSearchEmail] = useState<string>('')
  const [debouncedSearchEmail, setDebouncedSearchEmail] = useState<string>('')
  const [lastSearchedEmail, setLastSearchedEmail] = useState<string>('') // Track last searched value to prevent duplicate calls
  const [unenrollingId, setUnenrollingId] = useState<string | null>(null)
  const [showUnenrollDialog, setShowUnenrollDialog] = useState(false)
  const [enrollmentToUnenroll, setEnrollmentToUnenroll] = useState<Enrollment | null>(null)
  const [showAddEnrollmentDialog, setShowAddEnrollmentDialog] = useState(false)
  const [students, setStudents] = useState<Array<{ id: string; email: string; name?: string }>>([])
  const [studentsLoading, setStudentsLoading] = useState(false)
  const [studentsSearchQuery, setStudentsSearchQuery] = useState('')
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

  // Load students with pagination and server-side email filtering
  // Passes email filter to API to avoid loading all pages
  const loadStudents = useCallback(async (searchQuery: string = '', page: number = 0, size: number = 100) => {
    try {
      setStudentsLoading(true)
      
      // Build API URL with email filter if provided
      let apiUrl = `/idp/users/role/STUDENT/paginated?page=${page}&size=${size}`
      if (searchQuery.trim()) {
        // Try email parameter first, fallback to search parameter
        const encodedQuery = encodeURIComponent(searchQuery.trim())
        apiUrl += `&email=${encodedQuery}`
      }
      
      try {
        const studentsResponse = await apiClient.get<{
          content: Array<{ id: string; email: string; name?: string }>
          totalElements: number
          totalPages: number
        }>(apiUrl)
        
        const loadedStudents = studentsResponse.data?.content || []
        
        // If backend doesn't support email filter, filter client-side on first page only
        if (searchQuery.trim() && loadedStudents.length > 0) {
          const query = searchQuery.toLowerCase().trim()
          const filtered = loadedStudents.filter(s => {
            const emailMatch = s.email?.toLowerCase().includes(query)
            const nameMatch = s.name?.toLowerCase().includes(query)
            return emailMatch || nameMatch
          })
          
          // If filtering removed all results but we have a search query,
          // the backend might not support email filtering - try with search parameter
          if (filtered.length === 0 && loadedStudents.length > 0) {
            try {
              const searchUrl = `/idp/users/role/STUDENT/paginated?page=${page}&size=${size}&search=${encodeURIComponent(searchQuery.trim())}`
              const searchResponse = await apiClient.get<{
                content: Array<{ id: string; email: string; name?: string }>
                totalElements: number
                totalPages: number
              }>(searchUrl)
              setStudents(searchResponse.data?.content || [])
              return
            } catch (searchError) {
              // Fall through to client-side filtering
            }
          }
          
          setStudents(filtered)
        } else {
          // No search query or backend filtered - use results as-is
          setStudents(loadedStudents)
        }
      } catch (paginatedError) {
        // Fallback to non-paginated endpoint if paginated doesn't exist
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
      // Continue without students - will show empty list
    } finally {
      setStudentsLoading(false)
    }
  }, [])

  // Load only enrollments (for search/filter updates without reloading everything)
  // Uses tableLoading instead of loading to avoid full page reload
  // Accepts searchEmail as parameter to avoid dependency issues
  const loadEnrollmentsOnly = useCallback(async (searchQuery: string = '') => {
    try {
      setTableLoading(true) // Only set table loading, not full page loading
      
      let enrollmentsResponse
      const emailToSearch = searchQuery || debouncedSearchEmail
      if (emailToSearch.trim()) {
        // When searching, use the same student API as the enrollment modal
        // Search for students by email using server-side filtering (single API call)
        const queryToSearch = emailToSearch.trim()
        let matchingStudentIds: string[] = []
        
        try {
          // Use server-side email filter - only fetch first page with filter (same as dialog)
          const emailParam = encodeURIComponent(queryToSearch)
          
          // Try email parameter first
          let studentsResponse
          try {
            studentsResponse = await apiClient.get<{
              content: Array<{ id: string; email: string; name?: string }>
              totalElements: number
              totalPages: number
            }>(`/idp/users/role/STUDENT/paginated?page=0&size=100&email=${emailParam}`)
          } catch (emailError) {
            // If email parameter doesn't work, try search parameter
            try {
              studentsResponse = await apiClient.get<{
                content: Array<{ id: string; email: string; name?: string }>
                totalElements: number
                totalPages: number
              }>(`/idp/users/role/STUDENT/paginated?page=0&size=100&search=${emailParam}`)
            } catch (searchError) {
              // If backend doesn't support filtering, filter client-side on first page only
              const fallbackResponse = await apiClient.get<{
                content: Array<{ id: string; email: string; name?: string }>
                totalElements: number
                totalPages: number
              }>(`/idp/users/role/STUDENT/paginated?page=0&size=100`)
              const allStudents = fallbackResponse.data?.content || []
              const query = queryToSearch.toLowerCase()
              const filtered = allStudents.filter(s => {
                const emailMatch = s.email?.toLowerCase().includes(query)
                const nameMatch = s.name?.toLowerCase().includes(query)
                return emailMatch || nameMatch
              })
              matchingStudentIds = filtered.map(s => s.id)
              throw new Error('Using client-side filter') // Skip to enrollment loading
            }
          }
          
          // Get students from successful response
          const students = studentsResponse.data?.content || []
          matchingStudentIds = students.map(s => s.id)
        } catch (studentSearchError: any) {
          // If we already have matchingStudentIds from client-side filter, continue
          if (studentSearchError.message !== 'Using client-side filter' && matchingStudentIds.length === 0) {
            console.error('Error searching students:', studentSearchError)
            // Fallback: continue with empty student IDs, will show no results
          }
        }
        
        // Filter enrollments by matching student IDs
        // Use existing enrollments for client-side filtering (no API call needed)
        let enrollmentsToFilter: Enrollment[] = []
        
        if (matchingStudentIds.length > 0) {
          // Filter existing enrollments client-side (fast, no API call)
          // If we don't have all enrollments cached, use what we have
          const enrollmentsToSearch = allEnrollments.length > 0 ? allEnrollments : enrollments
          enrollmentsToFilter = enrollmentsToSearch.filter(e => matchingStudentIds.includes(e.studentId))
          
          // No API call here - just filter cached enrollments client-side
          // This matches the dialog behavior - only student search API call, filter client-side
        }
        
        // Create a response-like object with filtered enrollments
        enrollmentsResponse = {
          content: enrollmentsToFilter,
          totalElements: enrollmentsToFilter.length,
          totalPages: 1,
          number: 0,
          size: enrollmentsToFilter.length,
          first: true,
          last: true
        }
      } else {
        // Normal pagination - load single page
        enrollmentsResponse = await enrollmentsApi.listAllEnrollmentsPaginated(currentPage, pageSize)
      }
      
      setEnrollments(enrollmentsResponse.content)
      setTotalElements(enrollmentsResponse.totalElements)
      setTotalPages(enrollmentsResponse.totalPages)

      // Load courses for current page (for display) - only reload course details, not all courses
      const courseIds = Array.from(new Set(enrollmentsResponse.content.map(e => e.courseId)))
      const coursePromises = courseIds.map(id => coursesApi.getCourse(id).catch(() => null))
      const coursesData = await Promise.all(coursePromises)
      const coursesMap: Record<string, Course> = {}
      coursesData.forEach((course, index) => {
        if (course) {
          coursesMap[courseIds[index]] = course
        }
      })
      // Merge with existing courses to avoid losing data
      setCourses(prev => ({ ...prev, ...coursesMap }))
    } catch (err: any) {
      console.error('Error loading enrollments:', err)
      toast({
        variant: 'destructive',
        title: 'Failed to load enrollments',
        description: extractErrorMessage(err),
      })
    } finally {
      setTableLoading(false) // Only clear table loading
    }
  }, [currentPage, pageSize, toast]) // Removed debouncedSearchEmail from deps - passed as parameter instead

  // Load all data (enrollments, courses, institutes, classes, sections) - only on initial load
  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      
      // Load enrollments - load more initially to enable client-side filtering
      const enrollmentsResponse = await enrollmentsApi.listAllEnrollmentsPaginated(currentPage, pageSize)
      
      // Also load first 500 enrollments for client-side filtering when searching
      let allEnrollmentsForFilter: Enrollment[] = []
      if (currentPage === 0) {
        try {
          const allEnrollmentsPage = await enrollmentsApi.listAllEnrollmentsPaginated(0, 500)
          allEnrollmentsForFilter = allEnrollmentsPage.content
        } catch (err) {
          // If loading all fails, just use current page
          allEnrollmentsForFilter = enrollmentsResponse.content
        }
      }
      
      const [institutesData, allCoursesData] = await Promise.all([
        institutesApi.listInstitutes(),
        coursesApi.listCourses().catch(() => []) // Load all courses for filter
      ])
      
      setEnrollments(enrollmentsResponse.content)
      setAllEnrollments(allEnrollmentsForFilter.length > 0 ? allEnrollmentsForFilter : enrollmentsResponse.content) // Store for client-side filtering
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

      // Students are loaded only when enrollment dialog opens (see useEffect below)
      // This avoids loading all students on page load
      setInitialLoadDone(true)
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

    // Note: Email search is now handled in loadData by searching students first
    // This ensures both the enrollment search and student dropdown use the same API
    // No additional filtering needed here for email search

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
  }, [enrollments, debouncedSearchEmail, selectedCourseId, selectedInstituteId, selectedClassId, selectedSectionId])

  // Initial load - load all data (enrollments, courses, institutes, etc.) only once
  useEffect(() => {
    if (!initialLoadDone) {
      loadData()
    }
  }, [loadData, initialLoadDone])

  // When pagination changes (not search), reload enrollments only
  useEffect(() => {
    // Only reload on pagination changes, not when searching
    if (initialLoadDone && !debouncedSearchEmail.trim() && !searchEmail.trim()) {
      // Only reload enrollments, not all data
      loadEnrollmentsOnly('')
    }
  }, [currentPage, pageSize, initialLoadDone, debouncedSearchEmail, searchEmail, loadEnrollmentsOnly])

  // When search changes, only reload enrollments (not all data) - debounced
  // This mimics the dialog behavior - only updates table, no page reload
  useEffect(() => {
    // Skip if initial load hasn't completed
    if (!initialLoadDone) return
    
    // Skip if search hasn't actually changed (prevents duplicate calls)
    if (debouncedSearchEmail === lastSearchedEmail) return
    
    // Update last searched to prevent duplicate calls
    setLastSearchedEmail(debouncedSearchEmail)
    
    // If searching or search was cleared, reload only enrollments
    // Uses tableLoading instead of loading to avoid full page reload
    // Pass the search query explicitly to avoid dependency issues
    loadEnrollmentsOnly(debouncedSearchEmail)
  }, [debouncedSearchEmail, loadEnrollmentsOnly, initialLoadDone, lastSearchedEmail])

  useEffect(() => {
    filterEnrollments()
  }, [filterEnrollments])

  // Debounce search email for enrollment page search (same as dialog)
  // This prevents API calls on every keystroke
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setDebouncedSearchEmail(searchEmail)
    }, 300) // 300ms debounce - same as dialog
    
    return () => clearTimeout(timeoutId)
  }, [searchEmail])

  // Load students when enrollment dialog opens (only first page - 100 students)
  useEffect(() => {
    if (showAddEnrollmentDialog && students.length === 0 && !studentsSearchQuery.trim()) {
      loadStudents('', 0, 100)
    }
  }, [showAddEnrollmentDialog, loadStudents, students.length, studentsSearchQuery])

  // Handle student search - updates search query state
  const handleStudentSearch = useCallback((query: string) => {
    setStudentsSearchQuery(query)
  }, [])

  // Debounced effect to search students by email when search query changes
  useEffect(() => {
    if (!showAddEnrollmentDialog) {
      // Reset search query when dialog closes
      setStudentsSearchQuery('')
      return
    }
    
    // Skip if this is the initial load (students.length === 0 and no search query)
    if (students.length === 0 && !studentsSearchQuery.trim()) {
      return
    }
    
    // Debounce the search - wait 300ms after user stops typing
    const timeoutId = setTimeout(() => {
      const trimmedQuery = studentsSearchQuery.trim()
      // Always use server-side filtering - pass search query to API
      // This only loads one page with the filter applied, not all pages
      loadStudents(trimmedQuery, 0, 100)
    }, 300) // 300ms debounce - wait for user to stop typing
    
    return () => clearTimeout(timeoutId)
  }, [studentsSearchQuery, showAddEnrollmentDialog, loadStudents, students.length])


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
    setDebouncedSearchEmail('')
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
      
      // Reload only enrollments to refresh table (not all data)
      await loadEnrollmentsOnly(debouncedSearchEmail)
      
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
      if (selectedEnrollClassId) {
        options.classId = selectedEnrollClassId
      }
      if (selectedEnrollSectionId) {
        options.sectionId = selectedEnrollSectionId
      }
      if (selectedEnrollInstituteId) {
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
      
      // Reload only enrollments to show new enrollment (not all data)
      await loadEnrollmentsOnly(debouncedSearchEmail)
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
                      placeholder="Search by email or username..."
                      value={searchEmail}
                      onChange={(e) => {
                        setSearchEmail(e.target.value)
                        setCurrentPage(0) // Reset to first page when searching
                        // The loadData will be triggered by the useEffect when searchEmail changes
                      }}
                      className="pl-8"
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label>Course</Label>
                  <SearchableSelect
                    options={[
                      { value: 'all', label: 'All Courses' },
                      ...allCourses
                        .filter(c => c.isPublished && c.status !== 'ARCHIVED')
                        .map(course => ({
                          value: course.id,
                          label: course.title,
                          searchText: course.title.toLowerCase(),
                        })),
                    ]}
                    value={selectedCourseId}
                    onValueChange={(value) => {
                      setSelectedCourseId(value)
                      setCurrentPage(0) // Reset to first page when filtering
                    }}
                    placeholder="All Courses"
                    emptyMessage="No courses found"
                  />
                </div>
                <div className="space-y-2">
                  <Label>Institute</Label>
                  <SearchableSelect
                    options={[
                      { value: 'all', label: 'All Institutes' },
                      ...institutes.map(inst => ({
                        value: inst.id,
                        label: inst.name,
                        searchText: inst.name.toLowerCase(),
                      })),
                    ]}
                    value={selectedInstituteId}
                    onValueChange={(value) => {
                      setSelectedInstituteId(value)
                      setSelectedClassId('all')
                      setSelectedSectionId('all')
                    }}
                    placeholder="All Institutes"
                    emptyMessage="No institutes found"
                  />
                </div>
                <div className="space-y-2">
                  <Label>Class</Label>
                  <SearchableSelect
                    options={[
                      { value: 'all', label: 'All Classes' },
                      ...getFilteredClasses().map(classItem => ({
                        value: classItem.id,
                        label: classItem.name,
                        searchText: classItem.name.toLowerCase(),
                      })),
                    ]}
                    value={selectedClassId}
                    onValueChange={(value) => {
                      setSelectedClassId(value)
                      setSelectedSectionId('all')
                    }}
                    placeholder="All Classes"
                    emptyMessage="No classes found"
                    disabled={!selectedInstituteId || selectedInstituteId === 'all'}
                  />
                </div>
                <div className="space-y-2">
                  <Label>Section</Label>
                  <SearchableSelect
                    options={[
                      { value: 'all', label: 'All Sections' },
                      ...getFilteredSections().map(section => ({
                        value: section.id,
                        label: section.name,
                        searchText: section.name.toLowerCase(),
                      })),
                    ]}
                    value={selectedSectionId}
                    onValueChange={setSelectedSectionId}
                    placeholder="All Sections"
                    emptyMessage="No sections found"
                    disabled={!selectedClassId || selectedClassId === 'all'}
                  />
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
              {tableLoading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-primary" />
                  <span className="ml-2 text-sm text-gray-600">Loading enrollments...</span>
                </div>
              ) : filteredEnrollments.length === 0 ? (
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
                options={allCourses
                  .filter(c => c.isPublished && c.status !== 'ARCHIVED')
                  .map((course) => ({
                    value: course.id,
                    label: course.title,
                    searchText: course.title.toLowerCase(),
                  }))}
                value={selectedEnrollCourseId}
                onValueChange={setSelectedEnrollCourseId}
                placeholder="Select a course"
                emptyMessage="No courses found"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="institute">Institute (Optional)</Label>
              <SearchableSelect
                options={[
                  { value: '', label: 'None' },
                  ...institutes.map((inst) => ({
                    value: inst.id,
                    label: inst.name,
                    searchText: inst.name.toLowerCase(),
                  })),
                ]}
                value={selectedEnrollInstituteId || ''}
                onValueChange={(value) => {
                  setSelectedEnrollInstituteId(value)
                  setSelectedEnrollClassId('')
                  setSelectedEnrollSectionId('')
                }}
                placeholder="Select an institute"
                emptyMessage="No institutes found"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="class">Class (Optional)</Label>
              <SearchableSelect
                options={(() => {
                  const filtered = selectedEnrollInstituteId
                    ? classes.filter(c => c.instituteId === selectedEnrollInstituteId)
                    : classes
                  return [
                    { value: '', label: 'None' },
                    ...filtered.map((classItem) => ({
                      value: classItem.id,
                      label: classItem.name,
                      searchText: classItem.name.toLowerCase(),
                    })),
                  ]
                })()}
                value={selectedEnrollClassId || ''}
                onValueChange={(value) => {
                  setSelectedEnrollClassId(value)
                  setSelectedEnrollSectionId('')
                }}
                placeholder="Select a class"
                emptyMessage="No classes found"
                disabled={!selectedEnrollInstituteId}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="section">Section (Optional)</Label>
              <SearchableSelect
                options={(() => {
                  const filtered = selectedEnrollClassId
                    ? sections.filter(s => s.classId === selectedEnrollClassId)
                    : sections
                  return [
                    { value: '', label: 'None' },
                    ...filtered.map((section) => ({
                      value: section.id,
                      label: section.name,
                      searchText: section.name.toLowerCase(),
                    })),
                  ]
                })()}
                value={selectedEnrollSectionId || ''}
                onValueChange={setSelectedEnrollSectionId}
                placeholder="Select a section"
                emptyMessage="No sections found"
                disabled={!selectedEnrollClassId}
              />
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


