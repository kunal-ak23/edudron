'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, Button, Card } from '@edudron/ui-components'
import { coursesApi, enrollmentsApi } from '@/lib/api'
import type { Course, Section, Lecture, Progress } from '@edudron/shared-utils'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function LearnPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [progress, setProgress] = useState<Progress | null>(null)
  const [selectedLecture, setSelectedLecture] = useState<Lecture | null>(null)
  const [completedLectures, setCompletedLectures] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadCourseData()
  }, [courseId])

  const loadCourseData = async () => {
    try {
      const [courseData, sectionsData, progressData] = await Promise.all([
        coursesApi.getCourse(courseId),
        coursesApi.getChapters(courseId).catch(() => []),
        enrollmentsApi.getProgress(courseId).catch(() => null)
      ])
      setCourse(courseData)
      setSections(sectionsData as any)
      setProgress(progressData)

      // Set first lecture as selected
      if (sectionsData && sectionsData.length > 0) {
        const firstSection = sectionsData[0] as any
        if (firstSection.lectures && firstSection.lectures.length > 0) {
          setSelectedLecture(firstSection.lectures[0])
        }
      }
    } catch (error) {
      console.error('Failed to load course data:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleLectureSelect = (lecture: Lecture) => {
    setSelectedLecture(lecture)
  }

  const handleMarkComplete = async (lectureId: string) => {
    try {
      const newCompleted = new Set(completedLectures)
      newCompleted.add(lectureId)
      setCompletedLectures(newCompleted)

      // Update progress
      if (progress) {
        const totalLectures = sections.reduce(
          (acc, section) => acc + (section.lectures?.length || 0),
          0
        )
        const completedCount = newCompleted.size
        const newProgress = completedCount / totalLectures

        await enrollmentsApi.updateProgress(courseId, {
          courseId,
          studentId: '',
          overallProgress: newProgress,
          chaptersCompleted: completedCount,
          totalChapters: totalLectures
        })

        // Reload progress
        const updatedProgress = await enrollmentsApi.getProgress(courseId)
        setProgress(updatedProgress)
      }
    } catch (error) {
      console.error('Failed to mark lecture as complete:', error)
      // Revert on error
      const reverted = new Set(completedLectures)
      reverted.delete(lectureId)
      setCompletedLectures(reverted)
    }
  }

  const getNextLecture = () => {
    if (!selectedLecture || !sections) return null

    for (let i = 0; i < sections.length; i++) {
      const section = sections[i]
      const lectures = section.lectures || []
      const currentIndex = lectures.findIndex((l) => l.id === selectedLecture.id)

      if (currentIndex !== -1) {
        // Next lecture in same section
        if (currentIndex < lectures.length - 1) {
          return lectures[currentIndex + 1]
        }
        // First lecture in next section
        if (i < sections.length - 1 && sections[i + 1].lectures && sections[i + 1].lectures!.length > 0) {
          return sections[i + 1].lectures![0]
        }
      }
    }
    return null
  }

  const getPrevLecture = () => {
    if (!selectedLecture || !sections) return null

    for (let i = 0; i < sections.length; i++) {
      const section = sections[i]
      const lectures = section.lectures || []
      const currentIndex = lectures.findIndex((l) => l.id === selectedLecture.id)

      if (currentIndex !== -1) {
        // Previous lecture in same section
        if (currentIndex > 0) {
          return lectures[currentIndex - 1]
        }
        // Last lecture in previous section
        if (i > 0) {
          const prevSection = sections[i - 1]
          if (prevSection.lectures && prevSection.lectures.length > 0) {
            return prevSection.lectures[prevSection.lectures.length - 1]
          }
        }
      }
    }
    return null
  }

  const nextLecture = getNextLecture()
  const prevLecture = getPrevLecture()

  if (loading) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-gray-50">
          <div className="animate-pulse">Loading...</div>
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
      <div className="min-h-screen bg-gray-900">
        {/* Top Bar */}
        <header className="bg-gray-800 border-b border-gray-700">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-14">
              <div className="flex items-center space-x-4">
                <button
                  onClick={() => router.push(`/courses/${courseId}`)}
                  className="text-gray-300 hover:text-white"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <h1 className="text-white font-semibold text-lg truncate max-w-md">
                  {course.title}
                </h1>
              </div>
              {progress && (
                <div className="text-sm text-gray-300">
                  {Math.round(progress.overallProgress * 100)}% Complete
                </div>
              )}
            </div>
          </div>
        </header>

        <div className="flex h-[calc(100vh-3.5rem)]">
          {/* Main Content - Video Player */}
          <div className="flex-1 flex flex-col bg-black">
            {selectedLecture ? (
              <>
                {/* Video Player Area */}
                <div className="flex-1 flex items-center justify-center bg-black">
                  {selectedLecture.contentType === 'VIDEO' && selectedLecture.contentUrl ? (
                    <div className="w-full h-full max-w-7xl mx-auto">
                      <video
                        controls
                        className="w-full h-full"
                        src={selectedLecture.contentUrl}
                      >
                        Your browser does not support the video tag.
                      </video>
                    </div>
                  ) : selectedLecture.contentType === 'TEXT' ? (
                    <div className="w-full h-full overflow-y-auto bg-gray-900 p-8">
                      <div className="max-w-4xl mx-auto">
                        <h2 className="text-3xl font-bold text-white mb-4">{selectedLecture.title}</h2>
                        {selectedLecture.description && (
                          <div className="text-gray-400 mb-6">
                            <MarkdownRenderer content={selectedLecture.description} />
                          </div>
                        )}
                        {/* Text content will be rendered here when available */}
                        {selectedLecture.contents && selectedLecture.contents.length > 0 ? (
                          <div className="mt-6 space-y-6">
                            {selectedLecture.contents
                              .filter((content: any) => content.contentType === 'TEXT' && content.textContent)
                              .map((content: any) => (
                                <div key={content.id}>
                                  {content.title && (
                                    <h3 className="text-2xl font-semibold text-white mb-3">{content.title}</h3>
                                  )}
                                  <MarkdownRenderer
                                    content={content.textContent}
                                    className="text-gray-300"
                                  />
                                </div>
                              ))}
                          </div>
                        ) : (
                          <div className="text-center text-gray-400 mt-8">
                            <p>Content is being generated. Please check back soon.</p>
                          </div>
                        )}
                      </div>
                    </div>
                  ) : (
                    <div className="text-center text-white p-8">
                      <div className="mb-4">
                        <svg
                          className="w-16 h-16 mx-auto text-gray-400"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
                          />
                        </svg>
                      </div>
                      <h3 className="text-xl font-semibold mb-2">{selectedLecture.title}</h3>
                      {selectedLecture.description && (
                        <div className="text-gray-400">
                          <MarkdownRenderer content={selectedLecture.description} />
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Lecture Info and Navigation */}
                <div className="bg-gray-800 border-t border-gray-700 p-4">
                  <div className="max-w-7xl mx-auto">
                    <div className="flex items-center justify-between mb-4">
                      <div>
                        <h2 className="text-white text-xl font-semibold mb-1">
                          {selectedLecture.title}
                        </h2>
                        {selectedLecture.description && (
                          <div className="text-gray-400 text-sm">
                            <MarkdownRenderer content={selectedLecture.description} />
                          </div>
                        )}
                      </div>
                      <Button
                        variant={completedLectures.has(selectedLecture.id) ? 'secondary' : 'primary'}
                        onClick={() => handleMarkComplete(selectedLecture.id)}
                        disabled={completedLectures.has(selectedLecture.id)}
                      >
                        {completedLectures.has(selectedLecture.id) ? (
                          <>
                            <svg className="w-4 h-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                            </svg>
                            Completed
                          </>
                        ) : (
                          'Mark as Complete'
                        )}
                      </Button>
                    </div>

                    {/* Navigation */}
                    <div className="flex justify-between">
                      <Button
                        variant="outline"
                        onClick={() => prevLecture && handleLectureSelect(prevLecture)}
                        disabled={!prevLecture}
                      >
                        <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                        </svg>
                        Previous
                      </Button>
                      <Button
                        onClick={() => nextLecture && handleLectureSelect(nextLecture)}
                        disabled={!nextLecture}
                      >
                        Next
                        <svg className="w-4 h-4 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                      </Button>
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-white">
                <p>Select a lecture to begin</p>
              </div>
            )}
          </div>

          {/* Sidebar - Course Content */}
          <div className="w-80 bg-gray-800 border-l border-gray-700 overflow-y-auto">
            <div className="p-4">
              <h3 className="text-white font-semibold mb-4">Course Content</h3>
              {progress && (
                <div className="mb-4 p-3 bg-gray-700 rounded-lg">
                  <div className="flex justify-between text-sm text-gray-300 mb-2">
                    <span>Your Progress</span>
                    <span>{Math.round(progress.overallProgress * 100)}%</span>
                  </div>
                  <div className="w-full bg-gray-600 rounded-full h-2">
                    <div
                      className="bg-blue-600 h-2 rounded-full transition-all"
                      style={{ width: `${progress.overallProgress * 100}%` }}
                    />
                  </div>
                </div>
              )}

              <div className="space-y-2">
                {sections.map((section, sectionIdx) => (
                  <div key={section.id} className="mb-4">
                    <div className="text-gray-300 font-medium text-sm mb-2 px-2">
                      Week {sectionIdx + 1}: {section.title}
                    </div>
                    {section.lectures && section.lectures.length > 0 && (
                      <div className="space-y-1">
                        {section.lectures.map((lecture) => {
                          const isSelected = selectedLecture?.id === lecture.id
                          const isCompleted = completedLectures.has(lecture.id)

                          return (
                            <button
                              key={lecture.id}
                              onClick={() => handleLectureSelect(lecture)}
                              className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                                isSelected
                                  ? 'bg-blue-600 text-white'
                                  : 'text-gray-300 hover:bg-gray-700'
                              }`}
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex items-center flex-1 min-w-0">
                                  {isCompleted ? (
                                    <svg
                                      className="w-4 h-4 mr-2 text-green-400 flex-shrink-0"
                                      fill="currentColor"
                                      viewBox="0 0 20 20"
                                    >
                                      <path
                                        fillRule="evenodd"
                                        d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                                        clipRule="evenodd"
                                      />
                                    </svg>
                                  ) : (
                                    <svg
                                      className="w-4 h-4 mr-2 text-gray-500 flex-shrink-0"
                                      fill="currentColor"
                                      viewBox="0 0 20 20"
                                    >
                                      <path d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" />
                                    </svg>
                                  )}
                                  <span className="truncate">{lecture.title}</span>
                                </div>
                                {lecture.duration && (
                                  <span className="text-xs text-gray-400 ml-2 flex-shrink-0">
                                    {Math.floor(lecture.duration / 60)}m
                                  </span>
                                )}
                              </div>
                            </button>
                          )
                        })}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  )
}
