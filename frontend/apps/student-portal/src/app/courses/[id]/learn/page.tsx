'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, Button } from '@edudron/ui-components'
import { coursesApi, enrollmentsApi, lecturesApi } from '@/lib/api'
import type { Course, Section, Lecture, Progress } from '@edudron/shared-utils'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'
import { useAuth } from '@edudron/shared-utils'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

type TabType = 'transcript' | 'notes' | 'downloads'

export default function LearnPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const { user } = useAuth()
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [progress, setProgress] = useState<Progress | null>(null)
  const [selectedLecture, setSelectedLecture] = useState<Lecture | null>(null)
  const [completedLectures, setCompletedLectures] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [activeTab, setActiveTab] = useState<TabType>('transcript')
  const [searchQuery, setSearchQuery] = useState('')

  useEffect(() => {
    loadCourseData()
  }, [courseId])

  const loadCourseData = async () => {
    try {
      console.log('[LearnPage] Loading course data for courseId:', courseId)
      setLoading(true)
      
      let courseData: Course | null = null
      try {
        courseData = await coursesApi.getCourse(courseId)
        console.log('[LearnPage] Course loaded successfully:', courseData ? { id: courseData.id, title: courseData.title } : 'null')
      } catch (error: any) {
        console.error('[LearnPage] Failed to load course:', error)
        setCourse(null)
        setLoading(false)
        return
      }
      
      if (!courseData) {
        console.error('[LearnPage] Course data is null after successful API call')
        setCourse(null)
        setLoading(false)
        return
      }
      
      setCourse(courseData)
      
      let sectionsData: any[] = []
      let progressData: any = null
      let lectureProgressData: any[] = []
      
      try {
        const fetchedSections = await coursesApi.getChapters(courseId).catch((error) => {
          console.warn('[LearnPage] Failed to load chapters:', error)
          return []
        })
        sectionsData = await Promise.all(
          fetchedSections.map(async (section: Section) => {
            try {
              const subLectures = await lecturesApi.getSubLecturesByLecture(courseId, section.id)
              return { ...section, lectures: subLectures }
            } catch (error) {
              console.warn(`[LearnPage] Failed to load sub-lectures for section ${section.id}:`, error)
              return { ...section, lectures: [] }
            }
          })
        )
      } catch (error) {
        console.warn('[LearnPage] Error loading chapters or sub-lectures:', error)
      }
      
      try {
        progressData = await enrollmentsApi.getProgress(courseId).catch((error) => {
          console.warn('[LearnPage] Failed to load progress:', error)
          return null
        })
      } catch (error) {
        console.warn('[LearnPage] Error loading progress:', error)
      }
      
      try {
        if (enrollmentsApi && typeof enrollmentsApi.getLectureProgress === 'function') {
          lectureProgressData = await enrollmentsApi.getLectureProgress(courseId).catch((error) => {
            console.warn('[LearnPage] Failed to load lecture progress:', error)
            return []
          })
        } else {
          console.warn('[LearnPage] getLectureProgress method not available on enrollmentsApi')
          lectureProgressData = []
        }
      } catch (error) {
        console.warn('[LearnPage] Error loading lecture progress:', error)
        lectureProgressData = []
      }
      
      console.log('[LearnPage] All data loaded:', {
        course: { id: courseData.id, title: courseData.title },
        sectionsCount: sectionsData?.length || 0,
        hasProgress: !!progressData,
        lectureProgressCount: lectureProgressData?.length || 0
      })
      
      setSections(sectionsData as any)
      setProgress(progressData)

      const completed = new Set<string>()
      lectureProgressData.forEach((lp: any) => {
        if (lp.isCompleted && lp.lectureId) {
          completed.add(lp.lectureId)
        }
      })
      setCompletedLectures(completed)

      if (sectionsData && sectionsData.length > 0) {
        const firstSection = sectionsData[0] as any
        if (firstSection.lectures && firstSection.lectures.length > 0) {
          setSelectedLecture(firstSection.lectures[0])
        }
      }
    } catch (error) {
      console.error('[LearnPage] Unexpected error loading course data:', error)
      if (!course) {
        setCourse(null)
      }
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

      await enrollmentsApi.updateProgress(courseId, {
        lectureId,
        isCompleted: true,
        progressPercentage: 100
      })

      let updatedProgress = null
      let updatedLectureProgress: any[] = []
      
      try {
        updatedProgress = await enrollmentsApi.getProgress(courseId).catch(() => null)
      } catch (error) {
        console.warn('[LearnPage] Error reloading progress:', error)
      }
      
      try {
        if (enrollmentsApi && typeof enrollmentsApi.getLectureProgress === 'function') {
          updatedLectureProgress = await enrollmentsApi.getLectureProgress(courseId).catch(() => [])
        }
      } catch (error) {
        console.warn('[LearnPage] Error reloading lecture progress:', error)
      }
      
      setProgress(updatedProgress)
      
      const completed = new Set<string>()
      updatedLectureProgress.forEach((lp: any) => {
        if (lp.isCompleted && lp.lectureId) {
          completed.add(lp.lectureId)
        }
      })
      setCompletedLectures(completed)
    } catch (error) {
      console.error('Failed to mark lecture as complete:', error)
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
        if (currentIndex < lectures.length - 1) {
          return lectures[currentIndex + 1]
        }
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
        if (currentIndex > 0) {
          return lectures[currentIndex - 1]
        }
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

  const getContentTypeIcon = (contentType: string) => {
    switch (contentType) {
      case 'VIDEO':
        return (
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" />
          </svg>
        )
      case 'TEXT':
      case 'READING':
        return (
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path d="M9 4.804A7.968 7.968 0 005.5 4c-1.255 0-2.443.29-3.5.804v10A7.969 7.969 0 015.5 14c1.669 0 3.218.51 4.5 1.385A7.962 7.962 0 0114.5 14c1.255 0 2.443.29 3.5.804v-10A7.968 7.968 0 0014.5 4c-1.255 0-2.443.29-3.5.804v12.392A7.962 7.962 0 0110.5 14c-1.669 0-3.218.51-4.5 1.385V4.804z" />
          </svg>
        )
      default:
        return (
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clipRule="evenodd" />
          </svg>
        )
    }
  }

  if (loading) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-white">
          <div className="animate-pulse">Loading...</div>
        </div>
      </ProtectedRoute>
    )
  }

  if (!course) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-white flex items-center justify-center">
          <p className="text-gray-500">Course not found</p>
        </div>
      </ProtectedRoute>
    )
  }

  const currentSection = sections.find(s => s.lectures?.some(l => l.id === selectedLecture?.id))
  const currentSectionIndex = sections.findIndex(s => s.id === currentSection?.id)

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-white">
        {/* Header */}
        <header className="bg-white border-b border-gray-200 sticky top-0 z-40">
          <div className="px-6 py-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-4">
                <div className="text-blue-600 font-bold text-xl">coursera</div>
                <div className="text-gray-400">|</div>
                <div className="text-gray-700 font-medium">{course.instructors?.[0]?.name || 'Course'}</div>
              </div>
              <div className="flex-1 max-w-md mx-8">
                <div className="relative">
                  <input
                    type="text"
                    placeholder="Search in course"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full px-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <button className="absolute right-2 top-1/2 transform -translate-y-1/2 px-3 py-1 bg-blue-600 text-white rounded text-sm font-medium">
                    Search
                  </button>
                </div>
              </div>
              <div className="flex items-center space-x-4">
                <div className="flex items-center space-x-1 text-gray-700 cursor-pointer">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5h12M9 3v2m1.048 9.5A18.022 18.022 0 016.412 9m6.088 9h7M11 21l5-10 5 10M12.751 5C11.783 10.77 8.07 15.61 3 18.129" />
                  </svg>
                  <span className="text-sm">English</span>
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </div>
                <button className="text-gray-700 hover:text-gray-900">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                  </svg>
                </button>
                <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center font-semibold cursor-pointer">
                  {user?.name?.charAt(0).toUpperCase() || 'A'}
                </div>
              </div>
            </div>
          </div>
        </header>

        <div className="flex h-[calc(100vh-4rem)]">
          {/* Left Sidebar */}
          {sidebarCollapsed && (
            <button
              onClick={() => setSidebarCollapsed(false)}
              className="fixed left-0 top-1/2 transform -translate-y-1/2 z-30 bg-white border-r border-t border-b border-gray-200 rounded-r-lg px-2 py-4 shadow-md hover:bg-gray-50"
            >
              <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </button>
          )}
          <div className={`${sidebarCollapsed ? 'w-0' : 'w-80'} bg-white border-r border-gray-200 overflow-y-auto transition-all duration-300 flex-shrink-0`}>
            {!sidebarCollapsed && (
              <div className="p-4">
                <button
                  onClick={() => setSidebarCollapsed(true)}
                  className="text-gray-600 hover:text-gray-900 mb-4 text-sm font-medium flex items-center space-x-1"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                  <span>Hide menu</span>
                </button>
                
                <div className="mb-6">
                  <h3 className="text-sm font-semibold text-gray-900 mb-3">Start the program</h3>
                  <div className="space-y-1">
                    {sections.map((section, sectionIdx) => (
                      <div key={section.id}>
                        {section.lectures && section.lectures.map((lecture) => {
                          const isSelected = selectedLecture?.id === lecture.id
                          const isCompleted = completedLectures.has(lecture.id)
                          const contentType = lecture.contentType || 'TEXT'

                          return (
                            <button
                              key={lecture.id}
                              onClick={() => handleLectureSelect(lecture)}
                              className={`w-full text-left px-3 py-2.5 rounded text-sm transition-colors relative ${
                                isSelected
                                  ? 'bg-blue-50 text-blue-700'
                                  : 'text-gray-700 hover:bg-gray-50'
                              }`}
                            >
                              {isSelected && (
                                <div className="absolute left-0 top-0 bottom-0 w-1 bg-blue-600 rounded-r"></div>
                              )}
                              <div className="flex items-center space-x-2">
                                <div className={`flex-shrink-0 ${isSelected ? 'text-blue-600' : 'text-gray-500'}`}>
                                  {getContentTypeIcon(contentType)}
                                </div>
                                <div className="flex-1 min-w-0">
                                  <div className="flex items-center space-x-2">
                                    <span className="font-medium truncate">
                                      {contentType === 'VIDEO' ? 'Video' : contentType === 'TEXT' ? 'Reading' : 'Content'}: {lecture.title}
                                    </span>
                                    {isCompleted && (
                                      <svg className="w-4 h-4 text-green-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                                      </svg>
                                    )}
                                  </div>
                                  {lecture.duration && (
                                    <div className="text-xs text-gray-500 mt-0.5">
                                      {Math.floor(lecture.duration / 60)} min
                                    </div>
                                  )}
                                </div>
                              </div>
                            </button>
                          )
                        })}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Main Content */}
          <div className="flex-1 flex flex-col min-w-0 bg-white overflow-y-auto">
            {selectedLecture ? (
              <>
                {/* Breadcrumbs */}
                <div className="px-6 py-3 border-b border-gray-200 flex items-center justify-between">
                  <div className="flex items-center space-x-2 text-sm text-gray-600">
                    <span>{course.title}</span>
                    <span>></span>
                    {currentSection && (
                      <>
                        <span>Module {currentSectionIndex + 1}</span>
                        <span>></span>
                      </>
                    )}
                    <span className="text-gray-900 font-medium">{selectedLecture.title}</span>
                  </div>
                  {nextLecture && (
                    <button
                      onClick={() => handleLectureSelect(nextLecture)}
                      className="text-blue-600 hover:text-blue-700 font-medium text-sm flex items-center space-x-1"
                    >
                      <span>Next</span>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                      </svg>
                    </button>
                  )}
                </div>

                {/* Video/Content Player */}
                <div className="bg-black flex-1 flex items-center justify-center relative">
                  {selectedLecture.contentType === 'VIDEO' && selectedLecture.contentUrl ? (
                    <div className="w-full h-full">
                      <video
                        controls
                        className="w-full h-full"
                        src={selectedLecture.contentUrl}
                      >
                        Your browser does not support the video tag.
                      </video>
                    </div>
                  ) : selectedLecture.contentType === 'TEXT' ? (
                    <div className="w-full h-full overflow-y-auto bg-white">
                      <div className="max-w-4xl mx-auto p-8">
                        <h1 className="text-4xl font-bold text-gray-900 mb-3">{selectedLecture.title}</h1>
                        {selectedLecture.description && (
                          <div className="text-gray-600 mb-8 text-lg font-normal leading-relaxed">
                            <MarkdownRenderer content={selectedLecture.description} />
                          </div>
                        )}
                        {selectedLecture.contents && selectedLecture.contents.length > 0 ? (
                          <div className="mt-8 space-y-8">
                            {selectedLecture.contents
                              .filter((content: any) => content.contentType === 'TEXT' && content.textContent)
                              .map((content: any) => (
                                <div key={content.id}>
                                  {content.title && (
                                    <h2 className="text-3xl font-bold text-gray-900 mb-4">{content.title}</h2>
                                  )}
                                  <MarkdownRenderer
                                    content={content.textContent}
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

                {/* Content Below Video */}
                <div className="border-t border-gray-200 bg-white">
                  <div className="px-6 py-4">
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center space-x-4">
                        <a href="#" className="text-sm text-gray-600 hover:text-gray-900">Report an issue</a>
                      </div>
                      <button className="px-4 py-2 text-sm font-medium text-blue-600 hover:text-blue-700 border border-blue-600 rounded-md">
                        Save note
                      </button>
                    </div>

                    {/* Tabs */}
                    <div className="border-b border-gray-200 mb-4">
                      <div className="flex space-x-6">
                        <button
                          onClick={() => setActiveTab('transcript')}
                          className={`py-3 px-1 border-b-2 font-medium text-sm ${
                            activeTab === 'transcript'
                              ? 'border-blue-600 text-blue-600'
                              : 'border-transparent text-gray-600 hover:text-gray-900'
                          }`}
                        >
                          Transcript
                        </button>
                        <button
                          onClick={() => setActiveTab('notes')}
                          className={`py-3 px-1 border-b-2 font-medium text-sm ${
                            activeTab === 'notes'
                              ? 'border-blue-600 text-blue-600'
                              : 'border-transparent text-gray-600 hover:text-gray-900'
                          }`}
                        >
                          Notes
                        </button>
                        <button
                          onClick={() => setActiveTab('downloads')}
                          className={`py-3 px-1 border-b-2 font-medium text-sm ${
                            activeTab === 'downloads'
                              ? 'border-blue-600 text-blue-600'
                              : 'border-transparent text-gray-600 hover:text-gray-900'
                          }`}
                        >
                          Downloads
                        </button>
                      </div>
                    </div>

                    {/* Tab Content */}
                    <div className="mb-6">
                      {activeTab === 'transcript' && (
                        <div>
                          <div className="flex justify-between items-center mb-4">
                            <div className="text-sm text-gray-600">0:00</div>
                            <select className="text-sm text-gray-600 border border-gray-300 rounded px-2 py-1">
                              <option>Transcript language: English</option>
                            </select>
                          </div>
                          <div>
                            {selectedLecture.description && (
                              <MarkdownRenderer content={selectedLecture.description} />
                            )}
                            {selectedLecture.contents && selectedLecture.contents.length > 0 && (
                              <div className="mt-4">
                                {selectedLecture.contents
                                  .filter((content: any) => content.contentType === 'TEXT' && content.textContent)
                                  .map((content: any) => (
                                    <div key={content.id} className="mb-4">
                                      <MarkdownRenderer content={content.textContent} />
                                    </div>
                                  ))}
                              </div>
                            )}
                          </div>
                        </div>
                      )}

                      {activeTab === 'notes' && (
                        <div>
                          <p className="text-gray-700 mb-4">
                            Click the 'Save Note' button below the lecture when you want to capture a screen. You can also highlight and save lines from the transcript. Add your own notes to anything you've captured.
                          </p>
                          <a href="#" className="text-blue-600 hover:text-blue-700 text-sm font-medium flex items-center space-x-1">
                            <span>View all notes</span>
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                            </svg>
                          </a>
                        </div>
                      )}

                      {activeTab === 'downloads' && (
                        <div className="space-y-3">
                          <div className="flex items-center justify-between p-3 border border-gray-200 rounded hover:bg-gray-50 cursor-pointer">
                            <span className="text-gray-700">Lecture Video (240p) mp4</span>
                          </div>
                          <div className="flex items-center justify-between p-3 border border-gray-200 rounded hover:bg-gray-50 cursor-pointer">
                            <span className="text-gray-700">Lecture Video (1080p) mp4</span>
                          </div>
                          <div className="flex items-center justify-between p-3 border border-gray-200 rounded hover:bg-gray-50 cursor-pointer">
                            <span className="text-gray-700">Subtitles (English) WebVTT</span>
                          </div>
                          <div className="flex items-center justify-between p-3 border border-gray-200 rounded hover:bg-gray-50 cursor-pointer">
                            <span className="text-gray-700">Transcript (English) txt</span>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Engagement Buttons */}
                    <div className="flex items-center space-x-4 pb-4">
                      <button className="flex items-center space-x-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md">
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
                        </svg>
                        <span className="text-sm font-medium">Like</span>
                      </button>
                      <button className="flex items-center space-x-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md">
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.736 3h4.018a2 2 0 01.485.06l3.76.94m-7 10v5a2 2 0 002 2h.096c.5 0 .905-.405.905-.904 0-.715.211-1.413.608-2.008L17 13V4m-7 10h2m5-10h2a2 2 0 012 2v6a2 2 0 01-2 2h-2.5" />
                        </svg>
                        <span className="text-sm font-medium">Dislike</span>
                      </button>
                      <button className="flex items-center space-x-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md">
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
                        </svg>
                        <span className="text-sm font-medium">Share</span>
                      </button>
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-gray-500">
                <p>Select a lecture to begin</p>
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <footer className="bg-gray-800 text-white py-4 px-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <div className="w-6 h-6 bg-blue-600 rounded flex items-center justify-center text-white font-bold text-xs">c</div>
              <span className="text-sm">Coursera</span>
            </div>
            <div className="text-sm text-gray-400">curated by Mobbin</div>
          </div>
        </footer>

        {/* Floating Chat Button */}
        <button className="fixed bottom-6 right-6 w-12 h-12 bg-blue-600 text-white rounded-lg shadow-lg hover:bg-blue-700 flex items-center justify-center z-50">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
          </svg>
        </button>
      </div>
    </ProtectedRoute>
  )
}
