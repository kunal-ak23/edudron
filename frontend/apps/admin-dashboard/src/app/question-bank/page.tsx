'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Plus, Search, Edit, Trash2, Loader2, BookOpen, CheckCircle, Upload } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, coursesApi } from '@/lib/api'

interface QuestionBank {
  id: string
  courseId: string
  moduleIds?: string[] // Updated to array
  moduleId?: string // Legacy support
  subModuleId?: string
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

interface Course {
  id: string
  title: string
}

interface Section {
  id: string
  title: string
  courseId: string
}

export const dynamic = 'force-dynamic'

export default function QuestionBankPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const { user } = useAuth()
  
  const [questions, setQuestions] = useState<QuestionBank[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [allSections, setAllSections] = useState<Map<string, Section>>(new Map())
  const [loading, setLoading] = useState(true)
  const [selectedCourse, setSelectedCourse] = useState<string>('')
  const [selectedModule, setSelectedModule] = useState<string>('')
  const [selectedDifficulty, setSelectedDifficulty] = useState<string>('')
  const [searchKeyword, setSearchKeyword] = useState('')
  
  // Dialog states
  const [showCreateDialog, setShowCreateDialog] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [questionToDelete, setQuestionToDelete] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  
  // Form states for create dialog
  const [formData, setFormData] = useState({
    questionType: 'MULTIPLE_CHOICE' as QuestionBank['questionType'],
    questionText: '',
    points: 1,
    difficultyLevel: 'MEDIUM' as 'EASY' | 'MEDIUM' | 'HARD',
    explanation: '',
    tentativeAnswer: '',
    selectedModuleIds: [] as string[],
    subModuleId: '',
    options: [
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
    ]
  })
  
  const canManageQuestions = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'

  // Initialize from URL params
  useEffect(() => {
    const courseIdParam = searchParams.get('courseId')
    const moduleIdParam = searchParams.get('moduleId')
    
    if (courseIdParam) {
      setSelectedCourse(courseIdParam)
    }
    if (moduleIdParam) {
      setSelectedModule(moduleIdParam)
    }
  }, [searchParams])

  // Load courses
  const loadCourses = useCallback(async () => {
    try {
      const courses = await coursesApi.listCourses()
      if (Array.isArray(courses)) {
        setCourses(courses)
      }
    } catch (error) {
      console.error('Failed to load courses:', error)
    }
  }, [])

  // Load sections for selected course
  const loadSections = useCallback(async (courseId: string) => {
    if (!courseId) {
      setSections([])
      return
    }
    try {
      // Use getSections API to fetch sections separately
      const courseSections = await coursesApi.getSections(courseId)
      if (Array.isArray(courseSections)) {
        const mappedSections = courseSections.map(s => ({ id: s.id, title: s.title, courseId: s.courseId })) as Section[]
        setSections(mappedSections)
        // Also update the allSections map for display purposes
        const newMap = new Map(allSections)
        mappedSections.forEach((s: Section) => newMap.set(s.id, s))
        setAllSections(newMap)
      }
    } catch (error) {
      console.error('Failed to load sections:', error)
    }
  }, [allSections])

  // Load questions
  const loadQuestions = useCallback(async () => {
    if (!selectedCourse && !selectedModule) {
      setQuestions([])
      setLoading(false)
      return
    }
    
    try {
      setLoading(true)
      let url = '/api/question-bank?'
      
      if (selectedModule) {
        url += `moduleId=${selectedModule}`
      } else if (selectedCourse) {
        url += `courseId=${selectedCourse}`
      }
      
      if (selectedDifficulty) {
        url += `&difficultyLevel=${selectedDifficulty}`
      }
      
      if (searchKeyword) {
        url += `&keyword=${encodeURIComponent(searchKeyword)}`
      }
      
      const response = await apiClient.get<QuestionBank[]>(url)
      
      // Handle the response - it might be wrapped in data or be direct
      let questions: QuestionBank[] = []
      if (response && typeof response === 'object') {
        if ('data' in response) {
          const data = (response as any).data
          if (Array.isArray(data)) {
            questions = data
          } else if (data && 'content' in data) {
            questions = data.content || []
          }
        } else if (Array.isArray(response)) {
          questions = response
        } else if ('content' in response) {
          questions = (response as any).content || []
        }
      }
      
      setQuestions(questions)
    } catch (error) {
      console.error('Failed to load questions:', error)
      toast({
        title: 'Error',
        description: 'Failed to load questions',
        variant: 'destructive'
      })
      setQuestions([])
    } finally {
      setLoading(false)
    }
  }, [selectedCourse, selectedModule, selectedDifficulty, searchKeyword, toast])

  useEffect(() => {
    loadCourses()
  }, [loadCourses])

  useEffect(() => {
    if (selectedCourse) {
      loadSections(selectedCourse)
    }
  }, [selectedCourse, loadSections])

  useEffect(() => {
    loadQuestions()
  }, [loadQuestions])

  const handleCreateQuestion = async () => {
    if (!selectedCourse) {
      toast({
        title: 'Error',
        description: 'Please select a course first',
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
        courseId: selectedCourse,
        moduleIds: formData.selectedModuleIds,
        subModuleId: formData.subModuleId || null,
        questionType: formData.questionType,
        questionText: formData.questionText,
        points: formData.points,
        difficultyLevel: formData.difficultyLevel,
        explanation: formData.explanation || null,
        tentativeAnswer: formData.tentativeAnswer || null,
      }
      
      if (formData.questionType === 'MULTIPLE_CHOICE') {
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
      } else if (formData.questionType === 'TRUE_FALSE') {
        payload.options = [
          { text: 'True', correct: formData.tentativeAnswer?.toLowerCase() === 'true' },
          { text: 'False', correct: formData.tentativeAnswer?.toLowerCase() === 'false' }
        ]
      }
      
      await apiClient.post('/api/question-bank', payload)
      
      toast({
        title: 'Success',
        description: 'Question created successfully'
      })
      
      setShowCreateDialog(false)
      resetForm()
      loadQuestions()
    } catch (error) {
      console.error('Failed to create question:', error)
      toast({
        title: 'Error',
        description: 'Failed to create question',
        variant: 'destructive'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleDeleteQuestion = async () => {
    if (!questionToDelete) return
    
    try {
      await apiClient.delete(`/api/question-bank/${questionToDelete}`)
      
      toast({
        title: 'Success',
        description: 'Question deleted successfully'
      })
      
      setShowDeleteDialog(false)
      setQuestionToDelete(null)
      loadQuestions()
    } catch (error) {
      console.error('Failed to delete question:', error)
      toast({
        title: 'Error',
        description: 'Failed to delete question',
        variant: 'destructive'
      })
    }
  }

  const resetForm = () => {
    setFormData({
      questionType: 'MULTIPLE_CHOICE',
      questionText: '',
      points: 1,
      difficultyLevel: 'MEDIUM',
      explanation: '',
      tentativeAnswer: '',
      selectedModuleIds: selectedModule ? [selectedModule] : [],
      subModuleId: '',
      options: [
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false },
      ]
    })
  }

  const getModuleNames = (question: QuestionBank): string => {
    const moduleIds = question.moduleIds || (question.moduleId ? [question.moduleId] : [])
    return moduleIds
      .map(id => allSections.get(id)?.title || id)
      .join(', ') || '-'
  }

  const getLectureName = (question: QuestionBank): string => {
    if (!question.subModuleId) return '-'
    // For now, we'll just show the ID. In a real implementation,
    // you'd need to fetch lecture names as well
    return question.subModuleId
  }

  const getDifficultyBadge = (difficulty?: string) => {
    if (!difficulty) return null
    const colors: Record<string, string> = {
      EASY: 'bg-green-100 text-green-800',
      MEDIUM: 'bg-yellow-100 text-yellow-800',
      HARD: 'bg-red-100 text-red-800'
    }
    return (
      <span className={`px-2 py-0.5 rounded text-xs font-medium ${colors[difficulty] || 'bg-gray-100'}`}>
        {difficulty}
      </span>
    )
  }

  const getQuestionTypeBadge = (type: string) => {
    const labels: Record<string, string> = {
      MULTIPLE_CHOICE: 'MCQ',
      TRUE_FALSE: 'T/F',
      SHORT_ANSWER: 'Short',
      ESSAY: 'Essay',
      MATCHING: 'Match'
    }
    return (
      <Badge variant="outline" className="text-xs">{labels[type] || type}</Badge>
    )
  }

  const truncateText = (text: string, maxLength: number = 100) => {
    if (text.length <= maxLength) return text
    return text.substring(0, maxLength) + '...'
  }

  return (
    <div className="space-y-4 p-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Question Bank</h1>
          <p className="text-gray-500">Manage reusable questions for exams</p>
        </div>
        <div className="flex gap-2">
          {canManageQuestions && selectedCourse && (
            <>
              <Button variant="outline" onClick={() => {
                const params = new URLSearchParams()
                if (selectedCourse) params.set('courseId', selectedCourse)
                if (selectedModule) params.set('moduleId', selectedModule)
                const queryString = params.toString()
                router.push(`/question-bank/import${queryString ? '?' + queryString : ''}`)
              }}>
                <Upload className="h-4 w-4 mr-2" />
                Import
              </Button>
              <Button onClick={() => {
                resetForm()
                setShowCreateDialog(true)
              }}>
                <Plus className="h-4 w-4 mr-2" />
                Add Question
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div>
              <Label>Course</Label>
              <Select value={selectedCourse} onValueChange={(v) => {
                setSelectedCourse(v)
                setSelectedModule('')
              }}>
                <SelectTrigger>
                  <SelectValue placeholder="Select course" />
                </SelectTrigger>
                <SelectContent>
                  {courses.map(course => (
                    <SelectItem key={course.id} value={course.id}>
                      {course.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            
            <div>
              <Label>Module</Label>
              <Select 
                value={selectedModule || '_all'} 
                onValueChange={(v) => setSelectedModule(v === '_all' ? '' : v)}
                disabled={!selectedCourse}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All modules" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="_all">All Modules</SelectItem>
                  {sections.map(section => (
                    <SelectItem key={section.id} value={section.id}>
                      {section.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            
            <div>
              <Label>Difficulty</Label>
              <Select value={selectedDifficulty || '_all'} onValueChange={(v) => setSelectedDifficulty(v === '_all' ? '' : v)}>
                <SelectTrigger>
                  <SelectValue placeholder="All difficulties" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="_all">All</SelectItem>
                  <SelectItem value="EASY">Easy</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <div>
              <Label>Search</Label>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-gray-400" />
                <Input
                  placeholder="Search questions..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  className="pl-8"
                />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Questions Table */}
      {loading ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin" />
        </div>
      ) : !selectedCourse ? (
        <Card>
          <CardContent className="py-12 text-center">
            <BookOpen className="h-12 w-12 mx-auto text-gray-400 mb-4" />
            <p className="text-gray-500">Select a course to view questions</p>
          </CardContent>
        </Card>
      ) : questions.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-gray-500 mb-4">No questions found</p>
            {canManageQuestions && (
              <Button onClick={() => {
                resetForm()
                setShowCreateDialog(true)
              }}>
                <Plus className="h-4 w-4 mr-2" />
                Add Your First Question
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[400px]">Question</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Difficulty</TableHead>
                  <TableHead>Points</TableHead>
                  <TableHead>Module(s)</TableHead>
                  <TableHead>Lecture</TableHead>
                  {canManageQuestions && <TableHead className="text-right">Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {questions.map(question => (
                  <TableRow key={question.id} className="hover:bg-gray-50">
                    <TableCell className="font-medium">
                      <div className="max-w-[400px]">
                        <p className="truncate" title={question.questionText}>
                          {truncateText(question.questionText)}
                        </p>
                        {question.options && question.options.length > 0 && (
                          <div className="mt-1 text-xs text-gray-500">
                            {question.options.length} options
                            {question.options.some(o => o.isCorrect) && (
                              <span className="ml-2 text-green-600">
                                <CheckCircle className="h-3 w-3 inline" /> Has correct answer
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>{getQuestionTypeBadge(question.questionType)}</TableCell>
                    <TableCell>{getDifficultyBadge(question.difficultyLevel) || '-'}</TableCell>
                    <TableCell>{question.defaultPoints}</TableCell>
                    <TableCell>
                      <span className="text-sm" title={getModuleNames(question)}>
                        {truncateText(getModuleNames(question), 30)}
                      </span>
                    </TableCell>
                    <TableCell>
                      <span className="text-sm text-gray-500">
                        {getLectureName(question)}
                      </span>
                    </TableCell>
                    {canManageQuestions && (
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => router.push(`/question-bank/${question.id}`)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setQuestionToDelete(question.id)
                              setShowDeleteDialog(true)
                            }}
                          >
                            <Trash2 className="h-4 w-4 text-red-500" />
                          </Button>
                        </div>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Total count */}
      {!loading && questions.length > 0 && (
        <p className="text-sm text-gray-500">
          Showing {questions.length} question{questions.length !== 1 ? 's' : ''}
        </p>
      )}

      {/* Create Question Dialog */}
      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Add Question to Bank</DialogTitle>
            <DialogDescription>
              Create a new question that can be reused across multiple exams
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4">
            {/* Module Selection */}
            <div>
              <Label>Module(s) - Select one or more</Label>
              <div className="border rounded-md p-3 max-h-40 overflow-y-auto space-y-2 mt-1">
                {sections.length === 0 ? (
                  <p className="text-sm text-gray-500">No modules available. Select a course first.</p>
                ) : (
                  sections.map(section => (
                    <div key={section.id} className="flex items-center space-x-2">
                      <Checkbox
                        id={`module-${section.id}`}
                        checked={formData.selectedModuleIds.includes(section.id)}
                        onCheckedChange={(checked) => {
                          if (checked) {
                            setFormData({
                              ...formData,
                              selectedModuleIds: [...formData.selectedModuleIds, section.id]
                            })
                          } else {
                            setFormData({
                              ...formData,
                              selectedModuleIds: formData.selectedModuleIds.filter(id => id !== section.id)
                            })
                          }
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

            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>Question Type</Label>
                <Select 
                  value={formData.questionType} 
                  onValueChange={(v) => setFormData({...formData, questionType: v as any})}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="MULTIPLE_CHOICE">Multiple Choice</SelectItem>
                    <SelectItem value="TRUE_FALSE">True/False</SelectItem>
                    <SelectItem value="SHORT_ANSWER">Short Answer</SelectItem>
                    <SelectItem value="ESSAY">Essay</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              
              <div>
                <Label>Difficulty</Label>
                <Select 
                  value={formData.difficultyLevel} 
                  onValueChange={(v) => setFormData({...formData, difficultyLevel: v as any})}
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
              />
            </div>
            
            <div>
              <Label>Points</Label>
              <Input
                type="number"
                min={1}
                value={formData.points}
                onChange={(e) => setFormData({...formData, points: parseInt(e.target.value) || 1})}
              />
            </div>
            
            {formData.questionType === 'MULTIPLE_CHOICE' && (
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
                      />
                      <Input
                        value={opt.text}
                        onChange={(e) => {
                          const newOptions = [...formData.options]
                          newOptions[idx].text = e.target.value
                          setFormData({...formData, options: newOptions})
                        }}
                        placeholder={`Option ${idx + 1}`}
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}
            
            {formData.questionType === 'TRUE_FALSE' && (
              <div>
                <Label>Correct Answer</Label>
                <Select 
                  value={formData.tentativeAnswer} 
                  onValueChange={(v) => setFormData({...formData, tentativeAnswer: v})}
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
            
            {(formData.questionType === 'SHORT_ANSWER' || formData.questionType === 'ESSAY') && (
              <div>
                <Label>Expected Answer (for grading reference)</Label>
                <Textarea
                  value={formData.tentativeAnswer}
                  onChange={(e) => setFormData({...formData, tentativeAnswer: e.target.value})}
                  placeholder="Enter expected answer..."
                  rows={3}
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
              />
            </div>
          </div>
          
          <DialogFooter>
            <Button variant="outline" onClick={() => {
              setShowCreateDialog(false)
              resetForm()
            }}>
              Cancel
            </Button>
            <Button onClick={handleCreateQuestion} disabled={saving}>
              {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Create Question
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Question</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete this question? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeleteDialog(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDeleteQuestion}>
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
