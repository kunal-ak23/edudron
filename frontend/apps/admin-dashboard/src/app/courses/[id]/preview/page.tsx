'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Image from 'next/image'
import { Button } from '@/components/ui/button'
import { coursesApi, lecturesApi } from '@/lib/api'
import type { Course } from '@kunal-ak23/edudron-shared-utils'
import { PreviewVideoModal } from '@/components/PreviewVideoModal'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function CoursePreviewPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [showPreviewVideoModal, setShowPreviewVideoModal] = useState(false)
  const [openModuleId, setOpenModuleId] = useState<string | null>(null)

  const loadCourse = useCallback(async () => {
    try {
      const [courseData, sectionsData] = await Promise.all([
        coursesApi.getCourse(courseId),
        coursesApi.getChapters(courseId).catch(() => [])
      ])
      setCourse(courseData)
      
      // Load sub-lectures for each section
      if (sectionsData && sectionsData.length > 0) {
        const sectionsWithLectures = await Promise.all(
          sectionsData.map(async (section: any) => {
            try {
              const lectures = await lecturesApi.getSubLecturesByLecture(courseId, section.id)
              return { ...section, lectures }
            } catch (error) {
              console.warn(`Failed to load sub-lectures for section ${section.id}:`, error)
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
    } catch (error) {
      console.error('Failed to load course:', error)
    } finally {
      setLoading(false)
    }
  }, [courseId])

  useEffect(() => {
    loadCourse()
  }, [loadCourse])

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

  const totalLectures = getTotalLectures()

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
          <div className="animate-pulse space-y-6">
            <div className="h-8 bg-gray-200 rounded w-3/4"></div>
            <div className="h-64 bg-gray-200 rounded"></div>
          </div>
        </div>
      </div>
    )
  }

  if (!course) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center text-gray-500">Course not found</div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Preview Mode Banner */}
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
              onClick={() => router.push(`/courses/${courseId}`)}
              className="text-amber-800 border-amber-300 hover:bg-amber-100"
            >
              Exit Preview
            </Button>
          </div>
        </div>
      </div>

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
            
            {/* Preview Note */}
            <div className="mb-3">
              <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
                <p className="text-blue-800 text-sm font-medium mb-1">
                  Preview Mode - No Enrollment Required
                </p>
                <p className="text-blue-700 text-xs">
                  In preview mode, you can view all course content without enrolling.
                </p>
              </div>
            </div>
          </div>

          {/* Right: Preview Video/Image Only */}
          {(course.previewVideoUrl || course.thumbnailUrl) && (
            <aside>
              <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden sticky top-24">
                <div className="relative w-full bg-gray-100">
                  {course.previewVideoUrl ? (
                    <div className="relative w-full" style={{ paddingBottom: '56.25%' }}>
                      <div className="absolute inset-0 bg-gray-900">
                        <Image
                          src={course.thumbnailUrl || ''}
                          alt={course.title}
                          fill
                          className="object-cover"
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
                    <div className="relative w-full" style={{ aspectRatio: '16/9' }}>
                      <Image
                        src={course.thumbnailUrl || ''}
                        alt={course.title}
                        fill
                        className="object-cover"
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
                                router.push(`/courses/${courseId}/preview/learn`)
                              }}
                              className="px-4 py-2 font-bold rounded-xl border-gray-300 flex-shrink-0"
                            >
                              Preview
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
  )
}
