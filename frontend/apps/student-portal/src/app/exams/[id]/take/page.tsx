'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter, useParams, useSearchParams } from 'next/navigation'
import { Button, Label, ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ExamTimer } from '@/components/ExamTimer'
import { StudentLayout } from '@/components/StudentLayout'
import { Loader2, Save, CheckCircle, AlertTriangle, Eye, Clock, ArrowLeft } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { ProctoringSetupDialog } from '@/components/exams/ProctoringSetupDialog'
import { WebcamMonitor } from '@/components/exams/WebcamMonitor'
import { proctoringApi } from '@/lib/proctoring-api'
import { logJourneyEvent, logJourneyEventQuestionNavThrottled, sendJourneyEventBeacon } from '@/lib/journey-api'

interface Exam {
  id: string
  title: string
  instructions?: string
  timeLimitSeconds?: number
  courseId?: string
  questions: Question[]
  enableProctoring?: boolean
  proctoringMode?: 'DISABLED' | 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING'
  photoIntervalSeconds?: number
  requireIdentityVerification?: boolean
  blockCopyPaste?: boolean
  blockTabSwitch?: boolean
  maxTabSwitchesAllowed?: number
  reviewMethod?: 'AI' | 'INSTRUCTOR'
  /** When false, exam is not in its take window; do not show questions. */
  availableForTake?: boolean
  /** Message when availableForTake is false (e.g. "Exam has not begun yet", "Exam has ended"). */
  availabilityMessage?: string
  startTime?: string
  endTime?: string
  /** FIXED_WINDOW = time based on end time; FLEXIBLE_START = time starts when user clicks start */
  timingMode?: 'FIXED_WINDOW' | 'FLEXIBLE_START'
}

interface Question {
  id: string
  questionText: string
  questionType: 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'ESSAY' | 'TRUE_FALSE'
  points: number
  sequence: number
  options?: Option[]
}

interface Option {
  id: string
  optionText: string
  isCorrect: boolean
  sequence: number
}

export const dynamic = 'force-dynamic'

