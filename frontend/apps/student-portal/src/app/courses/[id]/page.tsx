'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, Button, Card } from '@kunal-ak23/edudron-ui-components'
import { coursesApi, enrollmentsApi, lecturesApi } from '@/lib/api'
import type { Course, Section } from '@kunal-ak23/edudron-shared-utils'
import { TenantFeaturesApi, TenantFeatureType } from '@kunal-ak23/edudron-shared-utils'
import { CommitmentModal } from '@/components/CommitmentModal'
import { EnrollmentSuccessModal } from '@/components/EnrollmentSuccessModal'
import { PreviewVideoModal } from '@/components/PreviewVideoModal'
import { StudentLayout } from '@/components/StudentLayout'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { getApiClient } from '@/lib/api'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function CourseDetailPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const { user } = useAuth()
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<any[]>([])
  const [enrolled, setEnrolled] = useState(false)
  const [loading, setLoading] = useState(true)
  const [progress, setProgress] = useState<any>(null)
  const [completedLectures, setCompletedLectures] = useState<Set<string>>(new Set())
  const [showCommitmentModal, setShowCommitmentModal] = useState(false)
  const [showSuccessModal, setShowSuccessModal] = useState(false)
  const [showPreviewVideoModal, setShowPreviewVideoModal] = useState(false)
  const [openModuleId, setOpenModuleId] = useState<string | null>(null)
  
  // Check for preview mode
  const [isPreviewMode, setIsPreviewMode] = useState(false)
  const isAdminUser = user && user.role !== 'STUDENT'
  const [selfEnrollmentEnabled, setSelfEnrollmentEnabled] = useState<boolean | null>(null)
  
  // Initialize tenant features API
  const tenantFeaturesApi = new TenantFeaturesApi(getApiClient())
  
  useEffect(() => {
    if (typeof window !== 'undefined') {
      const urlParams = new URLSearchParams(window.location.search)
      const preview = urlParams.get('preview') === 'true'
      setIsPreviewMode(preview && !!isAdminUser)
    }
  }, [isAdminUser])

  // Check if student self-enrollment is enabled
  useEffect(() => {
    const checkSelfEnrollment = async () => {
      // Only check for students
      if (user?.role === 'STUDENT') {
        try {
          const enabled = await tenantFeaturesApi.isStudentSelfEnrollmentEnabled()
          setSelfEnrollmentEnabled(enabled)
        } catch (error) {
          // Default to false if check fails
          setSelfEnrollmentEnabled(false)
        }
      } else {
        // Admins/instructors can always enroll
        setSelfEnrollmentEnabled(true)
      }
    }
    checkSelfEnrollment()
  }, [user])

  const loadCourse = useCallback(async () => {
    try {
      const [courseData, sectionsData] = await Promise.all([
        coursesApi.getCourse(courseId),
        coursesApi.getChapters(courseId).catch(() => [])
      ])
      
      // Backend will return error for unpublished courses for students
      // If we get here, the course is either published or user is admin/instructor
      setCourse(courseData)
      
      // Load sub-lectures for each section
      if (sectionsData && sectionsData.length > 0) {
        const sectionsWithLectures = await Promise.all(
          sectionsData.map(async (section: any) => {
            try {
              const lectures = await lecturesApi.getSubLecturesByLecture(courseId, section.id)
              return { ...section, lectures }
            } catch (error) {
              return { ...section, lectures: [] }
            }
          })
        )
        setSections(sectionsWithLectures)
        // Open the first module by default
        if (sectionsWithLectures.length > 0 && sectionsWithLectures[0].lectures && sectionsWithLectures[0].lectures.length > 0) {
          setOpenModuleId(sectionsWithLectures[0].id)
        }
      } else {
        setSections(sectionsData as any)
      }
      
      // Check enrollment status - check first, don't try to enroll
      // In preview mode, allow viewing without enrollment
      let isEnrolled = isPreviewMode
      if (!isPreviewMode) {
        try {
          // Check enrollment status first
          isEnrolled = await enrollmentsApi.checkEnrollment(courseId)
        } catch (error: any) {
          // If check fails, assume not enrolled
          isEnrolled = false
        }
      }
      
      setEnrolled(isEnrolled)
      
      // Load progress if enrolled
      if (isEnrolled) {
        const progressData = await enrollmentsApi.getProgress(courseId).catch(() => null)
        setProgress(progressData)
        
        // Load lecture progress to determine completed lectures
        try {
          if (enrollmentsApi && typeof enrollmentsApi.getLectureProgress === 'function') {
            const lectureProgressData = await enrollmentsApi.getLectureProgress(courseId).catch(() => [])
            const completed = new Set<string>()
            lectureProgressData.forEach((lp: any) => {
              if (lp.isCompleted && lp.lectureId) {
                completed.add(lp.lectureId)
              }
            })
            setCompletedLectures(completed)
          }
        } catch (error) {
          setCompletedLectures(new Set())
        }
      } else {
        setProgress(null)
        setCompletedLectures(new Set())
      }
    } catch (error) {
    } finally {
      setLoading(false)
    }
  }, [courseId])

  useEffect(() => {
    loadCourse()
  }, [loadCourse])

  const handleEnrollClick = () => {
    // Show commitment modal first
    setShowCommitmentModal(true)
  }

  const handleCommit = async () => {
    setShowCommitmentModal(false)
    
    try {
      const alreadyEnrolled = await enrollmentsApi.checkEnrollment(courseId).catch(() => false)
      if (alreadyEnrolled) {
        setEnrolled(true)
        router.push(`/courses/${courseId}/learn`)
        return
      }

      await enrollmentsApi.enrollInCourse(courseId)
      setEnrolled(true)
      
      // Show success modal
      setShowSuccessModal(true)
    } catch (error: any) {
      const statusCode = error.response?.status
      const errorMessage = error.response?.data?.error || error.message || error.response?.data?.message || ''
      const lowerMessage = errorMessage.toLowerCase()
      
      if (statusCode === 409 || statusCode === 403 || lowerMessage.includes('already enrolled')) {
        setEnrolled(true)
        router.push(`/courses/${courseId}/learn`)
        return
      }
      
      // Show error - you may want to add toast here if available
    }
  }

  const handleGetStarted = () => {
    setShowSuccessModal(false)
    router.push(`/courses/${courseId}/learn`)
  }

  const formatDuration = () => {
    if (!course?.totalDurationSeconds) return null
    const hours = Math.floor(course.totalDurationSeconds / 3600)
    if (hours > 0) {
      return `${hours} hour${hours > 1 ? 's' : ''}`
    }
    return null
  }

  const getTotalLectures = () => {
    return sections.reduce((total, section) => total + (section.lectures?.length || 0), 0)
  }

  const getProgressPercentage = () => {
    const total = getTotalLectures()
    if (!progress || !progress.completedLectures || total === 0) return 0
    return Math.round((progress.completedLectures / total) * 100)
  }

  const getNextLecture = () => {
    if (!sections || sections.length === 0) return null
    if (completedLectures.size === 0) {
      // If no lectures are completed, return the first lecture
      for (const section of sections) {
        if (section.lectures && section.lectures.length > 0) {
          return section.lectures[0]
        }
      }
      return null
    }
    
    // Find the first uncompleted lecture
    for (const section of sections) {
      if (section.lectures && section.lectures.length > 0) {
        for (const lecture of section.lectures) {
          if (!completedLectures.has(lecture.id)) {
            return lecture
          }
        }
      }
    }
    
    // If all lectures are completed, return null or the last lecture
    return null
  }

  if (loading) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-gray-50">
          <div className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
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

  const totalLectures = getTotalLectures()
  const progressPercentage = getProgressPercentage()
  const nextLecture = getNextLecture()

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="min-h-screen bg-gray-50">
          {/* Preview Mode Banner */}
          {isPreviewMode && (
            <div className="bg-amber-50 border-b border-amber-200 px-6 sm:px-8 lg:px-12 py-3">
              <div className="max-w-[1600px] mx-auto">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-amber-800 font-medium">Preview Mode</span>
                    <span className="text-amber-700 text-sm">You are viewing this course as a student would see it</span>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      const adminPortalUrl = typeof window !== 'undefined' 
                        ? (window.location.origin.includes('localhost') 
                            ? 'http://localhost:3000' 
                            : window.location.origin.replace('student', 'admin').replace('portal', 'dashboard'))
                        : 'http://localhost:3000'
                      window.location.href = `${adminPortalUrl}/courses/${courseId}`
                    }}
                    className="text-amber-800 border-amber-300 hover:bg-amber-100"
                  >
                    Exit Preview
                  </Button>
                </div>
              </div>
            </div>
          )}
          {/* Hero Section */}
          <div className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
          <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_0.9fr] gap-3 items-start">
            {/* Left: Course Info */}
            <div>
              <p className="text-sm text-gray-500 mb-1.5">
                Home / Courses / {course.categoryId || 'Programming'}
              </p>
              <h1 className="text-4xl font-bold text-gray-900 mb-2 leading-tight">
                {course.title}
              </h1>
              {course.description && (
                <div className="text-gray-600 mb-3">
                  <MarkdownRenderer content={course.description} className="prose-sm" />
                </div>
              )}
              {formatDuration() && (
                <div className="mb-3">
                  <span className="text-gray-500 text-sm">{formatDuration()}</span>
                </div>
              )}
              
              {/* Progress Card - Moved to left side (if enrolled) */}
              {enrolled && progress ? (
                <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-3 mb-3">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-semibold text-gray-900 text-sm">Your progress</h3>
                    <span className="text-xs font-semibold text-primary-600">{progressPercentage}%</span>
                  </div>
                  <div className="h-2 bg-primary-50 rounded-full overflow-hidden mb-2">
                    <div
                      className="h-full bg-primary-600 rounded-full transition-all"
                      style={{ width: `${progressPercentage}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-xs text-gray-500 mb-2">
                    <span>{progress.completedLectures || 0} of {totalLectures} lessons</span>
                  </div>
                  {nextLecture && (
                    <div className="pt-2 border-t border-gray-100 mb-2">
                      <p className="text-xs text-gray-600">
                        <span className="font-medium">Next:</span> {nextLecture.title}
                      </p>
                    </div>
                  )}
                  <Button
                    onClick={() => router.push(`/courses/${courseId}/learn`)}
                    className="w-full py-2 text-sm font-semibold rounded-lg"
                  >
                    Continue Learning
                  </Button>
                </div>
              ) : (
                <div className="mb-3">
                  {selfEnrollmentEnabled === false && user?.role === 'STUDENT' ? (
                    <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">
                      <p className="text-amber-800 text-sm font-medium mb-1">
                        Enrollment is managed by your instructor
                      </p>
                      <p className="text-amber-700 text-xs">
                        Please contact your instructor to enroll you in this course.
                      </p>
                    </div>
                  ) : (
                    <div className="flex gap-3">
                      <Button
                        onClick={handleEnrollClick}
                        variant="primary"
                        size="lg"
                        className="font-bold !text-white"
                      >
                        {course.isFree ? 'Start learning' : 'Enroll for Free'}
                      </Button>
                      {course.previewVideoUrl ? (
                        <Button
                          variant="outline"
                          onClick={() => setShowPreviewVideoModal(true)}
                          size="lg"
                          className="font-bold border-gray-300"
                        >
                          Preview
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          onClick={() => router.push(`/courses/${courseId}/learn`)}
                          size="lg"
                          className="font-bold border-gray-300"
                        >
                          Preview
                        </Button>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Right: Preview Video/Image Only */}
            {(course.previewVideoUrl || course.thumbnailUrl) && (
              <aside>
                <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden sticky top-24">
                  <div className="relative w-full bg-gray-100">
                    {course.previewVideoUrl ? (
                      <div className="relative w-full" style={{ paddingBottom: '56.25%' }}>
                        <div className="absolute inset-0 bg-gray-900">
                          <img
                            src={course.thumbnailUrl || ''}
                            alt={course.title}
                            className="w-full h-full object-cover"
                          />
                          <button
                            onClick={() => setShowPreviewVideoModal(true)}
                            className="absolute inset-0 flex items-center justify-center bg-black bg-opacity-30 hover:bg-opacity-40 transition-all group"
                          >
                            <div className="w-12 h-12 bg-white bg-opacity-90 rounded-full flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform">
                              <svg
                                className="w-6 h-6 text-primary-600 ml-0.5"
                                fill="currentColor"
                                viewBox="0 0 24 24"
                              >
                                <path d="M8 5v14l11-7z" />
                              </svg>
                            </div>
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="relative w-full">
                        <img
                          src={course.thumbnailUrl || ''}
                          alt={course.title}
                          className="w-full h-auto object-contain"
                        />
                      </div>
                    )}
                  </div>
                  {/* Compact metadata below image */}
                  <div className="p-2.5">
                    <div className="flex items-center gap-2 flex-wrap text-xs mb-1.5">
                      {course.difficultyLevel && (
                        <span className="px-2 py-0.5 border border-gray-200 bg-white rounded text-xs font-semibold">
                          {course.difficultyLevel}
                        </span>
                      )}
                      <span className="text-gray-500">
                        {course.isFree ? 'Free' : course.pricePaise ? `₹${(course.pricePaise / 100).toLocaleString('en-IN')}` : 'Free'}
                      </span>
                      {totalLectures > 0 && (
                        <span className="text-gray-500">• {totalLectures} lessons</span>
                      )}
                    </div>
                    {course.tags && course.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1">
                        {course.tags.slice(0, 2).map((tag, idx) => (
                          <span
                            key={idx}
                            className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded"
                          >
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </aside>
            )}
          </div>
        </div>

        {/* Lessons Section */}
        <div className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 pb-4">
          <section className="bg-white rounded-2xl border border-gray-200 p-3">
            <h2 className="text-xl font-semibold text-gray-900 mb-3">Lessons</h2>
            <div className="space-y-2">
              {sections.map((section, sectionIdx) => {
                if (!section.lectures || section.lectures.length === 0) return null
                
                const isOpen = openModuleId === section.id
                
                return (
                  <div key={section.id} className="border border-gray-200 rounded-xl overflow-hidden bg-white">
                    {/* Section Header - Clickable */}
                    <button
                      onClick={() => setOpenModuleId(isOpen ? null : section.id)}
                      className="w-full flex items-center gap-3 p-4 bg-primary-50 hover:bg-primary-100 transition-colors text-left rounded-t-xl"
                    >
                      <div className="w-10 h-10 rounded-lg bg-primary-100 border border-primary-300 flex items-center justify-center flex-shrink-0">
                        <svg className="w-5 h-5 text-primary-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                        </svg>
                      </div>
                      <div className="flex-1">
                        <h3 className="text-lg font-semibold text-primary-900">
                          {sectionIdx + 1}. {section.title}
                        </h3>
                        <p className="text-sm text-primary-700 mt-0.5">
                          {section.lectures.length} {section.lectures.length === 1 ? 'lesson' : 'lessons'}
                        </p>
                      </div>
                      <div className="flex-shrink-0">
                        <svg
                          className={`w-5 h-5 text-primary-600 transition-transform ${isOpen ? 'rotate-180' : ''}`}
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                        </svg>
                      </div>
                    </button>
                    
                    {/* Lessons in this section - Collapsible */}
                    {isOpen && (
                      <div className="px-4 pb-4 space-y-3 border-t border-gray-200 pt-3">
                        {section.lectures.map((lecture: any, lectureIdx: number) => {
                          const isVideo = lecture.contentType === 'VIDEO'
                          const isReading = lecture.contentType === 'TEXT' || lecture.contentType === 'READING'
                          
                          return (
                            <div
                              key={lecture.id}
                              className="bg-white border border-gray-200 rounded-xl p-4 flex justify-between items-center shadow-sm hover:border-primary-300 transition-colors"
                            >
                              <div className="flex gap-3 items-center">
                                <div className="w-9 h-9 rounded-lg bg-primary-50 border border-primary-200 flex items-center justify-center font-bold text-gray-900 flex-shrink-0">
                                  {lectureIdx + 1}
                                </div>
                                <div className="flex items-center gap-2 flex-shrink-0">
                                  {isVideo ? (
                                    <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                                    </svg>
                                  ) : isReading ? (
                                    <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                                    </svg>
                                  ) : (
                                    <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                    </svg>
                                  )}
                                </div>
                                <div>
                                  <h3 className="font-semibold text-gray-900 mb-1">
                                    {lecture.title}
                                  </h3>
                                  <p className="text-sm text-gray-500">
                                    {lecture.contentType === 'VIDEO' ? 'Video' : 'Reading'} •{' '}
                                    {lecture.duration ? `${Math.floor(lecture.duration / 60)} min` : 'N/A'}
                                    {lecture.contentType === 'TEXT' && ' • Practice'}
                                  </p>
                                </div>
                              </div>
                              <Button
                                variant="outline"
                                onClick={() => {
                                  if (enrolled) {
                                    router.push(`/courses/${courseId}/learn?lectureId=${lecture.id}`)
                                  } else {
                                    handleEnrollClick()
                                  }
                                }}
                                className="px-4 py-2 font-bold rounded-xl border-gray-300 flex-shrink-0"
                              >
                                {enrolled ? 'Open' : 'Preview'}
                              </Button>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </section>
        </div>

        {/* Commitment Modal */}
        <CommitmentModal
          courseTitle={course.title}
          isOpen={showCommitmentModal}
          onClose={() => setShowCommitmentModal(false)}
          onCommit={handleCommit}
        />

        {/* Success Modal */}
        <EnrollmentSuccessModal
          userName={user?.name || user?.email?.split('@')[0] || 'Learner'}
          isOpen={showSuccessModal}
          onClose={() => {
            setShowSuccessModal(false)
            router.push(`/courses/${courseId}/learn`)
          }}
          onGetStarted={handleGetStarted}
        />

        {/* Preview Video Modal */}
        {course.previewVideoUrl && (
          <PreviewVideoModal
            videoUrl={course.previewVideoUrl}
            courseTitle={course.title}
            isOpen={showPreviewVideoModal}
            onClose={() => setShowPreviewVideoModal(false)}
          />
        )}
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
