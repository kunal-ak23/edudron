'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter, useParams } from 'next/navigation'
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
import { Loader2, Save, CheckCircle, AlertTriangle } from 'lucide-react'
import { apiClient } from '@/lib/api'

interface Exam {
  id: string
  title: string
  instructions?: string
  timeLimitSeconds?: number
  courseId?: string
  questions: Question[]
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
  const examId = params.id as string
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

  useEffect(() => {
    loadExam()
    
    // Save progress before leaving
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
    
    // Also save on visibility change (tab switch, minimize, etc.)
    const handleVisibilityChange = () => {
      if (document.hidden && hasUnsavedChangesRef.current && submissionId) {
        // Save when user switches tabs or minimizes
        saveProgress()
      }
    }
    
    window.addEventListener('beforeunload', handleBeforeUnload)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
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
  }, [examId])

  const loadExam = async () => {
    try {
      setLoading(true)
      
      // Get exam details
      const examData = await apiClient.get<Exam>(`/api/student/exams/${examId}`)
      console.log('Exam data received:', examData)
      console.log('Exam questions:', examData?.questions)
      console.log('Questions type:', typeof examData?.questions)
      console.log('Is array?', Array.isArray(examData?.questions))
      
      // Handle response - apiClient might return data directly or wrapped
      let exam = examData
      if (examData && typeof examData === 'object' && 'data' in examData && !('id' in examData)) {
        // Response is wrapped in { data: {...} }
        exam = (examData as any).data
        console.log('Unwrapped exam data:', exam)
      }
      
      // Ensure questions is always an array
      const examWithQuestions = {
        ...exam,
        questions: Array.isArray(exam?.questions) ? exam.questions : (exam?.questions ? [exam.questions] : [])
      }
      console.log('Exam with questions:', examWithQuestions)
      console.log('Questions count:', examWithQuestions.questions.length)
      
      if (examWithQuestions.questions.length === 0) {
        console.warn('⚠️ No questions found for exam:', examId)
        console.warn('Exam data keys:', Object.keys(exam || {}))
      }
      
      setExam(examWithQuestions)

      // Check for existing submission or start new one
      let submissionIdValue: string | null = null
      try {
        const response = await apiClient.get<any>(`/api/student/exams/${examId}/submission`)
        // Handle response - apiClient might return data directly or wrapped
        let submission = response
        if (response && typeof response === 'object' && 'data' in response && !('id' in response)) {
          // Response is wrapped in { data: {...} }
          submission = (response as any).data
        }
        
        console.log('Submission response:', response)
        console.log('Extracted submission:', submission)
        
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
        console.log('Loaded existing submission:', submissionIdValue)
      } catch (error: any) {
        // No existing submission (404) or other error, start new exam
        console.log('No existing submission found (or error fetching), creating new one...', error?.status || error?.message)
        try {
          const response = await apiClient.post<any>(`/api/student/exams/${examId}/start`, {
            courseId: examWithQuestions.courseId,
            timeLimitSeconds: examWithQuestions.timeLimitSeconds
          })
          // Handle response - apiClient might return data directly or wrapped
          let submission = response
          if (response && typeof response === 'object' && 'data' in response && !('id' in response)) {
            // Response is wrapped in { data: {...} }
            submission = (response as any).data
          }
          
          console.log('Start exam response:', response)
          console.log('Extracted submission:', submission)
          
          submissionIdValue = submission?.id || submission?.submissionId || null
          
          if (!submissionIdValue) {
            console.error('Failed to extract submission ID from response:', submission)
            console.error('Full response:', response)
            // Try alternative extraction methods
            if (submission && typeof submission === 'object') {
              const keys = Object.keys(submission)
              console.log('Submission object keys:', keys)
              // Try to find ID in any property
              for (const key of keys) {
                if (key.toLowerCase().includes('id') && submission[key]) {
                  submissionIdValue = String(submission[key])
                  console.log(`Found ID in property ${key}:`, submissionIdValue)
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
          console.log('Created new submission:', submissionIdValue)
          
          // Immediately save empty answers to ensure submission exists
          if (submissionIdValue) {
            try {
              await apiClient.post(`/api/student/exams/${examId}/save-progress`, {
                submissionId: submissionIdValue,
                answers: {},
                timeRemainingSeconds: submission?.timeRemainingSeconds || examWithQuestions.timeLimitSeconds || 0
              })
              console.log('Initial save completed')
            } catch (saveError) {
              console.error('Failed to do initial save:', saveError)
            }
          }
        } catch (startError: any) {
          console.error('Failed to start exam submission:', startError)
          console.error('Error details:', startError?.response || startError?.message)
          // Don't throw - let the page continue to load even if starting fails
        }
      }

      // Start auto-save only if we have a submission ID
      if (submissionIdValue) {
        console.log('Starting auto-save with submission ID:', submissionIdValue)
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
      if (hasUnsavedChangesRef.current && submissionId) {
        console.log('Auto-saving progress...')
        saveProgress()
      }
    }, 15000) // Auto-save every 15 seconds
  }

  const saveProgress = useCallback(async (silent = false) => {
    if (!submissionId) {
      console.warn('Cannot save progress: no submission ID')
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
      console.log('Progress saved successfully')
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
        console.log('Debounced save triggered')
        saveProgress(true) // Silent save (no loading indicator)
      }
    }, 2000) // Save 2 seconds after last change
  }

  const handleSubmit = async () => {
    // Final save before submission
    if (hasUnsavedChangesRef.current && submissionId) {
      console.log('Performing final save before submission...')
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
        console.log('Attempting to create submission...')
        const submission = await apiClient.post<any>(`/api/student/exams/${examId}/start`, {
          courseId: exam.courseId || '',
          timeLimitSeconds: exam.timeLimitSeconds
        })
        const newSubmissionId = submission.id || submission.submissionId
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
      console.log('Submitting exam:', { examId, submissionId, answersCount: Object.keys(answers).length })
      
      const response = await apiClient.post(`/api/student/exams/${examId}/submit`, {
        submissionId,
        answers
      })
      
      console.log('Exam submitted successfully:', response)
      
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
                  <ExamTimer
                    timeRemainingSeconds={timeRemaining}
                    onTimeUp={handleTimeUp}
                    onUpdate={(remaining) => setTimeRemaining(remaining)}
                  />
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
                {saving && (
                  <>
                    <Save className="h-4 w-4 animate-pulse" />
                    <span>Saving...</span>
                  </>
                )}
              </div>
              <div className="flex gap-4">
                <Button variant="outline" onClick={() => setShowLeaveWarning(true)}>
                  Save & Exit
                </Button>
                <Button onClick={() => setShowSubmitDialog(true)}>
                  Submit Exam
                </Button>
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
