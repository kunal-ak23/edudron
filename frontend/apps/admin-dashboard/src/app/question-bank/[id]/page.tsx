'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useParams, useSearchParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { ArrowLeft, Save, Loader2, Trash2, AlertCircle } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, coursesApi, lecturesApi } from '@/lib/api'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface QuestionBank {
  id: string
  courseId: string
  moduleIds?: string[]
  moduleId?: string // Legacy support
  subModuleIds?: string[] // Supports multiple lectures
  subModuleId?: string // Legacy support
  questionType: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY' | 'MATCHING'
  questionText: string
  defaultPoints: number
  difficultyLevel?: 'EASY' | 'MEDIUM' | 'HARD'
  explanation?: string
  tags?: string[]
  isActive: boolean
  tentativeAnswer?: string
  options?: QuestionOption[]
  createdAt: string
}

interface QuestionOption {
  id: string
  optionText: string
  isCorrect: boolean
  sequence: number
}

interface Section {
  id: string
  title: string
  courseId: string
}

interface Lecture {
  id: string
  title: string
  sectionId: string
}

export const dynamic = 'force-dynamic'

export default function EditQuestionPage() {
  const router = useRouter()
  const params = useParams()
  const searchParams = useSearchParams()
  const questionId = params.id as string
  const courseIdFromUrl = searchParams.get('courseId')
  const moduleIdFromUrl = searchParams.get('moduleId')
  const difficultyFromUrl = searchParams.get('difficulty')
  const { toast } = useToast()
  const { user } = useAuth()
  
  const [question, setQuestion] = useState<QuestionBank | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [sections, setSections] = useState<Section[]>([])
  const [lectures, setLectures] = useState<Lecture[]>([])
  const [loadingSections, setLoadingSections] = useState(false)
  
  const [formData, setFormData] = useState({
    questionText: '',
    points: 1,
    difficultyLevel: 'MEDIUM' as 'EASY' | 'MEDIUM' | 'HARD',
    explanation: '',
    tentativeAnswer: '',
    selectedModuleIds: [] as string[],
    selectedLectureIds: [] as string[],
    options: [
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
    ]
  })
  
  const canManageQuestions = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'

  // Get the URL to navigate back to question bank with filter context
  const getBackUrl = useCallback(() => {
    const params = new URLSearchParams()
    const courseId = question?.courseId || courseIdFromUrl
    if (courseId) params.set('courseId', courseId)
    if (moduleIdFromUrl) params.set('moduleId', moduleIdFromUrl)
    if (difficultyFromUrl) params.set('difficulty', difficultyFromUrl)
    const queryString = params.toString()
    return queryString ? `/question-bank?${queryString}` : '/question-bank'
  }, [question?.courseId, courseIdFromUrl, moduleIdFromUrl, difficultyFromUrl])

  // Load sections for a course
  const loadSections = useCallback(async (courseId: string) => {
    if (!courseId) return
    setLoadingSections(true)
    try {
      const courseSections = await coursesApi.getSections(courseId)
      if (Array.isArray(courseSections)) {
        setSections(courseSections.map(s => ({ id: s.id, title: s.title, courseId: s.courseId })))
      }
    } catch (error) {
      console.error('Failed to load sections:', error)
    } finally {
      setLoadingSections(false)
    }
  }, [])

  // Load lectures for selected modules
  const loadLectures = useCallback(async (moduleIds: string[]) => {
    if (!moduleIds || moduleIds.length === 0) {
      setLectures([])
      return
    }
    try {
      const allLectures: Lecture[] = []
      for (const moduleId of moduleIds) {
        try {
          const response = await lecturesApi.getLecturesBySection(moduleId)
          if (Array.isArray(response)) {
            allLectures.push(...response.map((l: any) => ({ id: l.id, title: l.title, sectionId: moduleId })))
          }
        } catch (e) {
          // Continue with other modules
        }
      }
      setLectures(allLectures)
    } catch (error) {
      console.error('Failed to load lectures:', error)
    }
  }, [])

  const loadQuestion = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apiClient.get<QuestionBank>(`/api/question-bank/${questionId}`)
      
      // Handle wrapped response
      let questionData = response as any
      if (questionData && 'data' in questionData) {
        questionData = questionData.data
      }
      
      setQuestion(questionData)
      
      // Load sections for this course
      if (questionData.courseId) {
        await loadSections(questionData.courseId)
      }
      
      // Get module IDs (support both array and legacy single value)
      const moduleIds = questionData.moduleIds || (questionData.moduleId ? [questionData.moduleId] : [])
      
      // Get lecture IDs (support both array and legacy single value)
      const lectureIds = questionData.subModuleIds || (questionData.subModuleId ? [questionData.subModuleId] : [])
      
      // Load lectures for selected modules
      if (moduleIds.length > 0) {
        await loadLectures(moduleIds)
      }
      
      // Populate form
      setFormData({
        questionText: questionData.questionText || '',
        points: questionData.defaultPoints || 1,
        difficultyLevel: questionData.difficultyLevel || 'MEDIUM',
        explanation: questionData.explanation || '',
        tentativeAnswer: questionData.tentativeAnswer || '',
        selectedModuleIds: moduleIds,
        selectedLectureIds: lectureIds,
        options: questionData.options && questionData.options.length > 0 
          ? questionData.options.map((opt: QuestionOption) => ({ text: opt.optionText, correct: opt.isCorrect }))
          : [
              { text: '', correct: false },
              { text: '', correct: false },
              { text: '', correct: false },
              { text: '', correct: false },
            ]
      })
    } catch (error) {
      console.error('Failed to load question:', error)
      toast({
        title: 'Error',
        description: 'Failed to load question',
        variant: 'destructive'
      })
      router.push(getBackUrl())
    } finally {
      setLoading(false)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questionId, toast, router, loadSections, loadLectures])

  useEffect(() => {
    loadQuestion()
  }, [loadQuestion])

  const handleSave = async () => {
    if (!formData.questionText.trim()) {
      toast({
        title: 'Error',
        description: 'Question text is required',
        variant: 'destructive'
      })
      return
    }
    
    if (formData.selectedModuleIds.length === 0) {
      toast({
        title: 'Error',
        description: 'Please select at least one module',
        variant: 'destructive'
      })
      return
    }
    
    setSaving(true)
    try {
      const payload: any = {
        questionText: formData.questionText,
        points: formData.points,
        difficultyLevel: formData.difficultyLevel,
        explanation: formData.explanation || null,
        tentativeAnswer: formData.tentativeAnswer || null,
        moduleIds: formData.selectedModuleIds,
        subModuleIds: formData.selectedLectureIds.length > 0 ? formData.selectedLectureIds : null,
      }
      
      if (question?.questionType === 'MULTIPLE_CHOICE') {
        const validOptions = formData.options.filter(o => o.text.trim())
        if (validOptions.length < 2) {
          toast({
            title: 'Error',
            description: 'Multiple choice questions need at least 2 options',
            variant: 'destructive'
          })
          setSaving(false)
          return
        }
        if (!validOptions.some(o => o.correct)) {
          toast({
            title: 'Error',
            description: 'Please mark at least one option as correct',
            variant: 'destructive'
          })
          setSaving(false)
          return
        }
        payload.options = validOptions
      } else if (question?.questionType === 'TRUE_FALSE') {
        payload.options = [
          { text: 'True', correct: formData.tentativeAnswer?.toLowerCase() === 'true' },
          { text: 'False', correct: formData.tentativeAnswer?.toLowerCase() === 'false' }
        ]
      }
      
      await apiClient.put(`/api/question-bank/${questionId}`, payload)
      
      toast({
        title: 'Success',
        description: 'Question updated successfully'
      })
      
      // Navigate back with course context preserved
      router.push(getBackUrl())
    } catch (error) {
      console.error('Failed to update question:', error)
      toast({
        title: 'Error',
        description: 'Failed to update question',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setDeleting(true)
    try {
      await apiClient.delete(`/api/question-bank/${questionId}`)
      
      toast({
        title: 'Success',
        description: 'Question deleted successfully'
      })
      
      // Navigate back with course context preserved
      router.push(getBackUrl())
    } catch (error) {
      console.error('Failed to delete question:', error)
      toast({
        title: 'Error',
        description: 'Failed to delete question',
        variant: 'destructive'
      })
    } finally {
      setDeleting(false)
      setShowDeleteDialog(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    )
  }

  if (!question) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500">Question not found</p>
        <Button variant="link" onClick={() => router.push(getBackUrl())}>
          Back to Question Bank
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4 max-w-3xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" onClick={() => router.push(getBackUrl())}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <h1 className="text-2xl font-bold">Edit Question</h1>
        </div>
        {canManageQuestions && (
          <Button variant="destructive" onClick={() => setShowDeleteDialog(true)}>
            <Trash2 className="h-4 w-4 mr-2" />
            Delete
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Question Details</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Question Type</Label>
              <Input value={question.questionType.replace('_', ' ')} disabled />
            </div>
            
            <div>
              <Label>Difficulty</Label>
              <Select 
                value={formData.difficultyLevel} 
                onValueChange={(v) => setFormData({...formData, difficultyLevel: v as any})}
                disabled={!canManageQuestions}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="EASY">Easy</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          
          {/* Module Selection */}
          <div>
            <Label>Module(s) - Select one or more</Label>
            <div className="border rounded-md p-3 max-h-40 overflow-y-auto space-y-2 mt-1">
              {loadingSections ? (
                <div className="flex items-center justify-center py-2">
                  <Loader2 className="h-4 w-4 animate-spin mr-2" />
                  <span className="text-sm text-gray-500">Loading modules...</span>
                </div>
              ) : sections.length === 0 ? (
                <p className="text-sm text-gray-500">No modules available.</p>
              ) : (
                sections.map(section => (
                  <div key={section.id} className="flex items-center space-x-2">
                    <Checkbox
                      id={`module-${section.id}`}
                      checked={formData.selectedModuleIds.includes(section.id)}
                      disabled={!canManageQuestions}
                      onCheckedChange={(checked) => {
                        const newModuleIds = checked
                          ? [...formData.selectedModuleIds, section.id]
                          : formData.selectedModuleIds.filter(id => id !== section.id)
                        setFormData({
                          ...formData,
                          selectedModuleIds: newModuleIds
                        })
                        // Reload lectures when modules change
                        loadLectures(newModuleIds)
                      }}
                    />
                    <Label htmlFor={`module-${section.id}`} className="font-normal cursor-pointer">
                      {section.title}
                    </Label>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Lecture Selection */}
          {lectures.length > 0 && (
            <div>
              <Label>Lecture(s) - Optional, select one or more</Label>
              <div className="border rounded-md p-3 max-h-40 overflow-y-auto space-y-2 mt-1">
                {lectures.map(lecture => (
                  <div key={lecture.id} className="flex items-center space-x-2">
                    <Checkbox
                      id={`lecture-${lecture.id}`}
                      checked={formData.selectedLectureIds.includes(lecture.id)}
                      disabled={!canManageQuestions}
                      onCheckedChange={(checked) => {
                        if (checked) {
                          setFormData({
                            ...formData,
                            selectedLectureIds: [...formData.selectedLectureIds, lecture.id]
                          })
                        } else {
                          setFormData({
                            ...formData,
                            selectedLectureIds: formData.selectedLectureIds.filter(id => id !== lecture.id)
                          })
                        }
                      }}
                    />
                    <Label htmlFor={`lecture-${lecture.id}`} className="font-normal cursor-pointer">
                      {lecture.title}
                    </Label>
                  </div>
                ))}
              </div>
            </div>
          )}
          
          <div>
            <Label>Question Text</Label>
            <Textarea
              value={formData.questionText}
              onChange={(e) => setFormData({...formData, questionText: e.target.value})}
              placeholder="Enter your question..."
              rows={3}
              disabled={!canManageQuestions}
            />
          </div>
          
          <div>
            <Label>Points</Label>
            <Input
              type="number"
              min={1}
              value={formData.points}
              onChange={(e) => setFormData({...formData, points: parseInt(e.target.value) || 1})}
              disabled={!canManageQuestions}
            />
          </div>
          
          {question.questionType === 'MULTIPLE_CHOICE' && (
            <div>
              <Label>Options (mark correct answer)</Label>
              <div className="space-y-2 mt-2">
                {formData.options.map((opt, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={opt.correct}
                      onChange={(e) => {
                        const newOptions = [...formData.options]
                        newOptions[idx].correct = e.target.checked
                        setFormData({...formData, options: newOptions})
                      }}
                      className="h-4 w-4"
                      disabled={!canManageQuestions}
                    />
                    <Input
                      value={opt.text}
                      onChange={(e) => {
                        const newOptions = [...formData.options]
                        newOptions[idx].text = e.target.value
                        setFormData({...formData, options: newOptions})
                      }}
                      placeholder={`Option ${idx + 1}`}
                      disabled={!canManageQuestions}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}
          
          {question.questionType === 'TRUE_FALSE' && (
            <div>
              <Label>Correct Answer</Label>
              <Select 
                value={formData.tentativeAnswer} 
                onValueChange={(v) => setFormData({...formData, tentativeAnswer: v})}
                disabled={!canManageQuestions}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select correct answer" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="true">True</SelectItem>
                  <SelectItem value="false">False</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}
          
          {(question.questionType === 'SHORT_ANSWER' || question.questionType === 'ESSAY') && (
            <div>
              <Label>Expected Answer (for grading reference)</Label>
              <Textarea
                value={formData.tentativeAnswer}
                onChange={(e) => setFormData({...formData, tentativeAnswer: e.target.value})}
                placeholder="Enter expected answer..."
                rows={3}
                disabled={!canManageQuestions}
              />
            </div>
          )}
          
          <div>
            <Label>Explanation (shown after answering)</Label>
            <Textarea
              value={formData.explanation}
              onChange={(e) => setFormData({...formData, explanation: e.target.value})}
              placeholder="Optional explanation..."
              rows={2}
              disabled={!canManageQuestions}
            />
          </div>
          
          {canManageQuestions && (
            <div className="flex justify-end gap-2 pt-4">
              <Button variant="outline" onClick={() => router.push(getBackUrl())}>
                Cancel
              </Button>
              <Button onClick={handleSave} disabled={saving}>
                {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                <Save className="h-4 w-4 mr-2" />
                Save Changes
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Delete Confirmation Dialog */}
      <Dialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <AlertCircle className="h-5 w-5" />
              Delete Question
            </DialogTitle>
            <DialogDescription>
              Are you sure you want to delete this question? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          {question && (
            <div className="py-4">
              <div className="p-4 bg-gray-50 border rounded-lg">
                <p className="text-sm text-gray-600 line-clamp-3">
                  {question.questionText}
                </p>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button 
              variant="outline" 
              onClick={() => setShowDeleteDialog(false)} 
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button 
              variant="destructive" 
              onClick={handleDelete} 
              disabled={deleting}
            >
              {deleting ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Trash2 className="h-4 w-4 mr-2" />}
              {deleting ? 'Deleting...' : 'Delete Question'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
