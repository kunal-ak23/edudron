'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
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
import { ArrowLeft, Save, Loader2, Trash2 } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient } from '@/lib/api'

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

export const dynamic = 'force-dynamic'

export default function EditQuestionPage() {
  const router = useRouter()
  const params = useParams()
  const questionId = params.id as string
  const { toast } = useToast()
  const { user } = useAuth()
  
  const [question, setQuestion] = useState<QuestionBank | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  
  const [formData, setFormData] = useState({
    questionText: '',
    points: 1,
    difficultyLevel: 'MEDIUM' as 'EASY' | 'MEDIUM' | 'HARD',
    explanation: '',
    tentativeAnswer: '',
    options: [
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
    ]
  })
  
  const canManageQuestions = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'

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
      
      // Populate form
      setFormData({
        questionText: questionData.questionText || '',
        points: questionData.defaultPoints || 1,
        difficultyLevel: questionData.difficultyLevel || 'MEDIUM',
        explanation: questionData.explanation || '',
        tentativeAnswer: questionData.tentativeAnswer || '',
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
      router.push('/question-bank')
    } finally {
      setLoading(false)
    }
  }, [questionId, toast, router])

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
    
    setSaving(true)
    try {
      const payload: any = {
        questionText: formData.questionText,
        points: formData.points,
        difficultyLevel: formData.difficultyLevel,
        explanation: formData.explanation || null,
        tentativeAnswer: formData.tentativeAnswer || null,
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
      
      router.push('/question-bank')
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
    if (!confirm('Are you sure you want to delete this question?')) {
      return
    }
    
    try {
      await apiClient.delete(`/api/question-bank/${questionId}`)
      
      toast({
        title: 'Success',
        description: 'Question deleted successfully'
      })
      
      router.push('/question-bank')
    } catch (error) {
      console.error('Failed to delete question:', error)
      toast({
        title: 'Error',
        description: 'Failed to delete question',
        variant: 'destructive'
      })
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
        <Button variant="link" onClick={() => router.push('/question-bank')}>
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
          <Button variant="ghost" onClick={() => router.push('/question-bank')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <h1 className="text-2xl font-bold">Edit Question</h1>
        </div>
        {canManageQuestions && (
          <Button variant="destructive" onClick={handleDelete}>
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
              <Button variant="outline" onClick={() => router.push('/question-bank')}>
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
    </div>
  )
}
