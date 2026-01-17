'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, Button, VideoPlayer } from '@kunal-ak23/edudron-ui-components'
import { coursesApi, enrollmentsApi, lecturesApi, feedbackApi, notesApi, issuesApi } from '@/lib/api'
import type { Course, Section, Lecture, Progress, Feedback, Note, FeedbackType, IssueType, LectureContent } from '@kunal-ak23/edudron-shared-utils'
import { MarkdownRenderer } from '@/components/MarkdownRenderer'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Switch } from '@/components/ui/switch'
import { StudentLayout } from '@/components/StudentLayout'
import { FeedbackDialog } from '@/components/FeedbackDialog'
import { IssueReportDialog } from '@/components/IssueReportDialog'
import { MarkdownWithHighlights } from '@/components/MarkdownWithHighlights'
import { NotesSidebar } from '@/components/NotesSidebar'

type TabType = 'transcript' | 'notes'

export default function LearnPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const { user } = useAuth()
  const [course, setCourse] = useState<Course | null>(null)
  const [sections, setSections] = useState<any[]>([])
  const [progress, setProgress] = useState<Progress | null>(null)
  const [selectedLecture, setSelectedLecture] = useState<Lecture | null>(null)
  const [selectedSection, setSelectedSection] = useState<any>(null)
  const [completedLectures, setCompletedLectures] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [activeTab, setActiveTab] = useState<TabType>('transcript')
  const [searchQuery, setSearchQuery] = useState('')
  
  // Feedback state
  const [currentFeedback, setCurrentFeedback] = useState<Feedback | null>(null)
  const [showFeedbackDialog, setShowFeedbackDialog] = useState(false)
  const [pendingFeedbackType, setPendingFeedbackType] = useState<FeedbackType | null>(null)
  
  // Issue report state
  const [showIssueDialog, setShowIssueDialog] = useState(false)
  
  // Notes state
  const [notes, setNotes] = useState<Note[]>([])
  const [showNotesSidebar, setShowNotesSidebar] = useState(false)
  
  // Transcript state
  const [transcriptText, setTranscriptText] = useState<string | null>(null)
  const [loadingTranscript, setLoadingTranscript] = useState(false)
  
  // Attachments state
  const [attachments, setAttachments] = useState<LectureContent[]>([])
  const [loadingAttachments, setLoadingAttachments] = useState(false)
  

  // Refs for scrollable containers
  const mainContentRef = useRef<HTMLDivElement>(null)
  const textContentRef = useRef<HTMLDivElement>(null)
  // Ref to track if we're manually updating completion to prevent useEffect from interfering
  const isUpdatingCompletionRef = useRef(false)

  // Helper function to get localStorage key for course position
  const getStorageKey = (courseId: string) => `course_position_${courseId}`

  // Helper function to find last accessed lecture from progress data
  const findLastAccessedLecture = (lectureProgressData: any[], sectionsData: any[]): Lecture | null => {
    if (!lectureProgressData || lectureProgressData.length === 0 || !sectionsData || sectionsData.length === 0) {
      return null
    }

    // Filter progress entries that have lastAccessedAt and lectureId
    const accessedLectures = lectureProgressData
      .filter((lp: any) => lp.lastAccessedAt && lp.lectureId)
      .sort((a: any, b: any) => {
        // Sort by lastAccessedAt descending (most recent first)
        const dateA = new Date(a.lastAccessedAt).getTime()
        const dateB = new Date(b.lastAccessedAt).getTime()
        return dateB - dateA
      })

    if (accessedLectures.length === 0) {
      return null
    }

    // Find the most recently accessed lecture in the sections
    for (const progressEntry of accessedLectures) {
      for (const section of sectionsData) {
        if (section.lectures) {
          const lecture = section.lectures.find((l: Lecture) => l.id === progressEntry.lectureId)
          if (lecture) {
            return lecture
          }
        }
      }
    }

    return null
  }

  // Helper function to restore position from localStorage
  const restorePositionFromStorage = useCallback((sectionsData: any[]): { lecture: Lecture | null; section: Section | null } => {
    try {
      const stored = localStorage.getItem(getStorageKey(courseId))
      if (!stored) return { lecture: null, section: null }

      const position = JSON.parse(stored)
      if (!position.lectureId) return { lecture: null, section: null }

      // Find the lecture in sections
      for (const section of sectionsData) {
        if (section.lectures) {
          const lecture = section.lectures.find((l: Lecture) => l.id === position.lectureId)
          if (lecture) {
            return { lecture, section: null }
          }
        }
      }
    } catch (error) {
      console.warn('[LearnPage] Error restoring position from localStorage:', error)
    }
    return { lecture: null, section: null }
  }, [courseId])

  // Save current position to localStorage
  const savePositionToStorage = useCallback((lecture: Lecture | null) => {
    try {
      if (lecture) {
        localStorage.setItem(getStorageKey(courseId), JSON.stringify({ lectureId: lecture.id }))
      } else {
        localStorage.removeItem(getStorageKey(courseId))
      }
    } catch (error) {
      console.warn('[LearnPage] Error saving position to localStorage:', error)
    }
  }, [courseId])

  // Load feedback and notes for a lecture
  const loadFeedbackAndNotes = useCallback(async (lectureId: string) => {
    try {
      console.log('[LearnPage] Loading feedback and notes for lecture:', lectureId)
      const [feedback, lectureNotes] = await Promise.all([
        feedbackApi.getFeedback(lectureId).catch((err) => {
          console.warn('[LearnPage] Failed to load feedback:', err)
          return null
        }),
        notesApi.getNotesByLecture(lectureId).catch((err) => {
          console.warn('[LearnPage] Failed to load notes:', err)
          return []
        })
      ])
      console.log('[LearnPage] Loaded notes:', lectureNotes.length, lectureNotes)
      setCurrentFeedback(feedback)
      // Merge with existing notes, replacing notes for this lecture
      setNotes(prevNotes => {
        const otherNotes = prevNotes.filter(n => n.lectureId !== lectureId)
        return [...otherNotes, ...lectureNotes]
      })
    } catch (error) {
      console.error('[LearnPage] Failed to load feedback and notes:', error)
    }
  }, [])

  const loadCourseData = useCallback(async () => {
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
          fetchedSections.map(async (section: any) => {
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

      // Restore position: Try from progress data first, then localStorage, then default to first section
      if (sectionsData && sectionsData.length > 0) {
        let restoredLecture: Lecture | null = null
        let restoredSection: Section | null = null

        // Try to find last accessed lecture from progress data
        restoredLecture = findLastAccessedLecture(lectureProgressData, sectionsData)
        
        // If not found in progress, try localStorage
        if (!restoredLecture) {
          const storagePosition = restorePositionFromStorage(sectionsData)
          restoredLecture = storagePosition.lecture
          restoredSection = storagePosition.section
        }

        if (restoredLecture) {
          // Restore the last accessed lecture
          console.log('[LearnPage] Restoring last accessed lecture:', restoredLecture.id)
          setSelectedLecture(restoredLecture)
          setSelectedSection(null)
        } else {
          // Default to first section if no progress found
          const firstSection = sectionsData[0] as any
          if (firstSection.lectures && firstSection.lectures.length > 0) {
            console.log('[LearnPage] No previous progress found, showing first module')
            setSelectedSection(firstSection)
            setSelectedLecture(null)
          }
        }
      }
    } catch (error) {
      console.error('[LearnPage] Unexpected error loading course data:', error)
      setCourse(null)
    } finally {
      setLoading(false)
    }
  }, [courseId, restorePositionFromStorage])

  useEffect(() => {
    loadCourseData()
  }, [loadCourseData])

  // Add styles to prevent Video.js from overlapping content and apply primary theme
  useEffect(() => {
    const style = document.createElement('style')
    style.textContent = `
      [data-vjs-player] {
        position: relative !important;
        overflow: hidden !important;
        max-width: 100% !important;
        min-height: 400px !important;
      }
      .video-js {
        position: relative !important;
        width: 100% !important;
        max-width: 100% !important;
        overflow: hidden !important;
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
      }
      .video-js.vjs-fluid {
        padding-top: 56.25% !important; /* 16:9 default, will be overridden by video's natural aspect ratio */
      }
      .video-js .vjs-tech {
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        width: 100% !important;
        height: 100% !important;
        object-fit: contain !important;
        object-position: center center !important;
      }
      .video-js video {
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        width: 100% !important;
        height: 100% !important;
        object-fit: contain !important;
        object-position: center center !important;
      }
      .video-js .vjs-poster {
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        width: 100% !important;
        height: 100% !important;
        object-fit: contain !important;
        object-position: center center !important;
      }
      .video-js .vjs-tech-wrapper {
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
        width: 100% !important;
        height: 100% !important;
      }
      
      /* Primary Theme - Big Play Button */
      .video-js .vjs-big-play-button {
        background-color: hsl(var(--primary-600)) !important;
        border-color: hsl(var(--primary-600)) !important;
        border-radius: 50% !important;
        width: 80px !important;
        height: 80px !important;
        line-height: 80px !important;
        margin-top: -40px !important;
        margin-left: -40px !important;
        border-width: 4px !important;
        transition: all 0.3s ease !important;
        top: 50% !important;
        left: 50% !important;
        transform: translate(-50%, -50%) !important;
      }
      .video-js .vjs-big-play-button:hover {
        background-color: hsl(var(--primary-700)) !important;
        border-color: hsl(var(--primary-700)) !important;
        transform: translate(-50%, -50%) scale(1.1) !important;
      }
      .video-js .vjs-big-play-button .vjs-icon-placeholder:before {
        color: white !important;
        font-size: 2.5em !important;
      }
      
      /* Primary Theme - Control Bar */
      .video-js .vjs-control-bar {
        background: linear-gradient(to top, rgba(0, 0, 0, 0.8), transparent) !important;
        height: 4.5em !important;
        display: flex !important;
        flex-wrap: nowrap !important;
        align-items: center !important;
        justify-content: flex-start !important;
        overflow: visible !important;
        white-space: nowrap !important;
      }
      .video-js .vjs-control-bar > * {
        flex-shrink: 0 !important;
        flex-grow: 0 !important;
        display: inline-flex !important;
      }
      .video-js .vjs-progress-control {
        flex: 1 1 auto !important;
        min-width: 0 !important;
        display: flex !important;
      }
      .video-js .vjs-control {
        display: inline-flex !important;
        align-items: center !important;
        white-space: nowrap !important;
      }
      .video-js .vjs-button {
        display: inline-flex !important;
        align-items: center !important;
        white-space: nowrap !important;
      }
      
      /* Primary Theme - Progress Bar */
      .video-js .vjs-play-progress {
        background-color: hsl(var(--primary-600)) !important;
      }
      .video-js .vjs-load-progress {
        background-color: rgba(255, 255, 255, 0.3) !important;
      }
      .video-js .vjs-progress-holder {
        background-color: rgba(255, 255, 255, 0.2) !important;
      }
      .video-js .vjs-play-progress:before {
        color: hsl(var(--primary-600)) !important;
      }
      
      /* Primary Theme - Control Buttons */
      .video-js .vjs-play-control:hover,
      .video-js .vjs-play-control:focus {
        color: hsl(var(--primary-400)) !important;
      }
      .video-js .vjs-play-control.vjs-playing .vjs-icon-placeholder:before {
        color: hsl(var(--primary-400)) !important;
      }
      .video-js .vjs-play-control.vjs-paused .vjs-icon-placeholder:before {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Volume Control */
      .video-js .vjs-volume-level {
        background-color: hsl(var(--primary-600)) !important;
      }
      .video-js .vjs-volume-bar {
        background-color: rgba(255, 255, 255, 0.2) !important;
      }
      .video-js .vjs-volume-control:hover .vjs-volume-level {
        background-color: hsl(var(--primary-700)) !important;
      }
      
      /* Primary Theme - Time Displays */
      .video-js .vjs-current-time,
      .video-js .vjs-duration,
      .video-js .vjs-remaining-time {
        color: rgba(255, 255, 255, 0.9) !important;
        font-weight: 500 !important;
      }
      
      /* Primary Theme - Fullscreen Button */
      .video-js .vjs-fullscreen-control:hover {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Picture-in-Picture */
      .video-js .vjs-picture-in-picture-control:hover {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Playback Rate Menu */
      .video-js .vjs-playback-rate .vjs-playback-rate-value {
        color: hsl(var(--primary-400)) !important;
        font-weight: 600 !important;
      }
      .video-js .vjs-menu-button-popup .vjs-menu .vjs-menu-content {
        background-color: rgba(0, 0, 0, 0.9) !important;
        border: 1px solid hsl(var(--primary-600)) !important;
      }
      .video-js .vjs-menu li.vjs-menu-item:hover,
      .video-js .vjs-menu li.vjs-menu-item:focus {
        background-color: hsl(var(--primary-600)) !important;
        color: white !important;
      }
      .video-js .vjs-menu li.vjs-menu-item.vjs-selected {
        background-color: hsl(var(--primary-700)) !important;
        color: white !important;
      }
      
      /* Primary Theme - Subtitles/Captions Button */
      .video-js .vjs-subs-caps-button:hover,
      .video-js .vjs-chapters-button:hover,
      .video-js .vjs-descriptions-button:hover {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Seek Bar Handle */
      .video-js .vjs-progress-control .vjs-play-progress .vjs-time-tooltip {
        background-color: hsl(var(--primary-600)) !important;
        color: white !important;
        border: 1px solid hsl(var(--primary-700)) !important;
      }
      
      /* Primary Theme - Loading Spinner */
      .video-js .vjs-loading-spinner {
        border-color: hsl(var(--primary-600)) transparent transparent transparent !important;
      }
    `
    style.setAttribute('data-videojs-theme', 'true')
    document.head.appendChild(style)
    
    return () => {
      const existingStyle = document.head.querySelector('style[data-videojs-theme]')
      if (existingStyle) {
        document.head.removeChild(existingStyle)
      }
    }
  }, [])

  // Save position to localStorage when selectedLecture changes
  useEffect(() => {
    if (selectedLecture && !isUpdatingCompletionRef.current) {
      savePositionToStorage(selectedLecture)
      // Update progress to track last accessed position (only when lecture changes, not when completion status changes)
      enrollmentsApi.updateProgress(courseId, {
        lectureId: selectedLecture.id,
        progressPercentage: completedLectures.has(selectedLecture.id) ? 100 : 0
      }).catch((error) => {
        console.warn('[LearnPage] Failed to update progress for last accessed position:', error)
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedLecture, courseId, savePositionToStorage])

  // Load attachments for a lecture
  const loadAttachments = useCallback(async (lectureId: string) => {
    try {
      setLoadingAttachments(true)
      const media = await lecturesApi.getLectureMedia(lectureId)
      // Filter out VIDEO and TEXT content types - only show attachments (PDF, IMAGE, AUDIO, etc.)
      const attachmentItems = media.filter((item: LectureContent) => 
        item.contentType !== 'VIDEO' && item.contentType !== 'TEXT'
      )
      setAttachments(attachmentItems)
    } catch (error) {
      console.error('[LearnPage] Failed to load attachments:', error)
      setAttachments([])
    } finally {
      setLoadingAttachments(false)
    }
  }, [])

  // Load feedback and notes whenever selectedLecture changes
  useEffect(() => {
    if (selectedLecture?.id) {
      console.log('[LearnPage] selectedLecture changed, loading notes for:', selectedLecture.id)
      loadFeedbackAndNotes(selectedLecture.id)
      loadAttachments(selectedLecture.id)
    } else {
      // Clear notes when no lecture is selected
      setNotes([])
      setCurrentFeedback(null)
      setAttachments([])
    }
  }, [selectedLecture?.id, loadFeedbackAndNotes, loadAttachments])

  // Scroll to top when lecture changes
  useEffect(() => {
    if (selectedLecture) {
      // Use requestAnimationFrame to ensure DOM is ready
      requestAnimationFrame(() => {
        // Smoothly scroll main content container
        if (mainContentRef.current) {
          mainContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
        }
        // Smoothly scroll text content container if it exists
        if (textContentRef.current) {
          textContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
        }
        // Also scroll window as fallback
        window.scrollTo({ top: 0, behavior: 'smooth' })
      })
      
      // Also try after a small delay to catch any late DOM updates
      const timeoutId = setTimeout(() => {
        if (mainContentRef.current) {
          mainContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
        }
        if (textContentRef.current) {
          textContentRef.current.scrollTo({ top: 0, behavior: 'smooth' })
        }
        window.scrollTo({ top: 0, behavior: 'smooth' })
      }, 100)
      
      return () => clearTimeout(timeoutId)
    }
  }, [selectedLecture])


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

  const handleLectureSelect = (lecture: Lecture) => {
    setSelectedLecture(lecture)
    setSelectedSection(null) // Clear module selection when selecting a lecture
    // Position will be saved via useEffect hook
    // Notes will be loaded via useEffect hook when selectedLecture changes
    // Transcript will be loaded via useEffect hook when selectedLecture changes
    
    // Smoothly scroll to top
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

  // Handle like/dislike button click
  const handleFeedbackClick = (type: FeedbackType) => {
    setPendingFeedbackType(type)
    setShowFeedbackDialog(true)
  }

  // Handle feedback submission
  const handleFeedbackSubmit = async (type: FeedbackType, comment?: string) => {
    if (!selectedLecture) return
    
    try {
      const feedback = await feedbackApi.createOrUpdateFeedback(selectedLecture.id, {
        lectureId: selectedLecture.id,
        courseId: courseId,
        type,
        comment
      })
      setCurrentFeedback(feedback)
    } catch (error) {
      console.error('Failed to submit feedback:', error)
      throw error
    }
  }

  // Handle issue report
  const handleReportIssue = async (issueType: IssueType, description: string) => {
    if (!selectedLecture) return
    
    try {
      await issuesApi.reportIssue(selectedLecture.id, {
        lectureId: selectedLecture.id,
        courseId: courseId,
        issueType,
        description
      })
    } catch (error) {
      console.error('Failed to report issue:', error)
      throw error
    }
  }

  // Handle add note with highlight (called from GoogleDocsStyleHighlighter)
  const handleAddNote = async (selectedText: string, range: Range, color: string, noteText: string) => {
    if (!selectedLecture) return

    try {
      const note = await notesApi.createNote(selectedLecture.id, {
        lectureId: selectedLecture.id,
        courseId: courseId,
        highlightedText: selectedText,
        highlightColor: color,
        noteText: noteText || undefined,
        context: range.toString() || undefined
      })
      // Reload all notes for this lecture to ensure we have the latest data
      await loadFeedbackAndNotes(selectedLecture.id)
    } catch (error) {
      console.error('Failed to save note:', error)
      throw error
    }
  }

  // Handle update note
  const handleUpdateNote = async (noteId: string, noteText: string) => {
    try {
      const note = notes.find(n => n.id === noteId)
      if (!note || !selectedLecture) return

      const updatedNote = await notesApi.updateNote(noteId, {
        lectureId: note.lectureId,
        courseId: note.courseId,
        highlightedText: note.highlightedText,
        highlightColor: note.highlightColor,
        noteText: noteText || undefined,
        context: note.context
      })
      
      // Reload notes to ensure consistency
      await loadFeedbackAndNotes(selectedLecture.id)
    } catch (error) {
      console.error('Failed to update note:', error)
      throw error
    }
  }

  // Handle delete note
  const handleDeleteNote = async (noteId: string) => {
    try {
      if (!selectedLecture) return
      await notesApi.deleteNote(noteId)
      // Reload notes to ensure consistency
      await loadFeedbackAndNotes(selectedLecture.id)
    } catch (error) {
      console.error('Failed to delete note:', error)
      throw error
    }
  }

  // Handle note click (scroll to highlight)
  const handleNoteClick = (noteId: string) => {
    // Find the highlight in the DOM and scroll to it
    const highlight = document.querySelector(`[data-note-id="${noteId}"]`)
    if (highlight) {
      highlight.scrollIntoView({ behavior: 'smooth', block: 'center' })
      // Add a temporary highlight effect
      highlight.classList.add('animate-pulse')
      setTimeout(() => {
        highlight.classList.remove('animate-pulse')
      }, 2000)
    }
  }

  const handleSectionSelect = (section: Section) => {
    setSelectedSection(section)
    setSelectedLecture(null) // Clear lecture selection when selecting a module
  }

  const handleMarkComplete = async (lectureId: string, isCompleted: boolean) => {
    try {
      // Set flag to prevent useEffect from interfering
      isUpdatingCompletionRef.current = true
      
      const newCompleted = new Set(completedLectures)
      if (isCompleted) {
        newCompleted.add(lectureId)
      } else {
        newCompleted.delete(lectureId)
      }
      setCompletedLectures(newCompleted)

      await enrollmentsApi.updateProgress(courseId, {
        lectureId,
        isCompleted,
        progressPercentage: isCompleted ? 100 : 0
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
      console.error('Failed to update lecture completion:', error)
      // Revert to previous state on error
      const reverted = new Set(completedLectures)
      if (isCompleted) {
        reverted.delete(lectureId)
      } else {
        reverted.add(lectureId)
      }
      setCompletedLectures(reverted)
    } finally {
      // Clear flag after update is complete
      isUpdatingCompletionRef.current = false
    }
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

  const nextLecture = getNextLecture()
  const prevLecture = getPrevLecture()

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
      <ProtectedRoute>
        <StudentLayout>
          <div className="min-h-screen bg-white flex items-center justify-center">
            <div className="animate-pulse">Loading...</div>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!course) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="min-h-screen bg-white flex items-center justify-center">
            <p className="text-gray-500">Course not found</p>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  const currentSection = sections.find(s => s.lectures?.some((l: any) => l.id === selectedLecture?.id))
  const currentSectionIndex = sections.findIndex(s => s.id === currentSection?.id)

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="flex h-full bg-white">
          {/* Left Sidebar */}
          <div className={`${sidebarCollapsed ? 'w-0 border-l-0' : 'w-80 border-r'} bg-white border-gray-200 overflow-visible transition-all duration-300 flex-shrink-0 relative`}>
            {!sidebarCollapsed && (
              <>
                {/* Toggle Menu Button - Right edge of sidebar */}
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
                  <TooltipProvider>
                    <div className="space-y-4">
                      {sections.map((section, sectionIdx) => {
                        if (!section.lectures || section.lectures.length === 0) return null
                        
                        return (
                          <div key={section.id} className="mb-4">
                            {/* Module Header - Clickable */}
                            <Tooltip>
                              <TooltipTrigger asChild>
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
                              </TooltipTrigger>
                              <TooltipContent>
                                <p>{section.title}</p>
                              </TooltipContent>
                            </Tooltip>
                            
                            {/* Lessons in this module */}
                            <div className="space-y-1">
                              {section.lectures.map((lecture: any) => {
                                const isSelected = selectedLecture?.id === lecture.id
                                const isCompleted = completedLectures.has(lecture.id)
                                const contentType = lecture.contentType || 'TEXT'

                                return (
                                  <Tooltip key={lecture.id}>
                                    <TooltipTrigger asChild>
                                      <button
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
                                            <div className="flex items-center space-x-2">
                                              <span className="font-normal truncate">
                                                {lecture.title}
                                              </span>
                                              {isCompleted && (
                                                <svg className="w-3.5 h-3.5 text-green-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                                                </svg>
                                              )}
                                            </div>
                                            {lecture.duration && (
                                              <div className="text-xs text-gray-400 mt-0.5">
                                                {Math.floor(lecture.duration / 60)} min
                                              </div>
                                            )}
                                          </div>
                                        </div>
                                      </button>
                                    </TooltipTrigger>
                                    <TooltipContent>
                                      <p>{lecture.title}</p>
                                    </TooltipContent>
                                  </Tooltip>
                                )
                              })}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </TooltipProvider>
                </div>
              </>
            )}
          </div>
          {sidebarCollapsed && (
            <button
              onClick={() => setSidebarCollapsed(false)}
              className="fixed left-0 top-1/2 transform -translate-y-1/2 z-30 bg-white border-l border-t border-b border-gray-200 rounded-r-lg px-2 py-4 shadow-md hover:bg-gray-50"
            >
              <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </button>
          )}

          {/* Main Content */}
          <div ref={mainContentRef} className="flex-1 flex flex-col min-w-0 bg-white overflow-y-auto">
            {selectedSection && !selectedLecture ? (
              <>
                {/* Module Landing Page */}
                <div className="px-6 py-3 border-b border-gray-200 flex items-center justify-between flex-shrink-0">
                  <div className="flex items-center space-x-2 text-sm text-gray-600">
                    <button
                      onClick={() => router.push(`/courses/${courseId}`)}
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
                          const isCompleted = completedLectures.has(lecture.id)
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
                                {isCompleted && (
                                  <svg className="w-5 h-5 text-green-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                                  </svg>
                                )}
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
                      onClick={() => router.push(`/courses/${courseId}`)}
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
                <div className="bg-white flex-shrink-0 flex items-center justify-center relative" style={{ minHeight: '60vh', maxHeight: '80vh' }}>
                  {selectedLecture.contentType === 'VIDEO' ? (() => {
                    // Find video URL from contents array
                    const videoContent = selectedLecture.contents?.find((c: any) => 
                      c.contentType === 'VIDEO' && (c.videoUrl || c.fileUrl)
                    )
                    const videoUrl = videoContent?.videoUrl || videoContent?.fileUrl || selectedLecture.contentUrl
                    
                    return videoUrl ? (
                      <div className="w-full flex items-center justify-center p-4" style={{ maxHeight: '80vh', minHeight: '400px', width: '100%' }}>
                        <div className="w-full max-w-7xl flex items-center justify-center" style={{ maxHeight: '80vh', position: 'relative', width: '100%' }}>
                          <VideoPlayer
                            videoUrl={videoUrl}
                            autoplay={false}
                            className="w-full"
                            logPrefix="VideoPlayer"
                            showAllControls={true}
                          />
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
                                  <MarkdownWithHighlights
                                    content={content.textContent}
                                    notes={notes.filter(n => n.lectureId === selectedLecture.id)}
                                    onAddNote={handleAddNote}
                                    onUpdateNote={handleUpdateNote}
                                    onDeleteNote={handleDeleteNote}
                                  />
                                </div>
                              ))}
                          </div>
                        ) : (
                          <div className="text-center text-gray-400 mt-6">
                            <p>Content is being generated. Please check back soon.</p>
                          </div>
                        )}

                        {/* Attachments Section for TEXT lectures */}
                        {attachments.length > 0 && (
                          <div className="mt-8 pt-6 border-t border-gray-200">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">Attachments</h3>
                            {loadingAttachments ? (
                              <p className="text-sm text-gray-500">Loading attachments...</p>
                            ) : (
                              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                {attachments.map((attachment) => {
                                  const fileUrl = attachment.fileUrl || attachment.videoUrl || attachment.externalUrl
                                  const fileName = attachment.title || `Attachment.${attachment.contentType?.toLowerCase() || 'file'}`
                                  const isPDF = attachment.contentType === 'PDF'
                                  
                                  return (
                                    <div
                                      key={attachment.id}
                                      onClick={() => {
                                        if (fileUrl) {
                                          window.open(fileUrl, '_blank', 'noopener,noreferrer')
                                        }
                                      }}
                                      className="flex items-center justify-between p-3 border border-gray-200 rounded hover:bg-gray-50 cursor-pointer transition-colors"
                                    >
                                      <div className="flex items-center space-x-3 flex-1 min-w-0">
                                        {/* File type icon */}
                                        {attachment.contentType === 'PDF' && (
                                          <svg className="w-5 h-5 text-red-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                            <path fillRule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" clipRule="evenodd" />
                                          </svg>
                                        )}
                                        {attachment.contentType === 'IMAGE' && (
                                          <svg className="w-5 h-5 text-blue-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                          </svg>
                                        )}
                                        {attachment.contentType === 'AUDIO' && (
                                          <svg className="w-5 h-5 text-purple-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                                          </svg>
                                        )}
                                        {!['PDF', 'IMAGE', 'AUDIO'].includes(attachment.contentType || '') && (
                                          <svg className="w-5 h-5 text-gray-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                          </svg>
                                        )}
                                        <span className="text-gray-700 text-sm truncate">{fileName}</span>
                                      </div>
                                      {isPDF ? (
                                        <svg className="w-4 h-4 text-gray-400 flex-shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                        </svg>
                                      ) : (
                                        <svg className="w-4 h-4 text-gray-400 flex-shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                        </svg>
                                      )}
                                    </div>
                                  )
                                })}
                              </div>
                            )}
                          </div>
                        )}

                        {/* Controls at end of TEXT content */}
                        <div className="mt-12 pt-6 border-t border-gray-200">
                          {/* Save note and Report issue */}
                          <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center space-x-4">
                              <button 
                                onClick={() => setShowIssueDialog(true)}
                                className="text-sm text-gray-600 hover:text-gray-900"
                              >
                                Report an issue
                              </button>
                            </div>
                            <div className="flex items-center space-x-2">
                              <button 
                                onClick={() => setShowNotesSidebar(!showNotesSidebar)}
                                className={`px-4 py-2 text-sm font-medium border rounded-md transition-colors ${
                                  showNotesSidebar
                                    ? 'bg-primary-600 text-white border-primary-600'
                                    : 'text-primary-600 hover:text-primary-700 border-primary-600'
                                }`}
                              >
                                {showNotesSidebar ? 'Hide Notes' : `View Notes (${notes.length})`}
                              </button>
                            </div>
                          </div>

                          {/* Engagement Buttons */}
                          <div className="flex items-center space-x-3 mb-4">
                            <button 
                              onClick={() => handleFeedbackClick('LIKE')}
                              className={`flex items-center space-x-2 px-3 py-1.5 rounded-md transition-colors ${
                                currentFeedback?.type === 'LIKE'
                                  ? 'bg-primary-50 text-primary-700'
                                  : 'text-gray-700 hover:bg-gray-100'
                              }`}
                            >
                              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
                              </svg>
                              <span className="text-sm font-medium">Like</span>
                            </button>
                            <button 
                              onClick={() => handleFeedbackClick('DISLIKE')}
                              className={`flex items-center space-x-2 px-3 py-1.5 rounded-md transition-colors ${
                                currentFeedback?.type === 'DISLIKE'
                                  ? 'bg-primary-50 text-primary-700'
                                  : 'text-gray-700 hover:bg-gray-100'
                              }`}
                            >
                              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.736 3h4.018a2 2 0 01.485.06l3.76.94m-7 10v5a2 2 0 002 2h.096c.5 0 .905-.405.905-.904 0-.715.211-1.413.608-2.008L17 13V4m-7 10h2m5-10h2a2 2 0 012 2v6a2 2 0 01-2 2h-2.5" />
                              </svg>
                              <span className="text-sm font-medium">Dislike</span>
                            </button>
                            <button className="flex items-center space-x-2 px-3 py-1.5 text-gray-700 hover:bg-gray-100 rounded-md">
                              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
                              </svg>
                              <span className="text-sm font-medium">Share</span>
                            </button>
                          </div>

                          {/* Mark Complete and Next Button */}
                          <div className="border-t border-gray-200 pt-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 sm:gap-4">
                            <div className="flex items-center space-x-3">
                              <Switch
                                checked={completedLectures.has(selectedLecture.id)}
                                onCheckedChange={(checked) => handleMarkComplete(selectedLecture.id, checked)}
                              />
                              <span 
                                className="text-sm font-medium text-gray-700 cursor-pointer select-none"
                                onClick={() => handleMarkComplete(selectedLecture.id, !completedLectures.has(selectedLecture.id))}
                              >
                                {completedLectures.has(selectedLecture.id) ? 'Completed' : 'Mark as complete'}
                              </span>
                            </div>
                            {nextLecture && (
                              <button
                                onClick={() => handleLectureSelect(nextLecture)}
                                className="w-full sm:w-auto px-6 py-2 bg-primary-600 text-white font-medium rounded-md hover:bg-primary-700 transition-colors flex items-center justify-center space-x-2 sm:ml-auto"
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
                    {/* Text Content Section */}
                    {(selectedLecture.description || (selectedLecture.contents && selectedLecture.contents.some((c: any) => c.contentType === 'TEXT' && c.textContent))) && (
                      <div className="border-t border-gray-200 bg-white">
                        <div className="mx-[36px] px-4 sm:px-6 lg:px-8 py-6">
                          <h2 className="text-2xl sm:text-3xl font-bold text-gray-900 mb-4">{selectedLecture.title}</h2>
                          
                          {/* Lecture Description */}
                          {selectedLecture.description && (
                            <div className="text-gray-700 mb-6 text-base sm:text-lg font-normal leading-relaxed">
                              <MarkdownRenderer content={selectedLecture.description} />
                            </div>
                          )}
                          
                          {/* TEXT Content Items */}
                          {selectedLecture.contents && selectedLecture.contents.length > 0 && (
                            <div className="space-y-6">
                              {selectedLecture.contents
                                .filter((content: any) => content.contentType === 'TEXT' && content.textContent)
                                .map((content: any, index: number) => (
                                  <div key={content.id || index} className="border-t border-gray-100 pt-6 first:border-t-0 first:pt-0">
                                    {content.title && content.title.trim() !== selectedLecture.title.trim() && (
                                      <h3 className="text-xl sm:text-2xl font-bold text-gray-900 mb-3">{content.title}</h3>
                                    )}
                                    <MarkdownWithHighlights
                                      content={content.textContent}
                                      notes={notes.filter(n => n.lectureId === selectedLecture.id)}
                                      onAddNote={handleAddNote}
                                      onUpdateNote={handleUpdateNote}
                                      onDeleteNote={handleDeleteNote}
                                    />
                                  </div>
                                ))}
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                    
                    {/* Tab Section */}
                    <div className="border-t border-gray-200 bg-white pb-20 md:pb-4">
                      <div className="mx-[36px] px-4 sm:px-6 lg:px-8 py-3">
                        {/* Tab Content */}
                        <div className="mb-6">
                        {activeTab === 'transcript' && (
                          <div>
                            <div className="flex justify-between items-center mb-3">
                              <div className="text-sm text-gray-600">0:00</div>
                              <select className="text-sm text-gray-600 border border-gray-300 rounded px-2 py-1">
                                <option>Transcript language: English</option>
                              </select>
                            </div>
                            <div className="text-gray-700">
                              {loadingTranscript ? (
                                <p className="text-sm text-gray-500 mb-3">Loading transcript...</p>
                              ) : transcriptText ? (
                                <div className="whitespace-pre-wrap text-sm leading-relaxed max-h-96 overflow-y-auto">
                                  {transcriptText}
                                </div>
                              ) : (
                                <p className="text-sm text-gray-500 mb-3">
                                  Transcript will be available here once the video transcript is uploaded.
                                </p>
                              )}
                            </div>
                          </div>
                        )}

                        {activeTab === 'notes' && (
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            {/* Notes Section */}
                            <div>
                              <h3 className="text-lg font-semibold text-gray-900 mb-3">Course Notes</h3>
                              <p className="text-gray-700 mb-3 text-sm">
                                Click the &apos;Save Note&apos; button below the lecture when you want to capture a screen. You can also highlight and save lines from the transcript. Add your own notes to anything you&apos;ve captured.
                              </p>
                              <a href="#" className="text-primary-600 hover:text-primary-700 text-sm font-medium flex items-center space-x-1">
                                <span>View all notes</span>
                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                </svg>
                              </a>
                            </div>
                            
                            {/* Attachments Section */}
                            <div>
                              <h3 className="text-lg font-semibold text-gray-900 mb-3">Attachments</h3>
                              {loadingAttachments ? (
                                <p className="text-sm text-gray-500">Loading attachments...</p>
                              ) : attachments.length > 0 ? (
                                <div className="space-y-2">
                                  {attachments.map((attachment) => {
                                    const fileUrl = attachment.fileUrl || attachment.videoUrl || attachment.externalUrl
                                    const fileName = attachment.title || `Attachment.${attachment.contentType?.toLowerCase() || 'file'}`
                                    const isPDF = attachment.contentType === 'PDF'
                                    
                                    return (
                                      <div
                                        key={attachment.id}
                                        onClick={() => {
                                          if (fileUrl) {
                                            window.open(fileUrl, '_blank', 'noopener,noreferrer')
                                          }
                                        }}
                                        className="flex items-center justify-between p-3 border border-gray-200 rounded hover:bg-gray-50 cursor-pointer transition-colors"
                                      >
                                        <div className="flex items-center space-x-3 flex-1 min-w-0">
                                          {/* File type icon */}
                                          {attachment.contentType === 'PDF' && (
                                            <svg className="w-5 h-5 text-red-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                              <path fillRule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" clipRule="evenodd" />
                                            </svg>
                                          )}
                                          {attachment.contentType === 'IMAGE' && (
                                            <svg className="w-5 h-5 text-blue-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                            </svg>
                                          )}
                                          {attachment.contentType === 'AUDIO' && (
                                            <svg className="w-5 h-5 text-purple-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                                            </svg>
                                          )}
                                          {!['PDF', 'IMAGE', 'AUDIO'].includes(attachment.contentType || '') && (
                                            <svg className="w-5 h-5 text-gray-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                            </svg>
                                          )}
                                          <span className="text-gray-700 text-sm truncate">{fileName}</span>
                                        </div>
                                        {isPDF ? (
                                          <svg className="w-4 h-4 text-gray-400 flex-shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                          </svg>
                                        ) : (
                                          <svg className="w-4 h-4 text-gray-400 flex-shrink-0 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                          </svg>
                                        )}
                                      </div>
                                    )
                                  })}
                                </div>
                              ) : (
                                <p className="text-sm text-gray-500">No attachments available for this lecture.</p>
                              )}
                            </div>
                          </div>
                        )}
                      </div>

                    {/* Controls moved to end - Tabs, Save note, Report issue, Engagement buttons */}
                    {/* Tabs */}
                    <div className="border-t border-gray-200 pt-4 mt-6">
                      <div className="border-b border-gray-200 mb-4">
                        <div className="flex space-x-6">
                          <button
                            onClick={() => setActiveTab('transcript')}
                            className={`py-2 px-1 border-b-2 font-medium text-sm ${
                              activeTab === 'transcript'
                                ? 'border-primary-600 text-primary-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                          >
                            Transcript
                          </button>
                          <button
                            onClick={() => setActiveTab('notes')}
                            className={`py-2 px-1 border-b-2 font-medium text-sm ${
                              activeTab === 'notes'
                                ? 'border-primary-600 text-primary-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                          >
                            Notes
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Save note and Report issue */}
                    <div className="flex items-center justify-between pt-4 border-t border-gray-200">
                      <div className="flex items-center space-x-4">
                        <button 
                          onClick={() => setShowIssueDialog(true)}
                          className="text-sm text-gray-600 hover:text-gray-900"
                        >
                          Report an issue
                        </button>
                      </div>
                      <button 
                        onClick={() => setShowNotesSidebar(!showNotesSidebar)}
                        className={`px-4 py-2 text-sm font-medium border rounded-md transition-colors ${
                          showNotesSidebar
                            ? 'bg-primary-600 text-white border-primary-600'
                            : 'text-primary-600 hover:text-primary-700 border-primary-600'
                        }`}
                      >
                        {showNotesSidebar ? 'Hide Notes' : `View Notes (${notes.length})`}
                      </button>
                    </div>

                    {/* Engagement Buttons */}
                    <div className="flex items-center space-x-3 pt-3">
                      <button 
                        onClick={() => handleFeedbackClick('LIKE')}
                        className={`flex items-center space-x-2 px-3 py-1.5 rounded-md transition-colors ${
                          currentFeedback?.type === 'LIKE'
                            ? 'bg-primary-50 text-primary-700'
                            : 'text-gray-700 hover:bg-gray-100'
                        }`}
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
                        </svg>
                        <span className="text-sm font-medium">Like</span>
                      </button>
                      <button 
                        onClick={() => handleFeedbackClick('DISLIKE')}
                        className={`flex items-center space-x-2 px-3 py-1.5 rounded-md transition-colors ${
                          currentFeedback?.type === 'DISLIKE'
                            ? 'bg-primary-50 text-primary-700'
                            : 'text-gray-700 hover:bg-gray-100'
                        }`}
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.736 3h4.018a2 2 0 01.485.06l3.76.94m-7 10v5a2 2 0 002 2h.096c.5 0 .905-.405.905-.904 0-.715.211-1.413.608-2.008L17 13V4m-7 10h2m5-10h2a2 2 0 012 2v6a2 2 0 01-2 2h-2.5" />
                        </svg>
                        <span className="text-sm font-medium">Dislike</span>
                      </button>
                      <button className="flex items-center space-x-2 px-3 py-1.5 text-gray-700 hover:bg-gray-100 rounded-md">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
                        </svg>
                        <span className="text-sm font-medium">Share</span>
                      </button>
                    </div>

                    {/* Mark Complete and Next Button */}
                    <div className="border-t border-gray-200 pt-3 mt-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 sm:gap-4">
                      <div className="flex items-center space-x-3">
                        <Switch
                          checked={completedLectures.has(selectedLecture.id)}
                          onCheckedChange={(checked) => handleMarkComplete(selectedLecture.id, checked)}
                        />
                        <span 
                          className="text-sm font-medium text-gray-700 cursor-pointer select-none"
                          onClick={() => handleMarkComplete(selectedLecture.id, !completedLectures.has(selectedLecture.id))}
                        >
                          {completedLectures.has(selectedLecture.id) ? 'Completed' : 'Mark as complete'}
                        </span>
                      </div>
                      {nextLecture && (
                        <button
                          onClick={() => handleLectureSelect(nextLecture)}
                          className="w-full sm:w-auto px-6 py-2 bg-primary-600 text-white font-medium rounded-md hover:bg-primary-700 transition-colors flex items-center justify-center space-x-2 sm:ml-auto"
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
                  </>
                )}
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-gray-500">
                <p>Select a module or lecture to begin</p>
              </div>
            )}
          </div>

          {/* Floating Chat Button */}
          <button 
            className="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 w-12 h-12 sm:w-14 sm:h-14 bg-primary-600 text-white rounded-full shadow-lg hover:bg-primary-700 flex items-center justify-center z-50 transition-all hover:scale-105"
            aria-label="Open chat"
          >
            <svg className="w-5 h-5 sm:w-6 sm:h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
          </button>
        </div>

        {/* Feedback Dialog */}
        <FeedbackDialog
          isOpen={showFeedbackDialog}
          onClose={() => {
            setShowFeedbackDialog(false)
            setPendingFeedbackType(null)
          }}
          onSubmit={handleFeedbackSubmit}
          initialType={pendingFeedbackType || undefined}
          initialComment={currentFeedback?.comment}
        />

        {/* Issue Report Dialog */}
        <IssueReportDialog
          isOpen={showIssueDialog}
          onClose={() => setShowIssueDialog(false)}
          onSubmit={handleReportIssue}
        />

        {/* Notes Sidebar */}
        <NotesSidebar
          notes={notes.filter(n => selectedLecture?.id === n.lectureId)}
          isOpen={showNotesSidebar}
          onClose={() => setShowNotesSidebar(false)}
          onNoteClick={handleNoteClick}
          onDeleteNote={handleDeleteNote}
          onUpdateNote={handleUpdateNote}
        />

      </StudentLayout>
    </ProtectedRoute>
  )
}
