'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, CourseCard, SearchBar, FilterBar } from '@edudron/ui-components'
import { coursesApi, enrollmentsApi } from '@/lib/api'
import { StudentLayout } from '@/components/StudentLayout'
import type { Course } from '@edudron/shared-utils'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function CoursesPage() {
  const router = useRouter()
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

  useEffect(() => {
    loadData()
  }, [])

  useEffect(() => {
    filterCourses()
  }, [courses, searchQuery, selectedFilters])

  const loadData = async () => {
    try {
      const userStr = localStorage.getItem('user')
      if (userStr) {
        setUser(JSON.parse(userStr))
      }

      const coursesData = await coursesApi.listCourses({ status: 'PUBLISHED' })
      setCourses(coursesData)
      setFilteredCourses(coursesData)
    } catch (error) {
      console.error('Failed to load courses:', error)
    } finally {
      setLoading(false)
    }
  }

  const filterCourses = () => {
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
  }

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
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          {/* Hero Section */}
          <div className="mb-8">
            <h2 className="text-4xl font-bold text-gray-900 mb-4">
              Learn Without Limits
            </h2>
            <p className="text-xl text-gray-600 mb-6">
              Start, switch, or advance your career with thousands of courses.
            </p>

            {/* Search Bar */}
            <div className="max-w-2xl">
              <SearchBar
                placeholder="What do you want to learn?"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full"
              />
            </div>
          </div>

          {/* Filters */}
          <div className="mb-6">
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
          <div className="mb-4">
            <p className="text-sm text-gray-600">
              {filteredCourses.length} course{filteredCourses.length !== 1 ? 's' : ''} found
            </p>
          </div>

          {/* Course Grid */}
          {loading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {[...Array(8)].map((_, i) => (
                <div
                  key={i}
                  className="bg-white rounded-lg shadow-sm border border-gray-200 animate-pulse"
                >
                  <div className="h-40 bg-gray-200"></div>
                  <div className="p-4 space-y-3">
                    <div className="h-4 bg-gray-200 rounded w-3/4"></div>
                    <div className="h-4 bg-gray-200 rounded w-1/2"></div>
                    <div className="h-4 bg-gray-200 rounded w-2/3"></div>
                  </div>
                </div>
              ))}
            </div>
          ) : filteredCourses.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-gray-500 text-lg mb-4">No courses found</p>
              <p className="text-gray-400 text-sm">
                Try adjusting your search or filters
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
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
