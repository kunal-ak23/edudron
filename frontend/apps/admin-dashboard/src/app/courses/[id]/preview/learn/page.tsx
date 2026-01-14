'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { coursesApi, lecturesApi } from '@/lib/api'
import type { Course, Section, Lecture } from '@kunal-ak23/edudron-shared-utils'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function CourseLearnPreviewPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<any[]>([])
  const [selectedLecture, setSelectedLecture] = useState<Lecture | null>(null)
  const [selectedSection, setSelectedSection] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [transcriptText, setTranscriptText] = useState<string | null>(null)
  const [loadingTranscript, setLoadingTranscript] = useState(false)

  // Refs for scrollable containers
  const mainContentRef = useRef<HTMLDivElement>(null)
  const textContentRef = useRef<HTMLDivElement>(null)

  const loadCourseData = useCallback(async () => {
    try {
      setLoading(true)
      
      const courseData = await coursesApi.getCourse(courseId)
      setCourse(courseData)
      
      const fetchedSections = await coursesApi.getChapters(courseId).catch(() => [])
      const sectionsData = await Promise.all(
        fetchedSections.map(async (section: any) => {
          try {
            const subLectures = await lecturesApi.getSubLecturesByLecture(courseId, section.id)
            return { ...section, lectures: subLectures }
          } catch (error) {
            console.warn(`Failed to load sub-lectures for section ${section.id}:`, error)
            return { ...section, lectures: [] }
          }
        })
      )
      
      setSections(sectionsData as any)

      // Default to first section if available
      if (sectionsData && sectionsData.length > 0) {
        const firstSection = sectionsData[0] as any
        if (firstSection.lectures && firstSection.lectures.length > 0) {
          setSelectedSection(firstSection)
          setSelectedLecture(null)
        }
      }
    } catch (error) {
      console.error('Failed to load course data:', error)
      setCourse(null)
    } finally {
      setLoading(false)
    }
  }, [courseId])

  useEffect(() => {
    loadCourseData()
  }, [loadCourseData])

  // Load transcript when selectedLecture changes
  useEffect(() => {
    if (selectedLecture?.contentType === 'VIDEO') {
      const videoContent = selectedLecture.contents?.find((c: any) => 
        c.contentType === 'VIDEO' && (c.videoUrl || c.fileUrl)
      )
      const transcriptUrl = (videoContent as any)?.transcriptUrl
      
      if (transcriptUrl) {
        setLoadingTranscript(true)
        fetch(transcriptUrl)
          .then(res => res.text())
          .then(text => {
            setTranscriptText(text)
            setLoadingTranscript(false)
          })
          .catch(err => {
            console.error('Failed to load transcript:', err)
            setTranscriptText(null)
            setLoadingTranscript(false)
          })
      } else {
        setTranscriptText(null)
        setLoadingTranscript(false)
      }
    } else {
      setTranscriptText(null)
      setLoadingTranscript(false)
    }
  }, [selectedLecture])

  // Scroll to top when lecture changes
  useEffect(() => {
    if (selectedLecture) {
      requestAnimationFrame(() => {
        if (mainContentRef.current) {
          mainContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
        }
        if (textContentRef.current) {
          textContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
        }
        window.scrollTo({ top: 0, behavior: 'smooth' })
      })
    }
  }, [selectedLecture])

  const handleLectureSelect = (lecture: Lecture) => {
    setSelectedLecture(lecture)
    setSelectedSection(null)
    
    requestAnimationFrame(() => {
      if (mainContentRef.current) {
        mainContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
      }
      if (textContentRef.current) {
        textContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    })
  }

  const handleSectionSelect = (section: Section) => {
    setSelectedSection(section)
    setSelectedLecture(null)
  }

  const getNextLecture = () => {
    if (!selectedLecture || !sections) return null

    for (let i = 0; i < sections.length; i++) {
      const section = sections[i]
      const lectures = section.lectures || []
      const currentIndex = lectures.findIndex((l: any) => l.id === selectedLecture.id)

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
      const currentIndex = lectures.findIndex((l: any) => l.id === selectedLecture.id)

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

  const getContentTypeIcon = (contentType: string) => {
    switch (contentType) {
      case 'VIDEO':
        return (
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
          </svg>
        )
      case 'TEXT':
      case 'READING':
        return (
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
          </svg>
        )
      default:
        return (
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        )
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-white flex items-center justify-center">
        <div className="animate-pulse">Loading...</div>
      </div>
    )
  }

  if (!course) {
    return (
      <div className="min-h-screen bg-white flex items-center justify-center">
        <p className="text-gray-500">Course not found</p>
      </div>
    )
  }

  const currentSection = sections.find(s => s.lectures?.some((l: any) => l.id === selectedLecture?.id))
  const nextLecture = getNextLecture()
  const prevLecture = getPrevLecture()

  return (
    <div className="flex h-screen bg-white">
      {/* Preview Mode Banner */}
      <div className="fixed top-0 left-0 right-0 bg-amber-50 border-b border-amber-200 px-6 sm:px-8 lg:px-12 py-2 z-50">
        <div className="max-w-[1600px] mx-auto">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-amber-800 font-medium text-sm">Preview Mode</span>
              <span className="text-amber-700 text-xs">Viewing course as a student would see it</span>
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

      {/* Left Sidebar */}
      <div className={`${sidebarCollapsed ? 'w-0 border-l-0' : 'w-80 border-r'} bg-white border-gray-200 overflow-visible transition-all duration-300 flex-shrink-0 relative mt-12`}>
        {!sidebarCollapsed && (
          <>
            {/* Toggle Menu Button */}
            <button
              onClick={() => setSidebarCollapsed(true)}
              className="absolute right-0 top-1/2 transform -translate-y-1/2 translate-x-full z-30 bg-white hover:bg-gray-50 rounded-r-lg px-2 py-4 shadow-md border-r border-t border-b border-gray-200"
              aria-label="Toggle menu"
              style={{ right: '-1px' }}
            >
              <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <div className="p-4 overflow-y-auto h-full">
              <div className="space-y-4">
                {sections.map((section, sectionIdx) => {
                  if (!section.lectures || section.lectures.length === 0) return null
                  
                  return (
                    <div key={section.id} className="mb-4">
                      {/* Module Header */}
                      <button
                        onClick={() => handleSectionSelect(section)}
                        className={`w-full text-left mb-2 px-2 py-1.5 rounded transition-colors ${
                          selectedSection?.id === section.id
                            ? 'bg-primary-50'
                            : 'hover:bg-gray-50'
                        }`}
                      >
                        <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                          Module {sectionIdx + 1}
                        </h4>
                        <p className={`text-sm font-medium mt-0.5 truncate ${
                          selectedSection?.id === section.id
                            ? 'text-primary-700'
                            : 'text-gray-900'
                        }`}>
                          {section.title}
                        </p>
                      </button>
                      
                      {/* Lessons in this module */}
                      <div className="space-y-1">
                        {section.lectures.map((lecture: any) => {
                          const isSelected = selectedLecture?.id === lecture.id
                          const contentType = lecture.contentType || 'TEXT'

                          return (
                            <button
                              key={lecture.id}
                              onClick={() => handleLectureSelect(lecture)}
                              className={`w-full text-left px-3 py-2 rounded text-xs transition-colors relative ${
                                isSelected
                                  ? 'bg-primary-50 text-primary-700'
                                  : 'text-gray-600 hover:bg-gray-50'
                              }`}
                            >
                              {isSelected && (
                                <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary-600 rounded-r"></div>
                              )}
                              <div className="flex items-center space-x-2">
                                <div className={`flex-shrink-0 ${isSelected ? 'text-primary-600' : 'text-gray-400'}`}>
                                  {getContentTypeIcon(contentType)}
                                </div>
                                <div className="flex-1 min-w-0">
                                  <span className="font-normal truncate">
                                    {lecture.title}
                                  </span>
                                  {lecture.duration && (
                                    <div className="text-xs text-gray-400 mt-0.5">
                                      {Math.floor(lecture.duration / 60)} min
                                    </div>
                                  )}
                                </div>
                              </div>
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </>
        )}
      </div>
      {sidebarCollapsed && (
        <button
          onClick={() => setSidebarCollapsed(false)}
          className="fixed left-0 top-1/2 transform -translate-y-1/2 z-30 bg-white border-l border-t border-b border-gray-200 rounded-r-lg px-2 py-4 shadow-md hover:bg-gray-50 mt-12"
        >
          <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>
      )}

      {/* Main Content */}
      <div ref={mainContentRef} className="flex-1 flex flex-col min-w-0 bg-white overflow-y-auto mt-12">
        {selectedSection && !selectedLecture ? (
          <>
            {/* Module Landing Page */}
            <div className="px-6 py-3 border-b border-gray-200 flex items-center justify-between flex-shrink-0">
              <div className="flex items-center space-x-2 text-sm text-gray-600">
                <button
                  onClick={() => router.push(`/courses/${courseId}/preview`)}
                  className="hover:text-primary-600 transition-colors"
                >
                  {course.title}
                </button>
                <span>{'>'}</span>
                <span className="text-gray-900 font-medium">{selectedSection.title}</span>
              </div>
            </div>

            <div className="flex-1 bg-white">
              <div className="mx-[36px] p-8">
                <h1 className="text-4xl font-bold text-gray-900 mb-4">
                  {selectedSection.title}
                </h1>
                
                {selectedSection.description && (
                  <div className="text-gray-600 mb-8 text-lg leading-relaxed">
                    <MarkdownRenderer content={selectedSection.description} />
                  </div>
                )}

                <div className="mt-8">
                  <h2 className="text-2xl font-semibold text-gray-900 mb-6">
                    Lessons in this module
                  </h2>
                  <div className="space-y-3">
                    {selectedSection.lectures?.map((lecture: any, lectureIdx: number) => {
                      const contentType = lecture.contentType || 'TEXT'
                      
                      return (
                        <div
                          key={lecture.id}
                          className="bg-white border border-gray-200 rounded-xl p-4 flex justify-between items-center shadow-sm hover:border-primary-300 transition-colors cursor-pointer"
                          onClick={() => handleLectureSelect(lecture)}
                        >
                          <div className="flex gap-3 items-center flex-1">
                            <div className="w-9 h-9 rounded-lg bg-primary-50 border border-primary-200 flex items-center justify-center font-bold text-gray-900 flex-shrink-0">
                              {lectureIdx + 1}
                            </div>
                            <div className="flex items-center gap-2 flex-shrink-0">
                              {getContentTypeIcon(contentType)}
                            </div>
                            <div className="flex-1 min-w-0">
                              <h3 className="font-semibold text-gray-900 mb-1">
                                {lecture.title}
                              </h3>
                              <p className="text-sm text-gray-500">
                                {contentType === 'VIDEO' ? 'Video' : 'Reading'} •{' '}
                                {lecture.duration ? `${Math.floor(lecture.duration / 60)} min` : 'N/A'}
                              </p>
                            </div>
                          </div>
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleLectureSelect(lecture)
                            }}
                            className="px-4 py-2 text-sm font-medium text-primary-600 hover:text-primary-700 border border-primary-600 rounded-md flex-shrink-0"
                          >
                            Open
                          </button>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            </div>
          </>
        ) : selectedLecture ? (
          <>
            {/* Breadcrumbs */}
            <div className="px-6 py-3 border-b border-gray-200 flex-shrink-0">
              <div className="flex items-center space-x-2 text-sm text-gray-600">
                <button
                  onClick={() => router.push(`/courses/${courseId}/preview`)}
                  className="hover:text-primary-600 transition-colors"
                >
                  {course.title}
                </button>
                <span>{'>'}</span>
                {currentSection && (
                  <>
                    <button
                      onClick={() => handleSectionSelect(currentSection)}
                      className="hover:text-primary-600 transition-colors"
                    >
                      {currentSection.title}
                    </button>
                    <span>{'>'}</span>
                  </>
                )}
                <span className="text-gray-900 font-medium">{selectedLecture.title}</span>
              </div>
            </div>

            {/* Video/Content Player */}
            <div className="bg-white flex-1 flex items-center justify-center relative min-h-0">
              {selectedLecture.contentType === 'VIDEO' ? (() => {
                const videoContent = selectedLecture.contents?.find((c: any) => 
                  c.contentType === 'VIDEO' && (c.videoUrl || c.fileUrl)
                )
                const videoUrl = videoContent?.videoUrl || videoContent?.fileUrl || selectedLecture.contentUrl
                
                return videoUrl ? (
                  <div className="w-full h-full flex items-center justify-center p-4">
                    <div className="w-full max-w-7xl h-full max-h-[90vh] flex items-center justify-center">
                      <video
                        controls
                        className="w-full h-full object-contain"
                        src={videoUrl}
                      >
                        Your browser does not support the video tag.
                      </video>
                    </div>
                  </div>
                ) : (
                  <div className="text-gray-600 text-center p-8">
                    <p className="text-lg">Video not available</p>
                    <p className="text-sm text-gray-400 mt-2">The video file has not been uploaded yet.</p>
                  </div>
                )
              })() : selectedLecture.contentType === 'TEXT' ? (
                <div ref={textContentRef} className="w-full h-full bg-white overflow-y-auto">
                  <div className="mx-[36px] px-4 sm:px-6 lg:px-8 py-6">
                    <h1 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-3">{selectedLecture.title}</h1>
                    {selectedLecture.description && (
                      <div className="text-gray-600 mb-6 text-base sm:text-lg font-normal leading-relaxed">
                        <MarkdownRenderer content={selectedLecture.description} />
                      </div>
                    )}
                    {selectedLecture.contents && selectedLecture.contents.length > 0 ? (
                      <div className="mt-6 space-y-6">
                        {selectedLecture.contents
                          .filter((content: any) => content.contentType === 'TEXT' && content.textContent)
                          .map((content: any, index: number) => (
                            <div key={content.id || index}>
                              {content.title && content.title.trim() !== selectedLecture.title.trim() && (
                                <h2 className="text-2xl sm:text-3xl font-bold text-gray-900 mb-3">{content.title}</h2>
                              )}
                              <MarkdownRenderer content={content.textContent} />
                            </div>
                          ))}
                      </div>
                    ) : (
                      <div className="text-center text-gray-400 mt-6">
                        <p>Content is being generated. Please check back soon.</p>
                      </div>
                    )}

                    {/* Navigation Controls */}
                    <div className="mt-12 pt-6 border-t border-gray-200 flex items-center justify-between">
                      {prevLecture && (
                        <button
                          onClick={() => handleLectureSelect(prevLecture)}
                          className="px-6 py-2 bg-gray-100 text-gray-700 font-medium rounded-md hover:bg-gray-200 transition-colors flex items-center space-x-2"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                          </svg>
                          <span>Previous</span>
                        </button>
                      )}
                      {nextLecture && (
                        <button
                          onClick={() => handleLectureSelect(nextLecture)}
                          className="px-6 py-2 bg-primary-600 text-white font-medium rounded-md hover:bg-primary-700 transition-colors flex items-center justify-center space-x-2 ml-auto"
                        >
                          <span>Next</span>
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                          </svg>
                        </button>
                      )}
                    </div>
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

            {/* Text Content Below Video - Only for VIDEO content */}
            {selectedLecture.contentType === 'VIDEO' && (
              <>
                {(selectedLecture.description || (selectedLecture.contents && selectedLecture.contents.some((c: any) => c.contentType === 'TEXT' && c.textContent))) && (
                  <div className="border-t border-gray-200 bg-white">
                    <div className="mx-[36px] px-4 sm:px-6 lg:px-8 py-6">
                      <h2 className="text-2xl sm:text-3xl font-bold text-gray-900 mb-4">{selectedLecture.title}</h2>
                      
                      {selectedLecture.description && (
                        <div className="text-gray-700 mb-6 text-base sm:text-lg font-normal leading-relaxed">
                          <MarkdownRenderer content={selectedLecture.description} />
                        </div>
                      )}
                      
                      {selectedLecture.contents && selectedLecture.contents.length > 0 && (
                        <div className="space-y-6">
                          {selectedLecture.contents
                            .filter((content: any) => content.contentType === 'TEXT' && content.textContent)
                            .map((content: any, index: number) => (
                              <div key={content.id || index} className="border-t border-gray-100 pt-6 first:border-t-0 first:pt-0">
                                {content.title && content.title.trim() !== selectedLecture.title.trim() && (
                                  <h3 className="text-xl sm:text-2xl font-bold text-gray-900 mb-3">{content.title}</h3>
                                )}
                                <MarkdownRenderer content={content.textContent} />
                              </div>
                            ))}
                        </div>
                      )}
                    </div>
                  </div>
                )}
                
                {/* Transcript Section */}
                {transcriptText && (
                  <div className="border-t border-gray-200 bg-white pb-20 md:pb-4">
                    <div className="mx-[36px] px-4 sm:px-6 lg:px-8 py-3">
                      <h3 className="text-lg font-semibold text-gray-900 mb-3">Transcript</h3>
                      <div className="text-gray-700">
                        {loadingTranscript ? (
                          <p className="text-sm text-gray-500 mb-3">Loading transcript...</p>
                        ) : transcriptText ? (
                          <div className="whitespace-pre-wrap text-sm leading-relaxed max-h-96 overflow-y-auto">
                            {transcriptText}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </div>
                )}

                {/* Navigation Controls */}
                <div className="border-t border-gray-200 bg-white pt-3 pb-4">
                  <div className="mx-[36px] px-4 sm:px-6 lg:px-8 flex items-center justify-between">
                    {prevLecture && (
                      <button
                        onClick={() => handleLectureSelect(prevLecture)}
                        className="px-6 py-2 bg-gray-100 text-gray-700 font-medium rounded-md hover:bg-gray-200 transition-colors flex items-center space-x-2"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                        </svg>
                        <span>Previous</span>
                      </button>
                    )}
                    {nextLecture && (
                      <button
                        onClick={() => handleLectureSelect(nextLecture)}
                        className="px-6 py-2 bg-primary-600 text-white font-medium rounded-md hover:bg-primary-700 transition-colors flex items-center justify-center space-x-2 ml-auto"
                      >
                        <span>Next</span>
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                      </button>
                    )}
                  </div>
                </div>
              </>
            )}
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-500">
            <p>Select a module or lecture to begin</p>
          </div>
        )}
      </div>
    </div>
  )
}
