'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, CourseCard, SearchBar, FilterBar } from '@kunal-ak23/edudron-ui-components'
import { coursesApi, enrollmentsApi } from '@/lib/api'
import { StudentLayout } from '@/components/StudentLayout'
import type { Course, Enrollment } from '@kunal-ak23/edudron-shared-utils'
import { TenantFeaturesApi } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function CoursesPage() {
  const router = useRouter()
  const { needsTenantSelection, tenantId } = useAuth()
  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFilters, setSelectedFilters] = useState({
    difficulty: '',
    category: '',
    price: ''
  })
  const [user, setUser] = useState<any>(null)
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [selfEnrollmentEnabled, setSelfEnrollmentEnabled] = useState<boolean | null>(null)
  
  // Initialize tenant features API
  const tenantFeaturesApi = new TenantFeaturesApi(getApiClient())

  useEffect(() => {
    if (needsTenantSelection) {
      console.info('[StudentPortal][Courses] redirecting to /select-tenant', { tenantId })
      router.replace('/select-tenant')
    }
  }, [needsTenantSelection, router, tenantId])

  const loadData = useCallback(async () => {
    if (needsTenantSelection) {
      // Avoid firing tenant-scoped API calls until a tenant is selected
      setLoading(false)
      return
    }

    try {
      const userStr = localStorage.getItem('user')
      let currentUser = null
      if (userStr) {
        currentUser = JSON.parse(userStr)
        setUser(currentUser)
      }

      // Check if student self-enrollment is enabled (only for students)
      let isSelfEnrollmentEnabled = true // Default to true for admins/instructors
      if (currentUser?.role === 'STUDENT') {
        try {
          isSelfEnrollmentEnabled = await tenantFeaturesApi.isStudentSelfEnrollmentEnabled()
          setSelfEnrollmentEnabled(isSelfEnrollmentEnabled)
        } catch (error) {
          // Default to false if check fails
          isSelfEnrollmentEnabled = false
          setSelfEnrollmentEnabled(false)
        }
      } else {
        setSelfEnrollmentEnabled(true)
      }

      // Fetch published courses and enrollments in parallel
      let enrollmentsData: Enrollment[] = []
      try {
        const enrollmentsResponse = await enrollmentsApi.listEnrollments()
        enrollmentsData = Array.isArray(enrollmentsResponse) ? enrollmentsResponse : []
      } catch (error) {
        enrollmentsData = []
      }
      
      const coursesData = await coursesApi.listCourses({ status: 'PUBLISHED' })

      setEnrollments(enrollmentsData)
      
      // Get enrolled course IDs - filter out any null/undefined courseIds
      const enrolledCourseIds = new Set(
        enrollmentsData
          .map(e => e.courseId)
          .filter((courseId): courseId is string => courseId != null && courseId !== '')
      )
      
      // Backend now filters unpublished courses for students, so we can use the published courses directly
      // For students with self-enrollment disabled, filter to only enrolled courses
      let visibleCourses: Course[]
      if (currentUser?.role === 'STUDENT' && !isSelfEnrollmentEnabled) {
        // If self-enrollment is disabled, only show enrolled courses
        // Backend already ensures only published courses are returned
        visibleCourses = coursesData.filter(course => {
          const isEnrolled = enrolledCourseIds.has(course.id)
          return isEnrolled
        })
      } else {
        // For students with self-enrollment enabled OR admins/instructors
        // Backend already filters to published courses for students
        visibleCourses = coursesData
      }
      
      setCourses(visibleCourses)
      setFilteredCourses(visibleCourses)
    } catch (error) {
    } finally {
      setLoading(false)
    }
  }, [needsTenantSelection])

  useEffect(() => {
    if (!needsTenantSelection) {
      loadData()
    }
  }, [loadData])

  const filterCourses = useCallback(() => {
    let filtered = [...courses]

    // Search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase()
      filtered = filtered.filter(
        (course) =>
          course.title.toLowerCase().includes(query) ||
          course.description?.toLowerCase().includes(query) ||
          course.tags?.some((tag) => tag.toLowerCase().includes(query))
      )
    }

    // Difficulty filter
    if (selectedFilters.difficulty) {
      filtered = filtered.filter(
        (course) => course.difficultyLevel === selectedFilters.difficulty
      )
    }

    // Price filter
    if (selectedFilters.price) {
      if (selectedFilters.price === 'free') {
        filtered = filtered.filter((course) => course.isFree)
      } else if (selectedFilters.price === 'paid') {
        filtered = filtered.filter((course) => !course.isFree)
      }
    }

    setFilteredCourses(filtered)
  }, [courses, searchQuery, selectedFilters])

  useEffect(() => {
    filterCourses()
  }, [filterCourses])

  const handleFilterChange = (filterType: string, value: string) => {
    setSelectedFilters((prev) => ({
      ...prev,
      [filterType]: value
    }))
  }


  const difficultyOptions = [
    { label: 'Beginner', value: 'BEGINNER' },
    { label: 'Intermediate', value: 'INTERMEDIATE' },
    { label: 'Advanced', value: 'ADVANCED' }
  ]

  const priceOptions = [
    { label: 'Free', value: 'free' },
    { label: 'Paid', value: 'paid' }
  ]

  return (
    <ProtectedRoute>
      <StudentLayout>
        <main className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
          {/* Hero Section */}
          <div className="mb-3">
            <h2 className="text-4xl font-bold text-gray-900 mb-1.5">
              Learn Without Limits
            </h2>
            <p className="text-xl text-gray-600 mb-3">
              Start, switch, or advance your career with thousands of courses.
            </p>

            {/* Search Bar */}
            <div className="max-w-xl">
              <SearchBar
                placeholder="What do you want to learn?"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full"
              />
            </div>
          </div>

          {/* Filters */}
          <div className="mb-3">
            <FilterBar
              filters={{
                difficulty: difficultyOptions,
                price: priceOptions
              }}
              selectedFilters={selectedFilters}
              onFilterChange={handleFilterChange}
            />
          </div>

          {/* Results Count */}
          <div className="mb-2">
            <p className="text-sm text-gray-600">
              {filteredCourses.length} course{filteredCourses.length !== 1 ? 's' : ''} found
            </p>
          </div>

          {/* Course Grid */}
          {loading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
              {[...Array(8)].map((_, i) => (
                <div
                  key={i}
                  className="bg-white rounded-lg shadow-sm border border-gray-200 animate-pulse"
                >
                  <div className="w-full aspect-video bg-gray-200"></div>
                  <div className="p-4 space-y-3">
                    <div className="h-4 bg-gray-200 rounded w-3/4"></div>
                    <div className="h-4 bg-gray-200 rounded w-1/2"></div>
                    <div className="h-4 bg-gray-200 rounded w-2/3"></div>
                  </div>
                </div>
              ))}
            </div>
          ) : filteredCourses.length === 0 ? (
            <div className="text-center py-6">
              <p className="text-gray-500 text-lg mb-2">No courses found</p>
              <p className="text-gray-400 text-sm">
                Try adjusting your search or filters
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
              {filteredCourses.map((course) => (
                <CourseCard
                  key={course.id}
                  course={course}
                  onClick={() => router.push(`/courses/${course.id}`)}
                />
              ))}
            </div>
          )}
        </main>
      </StudentLayout>
    </ProtectedRoute>
  )
}
