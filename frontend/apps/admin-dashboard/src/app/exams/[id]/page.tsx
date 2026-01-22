'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ArrowLeft, Loader2, Calendar, Clock, Users, Edit, Save, Sparkles, Eye, Plus, Trash2 } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, questionsApi, type QuestionData } from '@/lib/api'
import { QuestionEditor } from '@/components/exams/QuestionEditor'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

interface Exam {
  id: string
  title: string
  description?: string
  instructions?: string
  status: 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'COMPLETED'
  startTime?: string
  endTime?: string
  reviewMethod: 'INSTRUCTOR' | 'AI' | 'BOTH'
  moduleIds: string[]
  questions?: Question[]
  courseId: string
}

interface Question {
  id: string
  questionText: string
  questionType: 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'ESSAY' | 'TRUE_FALSE'
  points: number
  sequence: number
  tentativeAnswer?: string
  editedTentativeAnswer?: string
  useTentativeAnswerForGrading: boolean
  options?: Option[]
}

interface Option {
  id: string
  optionText: string
  isCorrect: boolean
  sequence: number
}

export const dynamic = 'force-dynamic'

export default function ExamDetailPage() {
  const router = useRouter()
  const params = useParams()
  const examId = params.id as string
  const { toast } = useToast()
  const { user } = useAuth()
  const canUseAI = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'
  const [exam, setExam] = useState<Exam | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(false)
  const [showScheduleDialog, setShowScheduleDialog] = useState(false)
  const [scheduleData, setScheduleData] = useState({
    startTime: '',
    endTime: ''
  })
  const [editingQuestion, setEditingQuestion] = useState<Question | null>(null)
  const [showTentativeAnswerDialog, setShowTentativeAnswerDialog] = useState(false)
  const [showQuestionEditor, setShowQuestionEditor] = useState(false)
  const [isCreatingQuestion, setIsCreatingQuestion] = useState(false)

  const loadExam = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apiClient.get<any>(`/api/exams/${examId}`)
      // Handle response - apiClient might return data directly or wrapped
      let data = response
      if (response && typeof response === 'object' && 'data' in response && !('id' in response)) {
        // Response is wrapped in { data: {...} }
        data = (response as any).data
      }
      setExam(data as unknown as Exam)
    } catch (error) {
      console.error('Failed to load exam:', error)
      toast({
        title: 'Error',
        description: 'Failed to load exam',
        variant: 'destructive'
      })
    } finally {
      setLoading(false)
    }
  }, [examId, toast])

  useEffect(() => {
    loadExam()
  }, [examId, loadExam])

  const handleSchedule = async () => {
    if (!scheduleData.startTime || !scheduleData.endTime) {
      toast({
        title: 'Validation Error',
        description: 'Please provide both start and end times',
        variant: 'destructive'
      })
      return
    }

    setSaving(true)
    try {
      // Convert datetime-local values to ISO 8601 format with timezone
      // This preserves the user's local timezone so students in different timezones see correct times
      const startTimeISO = convertToISOWithTimezone(scheduleData.startTime)
      const endTimeISO = convertToISOWithTimezone(scheduleData.endTime)
      
      const response = await apiClient.put<any>(`/api/exams/${examId}/schedule`, {
        startTime: startTimeISO,
        endTime: endTimeISO
      })
      // Handle response - apiClient might return data directly or wrapped
      let updated = response
      if (response && typeof response === 'object' && 'data' in response && !('id' in response)) {
        // Response is wrapped in { data: {...} }
        updated = (response as any).data
      }
      setExam(updated as unknown as Exam)
      setShowScheduleDialog(false)
      const examStatus = (updated as any)?.status || 'DRAFT'
      toast({
        title: 'Success',
        description: examStatus === 'SCHEDULED' 
          ? 'Exam schedule updated successfully'
          : 'Exam scheduled successfully'
      })
    } catch (error) {
      console.error('Failed to schedule exam:', error)
      toast({
        title: 'Error',
        description: 'Failed to schedule exam',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleUpdateTentativeAnswer = async (questionId: string, editedAnswer: string) => {
    setSaving(true)
    try {
      await apiClient.put(`/api/exams/${examId}/questions/${questionId}/tentative-answer`, {
        editedTentativeAnswer: editedAnswer
      })
      await loadExam()
      setShowTentativeAnswerDialog(false)
      setEditingQuestion(null)
      toast({
        title: 'Success',
        description: 'Tentative answer updated successfully'
      })
    } catch (error) {
      console.error('Failed to update tentative answer:', error)
      toast({
        title: 'Error',
        description: 'Failed to update tentative answer',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  // Helper function to convert datetime-local string to ISO 8601 with timezone
  // datetime-local gives "YYYY-MM-DDTHH:mm" in user's local timezone
  const convertToISOWithTimezone = (datetimeLocal: string): string => {
    // Create a Date object from the datetime-local string (interpreted as local time)
    const date = new Date(datetimeLocal)
    
    // Get timezone offset in minutes (negative means ahead of UTC)
    const offsetMinutes = -date.getTimezoneOffset()
    const offsetHours = Math.floor(Math.abs(offsetMinutes) / 60)
    const offsetMins = Math.abs(offsetMinutes) % 60
    const sign = offsetMinutes >= 0 ? '+' : '-'
    const offsetString = `${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMins).padStart(2, '0')}`
    
    // Format as ISO 8601: YYYY-MM-DDTHH:mm:ss+HH:mm
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${offsetString}`
  }

  const getStatusBadge = (status: string) => {
    const variants: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
      DRAFT: 'outline',
      SCHEDULED: 'secondary',
      LIVE: 'default',
      COMPLETED: 'outline'
    }
    
    return (
      <Badge variant={variants[status] || 'outline'}>
        {status}
      </Badge>
    )
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    )
  }

  if (!exam) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500">Exam not found</p>
      </div>
    )
  }

  // Ensure questions is always an array
  const questions = Array.isArray(exam.questions) ? exam.questions : []

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" onClick={() => router.back()}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <div className="flex items-center gap-3">
            {getStatusBadge(exam.status)}
          </div>
        </div>
        <div className="flex gap-2">
          {(exam.status === 'DRAFT' || exam.status === 'SCHEDULED') && (
            <Button onClick={() => {
              // Pre-populate with existing times if rescheduling
              if (exam.startTime && exam.endTime) {
                // Convert ISO datetime to datetime-local format
                const startDate = new Date(exam.startTime)
                const endDate = new Date(exam.endTime)
                const startLocal = new Date(startDate.getTime() - startDate.getTimezoneOffset() * 60000)
                  .toISOString().slice(0, 16)
                const endLocal = new Date(endDate.getTime() - endDate.getTimezoneOffset() * 60000)
                  .toISOString().slice(0, 16)
                setScheduleData({
                  startTime: startLocal,
                  endTime: endLocal
                })
              } else {
                setScheduleData({ startTime: '', endTime: '' })
              }
              setShowScheduleDialog(true)
            }}>
              <Calendar className="h-4 w-4 mr-2" />
              {exam.status === 'SCHEDULED' ? 'Reschedule Exam' : 'Schedule Exam'}
            </Button>
          )}
          <Button variant="outline" onClick={() => setEditing(!editing)}>
            <Edit className="h-4 w-4 mr-2" />
            {editing ? 'Cancel Edit' : 'Edit'}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="details" className="space-y-4">
          <TabsList>
            <TabsTrigger value="details">Details</TabsTrigger>
            <TabsTrigger value="questions">Questions ({questions.length})</TabsTrigger>
            <TabsTrigger value="submissions">Submissions</TabsTrigger>
          </TabsList>

          <TabsContent value="details" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Exam Information</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Status</Label>
                    <div className="mt-1">{getStatusBadge(exam.status)}</div>
                  </div>
                  <div>
                    <Label>Review Method</Label>
                    <div className="mt-1 font-medium">{exam.reviewMethod}</div>
                  </div>
                  {exam.startTime && (
                    <div>
                      <Label>Start Time</Label>
                      <div className="mt-1 font-medium">
                        {new Date(exam.startTime).toLocaleString()}
                      </div>
                    </div>
                  )}
                  {exam.endTime && (
                    <div>
                      <Label>End Time</Label>
                      <div className="mt-1 font-medium">
                        {new Date(exam.endTime).toLocaleString()}
                      </div>
                    </div>
                  )}
                </div>
                {exam.instructions && (
                  <div>
                    <Label>Instructions</Label>
                    <div className="mt-1 text-gray-700 whitespace-pre-wrap">
                      {exam.instructions}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="questions" className="space-y-4">
            {exam.status === 'DRAFT' && (
              <div className="flex justify-end">
                <Button onClick={() => {
                  setIsCreatingQuestion(true)
                  setEditingQuestion(null)
                  setShowQuestionEditor(true)
                }}>
                  <Plus className="h-4 w-4 mr-2" />
                  Add Question
                </Button>
              </div>
            )}
            {questions.length > 0 ? (
              <div className="space-y-4">
                {questions.map((question, index) => (
                  <Card key={question.id}>
                    <CardHeader>
                      <div className="flex items-start justify-between">
                        <CardTitle className="text-lg">
                          Question {index + 1} ({question.points} points)
                        </CardTitle>
                        <div className="flex items-center gap-2">
                          <Badge variant="outline">{question.questionType}</Badge>
                          {exam.status === 'DRAFT' && (
                            <>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => {
                                  setEditingQuestion(question)
                                  setIsCreatingQuestion(false)
                                  setShowQuestionEditor(true)
                                }}
                              >
                                <Edit className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={async () => {
                                  if (confirm('Are you sure you want to delete this question?')) {
                                    try {
                                      await questionsApi.delete(examId, question.id)
                                      await loadExam()
                                      toast({
                                        title: 'Success',
                                        description: 'Question deleted successfully'
                                      })
                                    } catch (error) {
                                      console.error('Failed to delete question:', error)
                                      toast({
                                        title: 'Error',
                                        description: 'Failed to delete question',
                                        variant: 'destructive'
                                      })
                                    }
                                  }
                                }}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </>
                          )}
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div>
                        <p className="font-medium">{question.questionText}</p>
                      </div>

                      {question.questionType === 'MULTIPLE_CHOICE' && question.options && (
                        <div className="space-y-2">
                          {question.options.map((option, optIndex) => (
                            <div
                              key={option.id}
                              className={`p-2 rounded border ${
                                option.isCorrect ? 'bg-green-50 border-green-200' : 'bg-gray-50'
                              }`}
                            >
                              <div className="flex items-center gap-2">
                                <span className="font-medium">{String.fromCharCode(65 + optIndex)}.</span>
                                <span>{option.optionText}</span>
                                {option.isCorrect && (
                                  <Badge variant="default" className="ml-auto">Correct</Badge>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {(question.questionType === 'SHORT_ANSWER' || question.questionType === 'ESSAY') && (
                        <div className="space-y-2">
                          <div>
                            <Label className="text-sm font-medium">Tentative Answer</Label>
                            <div className="mt-1 p-3 bg-gray-50 rounded border">
                              {question.editedTentativeAnswer || question.tentativeAnswer || 
                               'No tentative answer available'}
                            </div>
                          </div>
                          {exam.status === 'DRAFT' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => {
                                setEditingQuestion(question)
                                setShowTentativeAnswerDialog(true)
                              }}
                            >
                              <Edit className="h-4 w-4 mr-2" />
                              Edit Tentative Answer
                            </Button>
                          )}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : (
              <Card>
                <CardContent className="py-12 text-center">
                  <p className="text-gray-500 mb-4">No questions added yet</p>
                  <div className="flex gap-2 justify-center">
                    <Button onClick={() => {
                      setIsCreatingQuestion(true)
                      setEditingQuestion(null)
                      setShowQuestionEditor(true)
                    }}>
                      <Plus className="h-4 w-4 mr-2" />
                      Add Question Manually
                    </Button>
                    {canUseAI && exam.moduleIds && exam.moduleIds.length > 0 && (
                      <Button onClick={async () => {
                        setSaving(true)
                        try {
                          await apiClient.post(`/api/exams/${examId}/generate`, {
                            numberOfQuestions: 10,
                            difficulty: 'INTERMEDIATE'
                          })
                          await loadExam()
                          toast({
                            title: 'Success',
                            description: 'Questions generated successfully'
                          })
                        } catch (error) {
                          console.error('Failed to generate questions:', error)
                          toast({
                            title: 'Error',
                            description: 'Failed to generate questions',
                            variant: 'destructive'
                          })
                        } finally {
                          setSaving(false)
                        }
                      }} disabled={saving} variant="outline">
                        {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : 
                         <Sparkles className="h-4 w-4 mr-2" />}
                        Generate Questions with AI
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            )}
          </TabsContent>

          <TabsContent value="submissions">
            <SubmissionsList examId={examId} questions={questions} />
          </TabsContent>
        </Tabs>

        {/* Schedule Dialog */}
        <Dialog open={showScheduleDialog} onOpenChange={setShowScheduleDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>
                {exam.status === 'SCHEDULED' ? 'Reschedule Exam' : 'Schedule Exam'}
              </DialogTitle>
              <DialogDescription>
                {exam.status === 'SCHEDULED' 
                  ? 'Update the start and end time for this exam'
                  : 'Set the start and end time for this exam'}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div>
                <Label htmlFor="startTime">Start Time</Label>
                <Input
                  id="startTime"
                  type="datetime-local"
                  value={scheduleData.startTime}
                  onChange={(e) => setScheduleData(prev => ({ ...prev, startTime: e.target.value }))}
                />
              </div>
              <div>
                <Label htmlFor="endTime">End Time</Label>
                <Input
                  id="endTime"
                  type="datetime-local"
                  value={scheduleData.endTime}
                  onChange={(e) => setScheduleData(prev => ({ ...prev, endTime: e.target.value }))}
                />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowScheduleDialog(false)}>
                Cancel
              </Button>
              <Button onClick={handleSchedule} disabled={saving}>
                {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                {exam.status === 'SCHEDULED' ? 'Update Schedule' : 'Schedule'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Tentative Answer Edit Dialog */}
        <Dialog open={showTentativeAnswerDialog} onOpenChange={setShowTentativeAnswerDialog}>
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>Edit Tentative Answer</DialogTitle>
              <DialogDescription>
                Edit the expected answer for this question
              </DialogDescription>
            </DialogHeader>
            {editingQuestion && (
              <div className="space-y-4">
                <div>
                  <Label>Question</Label>
                  <p className="mt-1 text-sm text-gray-700">{editingQuestion.questionText}</p>
                </div>
                <div>
                  <Label htmlFor="tentativeAnswer">Tentative Answer</Label>
                  <Textarea
                    id="tentativeAnswer"
                    rows={10}
                    defaultValue={editingQuestion.editedTentativeAnswer || editingQuestion.tentativeAnswer || ''}
                    onChange={(e) => {
                      // Store in state for submission
                      setEditingQuestion(prev => prev ? {
                        ...prev,
                        editedTentativeAnswer: e.target.value
                      } : null)
                    }}
                  />
                </div>
              </div>
            )}
            <DialogFooter>
              <Button variant="outline" onClick={() => {
                setShowTentativeAnswerDialog(false)
                setEditingQuestion(null)
              }}>
                Cancel
              </Button>
              <Button onClick={() => {
                if (editingQuestion) {
                  handleUpdateTentativeAnswer(
                    editingQuestion.id,
                    editingQuestion.editedTentativeAnswer || ''
                  )
                }
              }} disabled={saving}>
                {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                Save
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Question Editor Dialog */}
        <Dialog open={showQuestionEditor} onOpenChange={setShowQuestionEditor}>
          <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
            <QuestionEditor
              question={editingQuestion ? {
                id: editingQuestion.id,
                questionType: editingQuestion.questionType,
                questionText: editingQuestion.questionText,
                points: editingQuestion.points,
                options: editingQuestion.options,
                tentativeAnswer: editingQuestion.editedTentativeAnswer || editingQuestion.tentativeAnswer
              } : undefined}
              onSave={async (questionData: QuestionData) => {
                try {
                  if (isCreatingQuestion) {
                    await questionsApi.create(examId, questionData)
                    toast({
                      title: 'Success',
                      description: 'Question added successfully'
                    })
                  } else if (editingQuestion) {
                    await questionsApi.update(examId, editingQuestion.id, questionData)
                    toast({
                      title: 'Success',
                      description: 'Question updated successfully'
                    })
                  }
                  await loadExam()
                  setShowQuestionEditor(false)
                  setEditingQuestion(null)
                  setIsCreatingQuestion(false)
                } catch (error) {
                  console.error('Failed to save question:', error)
                  toast({
                    title: 'Error',
                    description: isCreatingQuestion ? 'Failed to add question' : 'Failed to update question',
                    variant: 'destructive'
                  })
                  throw error
                }
              }}
              onCancel={() => {
                setShowQuestionEditor(false)
                setEditingQuestion(null)
                setIsCreatingQuestion(false)
              }}
            />
          </DialogContent>
        </Dialog>
    </div>
  )
}

// Submissions List Component
function SubmissionsList({ examId, questions }: { examId: string; questions: Question[] }) {
  const [submissions, setSubmissions] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedSubmission, setSelectedSubmission] = useState<any | null>(null)
  const [showReviewDialog, setShowReviewDialog] = useState(false)
  const [reviewing, setReviewing] = useState(false)
  const [loadingSubmissionDetails, setLoadingSubmissionDetails] = useState(false)
  const { toast } = useToast()

  const loadSubmissions = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apiClient.get<any>(`/api/exams/${examId}/submissions`)
      // Handle response - apiClient might return data directly or wrapped
      const data = (response as any)?.data || response
      // Ensure it's always an array
      const submissionsArray = Array.isArray(data) ? data : []
      setSubmissions(submissionsArray)
    } catch (error: any) {
      console.error('Failed to load submissions:', error)
      // Check if it's a JSON parsing error
      const errorMessage = error?.message?.includes('JSON') 
        ? 'Failed to parse submissions data. The response may be too large or malformed.'
        : 'Failed to load submissions'
      toast({
        title: 'Error',
        description: errorMessage,
        variant: 'destructive'
      })
      setSubmissions([])
    } finally {
      setLoading(false)
    }
  }, [examId, toast])

  useEffect(() => {
    loadSubmissions()
  }, [examId, loadSubmissions])

  if (loading) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <Loader2 className="h-8 w-8 animate-spin mx-auto" />
        </CardContent>
      </Card>
    )
  }

  // Ensure submissions is always an array
  const submissionsArray = Array.isArray(submissions) ? submissions : []

  if (submissionsArray.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <p className="text-gray-500">No submissions yet</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Submissions ({submissionsArray.length})</CardTitle>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student ID</TableHead>
              <TableHead>Score</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Review Status</TableHead>
              <TableHead>Submitted At</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {submissionsArray.map((submission) => (
              <TableRow key={submission.id}>
                <TableCell>{submission.studentId}</TableCell>
                <TableCell>
                  {submission.score !== null && submission.score !== undefined 
                    ? `${submission.score} / ${submission.maxScore}` 
                    : 'Not graded'}
                </TableCell>
                <TableCell>
                  {submission.score !== null && submission.score !== undefined ? (
                    <Badge variant={submission.isPassed ? 'default' : 'destructive'}>
                      {submission.isPassed ? 'Passed' : 'Failed'}
                    </Badge>
                  ) : (
                    <Badge variant="outline">Not Graded</Badge>
                  )}
                </TableCell>
                <TableCell>
                  <Badge variant="outline">{submission.reviewStatus || 'PENDING'}</Badge>
                </TableCell>
                <TableCell>
                  {submission.submittedAt ? new Date(submission.submittedAt).toLocaleString() : 'Not submitted'}
                </TableCell>
                <TableCell>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={async () => {
                      setLoadingSubmissionDetails(true)
                      try {
                        // Fetch full submission details including answersJson
                        const response = await apiClient.get<any>(`/api/exams/${examId}/submissions/${submission.id}`)
                        const fullSubmission = (response as any)?.data || response
                        setSelectedSubmission(fullSubmission)
                        setShowReviewDialog(true)
                      } catch (error) {
                        console.error('Failed to load submission details:', error)
                        toast({
                          title: 'Error',
                          description: 'Failed to load submission details',
                          variant: 'destructive'
                        })
                        // Fallback to basic submission data
                        setSelectedSubmission(submission)
                        setShowReviewDialog(true)
                      } finally {
                        setLoadingSubmissionDetails(false)
                      }
                    }} disabled={loadingSubmissionDetails}>
                      {loadingSubmissionDetails ? (
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      ) : (
                        <Eye className="h-4 w-4 mr-2" />
                      )}
                      View
                    </Button>
                    {submission.reviewStatus !== 'AI_REVIEWED' && submission.reviewStatus !== 'COMPLETED' && (
                      <Button variant="outline" size="sm" onClick={async () => {
                        setReviewing(true)
                        try {
                          await apiClient.post(`/api/exams/${examId}/submissions/${submission.id}/review`)
                          toast({
                            title: 'Success',
                            description: 'AI review triggered successfully'
                          })
                          await loadSubmissions()
                        } catch (error) {
                          console.error('Failed to trigger review:', error)
                          toast({
                            title: 'Error',
                            description: 'Failed to trigger AI review',
                            variant: 'destructive'
                          })
                        } finally {
                          setReviewing(false)
                        }
                      }} disabled={reviewing}>
                        {reviewing ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Sparkles className="h-4 w-4 mr-2" />}
                        AI Review
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
      
      {/* Review Dialog */}
      <Dialog open={showReviewDialog} onOpenChange={setShowReviewDialog}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Submission Review</DialogTitle>
            <DialogDescription>
              Review student submission details
            </DialogDescription>
          </DialogHeader>
          {selectedSubmission && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label>Student ID</Label>
                  <p className="text-sm">{selectedSubmission.studentId}</p>
                </div>
                <div>
                  <Label>Score</Label>
                  <p className="text-sm font-medium">
                    {selectedSubmission.score !== null && selectedSubmission.score !== undefined
                      ? `${selectedSubmission.score} / ${selectedSubmission.maxScore}${selectedSubmission.percentage ? ` (${selectedSubmission.percentage.toFixed(1)}%)` : ''}`
                      : 'Not graded'}
                  </p>
                </div>
                <div>
                  <Label>Status</Label>
                  {selectedSubmission.score !== null && selectedSubmission.score !== undefined ? (
                    <Badge variant={selectedSubmission.isPassed ? 'default' : 'destructive'}>
                      {selectedSubmission.isPassed ? 'Passed' : 'Failed'}
                    </Badge>
                  ) : (
                    <Badge variant="outline">Not Graded</Badge>
                  )}
                </div>
                <div>
                  <Label>Review Status</Label>
                  <Badge variant="outline">{selectedSubmission.reviewStatus || 'PENDING'}</Badge>
                </div>
                {selectedSubmission.submittedAt && (
                  <div>
                    <Label>Submitted At</Label>
                    <p className="text-sm">{new Date(selectedSubmission.submittedAt).toLocaleString()}</p>
                  </div>
                )}
                {selectedSubmission.completedAt && (
                  <div>
                    <Label>Completed At</Label>
                    <p className="text-sm">{new Date(selectedSubmission.completedAt).toLocaleString()}</p>
                  </div>
                )}
              </div>
              
              {(() => {
                // Parse answersJson if it's a string, otherwise use as-is
                let answersJson = selectedSubmission?.answersJson
                if (typeof answersJson === 'string') {
                  try {
                    answersJson = JSON.parse(answersJson)
                  } catch (e) {
                    console.error('Failed to parse answersJson:', e)
                    answersJson = null
                  }
                }
                
                if (!answersJson || (typeof answersJson === 'object' && Object.keys(answersJson).length === 0)) {
                  return (
                    <div className="p-4 bg-yellow-50 border border-yellow-200 rounded">
                      <p className="text-sm text-yellow-800">
                        No answer data available for this submission. The submission may not have been completed.
                      </p>
                    </div>
                  )
                }
                
                if (questions.length === 0) {
                  return (
                    <div className="p-4 bg-yellow-50 border border-yellow-200 rounded">
                      <p className="text-sm text-yellow-800">
                        No questions available. Please ensure the exam has questions loaded.
                      </p>
                    </div>
                  )
                }
                
                return (
                  <div className="space-y-4">
                    <Label className="text-lg font-semibold">Questions & Answers</Label>
                    <div className="space-y-4">
                      {questions.map((question, index) => {
                        const answerValue = answersJson[question.id]
                      let answerDisplay: string | null = null
                      let isCorrect: boolean | null = null
                      
                      if (answerValue) {
                        if (question.questionType === 'MULTIPLE_CHOICE' && question.options) {
                          // Find the selected option
                          const selectedOption = question.options.find(opt => opt.id === answerValue)
                          if (selectedOption) {
                            answerDisplay = selectedOption.optionText
                            isCorrect = selectedOption.isCorrect
                          } else {
                            answerDisplay = `Selected option ID: ${answerValue} (not found)`
                          }
                        } else {
                          // For SHORT_ANSWER, ESSAY, TRUE_FALSE - display the text answer
                          answerDisplay = String(answerValue)
                        }
                      }
                      
                      return (
                        <Card key={question.id}>
                          <CardHeader>
                            <div className="flex items-start justify-between">
                              <CardTitle className="text-base">
                                Question {index + 1} ({question.points} points)
                              </CardTitle>
                              <Badge variant="outline">{question.questionType}</Badge>
                            </div>
                          </CardHeader>
                          <CardContent className="space-y-3">
                            <div>
                              <Label className="text-sm font-medium text-gray-700">Question:</Label>
                              <p className="mt-1 text-sm">{question.questionText}</p>
                            </div>
                            
                            {question.questionType === 'MULTIPLE_CHOICE' && question.options && (
                              <div className="space-y-2">
                                <Label className="text-sm font-medium text-gray-700">Options:</Label>
                                {question.options.map((option, optIndex) => {
                                  const isSelected = option.id === answerValue
                                  return (
                                    <div
                                      key={option.id}
                                      className={`p-2 rounded border text-sm ${
                                        isSelected
                                          ? option.isCorrect
                                            ? 'bg-green-50 border-green-300'
                                            : 'bg-red-50 border-red-300'
                                          : option.isCorrect
                                            ? 'bg-blue-50 border-blue-200'
                                            : 'bg-gray-50'
                                      }`}
                                    >
                                      <div className="flex items-center gap-2">
                                        <span className="font-medium">
                                          {String.fromCharCode(65 + optIndex)}.
                                        </span>
                                        <span>{option.optionText}</span>
                                        {isSelected && (
                                          <Badge variant="default" className="ml-auto">Selected</Badge>
                                        )}
                                        {option.isCorrect && (
                                          <Badge variant="default" className="bg-green-600">Correct</Badge>
                                        )}
                                      </div>
                                    </div>
                                  )
                                })}
                              </div>
                            )}
                            
                            <div>
                              <Label className="text-sm font-medium text-gray-700">Student&apos;s Answer:</Label>
                              {answerDisplay ? (
                                <div className={`mt-1 p-3 rounded border text-sm ${
                                  isCorrect === true
                                    ? 'bg-green-50 border-green-200'
                                    : isCorrect === false
                                    ? 'bg-red-50 border-red-200'
                                    : 'bg-gray-50'
                                }`}>
                                  {answerDisplay}
                                  {isCorrect === true && (
                                    <Badge variant="default" className="ml-2 bg-green-600">Correct</Badge>
                                  )}
                                  {isCorrect === false && (
                                    <Badge variant="destructive" className="ml-2">Incorrect</Badge>
                                  )}
                                </div>
                              ) : (
                                <div className="mt-1 p-3 bg-gray-50 rounded border text-sm text-gray-500">
                                  No answer provided
                                </div>
                              )}
                            </div>
                            
                            {(question.questionType === 'SHORT_ANSWER' || question.questionType === 'ESSAY') && 
                             (question.editedTentativeAnswer || question.tentativeAnswer) && (
                              <div>
                                <Label className="text-sm font-medium text-gray-700">Expected Answer:</Label>
                                <div className="mt-1 p-3 bg-blue-50 rounded border border-blue-200 text-sm">
                                  {question.editedTentativeAnswer || question.tentativeAnswer}
                                </div>
                              </div>
                            )}
                          </CardContent>
                        </Card>
                      )
                    })}
                  </div>
                </div>
                )
              })()}
              
              {selectedSubmission.aiReviewFeedback && (
                <div>
                  <Label className="text-lg font-semibold">AI Review Feedback</Label>
                  <div className="mt-1 p-3 bg-gray-50 rounded border text-sm">
                    <pre className="whitespace-pre-wrap">
                      {JSON.stringify(selectedSubmission.aiReviewFeedback, null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => {
              setShowReviewDialog(false)
              setSelectedSubmission(null)
            }}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
