'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, CourseCard, Card } from '@kunal-ak23/edudron-ui-components'
import { enrollmentsApi, coursesApi } from '@/lib/api'
import { StudentLayout } from '@/components/StudentLayout'
import type { Enrollment, Course, Progress } from '@kunal-ak23/edudron-shared-utils'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function MyCoursesPage() {
  const router = useRouter()
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [courses, setCourses] = useState<Record<string, Course>>({})
  const [progressData, setProgressData] = useState<Record<string, Progress>>({})
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<'all' | 'in-progress' | 'completed'>('all')

  useEffect(() => {
    loadEnrollments()
  }, [])

  const loadEnrollments = async () => {
    try {
      const enrollmentsData = await enrollmentsApi.listEnrollments()
      setEnrollments(enrollmentsData)

      // Deduplicate course IDs to avoid fetching the same course multiple times
      const uniqueCourseIds = Array.from(new Set(enrollmentsData.map(e => e.courseId)))
      
      // Load course details only for unique courses
      const coursePromises = uniqueCourseIds.map((courseId) =>
        coursesApi.getCourse(courseId).catch(() => null)
      )
      const coursesData = await Promise.all(coursePromises)
      const coursesMap: Record<string, Course> = {}
      coursesData.forEach((course, index) => {
        if (course) {
          coursesMap[uniqueCourseIds[index]] = course
        }
      })
      setCourses(coursesMap)

      // Load progress for each course
      const progressPromises = enrollmentsData.map((enrollment) =>
        enrollmentsApi.getProgress(enrollment.courseId).catch(() => null)
      )
      const progressResults = await Promise.all(progressPromises)
      const progressMap: Record<string, Progress> = {}
      progressResults.forEach((progress, index) => {
        if (progress) {
          progressMap[enrollmentsData[index].courseId] = progress
        }
      })
      setProgressData(progressMap)
    } catch (error) {
      console.error('Failed to load enrollments:', error)
    } finally {
      setLoading(false)
    }
  }

  const filteredEnrollments = enrollments.filter((enrollment) => {
    const progress = progressData[enrollment.courseId]
    const progressPercent = progress?.overallProgress ?? 0

    if (activeTab === 'completed') {
      return progressPercent >= 1 || enrollment.status === 'COMPLETED'
    }
    if (activeTab === 'in-progress') {
      return progressPercent > 0 && progressPercent < 1 && enrollment.status !== 'COMPLETED'
    }
    return true
  })

  return (
    <ProtectedRoute>
      <StudentLayout>
        <main className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
          {/* Page Header */}
          <div className="mb-8">
            <h2 className="text-3xl font-bold text-gray-900 mb-2">My Courses</h2>
            <p className="text-gray-600">
              Continue learning from where you left off
            </p>
          </div>

          {/* Tabs */}
          <div className="mb-6 border-b border-gray-200">
            <nav className="flex space-x-8">
              {[
                { id: 'all', label: 'All Courses' },
                { id: 'in-progress', label: 'In Progress' },
                { id: 'completed', label: 'Completed' }
              ].map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as any)}
                  className={`py-4 px-1 border-b-2 font-medium text-sm ${
                    activeTab === tab.id
                      ? 'border-primary-600 text-primary-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }`}
                >
                  {tab.label}
                  <span className="ml-2 text-gray-400">
                    ({tab.id === 'all' ? enrollments.length : filteredEnrollments.length})
                  </span>
                </button>
              ))}
            </nav>
          </div>

          {/* Course Grid */}
          {loading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {[...Array(4)].map((_, i) => (
                <div
                  key={i}
                  className="bg-white rounded-lg shadow-sm border border-gray-200 animate-pulse"
                >
                  <div className="w-full aspect-video bg-gray-200"></div>
                  <div className="p-4 space-y-3">
                    <div className="h-4 bg-gray-200 rounded w-3/4"></div>
                    <div className="h-4 bg-gray-200 rounded w-1/2"></div>
                  </div>
                </div>
              ))}
            </div>
          ) : filteredEnrollments.length === 0 ? (
            <Card>
              <div className="text-center py-12">
                <svg
                  className="mx-auto h-12 w-12 text-gray-400"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
                  />
                </svg>
                <h3 className="mt-2 text-sm font-medium text-gray-900">No courses</h3>
                <p className="mt-1 text-sm text-gray-500">
                  {activeTab === 'all'
                    ? "You haven't enrolled in any courses yet."
                    : activeTab === 'in-progress'
                    ? "You don't have any courses in progress."
                    : "You haven't completed any courses yet."}
                </p>
                <div className="mt-6">
                  <button
                    onClick={() => router.push('/courses')}
                    className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-primary-600 hover:bg-primary-700"
                  >
                    Browse Courses
                  </button>
                </div>
              </div>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {filteredEnrollments.map((enrollment) => {
                const course = courses[enrollment.courseId]
                const progress = progressData[enrollment.courseId]
                if (!course) return null

                const progressPercent = progress?.overallProgress ?? 0
                const isCompleted = progressPercent >= 1 || enrollment.status === 'COMPLETED'

                return (
                  <div key={enrollment.id} className="relative">
                    <CourseCard
                      course={course}
                      onClick={() => router.push(`/courses/${course.id}/learn`)}
                    />
                    {/* Progress Overlay */}
                    {progressPercent > 0 && (
                      <div className="absolute bottom-0 left-0 right-0 bg-white border-t border-gray-200 p-3 rounded-b-lg">
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs font-medium text-gray-700">Progress</span>
                          <span className="text-xs font-semibold text-primary-600">
                            {Math.round(progressPercent * 100)}%
                          </span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-1.5">
                          <div
                            className={`h-1.5 rounded-full transition-all ${
                              isCompleted ? 'bg-green-500' : 'bg-primary-600'
                            }`}
                            style={{ width: `${progressPercent * 100}%` }}
                          />
                        </div>
                        {isCompleted && (
                          <div className="mt-2 flex items-center text-xs text-green-600">
                            <svg className="w-4 h-4 mr-1" fill="currentColor" viewBox="0 0 20 20">
                              <path
                                fillRule="evenodd"
                                d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                                clipRule="evenodd"
                              />
                            </svg>
                            Course Completed
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </main>
      </StudentLayout>
    </ProtectedRoute>
  )
}
