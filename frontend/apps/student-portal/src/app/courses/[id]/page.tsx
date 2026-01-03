'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, Button, Card } from '@edudron/ui-components'
import { coursesApi, enrollmentsApi } from '@/lib/api'
import type { Course, Section } from '@edudron/shared-utils'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function CourseDetailPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [enrolled, setEnrolled] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadCourse()
  }, [courseId])

  const loadCourse = async () => {
    try {
      const [courseData, sectionsData, enrollmentStatus] = await Promise.all([
        coursesApi.getCourse(courseId),
        coursesApi.getChapters(courseId).catch(() => []),
        enrollmentsApi.checkEnrollment(courseId).catch(() => false)
      ])
      setCourse(courseData)
      setSections(sectionsData as any)
      setEnrolled(enrollmentStatus)
    } catch (error) {
      console.error('Failed to load course:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleEnroll = async () => {
    try {
      await enrollmentsApi.enrollInCourse(courseId)
      setEnrolled(true)
      router.push(`/courses/${courseId}/learn`)
    } catch (error: any) {
      alert(error.message || 'Failed to enroll')
    }
  }

  const formatPrice = () => {
    if (course?.isFree) return 'Free'
    if (course?.pricePaise) {
      return `₹${(course.pricePaise / 100).toLocaleString('en-IN')}`
    }
    return 'Free'
  }

  const formatDuration = () => {
    if (!course?.totalDurationSeconds) return null
    const hours = Math.floor(course.totalDurationSeconds / 3600)
    const minutes = Math.floor((course.totalDurationSeconds % 3600) / 60)
    if (hours > 0) {
      return `${hours} hour${hours > 1 ? 's' : ''} ${minutes} minute${minutes !== 1 ? 's' : ''}`
    }
    return `${minutes} minute${minutes !== 1 ? 's' : ''}`
  }

  if (loading) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-gray-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
            <div className="animate-pulse space-y-6">
              <div className="h-8 bg-gray-200 rounded w-3/4"></div>
              <div className="h-64 bg-gray-200 rounded"></div>
            </div>
          </div>
        </div>
      </ProtectedRoute>
    )
  }

  if (!course) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
          <Card>
            <p className="text-center text-gray-500">Course not found</p>
          </Card>
        </div>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="bg-white shadow-sm">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <button
                onClick={() => router.push('/courses')}
                className="text-gray-600 hover:text-gray-900 flex items-center"
              >
                <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
                Back to Courses
              </button>
            </div>
          </div>
        </header>

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Main Content */}
            <div className="lg:col-span-2 space-y-6">
              {/* Course Header */}
              <div>
                <div className="mb-2">
                  {course.difficultyLevel && (
                    <span className="inline-block px-3 py-1 bg-blue-100 text-blue-700 text-sm font-medium rounded-full">
                      {course.difficultyLevel}
                    </span>
                  )}
                </div>
                <h1 className="text-4xl font-bold text-gray-900 mb-4">{course.title}</h1>
                <p className="text-lg text-gray-600 mb-6">{course.description}</p>

                {/* Course Stats */}
                <div className="flex flex-wrap gap-6 text-sm text-gray-600 mb-6">
                  {course.instructors && course.instructors.length > 0 && (
                    <div>
                      <span className="font-medium">Instructor{course.instructors.length > 1 ? 's' : ''}: </span>
                      {course.instructors.map((i, idx) => (
                        <span key={i.id}>
                          {i.name}
                          {idx < course.instructors!.length - 1 && ', '}
                        </span>
                      ))}
                    </div>
                  )}
                  {formatDuration() && (
                    <div>
                      <span className="font-medium">Duration: </span>
                      {formatDuration()}
                    </div>
                  )}
                  {course.totalLecturesCount && (
                    <div>
                      <span className="font-medium">Lectures: </span>
                      {course.totalLecturesCount}
                    </div>
                  )}
                  {course.totalStudentsCount && (
                    <div>
                      <span className="font-medium">Students: </span>
                      {course.totalStudentsCount.toLocaleString()}
                    </div>
                  )}
                </div>

                {/* Tags */}
                {course.tags && course.tags.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-6">
                    {course.tags.map((tag, idx) => (
                      <span
                        key={idx}
                        className="px-3 py-1 bg-gray-100 text-gray-700 text-sm rounded-full"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              {/* Learning Objectives */}
              {course.learningObjectives && course.learningObjectives.length > 0 && (
                <Card title="What you'll learn">
                  <ul className="space-y-3">
                    {course.learningObjectives.map((objective) => (
                      <li key={objective.id} className="flex items-start">
                        <svg
                          className="w-5 h-5 text-green-500 mr-3 mt-0.5 flex-shrink-0"
                          fill="currentColor"
                          viewBox="0 0 20 20"
                        >
                          <path
                            fillRule="evenodd"
                            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                            clipRule="evenodd"
                          />
                        </svg>
                        <span className="text-gray-700">{objective.objective}</span>
                      </li>
                    ))}
                  </ul>
                </Card>
              )}

              {/* Course Content */}
              {sections && sections.length > 0 && (
                <Card title="Course Content">
                  <div className="space-y-4">
                    {sections.map((section, sectionIdx) => (
                      <div key={section.id} className="border-b border-gray-200 pb-4 last:border-0">
                        <div className="flex items-center justify-between mb-2">
                          <h3 className="font-semibold text-gray-900">
                            Week {sectionIdx + 1}: {section.title}
                          </h3>
                          <span className="text-sm text-gray-500">
                            {section.lectures?.length || 0} lectures
                          </span>
                        </div>
                        {section.description && (
                          <p className="text-sm text-gray-600 mb-2">{section.description}</p>
                        )}
                        {section.lectures && section.lectures.length > 0 && (
                          <ul className="ml-4 space-y-1">
                            {section.lectures.map((lecture) => (
                              <li key={lecture.id} className="text-sm text-gray-600 flex items-center">
                                <svg
                                  className="w-4 h-4 mr-2 text-gray-400"
                                  fill="currentColor"
                                  viewBox="0 0 20 20"
                                >
                                  <path d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" />
                                </svg>
                                {lecture.title}
                                {lecture.duration && (
                                  <span className="ml-2 text-gray-400">
                                    {Math.floor(lecture.duration / 60)}m
                                  </span>
                                )}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    ))}
                  </div>
                </Card>
              )}

              {/* Instructors */}
              {course.instructors && course.instructors.length > 0 && (
                <Card title="Instructor{course.instructors.length > 1 ? 's' : ''}">
                  <div className="space-y-4">
                    {course.instructors.map((instructor) => (
                      <div key={instructor.id} className="flex items-start">
                        {instructor.imageUrl ? (
                          <img
                            src={instructor.imageUrl}
                            alt={instructor.name}
                            className="w-16 h-16 rounded-full mr-4"
                          />
                        ) : (
                          <div className="w-16 h-16 rounded-full bg-blue-500 flex items-center justify-center text-white font-bold text-xl mr-4">
                            {instructor.name.charAt(0)}
                          </div>
                        )}
                        <div>
                          <h4 className="font-semibold text-gray-900">{instructor.name}</h4>
                          {instructor.title && (
                            <p className="text-sm text-gray-600 mb-2">{instructor.title}</p>
                          )}
                          {instructor.bio && (
                            <p className="text-sm text-gray-600">{instructor.bio}</p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </Card>
              )}
            </div>

            {/* Sidebar - Enrollment Card */}
            <div className="lg:col-span-1">
              <div className="sticky top-4">
                <Card className="border-2 border-gray-200">
                  {/* Course Preview Video/Image */}
                  {course.thumbnailUrl && (
                    <div className="mb-4 -mx-6 -mt-6">
                      <div className="relative w-full h-48 bg-gray-200">
                        <img
                          src={course.thumbnailUrl}
                          alt={course.title}
                          className="w-full h-full object-cover"
                        />
                        {course.previewVideoUrl && (
                          <div className="absolute inset-0 flex items-center justify-center bg-black bg-opacity-30">
                            <button className="w-16 h-16 bg-white rounded-full flex items-center justify-center hover:scale-110 transition-transform">
                              <svg
                                className="w-8 h-8 text-blue-600 ml-1"
                                fill="currentColor"
                                viewBox="0 0 20 20"
                              >
                                <path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
                              </svg>
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  <div className="space-y-4">
                    <div>
                      <div className="text-3xl font-bold text-gray-900 mb-2">
                        {formatPrice()}
                      </div>
                      {!course.isFree && course.pricePaise && (
                        <div className="text-sm text-gray-500 line-through">
                          ₹{((course.pricePaise * 1.2) / 100).toLocaleString('en-IN')}
                        </div>
                      )}
                    </div>

                    {enrolled ? (
                      <Button
                        onClick={() => router.push(`/courses/${courseId}/learn`)}
                        className="w-full"
                        size="lg"
                      >
                        Continue Learning
                      </Button>
                    ) : (
                      <Button
                        onClick={handleEnroll}
                        className="w-full"
                        size="lg"
                      >
                        {course.isFree ? 'Enroll for Free' : 'Enroll Now'}
                      </Button>
                    )}

                    <div className="text-sm text-gray-600 space-y-2 pt-4 border-t border-gray-200">
                      <div className="flex items-center">
                        <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                        </svg>
                        Full lifetime access
                      </div>
                      {course.certificateEligible && (
                        <div className="flex items-center">
                          <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                          </svg>
                          Certificate of completion
                        </div>
                      )}
                      <div className="flex items-center">
                        <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                        </svg>
                        Learn at your own pace
                      </div>
                    </div>
                  </div>
                </Card>
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  )
}