export default function TakeExamPage() {
  const router = useRouter()
  const params = useParams()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const examId = params.id as string
  const isPreviewMode = searchParams.get('preview') === 'true'
  const [exam, setExam] = useState<Exam | null>(null)
  const [submissionId, setSubmissionId] = useState<string | null>(null)
  const [answers, setAnswers] = useState<Record<string, any>>({})
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [timeRemaining, setTimeRemaining] = useState(0)
  const [showSubmitDialog, setShowSubmitDialog] = useState(false)
  const [showLeaveWarning, setShowLeaveWarning] = useState(false)
  const autoSaveIntervalRef = useRef<NodeJS.Timeout | null>(null)
  const debounceSaveRef = useRef<NodeJS.Timeout | null>(null)
  const hasUnsavedChangesRef = useRef(false)
  const lastSaveTimeRef = useRef<number>(0)
  const isSubmittingRef = useRef(false) // Synchronous guard to prevent double submission
  const isLoadingExamRef = useRef(false) // Guard to prevent double loading from React Strict Mode

  // Proctoring state
  const [showProctoringSetup, setShowProctoringSetup] = useState(false)
  const [proctoringComplete, setProctoringComplete] = useState(false)
  const [tabSwitchCount, setTabSwitchCount] = useState(0)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [showTabSwitchWarning, setShowTabSwitchWarning] = useState(false)
  const [tabSwitchWarningData, setTabSwitchWarningData] = useState<{ count: number, remaining: number, isLastWarning: boolean } | null>(null)
  const [showFullscreenWarning, setShowFullscreenWarning] = useState(false) // Dialog when user exits fullscreen
  const isFullscreenTransitionRef = useRef(false) // Flag to ignore visibility changes during fullscreen transitions
  const lastTabSwitchTimeRef = useRef<number>(0) // Debounce tab switches to prevent double counting

  // Log requestFullscreen availability on mount
  useEffect(() => {
  }, [])

  // Helper to log proctoring events
  const logProctoringEvent = useCallback(async (
    eventType: string,
    severity: 'INFO' | 'WARNING' | 'VIOLATION',
    metadata: Record<string, any> = {}
  ) => {
    if (!exam?.enableProctoring || !submissionId) return

    // In preview mode, skip API call
    if (isPreviewMode) {
      return
    }

    try {
      await proctoringApi.logEvent(examId, submissionId, {
        eventType,
        severity,
        metadata: {
          ...metadata,
          timestamp: new Date().toISOString(),
          userAgent: navigator.userAgent
        }
      })
    } catch (err) {
    }
  }, [exam, examId, submissionId])

  useEffect(() => {
    loadExam()
  }, [examId])

  // Separate effect for visibility change detection (tab switching)
  useEffect(() => {
    if (!exam?.enableProctoring) return

    const handleVisibilityChange = () => {
      const isCurrentlyFullscreen = !!document.fullscreenElement

      // Only count tab switches after proctoring setup is complete
      if (!proctoringComplete) {
        return
      }

      // Determine if this is a real tab switch
      // A real tab switch means: document is hidden AND we're NOT in fullscreen
      // If document is hidden but we ARE in fullscreen, it's a false positive from fullscreen transition
      if (document.hidden) {
        if (isCurrentlyFullscreen) {
          // Can't be in fullscreen AND have switched away - this is a false positive
          return
        }
        // Document is hidden AND not in fullscreen - this is a real tab switch
      } else {
        // Document is NOT hidden (user returned or is still on page)

        // Ignore visibility changes during fullscreen transitions when returning
        if (isFullscreenTransitionRef.current) {
          return
        }

        // Also ignore if this is likely a fullscreen toggle (not a real tab switch)
        // When entering/exiting fullscreen, the document might briefly become "hidden"
        // but we're still on the same page
        if (isCurrentlyFullscreen) {
          return
        }
      }

      if (document.hidden) {
        // Debounce: ignore if last tab switch was less than 500ms ago (prevents React Strict Mode double counting)
        const now = Date.now()
        if (now - lastTabSwitchTimeRef.current < 500) {
          return
        }
        lastTabSwitchTimeRef.current = now


        // Save when user switches tabs or minimizes (not in preview mode)
        if (!isPreviewMode && hasUnsavedChangesRef.current && submissionId) {
          saveProgress()
        }
        if (!isPreviewMode && submissionId) {
          logJourneyEvent(examId, submissionId, { eventType: 'PAGE_VISIBILITY_HIDDEN', severity: 'INFO', metadata: { unsavedChanges: hasUnsavedChangesRef.current } })
        }
        // Increment tab switch count directly (not using setState callback to avoid double execution)
        const newCount = tabSwitchCount + 1
        setTabSwitchCount(newCount)

        const maxAllowed = exam.maxTabSwitchesAllowed || 3

        logProctoringEvent('TAB_SWITCH', 'WARNING', {
          count: newCount,
          maxAllowed: maxAllowed
        })

        // Check if max switches exceeded (log only, not in preview)
        if (!isPreviewMode) {
          if (exam.blockTabSwitch) {
            logProctoringEvent('PROCTORING_VIOLATION', 'VIOLATION', {
              reason: 'Tab switching is blocked',
              count: newCount
            })
          } else if (newCount > maxAllowed) {
            logProctoringEvent('PROCTORING_VIOLATION', 'VIOLATION', {
              reason: 'Maximum tab switches exceeded',
              count: newCount,
              maxAllowed: maxAllowed
            })
          }
        }
      } else {
        // User has returned to the tab - show warning with count
        // Use the current tabSwitchCount directly (not callback to avoid double execution)
        if (tabSwitchCount > 0) {
          const maxAllowed = exam.maxTabSwitchesAllowed || 3
          const remaining = maxAllowed - tabSwitchCount


          // Close fullscreen warning if open - tab switch warning takes priority
          setShowFullscreenWarning(false)

          // If blockTabSwitch is enabled, auto-submit immediately (not in preview)
          if (exam.blockTabSwitch) {
            setTabSwitchWarningData({
              count: tabSwitchCount,
              remaining: 0,
              isLastWarning: true
            })
            setShowTabSwitchWarning(true)
            // Auto-submit after showing warning (not in preview mode)
            if (!isPreviewMode && submissionId) {
              logJourneyEvent(examId, submissionId, { eventType: 'AUTO_SUBMIT_TRIGGERED', severity: 'WARNING', metadata: { reason: 'BLOCK_TAB_SWITCH', tabSwitchCount } })
              setTimeout(() => handleSubmit(), 2000)
            }
          } else if (remaining <= 0) {
            // Max switches reached - auto-submit (not in preview)
            setTabSwitchWarningData({
              count: tabSwitchCount,
              remaining: 0,
              isLastWarning: true
            })
            setShowTabSwitchWarning(true)
            // Auto-submit after showing warning (not in preview mode)
            if (!isPreviewMode && submissionId) {
              logJourneyEvent(examId, submissionId, { eventType: 'AUTO_SUBMIT_TRIGGERED', severity: 'WARNING', metadata: { reason: 'TAB_SWITCH_MAX_EXCEEDED', tabSwitchCount, maxAllowed } })
              setTimeout(() => handleSubmit(), 3000)
            }
          } else {
            // Show warning with remaining count
            setTabSwitchWarningData({
              count: tabSwitchCount,
              remaining: remaining,
              isLastWarning: remaining === 1
            })
            setShowTabSwitchWarning(true)
          }
        }
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [exam?.enableProctoring, exam?.blockTabSwitch, exam?.maxTabSwitchesAllowed, isPreviewMode, submissionId, proctoringComplete, tabSwitchCount])

  // Handle before unload
  useEffect(() => {
    if (isPreviewMode) return

    const handleBeforeUnload = async (e: BeforeUnloadEvent) => {
      if (submissionId) {
        sendJourneyEventBeacon(examId, submissionId, {
          eventType: 'PAGE_UNLOAD',
          metadata: { unsavedChanges: hasUnsavedChangesRef.current, reason: 'user_closed_tab' }
        })
      }
      if (hasUnsavedChangesRef.current && submissionId) {
        // Try to save synchronously before leaving
        e.preventDefault()
        e.returnValue = 'Saving your progress...'

        // Use sendBeacon for reliable save on page unload
        try {
          const saveData = JSON.stringify({
            submissionId,
            answers,
            timeRemainingSeconds: timeRemaining
          })
          navigator.sendBeacon(
            `${process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'}/api/student/exams/${examId}/save-progress`,
            new Blob([saveData], { type: 'application/json' })
          )
        } catch (error) {
        }
      }
    }

    window.addEventListener('beforeunload', handleBeforeUnload)

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
      if (autoSaveIntervalRef.current) {
        clearInterval(autoSaveIntervalRef.current)
      }
      if (debounceSaveRef.current) {
        clearTimeout(debounceSaveRef.current)
      }
      // Final save on unmount
      if (hasUnsavedChangesRef.current && submissionId) {
        saveProgress()
      }
    }
  }, [examId, isPreviewMode, submissionId, answers, timeRemaining])

  // Copy/Paste detection
  useEffect(() => {
    if (!exam?.enableProctoring || !exam?.blockCopyPaste) return

    const handleCopy = (e: ClipboardEvent) => {
      e.preventDefault()
      logProctoringEvent('COPY_ATTEMPT', 'WARNING')
      toast({
        title: 'Action Blocked',
        description: 'Copy action is blocked during this proctored exam',
        variant: 'warning'
      })
    }

    const handlePaste = (e: ClipboardEvent) => {
      e.preventDefault()
      logProctoringEvent('PASTE_ATTEMPT', 'WARNING')
      toast({
        title: 'Action Blocked',
        description: 'Paste action is blocked during this proctored exam',
        variant: 'warning'
      })
    }

    document.addEventListener('copy', handleCopy)
    document.addEventListener('paste', handlePaste)

    return () => {
      document.removeEventListener('copy', handleCopy)
      document.removeEventListener('paste', handlePaste)
    }
  }, [exam, logProctoringEvent])

  // Window blur/focus detection
  useEffect(() => {
    if (!exam?.enableProctoring) return

    const handleBlur = () => {
      logProctoringEvent('WINDOW_BLUR', 'WARNING')
    }

    const handleFocus = () => {
      logProctoringEvent('WINDOW_FOCUS', 'INFO')
    }

    window.addEventListener('blur', handleBlur)
    window.addEventListener('focus', handleFocus)

    return () => {
      window.removeEventListener('blur', handleBlur)
      window.removeEventListener('focus', handleFocus)
    }
  }, [exam, logProctoringEvent])

  // Network online/offline for journey log
  useEffect(() => {
    if (!submissionId || isPreviewMode) return
    const handleOnline = () => {
      logJourneyEvent(examId, submissionId, { eventType: 'NETWORK_RESTORED', severity: 'INFO' })
    }
    const handleOffline = () => {
      logJourneyEvent(examId, submissionId, { eventType: 'NETWORK_OFFLINE', severity: 'WARNING' })
    }
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [examId, submissionId, isPreviewMode])

  const loadExam = async () => {
    // Guard against double loading from React Strict Mode
    if (isLoadingExamRef.current) {
      return
    }
    isLoadingExamRef.current = true

    try {
      setLoading(true)

      // Get exam details - use admin API in preview mode to get all proctoring settings
      const endpoint = isPreviewMode
        ? `/api/exams/${examId}` // Admin endpoint with full exam details
        : `/api/student/exams/${examId}` // Student endpoint

      const examResponse = await apiClient.get<Exam>(endpoint)

      // Handle response - apiClient might return data directly or wrapped
      let exam = (examResponse as any)?.data || examResponse
      exam = exam as Exam

      // Real-time check: if exam has ended, redirect to results
      if ((exam as any).endTime) {
        const endTime = new Date((exam as any).endTime)
        const now = new Date()
        if (now > endTime) {
          router.push(`/exams/${examId}/results`)
          return
        }
      }

      // Ensure questions is always an array
      // Handle both: direct array, nested { questions: [...] }, or single question object
      let questionsArray: Question[] = []
      if (Array.isArray(exam?.questions)) {
        questionsArray = exam.questions
      } else if (exam?.questions && typeof exam.questions === 'object') {
        // Check if questions is an object with a nested questions array (e.g., { questions: [...] })
        if (Array.isArray((exam.questions as any).questions)) {
          questionsArray = (exam.questions as any).questions
        } else {
          // Single question object
          questionsArray = [exam.questions as Question]
        }
      }

      // Normalize options for each question (handle nested { options: [...] } structure)
      questionsArray = questionsArray.map(q => {
        let normalizedOptions: Option[] | undefined = undefined
        if (q.options) {
          if (Array.isArray(q.options)) {
            normalizedOptions = q.options
          } else if (typeof q.options === 'object' && Array.isArray((q.options as any).options)) {
            normalizedOptions = (q.options as any).options
          } else if (typeof q.options === 'object') {
            // Single option object
            normalizedOptions = [q.options as Option]
          }
        }
        return { ...q, options: normalizedOptions }
      })

      const examWithQuestions = {
        ...exam,
        questions: questionsArray
      }

      // When exam is not available for take (not begun or ended), show message only; do not load submission or questions
      if (!isPreviewMode && (examWithQuestions as any).availableForTake === false) {
        setExam({
          ...examWithQuestions,
          questions: [],
          availableForTake: false,
          availabilityMessage: (examWithQuestions as any).availabilityMessage || 'Exam is not available.'
        })
        setLoading(false)
        isLoadingExamRef.current = false
        return
      }

      if (examWithQuestions.questions.length === 0) {
      }

      // In preview mode, replace real questions with dummy ones
      if (isPreviewMode) {
        const dummyQuestions: Question[] = [
          {
            id: 'preview-mcq-1',
            questionText: 'This is a sample multiple choice question. What is 2 + 2?',
            questionType: 'MULTIPLE_CHOICE',
            points: 5,
            sequence: 1,
            options: [
              { id: 'opt-1', optionText: '3', isCorrect: false, sequence: 1 },
              { id: 'opt-2', optionText: '4', isCorrect: true, sequence: 2 },
              { id: 'opt-3', optionText: '5', isCorrect: false, sequence: 3 },
              { id: 'opt-4', optionText: '6', isCorrect: false, sequence: 4 }
            ]
          },
          {
            id: 'preview-short-1',
            questionText: 'This is a sample short answer question. What is the capital of France?',
            questionType: 'SHORT_ANSWER',
            points: 3,
            sequence: 2
          },
          {
            id: 'preview-essay-1',
            questionText: 'This is a sample essay question. Describe your learning goals for this course.',
            questionType: 'ESSAY',
            points: 10,
            sequence: 3
          },
          {
            id: 'preview-mcq-2',
            questionText: 'Another sample multiple choice. Which color is the sky?',
            questionType: 'MULTIPLE_CHOICE',
            points: 5,
            sequence: 4,
            options: [
              { id: 'opt-5', optionText: 'Red', isCorrect: false, sequence: 1 },
              { id: 'opt-6', optionText: 'Blue', isCorrect: true, sequence: 2 },
              { id: 'opt-7', optionText: 'Green', isCorrect: false, sequence: 3 }
            ]
          },
          {
            id: 'preview-tf-1',
            questionText: 'Sample true/false question: The Earth is flat.',
            questionType: 'TRUE_FALSE',
            points: 2,
            sequence: 5,
            options: [
              { id: 'opt-8', optionText: 'True', isCorrect: false, sequence: 1 },
              { id: 'opt-9', optionText: 'False', isCorrect: true, sequence: 2 }
            ]
          }
        ]

        const previewExam = {
          ...examWithQuestions,
          questions: dummyQuestions
        }

        setExam(previewExam)

        // Set dummy submission ID for proctoring to work
        setSubmissionId('preview-submission-' + Date.now())

        // Set initial time for display purposes
        if (previewExam.timeLimitSeconds) {
          setTimeRemaining(previewExam.timeLimitSeconds)
        }

        // Show proctoring setup if enabled
        if (previewExam.enableProctoring && !proctoringComplete) {
          setShowProctoringSetup(true)
        } else {
          // No proctoring in preview - request fullscreen immediately
          requestFullscreen()
        }

        setLoading(false)
        isLoadingExamRef.current = false
        return
      }

      setExam(examWithQuestions)

      // Check if proctoring is enabled and show setup dialog
      if (examWithQuestions.enableProctoring && !proctoringComplete) {
        setShowProctoringSetup(true)
      } else {
        // No proctoring - request fullscreen immediately
        requestFullscreen()
      }

      // Check for existing submission or start new one
      let submissionIdValue: string | null = null
      try {
        const response = await apiClient.get<any>(`/api/student/exams/${examId}/submission`)
        // Handle response - apiClient might return data directly or wrapped
        const submission = ((response as any)?.data || response) as any

        submissionIdValue = submission?.id || submission?.submissionId || null
        setSubmissionId(submissionIdValue)

        if (submission?.answersJson) {
          setAnswers(submission.answersJson)
        }
        if (submission?.timeRemainingSeconds !== null && submission?.timeRemainingSeconds !== undefined) {
          setTimeRemaining(submission.timeRemainingSeconds)
        } else if (examWithQuestions.timeLimitSeconds) {
          setTimeRemaining(examWithQuestions.timeLimitSeconds)
        }
      } catch (error: any) {
        // No existing submission (404) or other error, start new exam
        try {
          const response = await apiClient.post<any>(`/api/student/exams/${examId}/start`, {
            courseId: examWithQuestions.courseId,
            timeLimitSeconds: examWithQuestions.timeLimitSeconds
          })
          // Handle response - apiClient might return data directly or wrapped
          const submission = ((response as any)?.data || response) as any

          submissionIdValue = submission?.id || submission?.submissionId || null

          if (!submissionIdValue) {
            // Try alternative extraction methods
            if (submission && typeof submission === 'object') {
              const keys = Object.keys(submission)
              // Try to find ID in any property
              for (const key of keys) {
                if (key.toLowerCase().includes('id') && submission[key]) {
                  submissionIdValue = String(submission[key])
                  break
                }
              }
            }
          }

          if (!submissionIdValue) {
            throw new Error('Could not extract submission ID from start exam response')
          }

          setSubmissionId(submissionIdValue)
          setTimeRemaining(submission?.timeRemainingSeconds || examWithQuestions.timeLimitSeconds || 0)

          // Re-fetch exam details to get randomized question/option order
          // The backend applies randomization based on the submission
          try {
            const refreshedExamResponse = await apiClient.get<Exam>(`/api/student/exams/${examId}`)
            let refreshedExam = (refreshedExamResponse as any)?.data || refreshedExamResponse
            refreshedExam = refreshedExam as Exam

            // Normalize questions array
            let refreshedQuestions: Question[] = []
            if (Array.isArray(refreshedExam?.questions)) {
              refreshedQuestions = refreshedExam.questions
            } else if (refreshedExam?.questions && typeof refreshedExam.questions === 'object') {
              if (Array.isArray((refreshedExam.questions as any).questions)) {
                refreshedQuestions = (refreshedExam.questions as any).questions
              } else {
                refreshedQuestions = [refreshedExam.questions as Question]
              }
            }

            // Normalize options for each question
            refreshedQuestions = refreshedQuestions.map(q => {
              let normalizedOptions: Option[] | undefined = undefined
              if (q.options) {
                if (Array.isArray(q.options)) {
                  normalizedOptions = q.options
                } else if (typeof q.options === 'object' && Array.isArray((q.options as any).options)) {
                  normalizedOptions = (q.options as any).options
                } else if (typeof q.options === 'object') {
                  normalizedOptions = [q.options as Option]
                }
              }
              return { ...q, options: normalizedOptions }
            })

            const refreshedExamWithQuestions = {
              ...refreshedExam,
              questions: refreshedQuestions
            }

            setExam(refreshedExamWithQuestions)
          } catch (refreshError) {
          }

          // Immediately save empty answers to ensure submission exists
          if (submissionIdValue) {
            try {
              await apiClient.post(`/api/student/exams/${examId}/save-progress`, {
                submissionId: submissionIdValue,
                answers: {},
                timeRemainingSeconds: submission?.timeRemainingSeconds || examWithQuestions.timeLimitSeconds || 0
              })
            } catch (saveError) {
            }
          }
        } catch (startError: any) {

          // Check if it's a max attempts error (409 Conflict)
          if (startError?.response?.status === 409 || startError?.status === 409) {
            toast({
              title: 'Maximum Attempts Reached',
              description: 'You cannot take this exam again.',
              variant: 'destructive'
            })
            router.push('/exams')
            return
          }

          // Check if exam not available (403 Forbidden) – show backend message
          if (startError?.response?.status === 403 || startError?.status === 403) {
            const data = startError?.response?.data
            const description = (typeof data?.message === 'string' && data.message)
              ? data.message
              : (typeof data?.error === 'string' ? data.error : null) || 'This exam is not available.'
            toast({
              title: 'Exam Unavailable',
              description,
              variant: 'destructive'
            })
            router.push('/exams')
            return
          }

          // Don't throw - let the page continue to load even if starting fails
        }
      }

      // Start auto-save only if we have a submission ID
      if (submissionIdValue) {
        startAutoSave()
        if (!isPreviewMode) {
          logJourneyEvent(examId, submissionIdValue, { eventType: 'EXAM_PAGE_LOADED', severity: 'INFO' })
          if (examWithQuestions.enableProctoring) {
            logJourneyEvent(examId, submissionIdValue, { eventType: 'PROCTORING_SETUP_STARTED', severity: 'INFO' })
          }
        }
      } else {
      }
    } catch (error) {
    } finally {
      setLoading(false)
      isLoadingExamRef.current = false
    }
  }

  const startAutoSave = () => {
    if (autoSaveIntervalRef.current) {
      clearInterval(autoSaveIntervalRef.current)
    }

    // Auto-save every 15 seconds (more frequent)
    autoSaveIntervalRef.current = setInterval(() => {
      // Check if exam has ended (real-time check every 15s)
      if (exam && (exam as any).endTime) {
        const endTime = new Date((exam as any).endTime)
        const now = new Date()
        if (now > endTime && submissionId) {
          logJourneyEvent(examId, submissionId, { eventType: 'AUTO_SUBMIT_TRIGGERED', severity: 'INFO', metadata: { reason: 'EXAM_END_TIME_PASSED' } })
          handleSubmit()
          return
        }
      }

      if (hasUnsavedChangesRef.current && submissionId) {
        saveProgress()
      }
    }, 15000) // Auto-save every 15 seconds
  }

  const saveProgress = useCallback(async (silent = false) => {
    if (isPreviewMode || !submissionId) {
      return
    }

    // Don't save if we just saved recently (within 2 seconds) to avoid too many requests
    const now = Date.now()
    if (now - lastSaveTimeRef.current < 2000) {
      return
    }

    try {
      if (!silent) {
        setSaving(true)
      }
      lastSaveTimeRef.current = now

      await apiClient.post(`/api/student/exams/${examId}/save-progress`, {
        submissionId,
        answers,
        timeRemainingSeconds: timeRemaining
      })

      hasUnsavedChangesRef.current = false
    } catch (error: any) {
      // Don't clear the unsaved changes flag on error so we can retry
      // The error might be temporary (network issue, etc.)
    } finally {
      if (!silent) {
        setSaving(false)
      }
    }
  }, [submissionId, examId, answers, timeRemaining])

  const handleAnswerChange = (questionId: string, value: any) => {
    setAnswers(prev => ({
      ...prev,
      [questionId]: value
    }))
    hasUnsavedChangesRef.current = true

    // Debounced save: save 2 seconds after user stops typing/changing answers
    if (debounceSaveRef.current) {
      clearTimeout(debounceSaveRef.current)
    }

    debounceSaveRef.current = setTimeout(() => {
      if (submissionId && hasUnsavedChangesRef.current) {
        saveProgress(true) // Silent save (no loading indicator)
      }
    }, 2000) // Save 2 seconds after last change
  }

  const handleSubmit = async () => {
    // Prevent submission in preview mode
    if (isPreviewMode) {
      return
    }

    // Synchronous guard to prevent multiple submission attempts
    if (isSubmittingRef.current) {
      return
    }
    isSubmittingRef.current = true

    // Final save before submission
    if (hasUnsavedChangesRef.current && submissionId) {
      await saveProgress()
    }

    if (!submissionId) {
      // Try to create submission if it doesn't exist
      if (!exam) {
        toast({
          title: 'Error',
          description: 'Exam data not loaded. Please refresh the page and try again.',
          variant: 'destructive'
        })
        isSubmittingRef.current = false
        return
      }
      try {
        const submissionResponse = await apiClient.post<any>(`/api/student/exams/${examId}/start`, {
          courseId: exam.courseId || '',
          timeLimitSeconds: exam.timeLimitSeconds
        })
        // Handle response - apiClient might return data directly or wrapped
        const submission = ((submissionResponse as any)?.data || submissionResponse) as any
        const newSubmissionId = submission?.id || submission?.submissionId
        if (newSubmissionId) {
          setSubmissionId(newSubmissionId)
          // Save answers immediately
          await apiClient.post(`/api/student/exams/${examId}/save-progress`, {
            submissionId: newSubmissionId,
            answers,
            timeRemainingSeconds: timeRemaining
          })
          // Retry submission with new ID (don't reset ref, recursive call)
          return handleSubmit()
        }
      } catch (createError) {
      }
      toast({
        title: 'Error',
        description: 'No submission found. Please refresh the page and try again.',
        variant: 'destructive'
      })
      isSubmittingRef.current = false
      return
    }

    if (!examId) {
      toast({
        title: 'Error',
        description: 'Exam ID is missing. Please refresh the page and try again.',
        variant: 'destructive'
      })
      isSubmittingRef.current = false
      return
    }

    setSubmitting(true)
    if (submissionId && !isPreviewMode) {
      logJourneyEvent(examId, submissionId, { eventType: 'SUBMIT_CLICKED', severity: 'INFO' })
    }
    try {
      const response = await apiClient.post(`/api/student/exams/${examId}/submit`, {
        submissionId,
        answers,
        reviewMethod: exam?.reviewMethod
      })

      // Clear unsaved changes flag
      hasUnsavedChangesRef.current = false

      // Close dialog before navigation
      setShowSubmitDialog(false)

      // Small delay to ensure dialog closes smoothly
      setTimeout(() => {
        router.push(`/exams/${examId}/results`)
      }, 100)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message ||
        error?.response?.data?.error ||
        error?.message || ''

      // Check if exam was already submitted - redirect to results instead of showing error
      const isAlreadySubmitted = errorMessage.toLowerCase().includes('already') &&
        (errorMessage.toLowerCase().includes('submitted') ||
          errorMessage.toLowerCase().includes('completed'))

      if (isAlreadySubmitted) {
        toast({
          title: 'Exam Submitted',
          description: 'Your exam has already been submitted. Redirecting to results...',
        })
        setShowSubmitDialog(false)
        setShowTabSwitchWarning(false)
        setTimeout(() => {
          router.push(`/exams/${examId}/results`)
        }, 1000)
        return
      }

      // Keep dialog open on error so user can try again or cancel
      if (submissionId && !isPreviewMode) {
        logJourneyEvent(examId, submissionId, { eventType: 'SUBMIT_FAILED', severity: 'WARNING', metadata: { error: errorMessage } })
      }
      toast({
        title: 'Submission Failed',
        description: errorMessage || `Failed to submit exam (${error?.response?.status || 'Unknown error'}). Please try again.`,
        variant: 'destructive'
      })
    } finally {
      setSubmitting(false)
      isSubmittingRef.current = false
    }
  }

  const handleTimeUp = () => {
    if (submissionId && !isPreviewMode) {
      logJourneyEvent(examId, submissionId, { eventType: 'AUTO_SUBMIT_TRIGGERED', severity: 'INFO', metadata: { reason: 'TIMER_EXPIRED' } })
    }
    setShowSubmitDialog(true)
    handleSubmit()
  }

  // Request fullscreen mode
  const requestFullscreen = async () => {

    // Set flag to ignore visibility changes during fullscreen transition
    isFullscreenTransitionRef.current = true

    try {
      const elem = document.documentElement

      // Check browser support
      if (!elem.requestFullscreen) {
        toast({
          title: 'Browser Not Supported',
          description: 'Your browser does not support fullscreen mode',
          variant: 'destructive'
        })
        isFullscreenTransitionRef.current = false
        return
      }

      await elem.requestFullscreen()
      setIsFullscreen(true)
      if (submissionId && !isPreviewMode) {
        logJourneyEvent(examId, submissionId, { eventType: 'FULLSCREEN_ENTERED', severity: 'INFO' })
      }
      // Reset the flag after a longer delay to allow the transition to fully complete
      setTimeout(() => {
        isFullscreenTransitionRef.current = false
      }, 1000) // Increased to 1 second
    } catch (err: any) {

      // Reset the flag on error
      isFullscreenTransitionRef.current = false

      // Show user-friendly error
      if (err?.name === 'TypeError' && err?.message?.includes('fullscreen')) {
        toast({
          title: 'Fullscreen Blocked',
          description: 'Please try clicking the "Start Exam" button again.',
          variant: 'warning'
        })
      }
    }
  }

  // Handle fullscreen change
  useEffect(() => {

    const handleFullscreenChange = () => {
      const isNowFullscreen = !!document.fullscreenElement

      // Set transition flag to prevent false tab switch detection during fullscreen changes
      isFullscreenTransitionRef.current = true

      setIsFullscreen(isNowFullscreen)

      // Show warning dialog if user exits fullscreen during proctored exam (after setup is complete)
      if (!isNowFullscreen && exam?.enableProctoring && proctoringComplete) {
        setShowFullscreenWarning(true)

        // Log the event (not in preview mode)
        if (!isPreviewMode) {
          logProctoringEvent('FULLSCREEN_EXIT', 'WARNING', {
            message: 'Student exited fullscreen mode'
          })
          if (submissionId) {
            logJourneyEvent(examId, submissionId, { eventType: 'FULLSCREEN_EXIT', severity: 'WARNING', metadata: { message: 'Student exited fullscreen mode' } })
          }
        }
      }

      // Reset the transition flag after a delay
      setTimeout(() => {
        isFullscreenTransitionRef.current = false
      }, 1000)
    }

    document.addEventListener('fullscreenchange', handleFullscreenChange)

    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
    }
  }, [exam?.enableProctoring, isPreviewMode, proctoringComplete, examId, submissionId])

  // Ensure questions is always an array
  const questions = Array.isArray(exam?.questions) ? exam.questions : []
  const currentQuestion = questions[currentQuestionIndex]
  const answeredCount = Object.keys(answers).length
  const totalQuestions = questions.length

  if (loading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="flex items-center justify-center h-64">
            <Loader2 className="h-8 w-8 animate-spin" />
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!exam) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="text-center py-12">
            <p className="text-gray-500">Exam not found</p>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  // Exam not available for take (not begun or ended) – show message only, no questions
  if (exam.availableForTake === false || (exam.availabilityMessage && (!exam.questions || exam.questions.length === 0))) {
    const message = exam.availabilityMessage || 'Exam is not available.'
    const formatSchedule = (dateStr: string) => {
      const d = new Date(dateStr)
      const date = d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
      const time = d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit', hour12: true })
      return { date, time }
    }
    const startSchedule = exam.startTime ? formatSchedule(exam.startTime) : null
    const endSchedule = exam.endTime ? formatSchedule(exam.endTime) : null
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="max-w-lg mx-auto py-16 px-4">
            <Card className="overflow-hidden border border-gray-200/80 shadow-lg shadow-gray-200/50">
              <div className="bg-amber-50 border-b border-amber-200/60 px-6 py-4">
                <div className="flex items-start gap-4">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-amber-100">
                    <AlertTriangle className="h-5 w-5 text-amber-600" />
                  </div>
                  <div>
                    <h1 className="text-lg font-semibold text-gray-900">{exam.title}</h1>
                    <p className="mt-1 text-base font-medium text-amber-800">{message}</p>
                  </div>
                </div>
              </div>
              <CardContent className="px-6 py-6">
                {(startSchedule || endSchedule) && (
                  <div className="mb-6 rounded-lg bg-gray-50 px-4 py-3">
                    <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-gray-500 mb-2">
                      <Clock className="h-3.5 w-3.5" />
                      Scheduled
                    </div>
                    <div className="grid gap-2 text-sm text-gray-700">
                      {startSchedule && (
                        <p>
                          <span className="text-gray-500">Starts</span>{' '}
                          <span className="font-medium">{startSchedule.date}</span> at {startSchedule.time}
                        </p>
                      )}
                      {endSchedule && (
                        <p>
                          <span className="text-gray-500">Ends</span>{' '}
                          <span className="font-medium">{endSchedule.date}</span> at {endSchedule.time}
                        </p>
                      )}
                    </div>
                  </div>
                )}
                <Button
                  variant="outline"
                  onClick={() => router.push('/exams')}
                  className="w-full sm:w-auto border-gray-300 bg-white font-medium hover:bg-gray-50 hover:border-gray-400"
                >
                  <ArrowLeft className="mr-2 h-4 w-4" />
                  Back to Exams
                </Button>
              </CardContent>
            </Card>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        {/* Preview Mode Banner */}
        {isPreviewMode && (
          <div className="bg-gradient-to-r from-primary-600 to-primary-700 text-white px-6 py-4 shadow-lg border-b-4 border-primary-800">
            <div className="max-w-7xl mx-auto">
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-3">
                  <div className="bg-white/20 p-2 rounded-lg">
                    <Eye className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="font-bold text-xl">Preview Mode - Testing User Flow</div>
                    <div className="text-sm text-primary-100 mt-1">
                      Experience the complete exam flow with dummy questions. Test proctoring settings, interface, and student experience.
                      <span className="font-semibold"> No responses saved.</span>
                    </div>
                  </div>
                </div>
                <div className="ml-auto flex-shrink-0">
                  <Button
                    variant="outline"
                    size="sm"
                    className="bg-white text-primary-600 hover:bg-primary-50 border-0 font-semibold"
                    onClick={() => window.close()}
                  >
                    Close Preview
                  </Button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Proctoring Components */}
        {exam?.enableProctoring && (
          <>
            {/* Proctoring Setup Dialog */}
            <ProctoringSetupDialog
              open={showProctoringSetup}
              examId={examId}
              submissionId={submissionId || ''}
              examTitle={exam.title}
              proctoringMode={(exam.proctoringMode !== 'DISABLED' ? exam.proctoringMode : 'BASIC_MONITORING') || 'BASIC_MONITORING'}
              requireIdentityVerification={exam.requireIdentityVerification || false}
              examSettings={{
                blockCopyPaste: exam.blockCopyPaste,
                blockTabSwitch: exam.blockTabSwitch,
                maxTabSwitchesAllowed: exam.maxTabSwitchesAllowed,
                timeLimitSeconds: exam.timeLimitSeconds,
                instructions: exam.instructions
              }}
              isPreview={isPreviewMode}
              requestFullscreen={requestFullscreen}
              onComplete={() => {
                setShowProctoringSetup(false)
                setProctoringComplete(true)
              }}
              onCancel={() => {
                if (isPreviewMode) {
                  window.close()
                } else {
                  router.push(`/exams/${examId}`)
                }
              }}
            />

            {/* Webcam Monitor (during exam) */}
            {proctoringComplete &&
              (exam.proctoringMode === 'WEBCAM_RECORDING' || exam.proctoringMode === 'LIVE_PROCTORING') &&
              submissionId && (
                <WebcamMonitor
                  examId={examId}
                  submissionId={submissionId}
                  photoIntervalSeconds={exam.photoIntervalSeconds || 120}
                  isPreview={isPreviewMode}
                  onPhotoCapture={(timestamp) => {
                  }}
                  onError={(error) => {
                    toast({
                      title: 'Webcam Error',
                      description: 'Your camera could not be accessed for proctoring. Please check your camera permissions and refresh the page.',
                      variant: 'destructive',
                    })
                    if (submissionId && !isPreviewMode) {
                      logJourneyEvent(examId, submissionId, {
                        eventType: 'WEBCAM_ERROR',
                        severity: 'WARNING',
                        metadata: { error }
                      })
                    }
                  }}
                />
              )}
          </>
        )}

        <div className="flex h-full min-h-0 bg-white">
          {/* Left Sidebar - Questions Navigation */}
          <div className="w-80 border-r bg-white border-gray-200 transition-all duration-300 flex-shrink-0 relative flex flex-col">
            <div className="p-4 flex-1 overflow-y-auto">
              <div className="mb-4">
                <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
                  Exam
                </h2>
                <h3 className="text-sm font-medium text-gray-900 truncate">
                  {exam.title}
                </h3>
              </div>

              {/* Timer */}
              {exam.timeLimitSeconds && (
                <div className="mb-4 pb-4 border-b border-gray-200">
                  {isPreviewMode ? (
                    <div className="bg-primary-50 border border-primary-200 rounded-lg p-3">
                      <div className="text-xs text-primary-600 font-semibold mb-1 text-center">⏱️ TIMER PREVIEW</div>
                      <div className="text-sm text-primary-900 text-center">
                        {Math.floor(exam.timeLimitSeconds / 60)} min {exam.timeLimitSeconds % 60} sec limit
                      </div>
                      <div className="text-xs text-primary-600 text-center mt-1">
                        (Timer active for students)
                      </div>
                    </div>
                  ) : (
                    <ExamTimer
                      timeRemainingSeconds={timeRemaining}
                      onTimeUp={handleTimeUp}
                      onUpdate={(remaining) => setTimeRemaining(remaining)}
                      timingMode={(exam as any).timingMode || 'FIXED_WINDOW'}
                      examEndTime={(exam as any).endTime}
                      examStartedAt={undefined}
                    />
                  )}
                </div>
              )}

              {/* Questions List */}
              <div>
                <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">
                  Questions
                </h4>
                <div className="space-y-1">
                  {questions.length === 0 ? (
                    <p className="text-xs text-gray-500 text-center py-4">No questions available</p>
                  ) : (
                    questions.map((q, index) => {
                      const isAnswered = answers[q.id] !== undefined && answers[q.id] !== ''
                      const isCurrent = index === currentQuestionIndex
                      return (
                        <button
                          key={q.id}
                          onClick={() => setCurrentQuestionIndex(index)}
                          className={`w-full text-left px-3 py-2 rounded text-xs transition-colors relative ${isCurrent
                            ? 'bg-primary-50 text-primary-700'
                            : 'text-gray-600 hover:bg-gray-50'
                            }`}
                        >
                          {isCurrent && (
                            <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary-600 rounded-r"></div>
                          )}
                          <div className="flex items-center space-x-2">
                            <div className={`flex-shrink-0 w-6 h-6 rounded flex items-center justify-center text-xs font-medium ${isCurrent
                              ? 'bg-primary-600 text-white'
                              : isAnswered
                                ? 'bg-green-100 text-green-700'
                                : 'bg-gray-100 text-gray-600'
                              }`}>
                              {index + 1}
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center space-x-2">
                                <span className="font-normal truncate">
                                  Question {index + 1}
                                </span>
                                {isAnswered && (
                                  <CheckCircle className="w-3.5 h-3.5 text-green-600 flex-shrink-0" />
                                )}
                              </div>
                              {q.points && (
                                <div className="text-xs text-gray-400 mt-0.5">
                                  {q.points} point{q.points !== 1 ? 's' : ''}
                                </div>
                              )}
                            </div>
                          </div>
                        </button>
                      )
                    })
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Main Content Area */}
          <div className="flex-1 flex flex-col min-w-0 bg-white overflow-hidden">
            {/* Header */}
            <div className="px-6 py-3 border-b border-gray-200 flex items-center justify-between flex-shrink-0">
              <div className="flex items-center space-x-2 text-sm text-gray-600">
                <button
                  onClick={() => router.push('/exams')}
                  className="hover:text-primary-600 transition-colors"
                >
                  Exams
                </button>
                <span>{'>'}</span>
                <span className="text-gray-900 font-medium">{exam.title}</span>
              </div>
              <div className="flex items-center gap-6">
                {/* Progress Bar */}
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-gray-600">Progress</span>
                    <span className="text-xs font-semibold text-gray-900">
                      {answeredCount} / {totalQuestions}
                    </span>
                  </div>
                  <div className="w-32 bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-primary-600 h-2 rounded-full transition-all"
                      style={{ width: `${totalQuestions > 0 ? (answeredCount / totalQuestions) * 100 : 0}%` }}
                    />
                  </div>
                </div>
                {saving && (
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <Save className="h-4 w-4 animate-pulse" />
                    <span>Saving...</span>
                  </div>
                )}
              </div>
            </div>

            {/* Scrollable Content */}
            <div className="flex-1 bg-white overflow-y-auto pb-24">
              <div className="mx-[36px] p-8 pb-12">
                {/* Preview Mode Question Notice */}
                {isPreviewMode && (
                  <div className="mb-6 bg-primary-50 border-2 border-primary-200 rounded-lg p-4">
                    <div className="flex items-start gap-3">
                      <div className="flex-shrink-0">
                        <div className="bg-primary-100 rounded-full p-2">
                          <Eye className="h-5 w-5 text-primary-600" />
                        </div>
                      </div>
                      <div>
                        <div className="font-semibold text-primary-900 mb-1">
                          Sample Questions for Testing
                        </div>
                        <div className="text-sm text-primary-700">
                          These are placeholder questions to demonstrate the exam flow. Your actual exam questions remain secure and are not shown in preview mode.
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Fullscreen Warning */}
                {!isFullscreen && exam?.enableProctoring && !isPreviewMode && (
                  <div className="mb-6 bg-yellow-50 border-2 border-yellow-300 rounded-lg p-4">
                    <div className="flex items-start gap-3">
                      <div className="flex-shrink-0">
                        <AlertTriangle className="h-5 w-5 text-yellow-600" />
                      </div>
                      <div className="flex-1">
                        <div className="font-semibold text-yellow-900 mb-1">
                          Fullscreen Mode Required
                        </div>
                        <div className="text-sm text-yellow-700 mb-2">
                          This proctored exam must be taken in fullscreen mode. Exiting fullscreen may be logged as suspicious activity.
                        </div>
                        <Button
                          size="sm"
                          onClick={requestFullscreen}
                          className="bg-yellow-600 hover:bg-yellow-700"
                        >
                          Enter Fullscreen
                        </Button>
                      </div>
                    </div>
                  </div>
                )}

                {questions.length === 0 ? (
                  <div className="text-center py-12">
                    <p className="text-gray-500 text-lg">No questions available for this exam</p>
                  </div>
                ) : currentQuestion ? (
                  <div className="space-y-6">
                    {/* Question Header */}
                    <div className="mb-6">
                      <div className="flex items-center justify-between mb-2">
                        <h2 className="text-2xl font-semibold text-gray-900">
                          Question {currentQuestionIndex + 1} of {totalQuestions}
                        </h2>
                        <div className="text-sm text-gray-600">
                          {currentQuestion.points} point{currentQuestion.points !== 1 ? 's' : ''}
                        </div>
                      </div>
                      {exam.instructions && (
                        <p className="text-gray-600 mt-2">{exam.instructions}</p>
                      )}
                    </div>

                    {/* Question Text */}
                    <div className="mb-6">
                      <p className="text-lg font-medium text-gray-900 leading-relaxed">
                        {currentQuestion.questionText}
                      </p>
                    </div>

                    {/* Answer Options */}
                    <div className="space-y-3">
                      {currentQuestion.questionType === 'MULTIPLE_CHOICE' && currentQuestion.options && (
                        <RadioGroup
                          value={answers[currentQuestion.id] || ''}
                          onValueChange={(value) => handleAnswerChange(currentQuestion.id, value)}
                        >
                          {currentQuestion.options.map((option, optIndex) => (
                            <div key={option.id} className="flex items-center space-x-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                              <RadioGroupItem value={option.id} id={option.id} />
                              <Label htmlFor={option.id} className="flex-1 cursor-pointer">
                                <span className="font-medium mr-2 text-gray-700">{String.fromCharCode(65 + optIndex)}.</span>
                                <span className="text-gray-900">{option.optionText}</span>
                              </Label>
                            </div>
                          ))}
                        </RadioGroup>
                      )}

                      {(currentQuestion.questionType === 'SHORT_ANSWER' || currentQuestion.questionType === 'ESSAY') && (
                        <Textarea
                          value={answers[currentQuestion.id] || ''}
                          onChange={(e) => handleAnswerChange(currentQuestion.id, e.target.value)}
                          placeholder="Enter your answer here..."
                          rows={currentQuestion.questionType === 'ESSAY' ? 12 : 6}
                          className="w-full"
                        />
                      )}

                      {currentQuestion.questionType === 'TRUE_FALSE' && (
                        <RadioGroup
                          value={
                            answers[currentQuestion.id] === undefined || answers[currentQuestion.id] === null
                              ? ''
                              : answers[currentQuestion.id] === true || answers[currentQuestion.id] === 'true'
                                ? 'true'
                                : 'false'
                          }
                          onValueChange={(value) => handleAnswerChange(currentQuestion.id, value === 'true')}
                        >
                          <div className="flex items-center space-x-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                            <RadioGroupItem value="true" id={`true-${currentQuestion.id}`} />
                            <Label htmlFor={`true-${currentQuestion.id}`} className="flex-1 cursor-pointer text-gray-900">True</Label>
                          </div>
                          <div className="flex items-center space-x-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                            <RadioGroupItem value="false" id={`false-${currentQuestion.id}`} />
                            <Label htmlFor={`false-${currentQuestion.id}`} className="flex-1 cursor-pointer text-gray-900">False</Label>
                          </div>
                        </RadioGroup>
                      )}
                    </div>

                    {/* Navigation Buttons */}
                    <div className="flex justify-between pt-6 mt-8 mb-8 border-t border-gray-200">
                      <Button
                        variant="outline"
                        onClick={async () => {
                          // Save progress when navigating to previous question
                          if (hasUnsavedChangesRef.current && submissionId) {
                            await saveProgress(true)
                          }
                          const nextIndex = Math.max(0, currentQuestionIndex - 1)
                          if (submissionId && !isPreviewMode) {
                            logJourneyEventQuestionNavThrottled(examId, submissionId, { fromIndex: currentQuestionIndex, toIndex: nextIndex, trigger: 'prev' })
                          }
                          setCurrentQuestionIndex(prev => Math.max(0, prev - 1))
                        }}
                        disabled={currentQuestionIndex === 0}
                      >
                        Previous
                      </Button>
                      <Button
                        onClick={async () => {
                          // Save progress when navigating to next question
                          if (hasUnsavedChangesRef.current && submissionId) {
                            await saveProgress(true)
                          }
                          const nextIndex = currentQuestionIndex < totalQuestions - 1 ? currentQuestionIndex + 1 : currentQuestionIndex
                          if (submissionId && !isPreviewMode) {
                            logJourneyEventQuestionNavThrottled(examId, submissionId, { fromIndex: currentQuestionIndex, toIndex: nextIndex, trigger: currentQuestionIndex < totalQuestions - 1 ? 'next' : 'review' })
                          }
                          if (currentQuestionIndex < totalQuestions - 1) {
                            setCurrentQuestionIndex(prev => prev + 1)
                          } else {
                            setShowSubmitDialog(true)
                          }
                        }}
                      >
                        {currentQuestionIndex < totalQuestions - 1 ? 'Next' : 'Review & Submit'}
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="text-center py-12">
                    <p className="text-gray-500">Question not found</p>
                  </div>
                )}
              </div>
            </div>

            {/* Fixed Footer */}
            <div className="bg-white border-t border-gray-200 px-6 py-4 flex items-center justify-between flex-shrink-0 shadow-sm">
              <div className="flex items-center gap-2 text-sm text-gray-600">
                {saving && !isPreviewMode && (
                  <>
                    <Save className="h-4 w-4 animate-pulse" />
                    <span>Saving...</span>
                  </>
                )}
                {isPreviewMode && (
                  <div className="flex items-center gap-2 text-primary-600">
                    <Eye className="h-4 w-4" />
                    <span className="font-medium">Preview Mode - Answers not saved</span>
                  </div>
                )}
              </div>
              <div className="flex gap-4">
                {!isPreviewMode ? (
                  <>
                    <Button variant="outline" onClick={() => setShowLeaveWarning(true)}>
                      Save & Exit
                    </Button>
                    <Button onClick={() => setShowSubmitDialog(true)}>
                      Submit Exam
                    </Button>
                  </>
                ) : (
                  <Button onClick={() => window.close()}>
                    Close Preview
                  </Button>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Submit Confirmation Dialog */}
        <Dialog open={showSubmitDialog} onOpenChange={(open) => {
          if (!submitting) {
            setShowSubmitDialog(open)
          }
        }}>
          <DialogContent className="bg-white">
            <DialogHeader>
              <DialogTitle>Submit Exam</DialogTitle>
              <DialogDescription>
                Are you sure you want to submit your exam? You will not be able to make changes after submission.
              </DialogDescription>
            </DialogHeader>
            <div className="py-4">
              <p className="text-sm text-gray-600">
                You have answered {answeredCount} out of {totalQuestions} questions.
              </p>
            </div>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setShowSubmitDialog(false)}
                disabled={submitting}
              >
                Cancel
              </Button>
              <Button onClick={handleSubmit} disabled={submitting}>
                {submitting ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Submitting...
                  </>
                ) : (
                  'Submit Exam'
                )}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Leave Warning Dialog */}
        <Dialog open={showLeaveWarning} onOpenChange={setShowLeaveWarning}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Save and Exit</DialogTitle>
              <DialogDescription>
                Your progress will be saved. You can return to complete the exam later.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowLeaveWarning(false)}>
                Cancel
              </Button>
              <Button onClick={async () => {
                await saveProgress()
                router.push('/exams')
              }}>
                Save & Exit
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Tab Switch Warning Dialog */}
        <Dialog open={showTabSwitchWarning} onOpenChange={(open) => {
          // Only allow closing if not auto-submitting (or in preview mode)
          if (!open && (isPreviewMode || (tabSwitchWarningData && tabSwitchWarningData.remaining > 0))) {
            setShowTabSwitchWarning(false)
          }
        }}>
          <DialogContent className={`${tabSwitchWarningData?.remaining === 0 ? 'border-red-500 border-2' : tabSwitchWarningData?.isLastWarning ? 'border-orange-500 border-2' : 'border-yellow-500 border-2'}`}>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <AlertTriangle className={`h-5 w-5 ${tabSwitchWarningData?.remaining === 0 ? 'text-red-600' : tabSwitchWarningData?.isLastWarning ? 'text-orange-600' : 'text-yellow-600'}`} />
                {tabSwitchWarningData?.remaining === 0 ? 'Exam Will Be Auto-Submitted' : 'Tab Switch Detected'}
              </DialogTitle>
            </DialogHeader>
            <div className="py-4 space-y-4">
              {tabSwitchWarningData?.remaining === 0 ? (
                <>
                  <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                    <p className="text-red-800 font-medium">
                      You have switched tabs {tabSwitchWarningData.count} time{tabSwitchWarningData.count !== 1 ? 's' : ''}.
                    </p>
                    <p className="text-red-700 mt-2">
                      {exam?.blockTabSwitch
                        ? 'Tab switching is not allowed during this exam.'
                        : `You have exceeded the maximum allowed tab switches (${exam?.maxTabSwitchesAllowed || 3}).`
                      }
                    </p>
                  </div>
                  {isPreviewMode ? (
                    <div className="bg-primary-50 border border-primary-200 rounded-lg p-3">
                      <p className="text-primary-700 text-sm flex items-center gap-2">
                        <Eye className="h-4 w-4" />
                        <span><strong>Preview Mode:</strong> In a real exam, the exam would be auto-submitted at this point.</span>
                      </p>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 text-red-600">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      <span className="font-medium">Your exam will be automatically submitted...</span>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div className={`${tabSwitchWarningData?.isLastWarning ? 'bg-orange-50 border-orange-200' : 'bg-yellow-50 border-yellow-200'} border rounded-lg p-4`}>
                    <p className={`${tabSwitchWarningData?.isLastWarning ? 'text-orange-800' : 'text-yellow-800'} font-medium`}>
                      You have switched tabs {tabSwitchWarningData?.count} time{tabSwitchWarningData?.count !== 1 ? 's' : ''}.
                    </p>
                    <p className={`${tabSwitchWarningData?.isLastWarning ? 'text-orange-700' : 'text-yellow-700'} mt-2`}>
                      {tabSwitchWarningData?.remaining === 1 ? (
                        <span className="font-semibold">⚠️ This is your last warning! If you switch tabs again, your exam will be automatically submitted.</span>
                      ) : (
                        <>You can switch tabs <span className="font-semibold">{tabSwitchWarningData?.remaining} more time{tabSwitchWarningData?.remaining !== 1 ? 's' : ''}</span> before your exam is automatically submitted.</>
                      )}
                    </p>
                  </div>
                  <p className="text-sm text-gray-600">
                    Please stay on this page to complete your exam. Switching tabs or windows is monitored as part of the proctoring process.
                  </p>
                </>
              )}
            </div>
            {(tabSwitchWarningData?.remaining !== 0 || isPreviewMode) && (
              <DialogFooter>
                <Button onClick={() => {
                  setShowTabSwitchWarning(false)
                  // Request fullscreen again when user confirms
                  if (exam?.enableProctoring && !document.fullscreenElement) {
                    requestFullscreen()
                  }
                }}>
                  {isPreviewMode && tabSwitchWarningData?.remaining === 0 ? 'Dismiss' : 'I Understand, Continue Exam'}
                </Button>
              </DialogFooter>
            )}
          </DialogContent>
        </Dialog>

        {/* Fullscreen Exit Warning Dialog - Blocks exam until user re-enters fullscreen */}
        <Dialog open={showFullscreenWarning} onOpenChange={() => {
          // Don't allow closing by clicking outside - user must click the button
        }}>
          <DialogContent className="border-primary-500 border-2" onPointerDownOutside={(e) => e.preventDefault()}>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-primary-600" />
                Fullscreen Mode Required
              </DialogTitle>
            </DialogHeader>
            <div className="py-4 space-y-4">
              <div className="bg-primary-50 border border-primary-200 rounded-lg p-4">
                <p className="text-primary-800 font-medium">
                  You have exited fullscreen mode.
                </p>
                <p className="text-primary-700 mt-2">
                  This proctored exam must be taken in fullscreen mode. Please click the button below to continue your exam.
                </p>
              </div>
              {isPreviewMode && (
                <div className="bg-primary-50 border border-primary-200 rounded-lg p-3">
                  <p className="text-primary-700 text-sm flex items-center gap-2">
                    <Eye className="h-4 w-4" />
                    <span><strong>Preview Mode:</strong> Testing fullscreen requirement behavior.</span>
                  </p>
                </div>
              )}
            </div>
            <DialogFooter>
              <Button
                onClick={async () => {
                  // Request fullscreen first (needs user gesture), then close dialog
                  try {
                    const elem = document.documentElement
                    if (elem.requestFullscreen) {
                      isFullscreenTransitionRef.current = true
                      await elem.requestFullscreen()
                      setIsFullscreen(true)
                      setTimeout(() => {
                        isFullscreenTransitionRef.current = false
                      }, 1000)
                    }
                  } catch (err) {
                  }
                  setShowFullscreenWarning(false)
                }}
                className="w-full"
              >
                Return to Fullscreen
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </StudentLayout>
    </ProtectedRoute>
  )
}
