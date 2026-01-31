'use client'

import { useEffect, useState, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
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
import { Search, Plus, Loader2, CheckCircle, Filter, Wand2 } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient } from '@/lib/api'

interface QuestionBank {
  id: string
  courseId: string
  moduleId: string
  questionType: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY' | 'MATCHING'
  questionText: string
  defaultPoints: number
  difficultyLevel?: 'EASY' | 'MEDIUM' | 'HARD'
  options?: { id: string; optionText: string; isCorrect: boolean }[]
}

interface ExamQuestion {
  id: string
  questionId: string
  sequence: number
  pointsOverride?: number
  question?: QuestionBank
}

interface Section {
  id: string
  title: string
}

interface QuestionPickerProps {
  examId: string
  courseId: string
  moduleIds: string[]
  open: boolean
  onClose: () => void
  onQuestionsAdded: () => void
}

export function QuestionPicker({ examId, courseId, moduleIds, open, onClose, onQuestionsAdded }: QuestionPickerProps) {
  const { toast } = useToast()
  
  const [sections, setSections] = useState<Section[]>([])
  const [questions, setQuestions] = useState<QuestionBank[]>([])
  const [existingQuestionIds, setExistingQuestionIds] = useState<Set<string>>(new Set())
  const [selectedQuestions, setSelectedQuestions] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)
  const [adding, setAdding] = useState(false)
  
  // Filters
  const [selectedModule, setSelectedModule] = useState<string>('')
  const [selectedDifficulty, setSelectedDifficulty] = useState<string>('')
  const [searchKeyword, setSearchKeyword] = useState('')
  
  // Auto-generate state
  const [showAutoGenerate, setShowAutoGenerate] = useState(false)
  const [autoGenConfig, setAutoGenConfig] = useState({
    numberOfQuestions: 10,
    difficultyLevel: '' as string,
    randomize: true,
    clearExisting: false
  })

  // Load sections for the course
  const loadSections = useCallback(async () => {
    try {
      const response = await apiClient.get<any>(`/api/courses/${courseId}`)
      if (response && response.sections) {
        // Filter to only show sections that are in moduleIds
        const filteredSections = moduleIds.length > 0
          ? response.sections.filter((s: Section) => moduleIds.includes(s.id))
          : response.sections
        setSections(filteredSections)
      }
    } catch (error) {
      console.error('Failed to load sections:', error)
    }
  }, [courseId, moduleIds])

  // Load existing exam questions
  const loadExistingQuestions = useCallback(async () => {
    try {
      const response = await apiClient.get<ExamQuestion[]>(`/api/exams/${examId}/questions`)
      if (Array.isArray(response)) {
        const ids = new Set(response.map(q => q.questionId))
        setExistingQuestionIds(ids)
      }
    } catch (error) {
      console.error('Failed to load existing questions:', error)
    }
  }, [examId])

  // Load available questions from bank
  const loadQuestions = useCallback(async () => {
    const searchModuleIds = selectedModule ? [selectedModule] : moduleIds
    if (searchModuleIds.length === 0) {
      setQuestions([])
      return
    }
    
    try {
      setLoading(true)
      
      let url = '/api/question-bank/modules?moduleIds=' + searchModuleIds.join(',')
      
      const response = await apiClient.get<QuestionBank[]>(url)
      
      let filteredQuestions = Array.isArray(response) ? response : []
      
      // Filter by difficulty
      if (selectedDifficulty) {
        filteredQuestions = filteredQuestions.filter(q => q.difficultyLevel === selectedDifficulty)
      }
      
      // Filter by keyword
      if (searchKeyword) {
        const keyword = searchKeyword.toLowerCase()
        filteredQuestions = filteredQuestions.filter(q => 
          q.questionText.toLowerCase().includes(keyword)
        )
      }
      
      setQuestions(filteredQuestions)
    } catch (error) {
      console.error('Failed to load questions:', error)
      setQuestions([])
    } finally {
      setLoading(false)
    }
  }, [moduleIds, selectedModule, selectedDifficulty, searchKeyword])

  useEffect(() => {
    if (open) {
      loadSections()
      loadExistingQuestions()
    }
  }, [open, loadSections, loadExistingQuestions])

  useEffect(() => {
    if (open) {
      loadQuestions()
    }
  }, [open, loadQuestions])

  const handleToggleQuestion = (questionId: string) => {
    const newSelected = new Set(selectedQuestions)
    if (newSelected.has(questionId)) {
      newSelected.delete(questionId)
    } else {
      newSelected.add(questionId)
    }
    setSelectedQuestions(newSelected)
  }

  const handleSelectAll = () => {
    const availableIds = questions
      .filter(q => !existingQuestionIds.has(q.id))
      .map(q => q.id)
    setSelectedQuestions(new Set(availableIds))
  }

  const handleDeselectAll = () => {
    setSelectedQuestions(new Set())
  }

  const handleAddSelected = async () => {
    if (selectedQuestions.size === 0) {
      toast({
        title: 'Error',
        description: 'Please select at least one question',
        variant: 'destructive'
      })
      return
    }
    
    setAdding(true)
    try {
      await apiClient.post(`/api/exams/${examId}/questions/add`, {
        questionIds: Array.from(selectedQuestions)
      })
      
      toast({
        title: 'Success',
        description: `Added ${selectedQuestions.size} question(s) to exam`
      })
      
      setSelectedQuestions(new Set())
      onQuestionsAdded()
      onClose()
    } catch (error) {
      console.error('Failed to add questions:', error)
      toast({
        title: 'Error',
        description: 'Failed to add questions to exam',
        variant: 'destructive'
      })
    } finally {
      setAdding(false)
    }
  }

  const handleAutoGenerate = async () => {
    setAdding(true)
    try {
      await apiClient.post(`/api/exams/${examId}/questions/generate`, {
        moduleIds: selectedModule ? [selectedModule] : moduleIds,
        numberOfQuestions: autoGenConfig.numberOfQuestions,
        difficultyLevel: autoGenConfig.difficultyLevel || null,
        randomize: autoGenConfig.randomize,
        clearExisting: autoGenConfig.clearExisting
      })
      
      toast({
        title: 'Success',
        description: 'Exam paper generated successfully'
      })
      
      setShowAutoGenerate(false)
      onQuestionsAdded()
      onClose()
    } catch (error: any) {
      console.error('Failed to generate exam paper:', error)
      toast({
        title: 'Error',
        description: error?.message || 'Failed to generate exam paper',
        variant: 'destructive'
      })
    } finally {
      setAdding(false)
    }
  }

  const getDifficultyBadge = (difficulty?: string) => {
    const colors: Record<string, string> = {
      EASY: 'bg-green-100 text-green-800',
      MEDIUM: 'bg-yellow-100 text-yellow-800',
      HARD: 'bg-red-100 text-red-800'
    }
    return difficulty ? (
      <span className={`px-2 py-0.5 rounded text-xs font-medium ${colors[difficulty] || 'bg-gray-100'}`}>
        {difficulty}
      </span>
    ) : null
  }

  const availableQuestions = questions.filter(q => !existingQuestionIds.has(q.id))

  return (
    <>
      <Dialog open={open && !showAutoGenerate} onOpenChange={onClose}>
        <DialogContent className="max-w-4xl max-h-[90vh] flex flex-col">
          <DialogHeader>
            <DialogTitle>Add Questions from Bank</DialogTitle>
            <DialogDescription>
              Select questions to add to this exam or auto-generate a paper
            </DialogDescription>
          </DialogHeader>
          
          {/* Filters */}
          <div className="flex gap-4 items-end border-b pb-4">
            <div className="flex-1">
              <Label>Module</Label>
              <Select value={selectedModule} onValueChange={setSelectedModule}>
                <SelectTrigger>
                  <SelectValue placeholder="All selected modules" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">All Modules</SelectItem>
                  {sections.map(section => (
                    <SelectItem key={section.id} value={section.id}>
                      {section.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            
            <div className="w-32">
              <Label>Difficulty</Label>
              <Select value={selectedDifficulty} onValueChange={setSelectedDifficulty}>
                <SelectTrigger>
                  <SelectValue placeholder="All" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">All</SelectItem>
                  <SelectItem value="EASY">Easy</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <div className="flex-1">
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
            
            <Button variant="outline" onClick={() => setShowAutoGenerate(true)}>
              <Wand2 className="h-4 w-4 mr-2" />
              Auto Generate
            </Button>
          </div>
          
          {/* Question List */}
          <div className="flex-1 overflow-y-auto min-h-0">
            {loading ? (
              <div className="flex items-center justify-center h-32">
                <Loader2 className="h-6 w-6 animate-spin" />
              </div>
            ) : availableQuestions.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                {questions.length === 0 
                  ? 'No questions found in the question bank for selected modules'
                  : 'All available questions have been added to this exam'}
              </div>
            ) : (
              <div className="space-y-2">
                <div className="flex items-center justify-between py-2 sticky top-0 bg-white z-10">
                  <span className="text-sm text-gray-500">
                    {availableQuestions.length} question(s) available, {selectedQuestions.size} selected
                  </span>
                  <div className="flex gap-2">
                    <Button variant="ghost" size="sm" onClick={handleSelectAll}>
                      Select All
                    </Button>
                    <Button variant="ghost" size="sm" onClick={handleDeselectAll}>
                      Deselect All
                    </Button>
                  </div>
                </div>
                
                {availableQuestions.map(question => (
                  <Card 
                    key={question.id} 
                    className={`cursor-pointer transition-colors ${
                      selectedQuestions.has(question.id) ? 'border-blue-500 bg-blue-50' : ''
                    }`}
                    onClick={() => handleToggleQuestion(question.id)}
                  >
                    <CardContent className="pt-3 pb-3">
                      <div className="flex items-start gap-3">
                        <Checkbox 
                          checked={selectedQuestions.has(question.id)}
                          onCheckedChange={() => handleToggleQuestion(question.id)}
                        />
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <Badge variant="outline" className="text-xs">
                              {question.questionType.replace('_', ' ')}
                            </Badge>
                            {getDifficultyBadge(question.difficultyLevel)}
                            <span className="text-xs text-gray-500">
                              {question.defaultPoints} pt{question.defaultPoints !== 1 ? 's' : ''}
                            </span>
                          </div>
                          <p className="text-sm">{question.questionText}</p>
                          
                          {question.options && question.options.length > 0 && (
                            <div className="mt-2 ml-2 space-y-0.5">
                              {question.options.slice(0, 4).map((opt, idx) => (
                                <div key={opt.id || idx} className="flex items-center gap-1 text-xs text-gray-600">
                                  {opt.isCorrect ? (
                                    <CheckCircle className="h-3 w-3 text-green-600" />
                                  ) : (
                                    <span className="h-3 w-3 rounded-full border border-gray-300 inline-block" />
                                  )}
                                  <span className={opt.isCorrect ? 'text-green-700' : ''}>
                                    {opt.optionText}
                                  </span>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </div>
          
          <DialogFooter className="border-t pt-4">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button 
              onClick={handleAddSelected} 
              disabled={selectedQuestions.size === 0 || adding}
            >
              {adding && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              <Plus className="h-4 w-4 mr-2" />
              Add {selectedQuestions.size} Question{selectedQuestions.size !== 1 ? 's' : ''}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Auto Generate Dialog */}
      <Dialog open={showAutoGenerate} onOpenChange={setShowAutoGenerate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Auto-Generate Exam Paper</DialogTitle>
            <DialogDescription>
              Automatically select questions from the question bank based on criteria
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4">
            <div>
              <Label>Number of Questions</Label>
              <Input
                type="number"
                min={1}
                max={100}
                value={autoGenConfig.numberOfQuestions}
                onChange={(e) => setAutoGenConfig({
                  ...autoGenConfig, 
                  numberOfQuestions: parseInt(e.target.value) || 10
                })}
              />
            </div>
            
            <div>
              <Label>Difficulty Level (optional)</Label>
              <Select 
                value={autoGenConfig.difficultyLevel} 
                onValueChange={(v) => setAutoGenConfig({...autoGenConfig, difficultyLevel: v})}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Any difficulty" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">Any</SelectItem>
                  <SelectItem value="EASY">Easy</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <div className="flex items-center gap-2">
              <Checkbox 
                id="randomize"
                checked={autoGenConfig.randomize}
                onCheckedChange={(v) => setAutoGenConfig({...autoGenConfig, randomize: !!v})}
              />
              <Label htmlFor="randomize">Randomize question selection</Label>
            </div>
            
            <div className="flex items-center gap-2">
              <Checkbox 
                id="clearExisting"
                checked={autoGenConfig.clearExisting}
                onCheckedChange={(v) => setAutoGenConfig({...autoGenConfig, clearExisting: !!v})}
              />
              <Label htmlFor="clearExisting">Clear existing questions first</Label>
            </div>
          </div>
          
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowAutoGenerate(false)}>
              Cancel
            </Button>
            <Button onClick={handleAutoGenerate} disabled={adding}>
              {adding && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              <Wand2 className="h-4 w-4 mr-2" />
              Generate Paper
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
