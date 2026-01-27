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
import { Loader2, Save, CheckCircle, AlertTriangle, Eye } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { ProctoringSetupDialog } from '@/components/exams/ProctoringSetupDialog'
import { WebcamMonitor } from '@/components/exams/WebcamMonitor'
import { proctoringApi } from '@/lib/proctoring-api'

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
  
  // Proctoring state
  const [showProctoringSetup, setShowProctoringSetup] = useState(false)
  const [proctoringComplete, setProctoringComplete] = useState(false)
  const [tabSwitchCount, setTabSwitchCount] = useState(0)
  const [isFullscreen, setIsFullscreen] = useState(false)

  // Helper to log proctoring events
  const logProctoringEvent = useCallback(async (
    eventType: string,
    severity: 'INFO' | 'WARNING' | 'VIOLATION',
    metadata: Record<string, any> = {}
  ) => {
    if (!exam?.enableProctoring || !submissionId) return
    
    // In preview mode, just log to console instead of API
    if (isPreviewMode) {
      console.log('[PREVIEW] Proctoring Event:', {
        eventType,
        severity,
        metadata,
        timestamp: new Date().toISOString()
      })
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
      console.error('Failed to log proctoring event:', err)
    }
  }, [exam, examId, submissionId])

  useEffect(() => {
    loadExam()
  }, [examId])
  
  // Separate effect for visibility change detection (tab switching)
  useEffect(() => {
    if (!exam?.enableProctoring || isPreviewMode) return
    
    const handleVisibilityChange = () => {
      if (document.hidden) {
        // Save when user switches tabs or minimizes
        if (hasUnsavedChangesRef.current && submissionId) {
          saveProgress()
        }
        
        // Log proctoring event for tab switch
        setTabSwitchCount(prev => {
          const newCount = prev + 1
          
          logProctoringEvent('TAB_SWITCH', 'WARNING', {
            count: newCount,
            maxAllowed: exam.maxTabSwitchesAllowed || 3
          })
          
          // Check if max switches exceeded
          if (exam.blockTabSwitch || (exam.maxTabSwitchesAllowed && newCount > exam.maxTabSwitchesAllowed)) {
            logProctoringEvent('PROCTORING_VIOLATION', 'VIOLATION', {
              reason: 'Maximum tab switches exceeded',
              count: newCount,
              maxAllowed: exam.maxTabSwitchesAllowed
            })
            
            // Auto-submit if configured
            if (exam.blockTabSwitch) {
              alert('Tab switching is not allowed. Your exam will be auto-submitted.')
              setTimeout(() => handleSubmit(), 1000)
            } else if (exam.maxTabSwitchesAllowed && newCount >= exam.maxTabSwitchesAllowed) {
              alert(`Warning: You have reached the maximum allowed tab switches (${exam.maxTabSwitchesAllowed}). Additional switches may result in penalties.`)
            }
          }
          
          return newCount
        })
      }
    }
    
    document.addEventListener('visibilitychange', handleVisibilityChange)
    
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [exam?.enableProctoring, exam?.blockTabSwitch, exam?.maxTabSwitchesAllowed, isPreviewMode, submissionId])
  
  // Handle before unload
  useEffect(() => {
    if (isPreviewMode) return
    
    const handleBeforeUnload = async (e: BeforeUnloadEvent) => {
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
          console.error('Failed to save on unload:', error)
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
      alert('Copy action is blocked during this proctored exam')
    }
    
    const handlePaste = (e: ClipboardEvent) => {
      e.preventDefault()
      logProctoringEvent('PASTE_ATTEMPT', 'WARNING')
      alert('Paste action is blocked during this proctored exam')
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

  const loadExam = async () => {
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
      
      // Log proctoring settings for debugging in preview mode
      if (isPreviewMode) {
        console.log('[PREVIEW] Exam loaded with proctoring settings:', {
          enableProctoring: exam.enableProctoring,
          proctoringMode: exam.proctoringMode,
          photoIntervalSeconds: exam.photoIntervalSeconds,
          requireIdentityVerification: exam.requireIdentityVerification,
          blockCopyPaste: exam.blockCopyPaste,
          blockTabSwitch: exam.blockTabSwitch,
          maxTabSwitchesAllowed: exam.maxTabSwitchesAllowed
        })
      }
      
      // Real-time check: if exam has ended, redirect to results
      if ((exam as any).endTime) {
        const endTime = new Date((exam as any).endTime)
        const now = new Date()
        if (now > endTime) {
          console.log('Exam has ended, redirecting to results')
          router.push(`/exams/${examId}/results`)
          return
        }
      }
      
      // Ensure questions is always an array
      const examWithQuestions = {
        ...exam,
        questions: Array.isArray(exam?.questions) ? exam.questions : (exam?.questions ? [exam.questions] : [])
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
            console.error('Failed to extract submission ID from response:', submission)
            console.error('Full response:', response)
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
            console.error('Could not extract submission ID from start exam response')
            throw new Error('Could not extract submission ID from start exam response')
          }
          
          setSubmissionId(submissionIdValue)
          setTimeRemaining(submission?.timeRemainingSeconds || examWithQuestions.timeLimitSeconds || 0)
          
          // Immediately save empty answers to ensure submission exists
          if (submissionIdValue) {
            try {
              await apiClient.post(`/api/student/exams/${examId}/save-progress`, {
                submissionId: submissionIdValue,
                answers: {},
                timeRemainingSeconds: submission?.timeRemainingSeconds || examWithQuestions.timeLimitSeconds || 0
              })
            } catch (saveError) {
              console.error('Failed to do initial save:', saveError)
            }
          }
        } catch (startError: any) {
          console.error('Failed to start exam submission:', startError)
          console.error('Error details:', startError?.response || startError?.message)
          
          // Check if it's a max attempts error (409 Conflict)
          if (startError?.response?.status === 409 || startError?.status === 409) {
            alert('Maximum attempts reached for this exam. You cannot take it again.')
            router.push('/exams')
            return
          }
          
          // Check if exam has ended (403 Forbidden)
          if (startError?.response?.status === 403 || startError?.status === 403) {
            alert('This exam is no longer available.')
            router.push('/exams')
            return
          }
          
          // Don't throw - let the page continue to load even if starting fails
        }
      }

      // Start auto-save only if we have a submission ID
      if (submissionIdValue) {
        startAutoSave()
      } else {
        console.error('No submission ID available, cannot start auto-save')
        console.error('This may prevent auto-save from working. Please refresh the page.')
      }
    } catch (error) {
      console.error('Failed to load exam:', error)
    } finally {
      setLoading(false)
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
        if (now > endTime) {
          console.log('Exam time expired, auto-submitting')
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
      console.error('Failed to save progress:', error)
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
    
    // Final save before submission
    if (hasUnsavedChangesRef.current && submissionId) {
      await saveProgress()
    }

    if (!submissionId) {
      console.error('No submission ID available')
      // Try to create submission if it doesn't exist
      if (!exam) {
        alert('Error: Exam data not loaded. Please refresh the page and try again.')
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
          // Retry submission with new ID
          return handleSubmit()
        }
      } catch (createError) {
        console.error('Failed to create submission:', createError)
      }
      alert('Error: No submission found. Please refresh the page and try again.')
      return
    }

    if (!examId) {
      console.error('No exam ID available')
      alert('Error: Exam ID is missing. Please refresh the page and try again.')
      return
    }

    setSubmitting(true)
    try {
      const response = await apiClient.post(`/api/student/exams/${examId}/submit`, {
        submissionId,
        answers
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
      console.error('Failed to submit exam:', error)
      console.error('Error details:', {
        message: error?.message,
        response: error?.response,
        status: error?.response?.status,
        data: error?.response?.data
      })
      
      // Keep dialog open on error so user can try again or cancel
      const errorMessage = error?.response?.data?.message || 
                          error?.response?.data?.error || 
                          error?.message || 
                          `Failed to submit exam (${error?.response?.status || 'Unknown error'}). Please try again.`
      alert(errorMessage)
    } finally {
      setSubmitting(false)
    }
  }

  const handleTimeUp = () => {
    setShowSubmitDialog(true)
    handleSubmit()
  }
  
  // Request fullscreen mode
  const requestFullscreen = async () => {
    try {
      const elem = document.documentElement
      if (elem.requestFullscreen) {
        await elem.requestFullscreen()
        setIsFullscreen(true)
      }
    } catch (err) {
      console.error('Fullscreen request failed:', err)
    }
  }
  
  // Handle fullscreen change
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement)
      
      // Log if user exits fullscreen during proctored exam
      if (!document.fullscreenElement && exam?.enableProctoring && !isPreviewMode) {
        logProctoringEvent('FULLSCREEN_EXIT', 'WARNING', {
          message: 'Student exited fullscreen mode'
        })
      }
    }
    
    document.addEventListener('fullscreenchange', handleFullscreenChange)
    
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
    }
  }, [exam?.enableProctoring, isPreviewMode])

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

  return (
    <ProtectedRoute>
      <StudentLayout>
        {/* Preview Mode Banner */}
        {isPreviewMode && (
          <div className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white px-6 py-4 shadow-lg border-b-4 border-blue-700">
            <div className="max-w-7xl mx-auto">
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-3">
                  <div className="bg-white/20 p-2 rounded-lg">
                    <Eye className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="font-bold text-xl">Preview Mode - Testing User Flow</div>
                    <div className="text-sm text-blue-100 mt-1">
                      Experience the complete exam flow with dummy questions. Test proctoring settings, interface, and student experience. 
                      <span className="font-semibold"> No responses saved.</span>
                    </div>
                  </div>
                </div>
                <div className="ml-auto flex-shrink-0">
                  <Button
                    variant="outline"
                    size="sm"
                    className="bg-white text-blue-600 hover:bg-blue-50 border-0 font-semibold"
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
             exam.proctoringMode === 'WEBCAM_RECORDING' && 
             submissionId && (
              <WebcamMonitor
                examId={examId}
                submissionId={submissionId}
                photoIntervalSeconds={exam.photoIntervalSeconds || 120}
                isPreview={isPreviewMode}
                onPhotoCapture={(timestamp) => {
                  console.log(isPreviewMode ? '[PREVIEW] Photo simulated at:' : 'Photo captured at:', timestamp)
                }}
                onError={(error) => {
                  console.error('Webcam error:', error)
                }}
              />
            )}
          </>
        )}
        
        <div className="flex h-full min-h-0 bg-white">
          {/* Left Sidebar - Questions Navigation */}
          <div className="w-80 border-r bg-white border-gray-200 overflow-hidden transition-all duration-300 flex-shrink-0 relative">
            <div className="p-4 overflow-y-auto h-full">
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
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                      <div className="text-xs text-blue-600 font-semibold mb-1 text-center">⏱️ TIMER PREVIEW</div>
                      <div className="text-sm text-blue-900 text-center">
                        {Math.floor(exam.timeLimitSeconds / 60)} min {exam.timeLimitSeconds % 60} sec limit
                      </div>
                      <div className="text-xs text-blue-600 text-center mt-1">
                        (Timer active for students)
                      </div>
                    </div>
                  ) : (
                    <ExamTimer
                      timeRemainingSeconds={timeRemaining}
                      onTimeUp={handleTimeUp}
                      onUpdate={(remaining) => setTimeRemaining(remaining)}
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
                          className={`w-full text-left px-3 py-2 rounded text-xs transition-colors relative ${
                            isCurrent
                              ? 'bg-primary-50 text-primary-700'
                              : 'text-gray-600 hover:bg-gray-50'
                          }`}
                        >
                          {isCurrent && (
                            <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary-600 rounded-r"></div>
                          )}
                          <div className="flex items-center space-x-2">
                            <div className={`flex-shrink-0 w-6 h-6 rounded flex items-center justify-center text-xs font-medium ${
                              isCurrent
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
                  <div className="mb-6 bg-blue-50 border-2 border-blue-200 rounded-lg p-4">
                    <div className="flex items-start gap-3">
                      <div className="flex-shrink-0">
                        <div className="bg-blue-100 rounded-full p-2">
                          <Eye className="h-5 w-5 text-blue-600" />
                        </div>
                      </div>
                      <div>
                        <div className="font-semibold text-blue-900 mb-1">
                          Sample Questions for Testing
                        </div>
                        <div className="text-sm text-blue-700">
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
                  <div className="flex items-center gap-2 text-blue-600">
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
      </StudentLayout>
    </ProtectedRoute>
  )
}
