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
import { Switch } from '@/components/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ArrowLeft, Loader2, Clock, Users, Edit, Save, Sparkles, Eye, Plus, Trash2, Shield, Shuffle, CheckCircle2, XCircle, AlertCircle, Lock, Camera, UserCheck, ClipboardX, TabletSmartphone, Play, Square } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, questionsApi, type QuestionData } from '@/lib/api'
import { proctoringApi } from '@/lib/proctoring-api'
import { ProctoringImagesViewer, type ProctoringImageItem } from '@/components/exams/ProctoringImagesViewer'
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
  classId?: string
  sectionId?: string
  randomizeQuestions?: boolean
  randomizeMcqOptions?: boolean
  // Timing fields
  timingMode?: 'FIXED_WINDOW' | 'FLEXIBLE_START'
  timeLimitSeconds?: number
  passingScorePercentage?: number
  // Proctoring fields
  enableProctoring?: boolean
  proctoringMode?: 'DISABLED' | 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING'
  photoIntervalSeconds?: number
  requireIdentityVerification?: boolean
  blockCopyPaste?: boolean
  blockTabSwitch?: boolean
  maxTabSwitchesAllowed?: number
  // Archive status
  archived?: boolean
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
  
  // Check if user can edit exams
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const canManageExams = !isInstructor && !isSupportStaff
  // Instructors can publish and complete exams within their assigned scope
  // The backend will verify the instructor's access to the specific exam
  const canPublishComplete = !isSupportStaff // Admin, ContentManager, and Instructors can try to publish/complete
  
  const [exam, setExam] = useState<Exam | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editingQuestion, setEditingQuestion] = useState<Question | null>(null)
  const [showTentativeAnswerDialog, setShowTentativeAnswerDialog] = useState(false)
  const [showQuestionEditor, setShowQuestionEditor] = useState(false)
  const [isCreatingQuestion, setIsCreatingQuestion] = useState(false)
  const [showProctoringDialog, setShowProctoringDialog] = useState(false)
  const [proctoringForm, setProctoringForm] = useState({
    enableProctoring: false,
    proctoringMode: 'BASIC_MONITORING' as 'DISABLED' | 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING',
    photoIntervalSeconds: 30,
    requireIdentityVerification: false,
    blockCopyPaste: false,
    blockTabSwitch: false,
    maxTabSwitchesAllowed: 3
  })
  const [showEditDialog, setShowEditDialog] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [showDeleteQuestionDialog, setShowDeleteQuestionDialog] = useState(false)
  const [questionToDelete, setQuestionToDelete] = useState<Question | null>(null)
  const [deletingQuestion, setDeletingQuestion] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [unpublishing, setUnpublishing] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [showPublishDialog, setShowPublishDialog] = useState(false)
  const [showUnpublishDialog, setShowUnpublishDialog] = useState(false)
  const [showCompleteDialog, setShowCompleteDialog] = useState(false)
  const [showAdjustScheduleDialog, setShowAdjustScheduleDialog] = useState(false)
  const [adjustScheduleForm, setAdjustScheduleForm] = useState({ startTime: '', endTime: '' })
  const [savingSchedule, setSavingSchedule] = useState(false)
  const [editForm, setEditForm] = useState({
    title: '',
    description: '',
    instructions: '',
    reviewMethod: 'INSTRUCTOR' as 'INSTRUCTOR' | 'AI' | 'BOTH',
    randomizeQuestions: false,
    randomizeMcqOptions: false,
    passingScorePercentage: 70,
    timingMode: 'FIXED_WINDOW' as 'FIXED_WINDOW' | 'FLEXIBLE_START',
    durationMinutes: 60,
    startTime: '',
    endTime: '',
    assignmentType: 'all' as 'all' | 'class' | 'section',
    classId: '',
    sectionId: ''
  })
  
  // State for classes and sections (for assignment editing)
  const [classes, setClasses] = useState<any[]>([])
  const [sections, setSections] = useState<any[]>([])

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

  // Load classes and sections for assignment editing
  const loadClassesAndSections = useCallback(async (courseId: string) => {
    try {
      // Load sections/batches
      const sectionsResponse = await apiClient.get(`/api/exams/courses/${courseId}/sections`)
      const sectionsData = Array.isArray(sectionsResponse) ? sectionsResponse : (sectionsResponse as any)?.data || []
      setSections(sectionsData)
      
      // Load classes
      const classesResponse = await apiClient.get(`/api/exams/courses/${courseId}/classes`)
      const classesData = Array.isArray(classesResponse) ? classesResponse : (classesResponse as any)?.data || []
      setClasses(classesData)
    } catch (error) {
      console.error('Failed to load classes/sections:', error)
      setSections([])
      setClasses([])
    }
  }, [])

  useEffect(() => {
    loadExam()
  }, [examId, loadExam])

  // Load classes and sections when exam is loaded
  useEffect(() => {
    if (exam?.courseId) {
      loadClassesAndSections(exam.courseId)
    }
  }, [exam?.courseId, loadClassesAndSections])

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

  const handleOpenProctoringDialog = () => {
    if (exam) {
      // Initialize form with current exam proctoring settings
      setProctoringForm({
        enableProctoring: exam.enableProctoring || false,
        proctoringMode: exam.proctoringMode || 'BASIC_MONITORING',
        photoIntervalSeconds: exam.photoIntervalSeconds || 30,
        requireIdentityVerification: exam.requireIdentityVerification || false,
        blockCopyPaste: exam.blockCopyPaste || false,
        blockTabSwitch: exam.blockTabSwitch || false,
        maxTabSwitchesAllowed: exam.maxTabSwitchesAllowed || 3
      })
    }
    setShowProctoringDialog(true)
  }

  const handleSaveProctoring = async () => {
    setSaving(true)
    try {
      const response = await apiClient.put(`/api/exams/${examId}`, proctoringForm)
      const updated = (response as any)?.data || response
      setExam(updated as unknown as Exam)
      setShowProctoringDialog(false)
      toast({
        title: 'Success',
        description: 'Proctoring settings updated successfully'
      })
    } catch (error) {
      console.error('Failed to update proctoring settings:', error)
      toast({
        title: 'Error',
        description: 'Failed to update proctoring settings',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleOpenEditDialog = () => {
    if (exam) {
      // Determine assignment type from classId/sectionId
      let assignmentType: 'all' | 'class' | 'section' = 'all'
      if (exam.sectionId) {
        assignmentType = 'section'
      } else if (exam.classId) {
        assignmentType = 'class'
      }
      
      // Convert ISO datetime to datetime-local format for the input
      let startTimeLocal = ''
      let endTimeLocal = ''
      if (exam.startTime) {
        const startDate = new Date(exam.startTime)
        startTimeLocal = new Date(startDate.getTime() - startDate.getTimezoneOffset() * 60000)
          .toISOString().slice(0, 16)
      }
      if (exam.endTime) {
        const endDate = new Date(exam.endTime)
        endTimeLocal = new Date(endDate.getTime() - endDate.getTimezoneOffset() * 60000)
          .toISOString().slice(0, 16)
      }
      
      // Initialize form with current exam data
      setEditForm({
        title: exam.title,
        description: exam.description || '',
        instructions: exam.instructions || '',
        reviewMethod: exam.reviewMethod,
        randomizeQuestions: exam.randomizeQuestions || false,
        randomizeMcqOptions: exam.randomizeMcqOptions || false,
        passingScorePercentage: exam.passingScorePercentage || 70,
        timingMode: exam.timingMode || 'FIXED_WINDOW',
        durationMinutes: exam.timeLimitSeconds ? Math.round(exam.timeLimitSeconds / 60) : 60,
        startTime: startTimeLocal,
        endTime: endTimeLocal,
        assignmentType,
        classId: exam.classId || '',
        sectionId: exam.sectionId || ''
      })
    }
    setShowEditDialog(true)
  }

  const handleOpenAdjustScheduleDialog = () => {
    if (exam) {
      let startTimeLocal = ''
      let endTimeLocal = ''
      if (exam.startTime) {
        const startDate = new Date(exam.startTime)
        startTimeLocal = new Date(startDate.getTime() - startDate.getTimezoneOffset() * 60000)
          .toISOString().slice(0, 16)
      }
      if (exam.endTime) {
        const endDate = new Date(exam.endTime)
        endTimeLocal = new Date(endDate.getTime() - endDate.getTimezoneOffset() * 60000)
          .toISOString().slice(0, 16)
      }
      setAdjustScheduleForm({ startTime: startTimeLocal, endTime: endTimeLocal })
    }
    setShowAdjustScheduleDialog(true)
  }

  const handleSaveAdjustSchedule = async () => {
    if (!adjustScheduleForm.startTime || !adjustScheduleForm.endTime) {
      toast({
        title: 'Validation Error',
        description: 'Start time and end time are required',
        variant: 'destructive'
      })
      return
    }
    const start = new Date(adjustScheduleForm.startTime)
    const end = new Date(adjustScheduleForm.endTime)
    if (end <= start) {
      toast({
        title: 'Validation Error',
        description: 'End time must be after start time',
        variant: 'destructive'
      })
      return
    }
    setSavingSchedule(true)
    try {
      const startTimeISO = convertToISOWithTimezone(adjustScheduleForm.startTime)
      const endTimeISO = convertToISOWithTimezone(adjustScheduleForm.endTime)
      const response = await apiClient.put(`/api/exams/${examId}/schedule`, {
        startTime: startTimeISO,
        endTime: endTimeISO
      })
      const updated = (response as any)?.data || response
      setExam(updated as unknown as Exam)
      setShowAdjustScheduleDialog(false)
      toast({
        title: 'Success',
        description: 'Exam schedule updated successfully'
      })
      await loadExam()
    } catch (error: any) {
      console.error('Failed to update schedule:', error)
      toast({
        title: 'Error',
        description: error?.response?.data?.message || error?.message || 'Failed to update schedule',
        variant: 'destructive'
      })
    } finally {
      setSavingSchedule(false)
    }
  }

  const handleSaveEdit = async () => {
    if (!editForm.title.trim()) {
      toast({
        title: 'Validation Error',
        description: 'Exam title is required',
        variant: 'destructive'
      })
      return
    }
    
    // Validate based on timing mode
    if (editForm.timingMode === 'FLEXIBLE_START' && (!editForm.durationMinutes || editForm.durationMinutes < 1)) {
      toast({
        title: 'Validation Error',
        description: 'Duration is required for Flexible Start mode',
        variant: 'destructive'
      })
      return
    }
    
    if (editForm.timingMode === 'FIXED_WINDOW' && editForm.startTime && editForm.endTime) {
      const start = new Date(editForm.startTime)
      const end = new Date(editForm.endTime)
      if (end <= start) {
        toast({
          title: 'Validation Error',
          description: 'End time must be after start time',
          variant: 'destructive'
        })
        return
      }
    }

    setSaving(true)
    try {
      // Update basic exam details
      const updateData: any = {
        title: editForm.title,
        description: editForm.description,
        instructions: editForm.instructions,
        reviewMethod: editForm.reviewMethod,
        randomizeQuestions: editForm.randomizeQuestions,
        randomizeMcqOptions: editForm.randomizeMcqOptions,
        passingScorePercentage: editForm.passingScorePercentage,
        timingMode: editForm.timingMode,
        classId: editForm.assignmentType === 'class' ? editForm.classId : null,
        sectionId: editForm.assignmentType === 'section' ? editForm.sectionId : null
      }
      
      // Only include duration for FLEXIBLE_START mode
      if (editForm.timingMode === 'FLEXIBLE_START') {
        updateData.timeLimitSeconds = editForm.durationMinutes * 60
      }
      
      const response = await apiClient.put(`/api/exams/${examId}`, updateData)
      let updated = (response as any)?.data || response
      
      // If FIXED_WINDOW mode and times are set, also schedule the exam
      if (editForm.timingMode === 'FIXED_WINDOW' && editForm.startTime && editForm.endTime) {
        const startTimeISO = convertToISOWithTimezone(editForm.startTime)
        const endTimeISO = convertToISOWithTimezone(editForm.endTime)
        
        const scheduleResponse = await apiClient.put(`/api/exams/${examId}/schedule`, {
          startTime: startTimeISO,
          endTime: endTimeISO
        })
        updated = (scheduleResponse as any)?.data || scheduleResponse
      }
      
      setExam(updated as unknown as Exam)
      setShowEditDialog(false)
      toast({
        title: 'Success',
        description: 'Exam details updated successfully'
      })
    } catch (error) {
      console.error('Failed to update exam details:', error)
      toast({
        title: 'Error',
        description: 'Failed to update exam details',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleDeleteExam = async () => {
    setDeleting(true)
    try {
      const response = await apiClient.delete<{ action: string; message: string }>(`/api/exams/${examId}`)
      const result = (response as any)?.data || response
      
      toast({
        title: result.action === 'deleted' ? 'Exam Deleted' : 'Exam Archived',
        description: result.message || (result.action === 'deleted' 
          ? 'The exam has been permanently deleted'
          : 'The exam has been archived because it has submissions'),
      })
      
      router.push('/exams')
    } catch (error) {
      console.error('Failed to delete exam:', error)
      toast({
        title: 'Error',
        description: 'Failed to delete exam',
        variant: 'destructive'
      })
    } finally {
      setDeleting(false)
      setShowDeleteDialog(false)
    }
  }

  const handlePublishExam = async () => {
    setPublishing(true)
    try {
      const response = await apiClient.put<Exam>(`/api/exams/${examId}/publish`)
      const updated = (response as any)?.data || response
      setExam(updated as unknown as Exam)
      setShowPublishDialog(false)
      
      const newStatus = (updated as any)?.status
      toast({
        title: newStatus === 'SCHEDULED' ? 'Exam Scheduled' : 'Exam Published',
        description: newStatus === 'SCHEDULED' 
          ? 'The exam is scheduled and will go live at the start time'
          : 'The exam is now live and available to students',
      })
    } catch (error: any) {
      console.error('Failed to publish exam:', error)
      toast({
        title: 'Error',
        description: error?.response?.data?.message || error?.message || 'Failed to publish exam',
        variant: 'destructive'
      })
    } finally {
      setPublishing(false)
    }
  }

  const handleUnpublishExam = async () => {
    setUnpublishing(true)
    try {
      const response = await apiClient.put<Exam>(`/api/exams/${examId}/unpublish`)
      const updated = (response as any)?.data || response
      setExam(updated as unknown as Exam)
      setShowUnpublishDialog(false)
      
      toast({
        title: 'Exam Unpublished',
        description: 'The exam has been moved back to draft status',
      })
    } catch (error: any) {
      console.error('Failed to unpublish exam:', error)
      toast({
        title: 'Error',
        description: error?.response?.data?.message || error?.message || 'Failed to unpublish exam',
        variant: 'destructive'
      })
    } finally {
      setUnpublishing(false)
    }
  }

  const handleCompleteExam = async () => {
    setCompleting(true)
    try {
      const response = await apiClient.put<Exam>(`/api/exams/${examId}/complete`)
      const updated = (response as any)?.data || response
      setExam(updated as unknown as Exam)
      setShowCompleteDialog(false)
      
      toast({
        title: 'Exam Completed',
        description: 'The exam has been marked as completed. No further submissions will be accepted.',
      })
    } catch (error: any) {
      console.error('Failed to complete exam:', error)
      toast({
        title: 'Error',
        description: error?.response?.data?.message || error?.message || 'Failed to complete exam',
        variant: 'destructive'
      })
    } finally {
      setCompleting(false)
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
        {canPublishComplete && (
          <div className="flex gap-2">
            {/* Publish button - only for DRAFT exams */}
            {/* Instructors can publish exams within their assigned scope - backend verifies access */}
            {exam.status === 'DRAFT' && (
              <Button 
                onClick={() => setShowPublishDialog(true)}
                className="bg-green-600 hover:bg-green-700"
              >
                <Play className="h-4 w-4 mr-2" />
                Publish Exam
              </Button>
            )}
            {/* Unpublish button - for SCHEDULED or LIVE exams */}
            {(exam.status === 'SCHEDULED' || exam.status === 'LIVE') && (
              <Button 
                onClick={() => setShowUnpublishDialog(true)}
                variant="outline"
              >
                <ArrowLeft className="h-4 w-4 mr-2" />
                Unpublish
              </Button>
            )}
            {/* Complete button - for LIVE exams (especially useful for FLEXIBLE_START) */}
            {exam.status === 'LIVE' && (
              <Button 
                onClick={() => setShowCompleteDialog(true)}
                variant="secondary"
              >
                <Square className="h-4 w-4 mr-2" />
                Complete Exam
              </Button>
            )}
            <Button 
              variant="outline" 
              onClick={() => {
                // Open exam in preview mode in student portal (new tab)
                const studentPortalUrl = process.env.NEXT_PUBLIC_STUDENT_PORTAL_URL || 'http://localhost:3001'
                window.open(`${studentPortalUrl}/exams/${examId}/take?preview=true`, '_blank')
              }}
            >
              <Eye className="h-4 w-4 mr-2" />
              Preview
            </Button>
            {canManageExams && (
              <Button variant="outline" onClick={handleOpenEditDialog}>
                <Edit className="h-4 w-4 mr-2" />
                Edit Details
              </Button>
            )}
            {isInstructor && exam.timingMode === 'FIXED_WINDOW' && (
              <Button variant="outline" onClick={handleOpenAdjustScheduleDialog}>
                <Clock className="h-4 w-4 mr-2" />
                Adjust date & time
              </Button>
            )}
            {exam.status !== 'LIVE' && (
              <Button variant="destructive" onClick={() => setShowDeleteDialog(true)}>
                <Trash2 className="h-4 w-4 mr-2" />
                Delete
              </Button>
            )}
          </div>
        )}
        {!canManageExams && !isInstructor && (
          <Badge variant="outline" className="text-sm">View Only</Badge>
        )}
        {isInstructor && (
          <Badge variant="outline" className="text-sm">Instructor - Can Publish/Complete</Badge>
        )}
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
                <div className="flex items-center justify-between">
                  <CardTitle>Exam Information</CardTitle>
                  {canManageExams && (
                    <Button variant="outline" size="sm" onClick={handleOpenEditDialog}>
                      <Edit className="h-4 w-4 mr-2" />
                      Edit
                    </Button>
                  )}
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <Label>Title</Label>
                  <div className="mt-1 text-lg font-semibold">{exam.title}</div>
                </div>
                {exam.description && (
                  <div>
                    <Label>Description</Label>
                    <div className="mt-1 text-gray-700">{exam.description}</div>
                  </div>
                )}
                <div className="grid grid-cols-2 gap-4 pt-2 border-t">
                  <div>
                    <Label>Status</Label>
                    <div className="mt-1">{getStatusBadge(exam.status)}</div>
                  </div>
                  <div>
                    <Label>Review Method</Label>
                    <div className="mt-1 font-medium">{exam.reviewMethod}</div>
                  </div>
                  <div>
                    <Label className="flex items-center gap-2">
                      <Clock className="h-4 w-4" />
                      Timing Mode
                    </Label>
                    <div className="mt-1">
                      <Badge variant={exam.timingMode === 'FLEXIBLE_START' ? 'default' : 'secondary'}>
                        {exam.timingMode === 'FLEXIBLE_START' ? 'Flexible Start' : 'Fixed Window'}
                      </Badge>
                      <p className="text-xs text-gray-500 mt-1">
                        {exam.timingMode === 'FLEXIBLE_START' 
                          ? 'Each student gets full duration from when they start'
                          : 'Exam runs between scheduled start and end times'}
                      </p>
                    </div>
                  </div>
                  {exam.timingMode === 'FLEXIBLE_START' && (
                    <div>
                      <Label>Duration</Label>
                      <div className="mt-1 font-medium">
                        {exam.timeLimitSeconds 
                          ? `${Math.floor(exam.timeLimitSeconds / 60)} minutes`
                          : 'Not set'}
                      </div>
                    </div>
                  )}
                  <div className="col-span-2">
                    <Label className="flex items-center gap-2">
                      <Shuffle className="h-4 w-4" />
                      Randomization
                    </Label>
                    <div className="mt-2">
                      {exam.randomizeQuestions || exam.randomizeMcqOptions ? (
                        <div className="space-y-2">
                          {exam.randomizeQuestions && (
                            <div className="flex items-center gap-2 px-3 py-2 bg-primary-50 border border-primary-200 rounded-lg">
                              <CheckCircle2 className="h-4 w-4 text-primary-600 flex-shrink-0" />
                              <span className="text-sm font-medium text-primary-900">Questions appear in random order</span>
                            </div>
                          )}
                          {exam.randomizeMcqOptions && (
                            <div className="flex items-center gap-2 px-3 py-2 bg-primary-50 border border-primary-200 rounded-lg">
                              <CheckCircle2 className="h-4 w-4 text-primary-600 flex-shrink-0" />
                              <span className="text-sm font-medium text-primary-900">Multiple choice options shuffled</span>
                            </div>
                          )}
                        </div>
                      ) : (
                        <div className="flex items-center gap-2 px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg">
                          <XCircle className="h-4 w-4 text-gray-400 flex-shrink-0" />
                          <span className="text-sm text-gray-600">No randomization enabled</span>
                        </div>
                      )}
                    </div>
                  </div>
                  <div>
                    <Label>Available To</Label>
                    <div className="mt-1">
                      {exam.sectionId ? (
                        <Badge variant="secondary">Specific Section</Badge>
                      ) : exam.classId ? (
                        <Badge variant="default">Class-Wide</Badge>
                      ) : (
                        <Badge variant="outline">All Students</Badge>
                      )}
                    </div>
                  </div>
                  <div></div>
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

            {/* Proctoring Settings Card */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Shield className="h-5 w-5" />
                    <CardTitle>Proctoring Settings</CardTitle>
                  </div>
                  {canManageExams && (
                    <Button variant="outline" size="sm" onClick={handleOpenProctoringDialog}>
                      <Edit className="h-4 w-4 mr-2" />
                      Configure
                    </Button>
                  )}
                </div>
              </CardHeader>
              <CardContent>
                {exam.enableProctoring ? (
                  <div className="space-y-4">
                    {/* Proctoring Mode Badge */}
                    <div>
                      <Label className="text-xs text-gray-500 uppercase mb-2 block">Active Mode</Label>
                      <div className="inline-flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-primary-50 to-primary-100 border border-primary-200 rounded-lg">
                        <Camera className="h-5 w-5 text-primary-600" />
                        <span className="font-semibold text-primary-900">
                          {exam.proctoringMode === 'BASIC_MONITORING' && 'Basic Monitoring'}
                          {exam.proctoringMode === 'WEBCAM_RECORDING' && 'Webcam Recording'}
                          {exam.proctoringMode === 'LIVE_PROCTORING' && 'Live Proctoring'}
                        </span>
                        {exam.proctoringMode === 'WEBCAM_RECORDING' && (
                          <Badge variant="secondary" className="ml-2">
                            <Clock className="h-3 w-3 mr-1" />
                            {exam.photoIntervalSeconds}s interval
                          </Badge>
                        )}
                      </div>
                    </div>

                    {/* Security Features */}
                    <div>
                      <Label className="text-xs text-gray-500 uppercase mb-2 block">Security Features</Label>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                        {exam.requireIdentityVerification && (
                          <div className="flex items-center gap-2 px-3 py-2 bg-emerald-50 border border-emerald-200 rounded-lg">
                            <UserCheck className="h-4 w-4 text-emerald-600 flex-shrink-0" />
                            <span className="text-sm font-medium text-emerald-900">Identity Verification</span>
                          </div>
                        )}
                        {exam.blockCopyPaste && (
                          <div className="flex items-center gap-2 px-3 py-2 bg-teal-50 border border-teal-200 rounded-lg">
                            <ClipboardX className="h-4 w-4 text-teal-600 flex-shrink-0" />
                            <span className="text-sm font-medium text-teal-900">Copy/Paste Blocked</span>
                          </div>
                        )}
                        {exam.blockTabSwitch ? (
                          <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg">
                            <Lock className="h-4 w-4 text-red-600 flex-shrink-0" />
                            <span className="text-sm font-medium text-red-900">Auto-Submit on Tab Switch</span>
                          </div>
                        ) : exam.maxTabSwitchesAllowed !== undefined && exam.maxTabSwitchesAllowed > 0 && (
                          <div className="flex items-center gap-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg">
                            <TabletSmartphone className="h-4 w-4 text-amber-600 flex-shrink-0" />
                            <span className="text-sm font-medium text-amber-900">Max {exam.maxTabSwitchesAllowed} Tab Switches</span>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg">
                    <XCircle className="h-4 w-4 text-gray-400 flex-shrink-0" />
                    <span className="text-sm text-gray-600">Proctoring is disabled for this exam</span>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="questions" className="space-y-4">
            {exam.status === 'DRAFT' && canManageExams && (
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
                          {exam.status === 'DRAFT' && canManageExams && (
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
                                onClick={() => {
                                  setQuestionToDelete(question)
                                  setShowDeleteQuestionDialog(true)
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
                          {exam.status === 'DRAFT' && canManageExams && (
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
                  {canManageExams && (
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
                  )}
                </CardContent>
              </Card>
            )}
          </TabsContent>

          <TabsContent value="submissions">
            <SubmissionsList examId={examId} questions={questions} reviewMethod={exam.reviewMethod} passingScorePercentage={exam.passingScorePercentage || 70} enableProctoring={exam.enableProctoring} />
          </TabsContent>
        </Tabs>

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

        {/* Proctoring Settings Dialog */}
        <Dialog open={showProctoringDialog} onOpenChange={setShowProctoringDialog}>
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>
                <div className="flex items-center gap-2">
                  <Shield className="h-5 w-5" />
                  Configure Proctoring Settings
                </div>
              </DialogTitle>
              <DialogDescription>
                Configure how students will be monitored during this exam
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="enableProctoring">Enable Proctoring</Label>
                  <p className="text-sm text-gray-500">Monitor students during the exam</p>
                </div>
                <Switch
                  id="enableProctoring"
                  checked={proctoringForm.enableProctoring}
                  onCheckedChange={(checked) => 
                    setProctoringForm(prev => ({ ...prev, enableProctoring: checked }))
                  }
                />
              </div>

              {proctoringForm.enableProctoring && (
                <div className="space-y-4 pt-4 border-t">
                  <div>
                    <Label htmlFor="proctoringMode">Proctoring Mode</Label>
                    <Select 
                      value={proctoringForm.proctoringMode} 
                      onValueChange={(value: any) => 
                        setProctoringForm(prev => ({ ...prev, proctoringMode: value }))
                      }
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="BASIC_MONITORING">Basic Monitoring (Events only)</SelectItem>
                        <SelectItem value="WEBCAM_RECORDING">Webcam Recording</SelectItem>
                        <SelectItem value="LIVE_PROCTORING">Live Proctoring (Future)</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {proctoringForm.proctoringMode === 'WEBCAM_RECORDING' && (
                    <div>
                      <Label htmlFor="photoInterval">Photo Capture Interval (seconds)</Label>
                      <Input
                        id="photoInterval"
                        type="number"
                        value={proctoringForm.photoIntervalSeconds}
                        onChange={(e) => setProctoringForm(prev => ({ 
                          ...prev, 
                          photoIntervalSeconds: parseInt(e.target.value) || 30 
                        }))}
                        min={10}
                        max={300}
                      />
                      <p className="text-xs text-gray-500 mt-1">Between 10 and 300 seconds</p>
                    </div>
                  )}

                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <Label htmlFor="requireIdentityVerification">Require Identity Verification</Label>
                      <p className="text-sm text-gray-500">Student must take a photo before starting</p>
                    </div>
                    <Switch
                      id="requireIdentityVerification"
                      checked={proctoringForm.requireIdentityVerification}
                      onCheckedChange={(checked) => 
                        setProctoringForm(prev => ({ ...prev, requireIdentityVerification: checked }))
                      }
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <Label htmlFor="blockCopyPaste">Block Copy & Paste</Label>
                      <p className="text-sm text-gray-500">Prevent copying/pasting during exam</p>
                    </div>
                    <Switch
                      id="blockCopyPaste"
                      checked={proctoringForm.blockCopyPaste}
                      onCheckedChange={(checked) => 
                        setProctoringForm(prev => ({ ...prev, blockCopyPaste: checked }))
                      }
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <Label htmlFor="blockTabSwitch">Auto-Submit on Tab Switch</Label>
                      <p className="text-sm text-gray-500">Automatically submit exam if student switches tabs</p>
                    </div>
                    <Switch
                      id="blockTabSwitch"
                      checked={proctoringForm.blockTabSwitch}
                      onCheckedChange={(checked) => 
                        setProctoringForm(prev => ({ ...prev, blockTabSwitch: checked }))
                      }
                    />
                  </div>

                  {!proctoringForm.blockTabSwitch && (
                    <div>
                      <Label htmlFor="maxTabSwitches">Maximum Tab Switches Allowed</Label>
                      <Input
                        id="maxTabSwitches"
                        type="number"
                        value={proctoringForm.maxTabSwitchesAllowed}
                        onChange={(e) => setProctoringForm(prev => ({ 
                          ...prev, 
                          maxTabSwitchesAllowed: parseInt(e.target.value) || 3 
                        }))}
                        min={0}
                        max={20}
                      />
                      <p className="text-xs text-gray-500 mt-1">0 = unlimited, 1-20 = max allowed</p>
                    </div>
                  )}
                </div>
              )}
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowProctoringDialog(false)}>
                Cancel
              </Button>
              <Button onClick={handleSaveProctoring} disabled={saving}>
                {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                Save Changes
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Edit Exam Details Dialog */}
        <Dialog open={showEditDialog} onOpenChange={setShowEditDialog}>
          <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>Edit Exam Details</DialogTitle>
              <DialogDescription>
                Update exam information, review method, and randomization settings
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 pt-4">
              <div>
                <Label htmlFor="editTitle">Exam Title *</Label>
                <Input
                  id="editTitle"
                  value={editForm.title}
                  onChange={(e) => setEditForm(prev => ({ ...prev, title: e.target.value }))}
                  placeholder="Enter exam title"
                />
              </div>

              <div>
                <Label htmlFor="editDescription">Description</Label>
                <Textarea
                  id="editDescription"
                  value={editForm.description}
                  onChange={(e) => setEditForm(prev => ({ ...prev, description: e.target.value }))}
                  placeholder="Brief description of the exam"
                  rows={3}
                />
              </div>

              <div>
                <Label htmlFor="editInstructions">Instructions</Label>
                <Textarea
                  id="editInstructions"
                  value={editForm.instructions}
                  onChange={(e) => setEditForm(prev => ({ ...prev, instructions: e.target.value }))}
                  placeholder="Detailed instructions for students"
                  rows={5}
                />
              </div>

              <div>
                <Label htmlFor="editPassingScore">Passing Score (%)</Label>
                <Input
                  id="editPassingScore"
                  type="number"
                  value={editForm.passingScorePercentage}
                  onChange={(e) => setEditForm(prev => ({ 
                    ...prev, 
                    passingScorePercentage: Math.min(100, Math.max(0, parseInt(e.target.value) || 0))
                  }))}
                  min={0}
                  max={100}
                  placeholder="70"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Students must score at least this percentage to pass
                </p>
              </div>

              <div>
                <Label htmlFor="reviewMethod">Review Method</Label>
                <Select 
                  value={editForm.reviewMethod} 
                  onValueChange={(value: any) => 
                    setEditForm(prev => ({ ...prev, reviewMethod: value }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INSTRUCTOR">Instructor Review Only</SelectItem>
                    <SelectItem value="AI">AI Review Only</SelectItem>
                    <SelectItem value="BOTH">AI + Instructor Review</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-gray-500 mt-1">
                  How should answers be reviewed and graded?
                </p>
              </div>

              <div className="space-y-3 pt-2 border-t">
                <Label>Timing Settings</Label>
                
                <div>
                  <Label htmlFor="editTimingMode">Timing Mode</Label>
                  <Select 
                    value={editForm.timingMode} 
                    onValueChange={(value: any) => 
                      setEditForm(prev => ({ ...prev, timingMode: value }))
                    }
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="FIXED_WINDOW">Fixed Window</SelectItem>
                      <SelectItem value="FLEXIBLE_START">Flexible Start</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="text-xs text-gray-500 mt-1">
                    {editForm.timingMode === 'FIXED_WINDOW' 
                      ? 'Exam has a fixed start and end time. All students must complete within this window.'
                      : 'Each student gets a fixed duration from when they start the exam.'}
                  </p>
                </div>

                {editForm.timingMode === 'FLEXIBLE_START' ? (
                  <div>
                    <Label htmlFor="editDurationMinutes">
                      Exam Duration (minutes) <span className="text-red-500">*</span>
                    </Label>
                    <Input
                      id="editDurationMinutes"
                      type="number"
                      value={editForm.durationMinutes}
                      onChange={(e) => setEditForm(prev => ({ 
                        ...prev, 
                        durationMinutes: parseInt(e.target.value) || 60 
                      }))}
                      min={1}
                      max={480}
                      placeholder="60"
                    />
                    <p className="text-xs text-gray-500 mt-1">
                      Each student gets this many minutes from when they start.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <div>
                      <Label htmlFor="editStartTime">Start Time</Label>
                      <Input
                        id="editStartTime"
                        type="datetime-local"
                        value={editForm.startTime}
                        onChange={(e) => setEditForm(prev => ({ ...prev, startTime: e.target.value }))}
                      />
                    </div>
                    <div>
                      <Label htmlFor="editEndTime">End Time</Label>
                      <Input
                        id="editEndTime"
                        type="datetime-local"
                        value={editForm.endTime}
                        onChange={(e) => setEditForm(prev => ({ ...prev, endTime: e.target.value }))}
                      />
                    </div>
                    <p className="text-xs text-gray-500">
                      The exam will be available between these times. Duration is calculated automatically.
                    </p>
                  </div>
                )}
              </div>

              <div className="space-y-3 pt-2 border-t">
                <Label>Student Assignment</Label>
                
                <div>
                  <Label htmlFor="editAssignmentType">Assign To</Label>
                  <Select 
                    value={editForm.assignmentType} 
                    onValueChange={(value: any) => 
                      setEditForm(prev => ({ ...prev, assignmentType: value, classId: '', sectionId: '' }))
                    }
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Students in Course</SelectItem>
                      <SelectItem value="class">Specific Class</SelectItem>
                      <SelectItem value="section">Specific Section/Batch</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {editForm.assignmentType === 'class' && (
                  <div>
                    <Label htmlFor="editClassId">Select Class</Label>
                    <Select 
                      value={editForm.classId} 
                      onValueChange={(value) => 
                        setEditForm(prev => ({ ...prev, classId: value }))
                      }
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Choose a class" />
                      </SelectTrigger>
                      <SelectContent>
                        {classes.length === 0 ? (
                          <SelectItem value="__none__" disabled>No classes found</SelectItem>
                        ) : (
                          classes.map((cls: any) => (
                            <SelectItem key={cls.id} value={cls.id}>
                              {cls.name} {cls.code ? `(${cls.code})` : ''}
                            </SelectItem>
                          ))
                        )}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {editForm.assignmentType === 'section' && (
                  <div>
                    <Label htmlFor="editSectionId">Select Section/Batch</Label>
                    <Select 
                      value={editForm.sectionId} 
                      onValueChange={(value) => 
                        setEditForm(prev => ({ ...prev, sectionId: value }))
                      }
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Choose a section" />
                      </SelectTrigger>
                      <SelectContent>
                        {sections.length === 0 ? (
                          <SelectItem value="__none__" disabled>No sections found</SelectItem>
                        ) : (
                          sections.map((section: any) => (
                            <SelectItem key={section.id} value={section.id}>
                              {section.name}
                            </SelectItem>
                          ))
                        )}
                      </SelectContent>
                    </Select>
                  </div>
                )}
              </div>

              <div className="space-y-3 pt-2 border-t">
                <Label>Randomization Options</Label>
                
                <div className="flex items-center justify-between">
                  <div className="space-y-0.5">
                    <Label htmlFor="randomizeQuestions">Randomize Question Order</Label>
                    <p className="text-sm text-gray-500">Each student sees questions in different order</p>
                  </div>
                  <Switch
                    id="randomizeQuestions"
                    checked={editForm.randomizeQuestions}
                    onCheckedChange={(checked) => 
                      setEditForm(prev => ({ ...prev, randomizeQuestions: checked }))
                    }
                  />
                </div>

                <div className="flex items-center justify-between">
                  <div className="space-y-0.5">
                    <Label htmlFor="randomizeMcqOptions">Randomize MCQ Options</Label>
                    <p className="text-sm text-gray-500">Multiple choice options appear in random order</p>
                  </div>
                  <Switch
                    id="randomizeMcqOptions"
                    checked={editForm.randomizeMcqOptions}
                    onCheckedChange={(checked) => 
                      setEditForm(prev => ({ ...prev, randomizeMcqOptions: checked }))
                    }
                  />
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowEditDialog(false)}>
                Cancel
              </Button>
              <Button onClick={handleSaveEdit} disabled={saving}>
                {saving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                Save Changes
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Adjust schedule dialog (instructor, fixed-window only) */}
        <Dialog open={showAdjustScheduleDialog} onOpenChange={setShowAdjustScheduleDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <Clock className="h-5 w-5" />
                Adjust date & time
              </DialogTitle>
              <DialogDescription>
                Change the start and end times for this fixed-window exam.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div>
                <Label htmlFor="adjustStartTime">Start Time</Label>
                <Input
                  id="adjustStartTime"
                  type="datetime-local"
                  value={adjustScheduleForm.startTime}
                  onChange={(e) => setAdjustScheduleForm(prev => ({ ...prev, startTime: e.target.value }))}
                />
              </div>
              <div>
                <Label htmlFor="adjustEndTime">End Time</Label>
                <Input
                  id="adjustEndTime"
                  type="datetime-local"
                  value={adjustScheduleForm.endTime}
                  onChange={(e) => setAdjustScheduleForm(prev => ({ ...prev, endTime: e.target.value }))}
                />
              </div>
              <p className="text-xs text-gray-500">
                The exam will be available between these times.
              </p>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowAdjustScheduleDialog(false)}>
                Cancel
              </Button>
              <Button onClick={handleSaveAdjustSchedule} disabled={savingSchedule}>
                {savingSchedule ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                Save schedule
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Delete Confirmation Dialog */}
        <Dialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-destructive">
                <AlertCircle className="h-5 w-5" />
                Delete Exam
              </DialogTitle>
              <DialogDescription>
                Are you sure you want to delete &quot;{exam.title}&quot;?
              </DialogDescription>
            </DialogHeader>
            <div className="py-4">
              <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg">
                <p className="text-sm text-amber-800">
                  <strong>Note:</strong> If this exam has student submissions, it will be 
                  <strong> archived</strong> instead of permanently deleted. Archived exams 
                  can be restored later. Exams without submissions will be permanently deleted.
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowDeleteDialog(false)} disabled={deleting}>
                Cancel
              </Button>
              <Button variant="destructive" onClick={handleDeleteExam} disabled={deleting}>
                {deleting ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Trash2 className="h-4 w-4 mr-2" />}
                {deleting ? 'Deleting...' : 'Delete Exam'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Publish Confirmation Dialog */}
        <Dialog open={showPublishDialog} onOpenChange={setShowPublishDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-green-600">
                <Play className="h-5 w-5" />
                Publish Exam
              </DialogTitle>
              <DialogDescription>
                Are you sure you want to publish &quot;{exam.title}&quot;?
              </DialogDescription>
            </DialogHeader>
            <div className="py-4">
              {exam.timingMode === 'FIXED_WINDOW' ? (
                <div className="space-y-3">
                  {exam.startTime && exam.endTime ? (
                    <div className="p-4 bg-green-50 border border-green-200 rounded-lg">
                      <p className="text-sm text-green-800">
                        <strong>Fixed Window Exam</strong><br />
                        Start: {new Date(exam.startTime).toLocaleString()}<br />
                        End: {new Date(exam.endTime).toLocaleString()}
                      </p>
                      <p className="text-sm text-green-700 mt-2">
                        {new Date() < new Date(exam.startTime) 
                          ? 'The exam will be scheduled and go live at the start time.'
                          : 'The exam will go live immediately.'}
                      </p>
                    </div>
                  ) : (
                    <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg">
                      <p className="text-sm text-amber-800">
                        <strong>Warning:</strong> Start and end times are not set. 
                        Please edit the exam details to set the schedule before publishing.
                      </p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
                  <p className="text-sm text-blue-800">
                    <strong>Flexible Start Exam</strong><br />
                    Duration: {exam.timeLimitSeconds ? `${Math.floor(exam.timeLimitSeconds / 60)} minutes` : 'Not set'}
                  </p>
                  <p className="text-sm text-blue-700 mt-2">
                    The exam will go live immediately. Each student will get the full duration from when they start.
                  </p>
                </div>
              )}
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowPublishDialog(false)} disabled={publishing}>
                Cancel
              </Button>
              <Button 
                onClick={handlePublishExam} 
                disabled={publishing || (exam.timingMode === 'FIXED_WINDOW' && (!exam.startTime || !exam.endTime))}
                className="bg-green-600 hover:bg-green-700"
              >
                {publishing ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Play className="h-4 w-4 mr-2" />}
                {publishing ? 'Publishing...' : 'Publish Exam'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Unpublish Confirmation Dialog */}
        <Dialog open={showUnpublishDialog} onOpenChange={setShowUnpublishDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-amber-600">
                <ArrowLeft className="h-5 w-5" />
                Unpublish Exam
              </DialogTitle>
              <DialogDescription>
                Are you sure you want to unpublish &quot;{exam.title}&quot;?
              </DialogDescription>
            </DialogHeader>
            <div className="py-4">
              <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg">
                <p className="text-sm text-amber-800">
                  <strong>Current Status:</strong> {exam.status}
                </p>
                <p className="text-sm text-amber-700 mt-2">
                  The exam will be moved back to <strong>DRAFT</strong> status. 
                  Students will no longer be able to access it until you publish it again.
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowUnpublishDialog(false)} disabled={unpublishing}>
                Cancel
              </Button>
              <Button 
                onClick={handleUnpublishExam} 
                disabled={unpublishing}
                variant="destructive"
              >
                {unpublishing ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <ArrowLeft className="h-4 w-4 mr-2" />}
                {unpublishing ? 'Unpublishing...' : 'Unpublish Exam'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Complete Confirmation Dialog */}
        <Dialog open={showCompleteDialog} onOpenChange={setShowCompleteDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <Square className="h-5 w-5" />
                Complete Exam
              </DialogTitle>
              <DialogDescription>
                Are you sure you want to mark &quot;{exam.title}&quot; as completed?
              </DialogDescription>
            </DialogHeader>
            <div className="py-4">
              <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg">
                <p className="text-sm text-amber-800">
                  <strong>Warning:</strong> Once completed, no more students will be able to 
                  start or submit the exam. This action cannot be undone easily.
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setShowCompleteDialog(false)} disabled={completing}>
                Cancel
              </Button>
              <Button onClick={handleCompleteExam} disabled={completing} variant="secondary">
                {completing ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Square className="h-4 w-4 mr-2" />}
                {completing ? 'Completing...' : 'Complete Exam'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Delete Question Confirmation Dialog */}
        <Dialog open={showDeleteQuestionDialog} onOpenChange={setShowDeleteQuestionDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-destructive">
                <AlertCircle className="h-5 w-5" />
                Delete Question
              </DialogTitle>
              <DialogDescription>
                Are you sure you want to delete this question from the exam?
              </DialogDescription>
            </DialogHeader>
            {questionToDelete && (
              <div className="py-4">
                <div className="p-4 bg-gray-50 border rounded-lg">
                  <p className="text-sm text-gray-600 line-clamp-3">
                    {questionToDelete.questionText}
                  </p>
                </div>
              </div>
            )}
            <DialogFooter>
              <Button 
                variant="outline" 
                onClick={() => {
                  setShowDeleteQuestionDialog(false)
                  setQuestionToDelete(null)
                }} 
                disabled={deletingQuestion}
              >
                Cancel
              </Button>
              <Button 
                variant="destructive" 
                onClick={async () => {
                  if (!questionToDelete) return
                  setDeletingQuestion(true)
                  try {
                    await questionsApi.delete(examId, questionToDelete.id)
                    await loadExam()
                    toast({
                      title: 'Success',
                      description: 'Question deleted successfully'
                    })
                    setShowDeleteQuestionDialog(false)
                    setQuestionToDelete(null)
                  } catch (error) {
                    console.error('Failed to delete question:', error)
                    toast({
                      title: 'Error',
                      description: 'Failed to delete question',
                      variant: 'destructive'
                    })
                  } finally {
                    setDeletingQuestion(false)
                  }
                }} 
                disabled={deletingQuestion}
              >
                {deletingQuestion ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Trash2 className="h-4 w-4 mr-2" />}
                {deletingQuestion ? 'Deleting...' : 'Delete Question'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
    </div>
  )
}

// Submissions List Component
function SubmissionsList({ examId, questions, reviewMethod, passingScorePercentage, enableProctoring }: { examId: string; questions: Question[]; reviewMethod?: string; passingScorePercentage: number; enableProctoring?: boolean }) {
  const router = useRouter()
  const [submissions, setSubmissions] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedSubmission, setSelectedSubmission] = useState<any | null>(null)
  const [showReviewDialog, setShowReviewDialog] = useState(false)
  const [showProctoringImagesDialog, setShowProctoringImagesDialog] = useState(false)
  const [proctoringImagesSubmissionId, setProctoringImagesSubmissionId] = useState<string | null>(null)
  const [reviewing, setReviewing] = useState(false)
  const [loadingSubmissionDetails, setLoadingSubmissionDetails] = useState(false)
  const [manualGrading, setManualGrading] = useState(false)
  const [manualGradeData, setManualGradeData] = useState({
    score: '',
    maxScore: '',
    isPassed: false,
    instructorFeedback: ''
  })
  const [questionGrades, setQuestionGrades] = useState<Record<string, { score: string; feedback: string }>>({})
  const [savingGrade, setSavingGrade] = useState(false)
  const [proctoringReportLoading, setProctoringReportLoading] = useState(false)
  const [proctoringImages, setProctoringImages] = useState<ProctoringImageItem[]>([])
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

  useEffect(() => {
    if (!showProctoringImagesDialog || !proctoringImagesSubmissionId) {
      setProctoringImages([])
      return
    }
    let cancelled = false
    setProctoringReportLoading(true)
    setProctoringImages([])
    proctoringApi.getReport(examId, proctoringImagesSubmissionId)
      .then((response) => {
        if (cancelled) return
        const report = (response as any)?.data ?? response
        const images: ProctoringImageItem[] = []
        if (report?.identityVerificationPhotoUrl) {
          images.push({
            url: report.identityVerificationPhotoUrl,
            label: 'Identity verification'
          })
        }
        report?.proctoringData?.photos?.forEach((p: { url: string; capturedAt: string }) => {
          images.push({ url: p.url, capturedAt: p.capturedAt })
        })
        setProctoringImages(images)
      })
      .catch(() => {
        if (!cancelled) setProctoringImages([])
      })
      .finally(() => {
        if (!cancelled) setProctoringReportLoading(false)
      })
    return () => { cancelled = true }
  }, [showProctoringImagesDialog, proctoringImagesSubmissionId, examId])

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
        <div className="flex items-center justify-between">
          <CardTitle>Submissions ({submissionsArray.length})</CardTitle>
          <Button 
            variant="outline" 
            onClick={() => router.push(`/exams/${examId}/submissions`)}
          >
            <Users className="h-4 w-4 mr-2" />
            Manage All Submissions
          </Button>
        </div>
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
                    {/* Only show AI Review button if review method allows AI (AI or BOTH) */}
                    {reviewMethod !== 'INSTRUCTOR' && submission.reviewStatus !== 'AI_REVIEWED' && submission.reviewStatus !== 'COMPLETED' && (
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
                    {enableProctoring && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setProctoringImagesSubmissionId(submission.id)
                          setShowProctoringImagesDialog(true)
                        }}
                      >
                        <Camera className="h-4 w-4 mr-2" />
                        Proctoring
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
                                            ? 'bg-primary-50 border-primary-200'
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
                                <div className="mt-1 p-3 bg-primary-50 rounded border border-primary-200 text-sm">
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
              
              {/* Manual Grading Section */}
              <div className="border-t pt-4 mt-4">
                <div className="flex items-center justify-between mb-4">
                  <Label className="text-lg font-semibold">Grade Submission</Label>
                  {!manualGrading && (
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => {
                        setManualGrading(true)
                        // Initialize question grades from existing AI review or empty
                        const initialGrades: Record<string, { score: string; feedback: string }> = {}
                        const existingReviews = selectedSubmission.aiReviewFeedback?.questionReviews || []
                        
                        questions.forEach((q: any) => {
                          const existingReview = existingReviews.find((r: any) => r.questionId === q.id)
                          initialGrades[q.id] = {
                            score: existingReview?.pointsEarned?.toString() || '0',
                            feedback: existingReview?.feedback || ''
                          }
                        })
                        setQuestionGrades(initialGrades)
                        setManualGradeData({
                          score: selectedSubmission.score?.toString() || '',
                          maxScore: selectedSubmission.maxScore?.toString() || '',
                          isPassed: selectedSubmission.isPassed || false,
                          instructorFeedback: selectedSubmission.aiReviewFeedback?.instructorFeedback || ''
                        })
                      }}
                    >
                      <Edit className="h-4 w-4 mr-2" />
                      Grade Answers
                    </Button>
                  )}
                </div>
                
                {manualGrading ? (
                  <div className="space-y-4">
                    {/* Per-Question Grading */}
                    {(() => {
                      const examQuestions = questions
                      let answersJson = selectedSubmission.answersJson || {}
                      if (typeof answersJson === 'string') {
                        try { answersJson = JSON.parse(answersJson) } catch (e) { answersJson = {} }
                      }
                      const passingPercentage = passingScorePercentage
                      
                      // Calculate totals
                      let totalEarned = 0
                      let totalMax = 0
                      examQuestions.forEach((q: any) => {
                        totalMax += q.points || 1
                        const isObjective = q.questionType === 'MULTIPLE_CHOICE' || q.questionType === 'TRUE_FALSE'
                        if (isObjective) {
                          // Auto-grade MCQ
                          const answer = answersJson[q.id]
                          if (answer && q.questionType === 'MULTIPLE_CHOICE') {
                            const selectedOption = q.options?.find((o: any) => o.id === answer)
                            if (selectedOption?.isCorrect) {
                              totalEarned += q.points || 1
                            }
                          } else if (q.questionType === 'TRUE_FALSE') {
                            // TRUE_FALSE grading logic
                            const correctOption = q.options?.find((o: any) => o.isCorrect)
                            if (correctOption && String(answer) === correctOption.optionText) {
                              totalEarned += q.points || 1
                            }
                          }
                        } else {
                          // Use entered grade for subjective
                          const grade = questionGrades[q.id]
                          if (grade?.score) {
                            totalEarned += Math.min(parseFloat(grade.score) || 0, q.points || 1)
                          }
                        }
                      })
                      
                      const percentage = totalMax > 0 ? (totalEarned / totalMax) * 100 : 0
                      const willPass = percentage >= passingPercentage
                      
                      return (
                        <>
                          <div className="bg-gray-50 p-4 rounded-lg mb-4">
                            <div className="grid grid-cols-3 gap-4 text-center">
                              <div>
                                <p className="text-2xl font-bold">{totalEarned.toFixed(1)} / {totalMax}</p>
                                <p className="text-sm text-gray-500">Total Score</p>
                              </div>
                              <div>
                                <p className="text-2xl font-bold">{percentage.toFixed(1)}%</p>
                                <p className="text-sm text-gray-500">Percentage</p>
                              </div>
                              <div>
                                <Badge className={willPass ? 'bg-green-600' : 'bg-red-600'}>
                                  {willPass ? 'WILL PASS' : 'WILL FAIL'}
                                </Badge>
                                <p className="text-sm text-gray-500 mt-1">Threshold: {passingPercentage}%</p>
                              </div>
                            </div>
                          </div>
                          
                          <div className="space-y-3 max-h-[300px] overflow-y-auto">
                            {examQuestions.map((question: any, index: number) => {
                              const isObjective = question.questionType === 'MULTIPLE_CHOICE' || question.questionType === 'TRUE_FALSE'
                              const answer = answersJson[question.id]
                              
                              // Calculate auto-grade for objective questions
                              let autoScore = 0
                              let isCorrect = false
                              if (isObjective && answer) {
                                if (question.questionType === 'MULTIPLE_CHOICE') {
                                  const selectedOption = question.options?.find((o: any) => o.id === answer)
                                  if (selectedOption?.isCorrect) {
                                    autoScore = question.points || 1
                                    isCorrect = true
                                  }
                                } else if (question.questionType === 'TRUE_FALSE') {
                                  const correctOption = question.options?.find((o: any) => o.isCorrect)
                                  if (correctOption && String(answer) === correctOption.optionText) {
                                    autoScore = question.points || 1
                                    isCorrect = true
                                  }
                                }
                              }
                              
                              return (
                                <div key={question.id} className="border rounded p-3 bg-white">
                                  <div className="flex items-start justify-between gap-4">
                                    <div className="flex-1 min-w-0">
                                      <p className="text-sm font-medium truncate">
                                        Q{index + 1}: {question.questionText?.substring(0, 60)}...
                                      </p>
                                      <div className="flex items-center gap-2 mt-1">
                                        <Badge variant="outline" className="text-xs">{question.questionType}</Badge>
                                        <span className="text-xs text-gray-500">Max: {question.points || 1} pts</span>
                                      </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                      {isObjective ? (
                                        // Read-only auto-graded score
                                        <div className="text-right">
                                          <Badge className={isCorrect ? 'bg-green-600' : 'bg-red-600'}>
                                            {autoScore} / {question.points || 1}
                                          </Badge>
                                          <p className="text-xs text-gray-500 mt-1">Auto-graded</p>
                                        </div>
                                      ) : (
                                        // Editable score for subjective
                                        <div className="flex items-center gap-2">
                                          <Input
                                            type="number"
                                            step="0.5"
                                            min="0"
                                            max={question.points || 1}
                                            className="w-20 h-8 text-sm"
                                            value={questionGrades[question.id]?.score || '0'}
                                            onChange={(e) => {
                                              const val = Math.min(
                                                parseFloat(e.target.value) || 0, 
                                                question.points || 1
                                              )
                                              setQuestionGrades(prev => ({
                                                ...prev,
                                                [question.id]: {
                                                  ...prev[question.id],
                                                  score: val.toString()
                                                }
                                              }))
                                            }}
                                          />
                                          <span className="text-sm text-gray-500">/ {question.points || 1}</span>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                </div>
                              )
                            })}
                          </div>
                          
                          <div>
                            <Label htmlFor="instructorFeedback">Overall Feedback (Optional)</Label>
                            <Textarea
                              id="instructorFeedback"
                              rows={3}
                              className="mt-1"
                              value={manualGradeData.instructorFeedback}
                              onChange={(e) => setManualGradeData(prev => ({ ...prev, instructorFeedback: e.target.value }))}
                              placeholder="Add overall feedback for the student..."
                            />
                          </div>
                        </>
                      )
                    })()}
                    
                    <div className="flex gap-2 pt-2">
                      <Button 
                        disabled={savingGrade}
                        onClick={async () => {
                          try {
                            setSavingGrade(true)
                            // Build questionGrades payload for subjective questions only
                            const examQuestions = questions
                            const subjectiveGrades: Record<string, { score: number; feedback?: string }> = {}
                            
                            examQuestions.forEach((q: any) => {
                              const isObjective = q.questionType === 'MULTIPLE_CHOICE' || q.questionType === 'TRUE_FALSE'
                              if (!isObjective && questionGrades[q.id]) {
                                subjectiveGrades[q.id] = {
                                  score: parseFloat(questionGrades[q.id].score) || 0,
                                  feedback: questionGrades[q.id].feedback || undefined
                                }
                              }
                            })
                            
                            await apiClient.put(`/api/exams/${examId}/submissions/${selectedSubmission.id}/manual-grade`, {
                              questionGrades: subjectiveGrades,
                              instructorFeedback: manualGradeData.instructorFeedback || undefined
                            })
                            
                            toast({
                              title: 'Success',
                              description: 'Submission graded successfully'
                            })
                            
                            setManualGrading(false)
                            await loadSubmissions()
                            setShowReviewDialog(false)
                            setSelectedSubmission(null)
                          } catch (error) {
                            console.error('Failed to grade submission:', error)
                            toast({
                              title: 'Error',
                              description: 'Failed to save grades',
                              variant: 'destructive'
                            })
                          } finally {
                            setSavingGrade(false)
                          }
                        }}
                      >
                        {savingGrade ? (
                          <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                        ) : (
                          <Save className="h-4 w-4 mr-2" />
                        )}
                        Save Grades
                      </Button>
                      <Button 
                        variant="outline"
                        onClick={() => setManualGrading(false)}
                        disabled={savingGrade}
                      >
                        Cancel
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="text-sm text-gray-600">
                    <p>Current Score: {selectedSubmission.score !== null && selectedSubmission.score !== undefined 
                      ? `${selectedSubmission.score} / ${selectedSubmission.maxScore} (${selectedSubmission.percentage?.toFixed(1)}%)` 
                      : 'Not graded'}</p>
                    <p>Status: {selectedSubmission.isPassed ? 
                      <Badge className="bg-green-600 ml-2">Passed</Badge> : 
                      <Badge className="bg-red-600 ml-2">Not Passed</Badge>}
                    </p>
                    {selectedSubmission.aiReviewFeedback?.instructorFeedback && (
                      <div className="mt-2">
                        <Label className="text-sm font-medium">Instructor Feedback:</Label>
                        <p className="mt-1 italic">{selectedSubmission.aiReviewFeedback.instructorFeedback}</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => {
              setShowReviewDialog(false)
              setSelectedSubmission(null)
              setManualGrading(false)
            }}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Proctoring images dialog */}
      <Dialog
        open={showProctoringImagesDialog}
        onOpenChange={(open) => {
          setShowProctoringImagesDialog(open)
          if (!open) setProctoringImagesSubmissionId(null)
        }}
      >
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Proctoring images</DialogTitle>
            <DialogDescription>
              {proctoringImagesSubmissionId && `Submission: ${proctoringImagesSubmissionId}`}
            </DialogDescription>
          </DialogHeader>
          {proctoringReportLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
            </div>
          ) : proctoringImages.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-gray-500">
              <Camera className="h-12 w-12 mb-2 opacity-50" />
              <p>No proctoring data or images captured for this submission</p>
            </div>
          ) : (
            <ProctoringImagesViewer images={proctoringImages} />
          )}
        </DialogContent>
      </Dialog>
    </Card>
  )
}
