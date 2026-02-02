'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Sparkles, Loader2, ArrowLeft, Shield, FileStack, File, CheckCircle, AlertCircle } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, coursesApi, examsApi, type BatchExamGenerationRequest } from '@/lib/api'
import { SectionMultiSelect } from '@/components/exams/SectionMultiSelect'
import { QuestionPreview } from '@/components/exams/QuestionPreview'
import type { Course, Chapter } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

type GenerationMode = 'single' | 'batch'
type DifficultyLevel = 'EASY' | 'MEDIUM' | 'HARD' | 'ANY'

export default function NewExamPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user } = useAuth()
  const canUseAI = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'
  
  // Check if user can create exams
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const canCreateExams = !isInstructor && !isSupportStaff
  
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [courses, setCourses] = useState<Course[]>([])
  const [sections, setSections] = useState<Chapter[]>([])
  const [batches, setBatches] = useState<any[]>([]) // Student sections/batches
  const [classes, setClasses] = useState<any[]>([]) // Student classes
  
  // Generation mode state
  const [generationMode, setGenerationMode] = useState<GenerationMode>('single')
  const [selectedSectionIds, setSelectedSectionIds] = useState<string[]>([])
  const [batchResult, setBatchResult] = useState<any>(null)
  
  const [formData, setFormData] = useState({
    courseId: '',
    title: '',
    description: '',
    instructions: '',
    moduleIds: [] as string[],
    reviewMethod: 'INSTRUCTOR' as 'INSTRUCTOR' | 'AI' | 'BOTH',
    assignmentType: 'all' as 'all' | 'class' | 'section', // How to assign exam (single mode)
    classId: '',
    sectionId: '',
    numberOfQuestions: 10,
    difficulty: 'ANY' as DifficultyLevel,
    // Custom difficulty distribution settings
    useDifficultyDistribution: false,
    difficultyDistribution: { EASY: 3, MEDIUM: 4, HARD: 3 } as Record<string, number>,
    useScoreOverride: false,
    scorePerDifficulty: { EASY: 1, MEDIUM: 2, HARD: 3 } as Record<string, number>,
    randomizeQuestions: false,
    randomizeMcqOptions: false,
    // Passing score
    passingScorePercentage: 70,
    // Proctoring settings
    enableProctoring: false,
    proctoringMode: 'BASIC_MONITORING' as 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING',
    photoIntervalSeconds: 30,
    requireIdentityVerification: false,
    blockCopyPaste: false,
    blockTabSwitch: false,
    maxTabSwitchesAllowed: 3,
    // Timing mode
    timingMode: 'FIXED_WINDOW' as 'FIXED_WINDOW' | 'FLEXIBLE_START',
    // Duration (in minutes for UI, converted to seconds for API)
    durationMinutes: 60,
    // Start and end times for FIXED_WINDOW mode (batch generation)
    startTime: '',
    endTime: ''
  })

  useEffect(() => {
    loadCourses()
  }, [])

  useEffect(() => {
    if (formData.courseId) {
      loadSections(formData.courseId)
      loadBatches(formData.courseId)
      loadClasses(formData.courseId)
    } else {
      setBatches([])
      setClasses([])
    }
  }, [formData.courseId])

  const loadCourses = async () => {
    try {
      const data = await coursesApi.listCourses()
      setCourses(Array.isArray(data) ? data : [])
    } catch (error) {
      console.error('Failed to load courses:', error)
    }
  }

  const loadSections = async (courseId: string) => {
    try {
      const data = await coursesApi.getChapters(courseId)
      setSections(Array.isArray(data) ? data : [])
    } catch (error) {
      console.error('Failed to load sections:', error)
      setSections([])
    }
  }

  const loadBatches = async (courseId: string) => {
    try {
      const response = await apiClient.get(`/api/exams/courses/${courseId}/sections`)
      const data = Array.isArray(response) ? response : (response as any)?.data || []
      setBatches(data)
    } catch (error) {
      console.error('Failed to load batches:', error)
      setBatches([])
    }
  }

  const loadClasses = async (courseId: string) => {
    try {
      const response = await apiClient.get(`/api/exams/courses/${courseId}/classes`)
      const data = Array.isArray(response) ? response : (response as any)?.data || []
      setClasses(data)
    } catch (error) {
      console.error('Failed to load classes:', error)
      setClasses([])
    }
  }

  const handleSubmit = async () => {
    if (!formData.courseId || !formData.title) {
      toast({
        title: 'Validation Error',
        description: 'Please fill in all required fields',
        variant: 'destructive'
      })
      return
    }

    setLoading(true)
    try {
      const response = await apiClient.post<any>('/api/exams', {
        courseId: formData.courseId,
        title: formData.title,
        description: formData.description,
        instructions: formData.instructions,
        moduleIds: formData.moduleIds,
        reviewMethod: formData.reviewMethod,
        classId: formData.assignmentType === 'class' ? formData.classId : null,
        sectionId: formData.assignmentType === 'section' ? formData.sectionId : null,
        randomizeQuestions: formData.randomizeQuestions,
        randomizeMcqOptions: formData.randomizeMcqOptions,
        passingScorePercentage: formData.passingScorePercentage,
        enableProctoring: formData.enableProctoring,
        proctoringMode: formData.enableProctoring ? formData.proctoringMode : 'DISABLED',
        photoIntervalSeconds: formData.photoIntervalSeconds,
        requireIdentityVerification: formData.requireIdentityVerification,
        blockCopyPaste: formData.blockCopyPaste,
        blockTabSwitch: formData.blockTabSwitch,
        maxTabSwitchesAllowed: formData.maxTabSwitchesAllowed,
        timingMode: formData.timingMode,
        timeLimitSeconds: formData.durationMinutes * 60
      })
      const exam = (response as any)?.data || response
      
      if (!exam || !exam.id) {
        throw new Error('Failed to get exam ID from response')
      }
      
      toast({
        title: 'Success',
        description: 'Exam created successfully'
      })
      router.push(`/exams/${exam.id}`)
    } catch (error) {
      console.error('Failed to create exam:', error)
      toast({
        title: 'Error',
        description: 'Failed to create exam',
        variant: 'destructive'
      })
    } finally {
      setLoading(false)
    }
  }

  const handleGenerateWithAI = async () => {
    if (!formData.courseId || !formData.title) {
      toast({
        title: 'Validation Error',
        description: 'Please fill in course and title first',
        variant: 'destructive'
      })
      return
    }

    if (formData.moduleIds.length === 0) {
      toast({
        title: 'Validation Error',
        description: 'Please select at least one module for AI generation',
        variant: 'destructive'
      })
      return
    }

    setGenerating(true)
    try {
      const response = await apiClient.post<any>('/api/exams', {
        courseId: formData.courseId,
        title: formData.title,
        description: formData.description,
        instructions: formData.instructions,
        moduleIds: formData.moduleIds,
        reviewMethod: formData.reviewMethod,
        classId: formData.assignmentType === 'class' ? formData.classId : null,
        sectionId: formData.assignmentType === 'section' ? formData.sectionId : null,
        randomizeQuestions: formData.randomizeQuestions,
        randomizeMcqOptions: formData.randomizeMcqOptions,
        passingScorePercentage: formData.passingScorePercentage,
        enableProctoring: formData.enableProctoring,
        proctoringMode: formData.enableProctoring ? formData.proctoringMode : 'DISABLED',
        photoIntervalSeconds: formData.photoIntervalSeconds,
        requireIdentityVerification: formData.requireIdentityVerification,
        blockCopyPaste: formData.blockCopyPaste,
        blockTabSwitch: formData.blockTabSwitch,
        maxTabSwitchesAllowed: formData.maxTabSwitchesAllowed,
        timingMode: formData.timingMode,
        timeLimitSeconds: formData.durationMinutes * 60
      })
      
      const exam = (response as any)?.data || response
      
      if (!exam || !exam.id) {
        throw new Error('Failed to get exam ID from response')
      }

      await apiClient.post(`/api/exams/${exam.id}/generate`, {
        numberOfQuestions: formData.numberOfQuestions,
        difficulty: formData.difficulty === 'ANY' ? 'INTERMEDIATE' : formData.difficulty
      })

      toast({
        title: 'Success',
        description: 'Exam created and questions generated successfully'
      })
      router.push(`/exams/${exam.id}`)
    } catch (error) {
      console.error('Failed to generate exam:', error)
      toast({
        title: 'Error',
        description: 'Failed to generate exam',
        variant: 'destructive'
      })
    } finally {
      setGenerating(false)
    }
  }

  const handleBatchGenerate = async () => {
    if (!formData.courseId || !formData.title) {
      toast({
        title: 'Validation Error',
        description: 'Please fill in course and title',
        variant: 'destructive'
      })
      return
    }

    if (formData.moduleIds.length === 0) {
      toast({
        title: 'Validation Error',
        description: 'Please select at least one module for question selection',
        variant: 'destructive'
      })
      return
    }

    if (selectedSectionIds.length === 0) {
      toast({
        title: 'Validation Error',
        description: 'Please select at least one section for batch generation',
        variant: 'destructive'
      })
      return
    }

    // Validate start/end times for FIXED_WINDOW mode
    if (formData.timingMode === 'FIXED_WINDOW') {
      if (!formData.startTime || !formData.endTime) {
        toast({
          title: 'Validation Error',
          description: 'Please set start and end times for Fixed Window mode',
          variant: 'destructive'
        })
        return
      }
      if (new Date(formData.endTime) <= new Date(formData.startTime)) {
        toast({
          title: 'Validation Error',
          description: 'End time must be after start time',
          variant: 'destructive'
        })
        return
      }
    }

    setGenerating(true)
    setBatchResult(null)
    
    try {
      const request: BatchExamGenerationRequest = {
        courseId: formData.courseId,
        title: formData.title,
        description: formData.description,
        instructions: formData.instructions,
        sectionIds: selectedSectionIds,
        moduleIds: formData.moduleIds,
        generationCriteria: {
          // When using custom distribution, let distribution determine total count
          numberOfQuestions: formData.useDifficultyDistribution 
            ? undefined 
            : formData.numberOfQuestions,
          // Only set single difficulty level when NOT using distribution
          difficultyLevel: formData.useDifficultyDistribution 
            ? undefined 
            : (formData.difficulty === 'ANY' ? undefined : formData.difficulty),
          // Include distribution when enabled
          difficultyDistribution: formData.useDifficultyDistribution 
            ? formData.difficultyDistribution 
            : undefined,
          // Only include score override when BOTH toggles are enabled
          scorePerDifficulty: (formData.useDifficultyDistribution && formData.useScoreOverride)
            ? formData.scorePerDifficulty 
            : undefined,
          randomize: true,
          uniquePerSection: true
        },
        examSettings: {
          reviewMethod: formData.reviewMethod,
          randomizeQuestions: formData.randomizeQuestions,
          randomizeMcqOptions: formData.randomizeMcqOptions,
          passingScorePercentage: formData.passingScorePercentage,
          enableProctoring: formData.enableProctoring,
          proctoringMode: formData.enableProctoring ? formData.proctoringMode : 'DISABLED',
          photoIntervalSeconds: formData.photoIntervalSeconds,
          requireIdentityVerification: formData.requireIdentityVerification,
          blockCopyPaste: formData.blockCopyPaste,
          blockTabSwitch: formData.blockTabSwitch,
          maxTabSwitchesAllowed: formData.maxTabSwitchesAllowed,
          timingMode: formData.timingMode,
          timeLimitSeconds: formData.timingMode === 'FLEXIBLE_START' ? formData.durationMinutes * 60 : undefined,
          startTime: formData.timingMode === 'FIXED_WINDOW' && formData.startTime ? new Date(formData.startTime).toISOString() : undefined,
          endTime: formData.timingMode === 'FIXED_WINDOW' && formData.endTime ? new Date(formData.endTime).toISOString() : undefined
        }
      }

      const response = await examsApi.batchGenerate(request)
      const result = (response as any)?.data || response
      setBatchResult(result)

      if (result.totalCreated > 0) {
        toast({
          title: 'Batch Generation Complete',
          description: `Created ${result.totalCreated} exam(s) for ${result.totalCreated} section(s)`
        })
      } else {
        toast({
          title: 'Batch Generation Failed',
          description: result.errors?.join(', ') || 'No exams were created',
          variant: 'destructive'
        })
      }
    } catch (error: any) {
      console.error('Failed to batch generate exams:', error)
      toast({
        title: 'Error',
        description: error?.message || 'Failed to batch generate exams',
        variant: 'destructive'
      })
    } finally {
      setGenerating(false)
    }
  }

  const toggleModule = (moduleId: string) => {
    setFormData(prev => ({
      ...prev,
      moduleIds: prev.moduleIds.includes(moduleId)
        ? prev.moduleIds.filter(id => id !== moduleId)
        : [...prev.moduleIds, moduleId]
    }))
  }

  // Block access for instructors and support staff
  if (!canCreateExams) {
    return (
      <div className="space-y-3">
        <div className="flex items-center gap-4">
          <Button variant="ghost" onClick={() => router.back()}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
        </div>
        <Card>
          <CardContent className="py-12 text-center">
            <h2 className="text-xl font-bold text-gray-900 mb-2">Access Denied</h2>
            <p className="text-gray-600">You do not have permission to create exams.</p>
            <p className="text-sm text-gray-500 mt-4">Only admins and content managers can create exams.</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  // If batch result is available, show success page
  if (batchResult && batchResult.totalCreated > 0) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-4">
          <Button variant="ghost" onClick={() => router.push('/exams')}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Exams
          </Button>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CheckCircle className="h-6 w-6 text-green-600" />
              Batch Generation Complete
            </CardTitle>
            <CardDescription>
              Created {batchResult.totalCreated} of {batchResult.totalRequested} requested exams
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {batchResult.exams?.map((exam: any) => (
                <div 
                  key={exam.examId} 
                  className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                >
                  <div>
                    <div className="font-medium">{exam.title}</div>
                    <div className="text-sm text-gray-500">
                      {exam.sectionName} - {exam.questionCount} questions
                    </div>
                  </div>
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => router.push(`/exams/${exam.examId}`)}
                  >
                    View Exam
                  </Button>
                </div>
              ))}
              
              {batchResult.errors?.length > 0 && (
                <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <div className="flex items-center gap-2 text-yellow-800 font-medium mb-2">
                    <AlertCircle className="h-4 w-4" />
                    Some sections had errors
                  </div>
                  <ul className="text-sm text-yellow-700 list-disc list-inside">
                    {batchResult.errors.map((error: string, idx: number) => (
                      <li key={idx}>{error}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
            
            <div className="mt-6 flex gap-3">
              <Button onClick={() => router.push('/exams')}>
                View All Exams
              </Button>
              <Button variant="outline" onClick={() => setBatchResult(null)}>
                Create More Exams
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
      </div>

      {/* Generation Mode Tabs */}
      <Tabs value={generationMode} onValueChange={(v) => setGenerationMode(v as GenerationMode)}>
        <TabsList className="grid w-full grid-cols-2 max-w-md">
          <TabsTrigger value="single" className="flex items-center gap-2">
            <File className="h-4 w-4" />
            Single Exam
          </TabsTrigger>
          <TabsTrigger value="batch" className="flex items-center gap-2">
            <FileStack className="h-4 w-4" />
            Batch Generate
          </TabsTrigger>
        </TabsList>

        <div className="mt-4">
          {generationMode === 'batch' && (
            <Card className="mb-4 border-blue-200 bg-blue-50">
              <CardContent className="py-3">
                <p className="text-sm text-blue-800">
                  <strong>Batch Mode:</strong> Create separate exams for multiple sections at once. 
                  Each section will get a different randomized question selection from the question bank.
                </p>
              </CardContent>
            </Card>
          )}
        </div>

        <div className="grid gap-6 md:grid-cols-2">
          {/* Left Column - Exam Details */}
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Exam Details</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <Label htmlFor="course">Course *</Label>
                  <Select value={formData.courseId} onValueChange={(value) => {
                    setFormData(prev => ({ ...prev, courseId: value, moduleIds: [] }))
                    setSelectedSectionIds([])
                  }}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a course" />
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
                  <Label htmlFor="title">Title *</Label>
                  <Input
                    id="title"
                    value={formData.title}
                    onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                    placeholder={generationMode === 'batch' ? "Base title (section name will be appended)" : "Enter exam title"}
                  />
                  {generationMode === 'batch' && formData.title && (
                    <p className="text-xs text-gray-500 mt-1">
                      Example: &quot;{formData.title} - Section A&quot;
                    </p>
                  )}
                </div>

                <div>
                  <Label htmlFor="description">Description</Label>
                  <Textarea
                    id="description"
                    value={formData.description}
                    onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                    placeholder="Enter exam description"
                    rows={2}
                  />
                </div>

                <div>
                  <Label htmlFor="instructions">Instructions</Label>
                  <Textarea
                    id="instructions"
                    value={formData.instructions}
                    onChange={(e) => setFormData(prev => ({ ...prev, instructions: e.target.value }))}
                    placeholder="Enter exam instructions"
                    rows={2}
                  />
                </div>

                <div>
                  <Label htmlFor="passingScorePercentage">Passing Score (%)</Label>
                  <Input
                    id="passingScorePercentage"
                    type="number"
                    value={formData.passingScorePercentage}
                    onChange={(e) => setFormData(prev => ({ 
                      ...prev, 
                      passingScorePercentage: Math.min(100, Math.max(0, parseInt(e.target.value) || 0))
                    }))}
                    min={0}
                    max={100}
                    placeholder="70"
                  />
                  <p className="text-sm text-muted-foreground mt-1">
                    Students must score at least this percentage to pass
                  </p>
                </div>

                <div>
                  <Label htmlFor="reviewMethod">Review Method</Label>
                  <Select value={formData.reviewMethod} onValueChange={(value: any) => 
                    setFormData(prev => ({ ...prev, reviewMethod: value }))}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INSTRUCTOR">Instructor Review</SelectItem>
                      <SelectItem value="AI">AI Review</SelectItem>
                      <SelectItem value="BOTH">Both (AI + Instructor)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label htmlFor="timingMode">Timing Mode</Label>
                  <Select value={formData.timingMode} onValueChange={(value: any) => 
                    setFormData(prev => ({ ...prev, timingMode: value }))}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="FIXED_WINDOW">Fixed Window</SelectItem>
                      <SelectItem value="FLEXIBLE_START">Flexible Start</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="text-sm text-muted-foreground mt-1">
                    {formData.timingMode === 'FIXED_WINDOW' 
                      ? 'Exam has a fixed start and end time. All students must complete within this window.'
                      : 'Each student gets a fixed duration from when they start the exam.'}
                  </p>
                </div>

                {formData.timingMode === 'FLEXIBLE_START' ? (
                  <div>
                    <Label htmlFor="durationMinutes">
                      Exam Duration (minutes) <span className="text-red-500">*</span>
                    </Label>
                    <Input
                      id="durationMinutes"
                      type="number"
                      value={formData.durationMinutes}
                      onChange={(e) => setFormData(prev => ({ 
                        ...prev, 
                        durationMinutes: parseInt(e.target.value) || 60 
                      }))}
                      min={1}
                      max={480}
                      placeholder="60"
                    />
                    <p className="text-sm text-muted-foreground mt-1">
                      Each student gets this many minutes from when they start.
                    </p>
                  </div>
                ) : generationMode === 'batch' ? (
                  <div className="space-y-3">
                    <div>
                      <Label htmlFor="startTime">
                        Start Time <span className="text-red-500">*</span>
                      </Label>
                      <Input
                        id="startTime"
                        type="datetime-local"
                        value={formData.startTime}
                        onChange={(e) => setFormData(prev => ({ 
                          ...prev, 
                          startTime: e.target.value 
                        }))}
                      />
                    </div>
                    <div>
                      <Label htmlFor="endTime">
                        End Time <span className="text-red-500">*</span>
                      </Label>
                      <Input
                        id="endTime"
                        type="datetime-local"
                        value={formData.endTime}
                        onChange={(e) => setFormData(prev => ({ 
                          ...prev, 
                          endTime: e.target.value 
                        }))}
                      />
                    </div>
                    <p className="text-sm text-muted-foreground">
                      All exams will share the same start and end time window.
                    </p>
                  </div>
                ) : (
                  <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg">
                    <p className="text-sm text-blue-800">
                      <strong>Note:</strong> Start and end times will be set when you schedule the exam after creation.
                      The exam duration is calculated from the scheduled window.
                    </p>
                  </div>
                )}

                <div className="space-y-3 border-t pt-3">
                  <Label>Randomization Settings</Label>
                  <div className="flex items-center space-x-2">
                    <Checkbox
                      id="randomizeQuestions"
                      checked={formData.randomizeQuestions}
                      onCheckedChange={(checked) => 
                        setFormData(prev => ({ ...prev, randomizeQuestions: checked === true }))
                      }
                    />
                    <label htmlFor="randomizeQuestions" className="text-sm">
                      Randomize question order
                    </label>
                  </div>
                  <div className="flex items-center space-x-2">
                    <Checkbox
                      id="randomizeMcqOptions"
                      checked={formData.randomizeMcqOptions}
                      onCheckedChange={(checked) => 
                        setFormData(prev => ({ ...prev, randomizeMcqOptions: checked === true }))
                      }
                    />
                    <label htmlFor="randomizeMcqOptions" className="text-sm">
                      Randomize MCQ options
                    </label>
                  </div>
                </div>

                {/* Single mode - Student Audience */}
                {generationMode === 'single' && (
                  <>
                    <div>
                      <Label htmlFor="assignmentType">Student Audience</Label>
                      <Select value={formData.assignmentType} onValueChange={(value: any) => 
                        setFormData(prev => ({ ...prev, assignmentType: value, classId: '', sectionId: '' }))}>
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

                    {formData.assignmentType === 'class' && (
                      <div>
                        <Label>Select Class</Label>
                        <Select value={formData.classId} onValueChange={(value) => 
                          setFormData(prev => ({ ...prev, classId: value }))}>
                          <SelectTrigger>
                            <SelectValue placeholder="Choose a class" />
                          </SelectTrigger>
                          <SelectContent>
                            {classes.length === 0 ? (
                              <SelectItem value="__none__" disabled>No classes found</SelectItem>
                            ) : (
                              classes.map(cls => (
                                <SelectItem key={cls.id} value={cls.id}>
                                  {cls.name} ({cls.code})
                                </SelectItem>
                              ))
                            )}
                          </SelectContent>
                        </Select>
                      </div>
                    )}

                    {formData.assignmentType === 'section' && (
                      <div>
                        <Label>Select Section/Batch</Label>
                        <Select value={formData.sectionId} onValueChange={(value) => 
                          setFormData(prev => ({ ...prev, sectionId: value }))}>
                          <SelectTrigger>
                            <SelectValue placeholder="Choose a section" />
                          </SelectTrigger>
                          <SelectContent>
                            {batches.length === 0 ? (
                              <SelectItem value="__none__" disabled>No sections found</SelectItem>
                            ) : (
                              batches.map(batch => (
                                <SelectItem key={batch.id} value={batch.id}>
                                  {batch.name}
                                </SelectItem>
                              ))
                            )}
                          </SelectContent>
                        </Select>
                      </div>
                    )}
                  </>
                )}

                {/* Batch mode - Multi-section selection */}
                {generationMode === 'batch' && (
                  <div>
                    <Label className="mb-2 block">Select Sections *</Label>
                    <SectionMultiSelect
                      courseId={formData.courseId}
                      selectedIds={selectedSectionIds}
                      onChange={setSelectedSectionIds}
                      disabled={generating}
                    />
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Proctoring Settings */}
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Shield className="h-5 w-5" />
                  <CardTitle>Proctoring Settings</CardTitle>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="enableProctoring">Enable Proctoring</Label>
                    <p className="text-sm text-gray-500">Monitor students during the exam</p>
                  </div>
                  <Switch
                    id="enableProctoring"
                    checked={formData.enableProctoring}
                    onCheckedChange={(checked) => 
                      setFormData(prev => ({ ...prev, enableProctoring: checked }))
                    }
                  />
                </div>

                {formData.enableProctoring && (
                  <div className="space-y-4 pt-4 border-t">
                    <div>
                      <Label>Proctoring Mode</Label>
                      <Select 
                        value={formData.proctoringMode} 
                        onValueChange={(value: any) => 
                          setFormData(prev => ({ ...prev, proctoringMode: value }))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="BASIC_MONITORING">Basic Monitoring</SelectItem>
                          <SelectItem value="WEBCAM_RECORDING">Webcam Recording</SelectItem>
                          <SelectItem value="LIVE_PROCTORING">Live Proctoring</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>

                    {formData.proctoringMode === 'WEBCAM_RECORDING' && (
                      <div>
                        <Label>Photo Capture Interval (seconds)</Label>
                        <Input
                          type="number"
                          value={formData.photoIntervalSeconds}
                          onChange={(e) => setFormData(prev => ({ 
                            ...prev, 
                            photoIntervalSeconds: parseInt(e.target.value) || 30 
                          }))}
                          min={10}
                          max={300}
                        />
                      </div>
                    )}

                    <div className="flex items-center justify-between">
                      <Label>Require Identity Verification</Label>
                      <Switch
                        checked={formData.requireIdentityVerification}
                        onCheckedChange={(checked) => 
                          setFormData(prev => ({ ...prev, requireIdentityVerification: checked }))
                        }
                      />
                    </div>

                    <div className="flex items-center justify-between">
                      <Label>Block Copy & Paste</Label>
                      <Switch
                        checked={formData.blockCopyPaste}
                        onCheckedChange={(checked) => 
                          setFormData(prev => ({ ...prev, blockCopyPaste: checked }))
                        }
                      />
                    </div>

                    <div className="flex items-center justify-between">
                      <Label>Auto-Submit on Tab Switch</Label>
                      <Switch
                        checked={formData.blockTabSwitch}
                        onCheckedChange={(checked) => 
                          setFormData(prev => ({ ...prev, blockTabSwitch: checked }))
                        }
                      />
                    </div>

                    {!formData.blockTabSwitch && (
                      <div>
                        <Label>Max Tab Switches Allowed</Label>
                        <Input
                          type="number"
                          value={formData.maxTabSwitchesAllowed}
                          onChange={(e) => setFormData(prev => ({ 
                            ...prev, 
                            maxTabSwitchesAllowed: parseInt(e.target.value) || 3 
                          }))}
                          min={0}
                          max={20}
                        />
                      </div>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Right Column - Modules and Question Settings */}
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>
                  Select Modules {generationMode === 'batch' ? '*' : '(Optional)'}
                </CardTitle>
                <CardDescription>
                  {generationMode === 'batch' 
                    ? 'Questions will be selected from these modules for each section'
                    : 'Required for AI generation. You can create an empty exam and add questions manually.'}
                </CardDescription>
              </CardHeader>
              <CardContent>
                {!formData.courseId ? (
                  <p className="text-gray-500 text-sm">Please select a course first</p>
                ) : sections.length === 0 ? (
                  <p className="text-gray-500 text-sm">No modules found for this course</p>
                ) : (
                  <div className="space-y-2 max-h-64 overflow-y-auto">
                    {sections.map(section => (
                      <div key={section.id} className="flex items-center space-x-2">
                        <Checkbox
                          checked={formData.moduleIds.includes(section.id)}
                          onCheckedChange={() => toggleModule(section.id)}
                        />
                        <Label className="flex-1 cursor-pointer" onClick={() => toggleModule(section.id)}>
                          {section.title}
                        </Label>
                      </div>
                    ))}
                  </div>
                )}
                {formData.moduleIds.length > 0 && (
                  <div className="mt-3 pt-3 border-t">
                    <Badge variant="secondary">
                      {formData.moduleIds.length} module(s) selected
                    </Badge>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Question Generation Settings */}
            <Card>
              <CardHeader>
                <CardTitle>Question Generation</CardTitle>
                <CardDescription>
                  {generationMode === 'batch' 
                    ? 'Configure how questions are selected from the bank for each section'
                    : 'Settings for AI question generation'}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {/* Custom Distribution Toggle (Batch mode only) */}
                {generationMode === 'batch' && (
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="useDifficultyDistribution">Custom Difficulty Distribution</Label>
                      <p className="text-sm text-gray-500">Specify exact count per difficulty level</p>
                    </div>
                    <Switch
                      id="useDifficultyDistribution"
                      checked={formData.useDifficultyDistribution}
                      onCheckedChange={(checked) => 
                        setFormData(prev => ({ 
                          ...prev, 
                          useDifficultyDistribution: checked,
                          // Reset score override when distribution is disabled
                          useScoreOverride: checked ? prev.useScoreOverride : false
                        }))
                      }
                    />
                  </div>
                )}

                {/* Standard mode OR when custom distribution is OFF */}
                {(generationMode === 'single' || !formData.useDifficultyDistribution) && (
                  <>
                    <div>
                      <Label>Number of Questions</Label>
                      <Input
                        type="number"
                        value={formData.numberOfQuestions}
                        onChange={(e) => setFormData(prev => ({ 
                          ...prev, 
                          numberOfQuestions: parseInt(e.target.value) || 10 
                        }))}
                        min={1}
                        max={100}
                      />
                    </div>
                    <div>
                      <Label>Difficulty Level</Label>
                      <Select 
                        value={formData.difficulty} 
                        onValueChange={(value: DifficultyLevel) => 
                          setFormData(prev => ({ ...prev, difficulty: value }))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="Any difficulty" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ANY">Any Difficulty</SelectItem>
                          <SelectItem value="EASY">Easy</SelectItem>
                          <SelectItem value="MEDIUM">Medium</SelectItem>
                          <SelectItem value="HARD">Hard</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </>
                )}

                {/* Custom Distribution UI (Batch mode with distribution enabled) */}
                {generationMode === 'batch' && formData.useDifficultyDistribution && (
                  <div className="space-y-4 pt-2 border-t">
                    <Label className="text-sm font-medium">Questions per Difficulty</Label>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <Label className="text-xs text-green-600">Easy</Label>
                        <Input
                          type="number"
                          value={formData.difficultyDistribution.EASY || 0}
                          onChange={(e) => setFormData(prev => ({
                            ...prev,
                            difficultyDistribution: {
                              ...prev.difficultyDistribution,
                              EASY: parseInt(e.target.value) || 0
                            }
                          }))}
                          min={0}
                          max={50}
                        />
                      </div>
                      <div>
                        <Label className="text-xs text-yellow-600">Medium</Label>
                        <Input
                          type="number"
                          value={formData.difficultyDistribution.MEDIUM || 0}
                          onChange={(e) => setFormData(prev => ({
                            ...prev,
                            difficultyDistribution: {
                              ...prev.difficultyDistribution,
                              MEDIUM: parseInt(e.target.value) || 0
                            }
                          }))}
                          min={0}
                          max={50}
                        />
                      </div>
                      <div>
                        <Label className="text-xs text-red-600">Hard</Label>
                        <Input
                          type="number"
                          value={formData.difficultyDistribution.HARD || 0}
                          onChange={(e) => setFormData(prev => ({
                            ...prev,
                            difficultyDistribution: {
                              ...prev.difficultyDistribution,
                              HARD: parseInt(e.target.value) || 0
                            }
                          }))}
                          min={0}
                          max={50}
                        />
                      </div>
                    </div>
                    <div className="text-sm text-gray-500">
                      Total: {(formData.difficultyDistribution.EASY || 0) + 
                              (formData.difficultyDistribution.MEDIUM || 0) + 
                              (formData.difficultyDistribution.HARD || 0)} questions
                    </div>

                    {/* Score Override Toggle */}
                    <div className="pt-3 border-t">
                      <div className="flex items-center space-x-2">
                        <Checkbox
                          id="useScoreOverride"
                          checked={formData.useScoreOverride}
                          onCheckedChange={(checked) => 
                            setFormData(prev => ({ ...prev, useScoreOverride: checked === true }))
                          }
                        />
                        <div>
                          <label htmlFor="useScoreOverride" className="text-sm font-medium cursor-pointer">
                            Override default question scores
                          </label>
                          <p className="text-xs text-gray-500">
                            Set custom points based on difficulty level
                          </p>
                        </div>
                      </div>
                    </div>

                    {/* Score per Difficulty (when override is enabled) */}
                    {formData.useScoreOverride && (
                      <div className="space-y-3 pl-6">
                        <Label className="text-sm font-medium">Points per Difficulty</Label>
                        <div className="grid grid-cols-3 gap-3">
                          <div>
                            <Label className="text-xs text-green-600">Easy</Label>
                            <Input
                              type="number"
                              value={formData.scorePerDifficulty.EASY || 1}
                              onChange={(e) => setFormData(prev => ({
                                ...prev,
                                scorePerDifficulty: {
                                  ...prev.scorePerDifficulty,
                                  EASY: parseInt(e.target.value) || 1
                                }
                              }))}
                              min={1}
                              max={100}
                            />
                          </div>
                          <div>
                            <Label className="text-xs text-yellow-600">Medium</Label>
                            <Input
                              type="number"
                              value={formData.scorePerDifficulty.MEDIUM || 2}
                              onChange={(e) => setFormData(prev => ({
                                ...prev,
                                scorePerDifficulty: {
                                  ...prev.scorePerDifficulty,
                                  MEDIUM: parseInt(e.target.value) || 2
                                }
                              }))}
                              min={1}
                              max={100}
                            />
                          </div>
                          <div>
                            <Label className="text-xs text-red-600">Hard</Label>
                            <Input
                              type="number"
                              value={formData.scorePerDifficulty.HARD || 3}
                              onChange={(e) => setFormData(prev => ({
                                ...prev,
                                scorePerDifficulty: {
                                  ...prev.scorePerDifficulty,
                                  HARD: parseInt(e.target.value) || 3
                                }
                              }))}
                              min={1}
                              max={100}
                            />
                          </div>
                        </div>
                        <div className="text-sm text-gray-500">
                          Max possible score: {
                            ((formData.difficultyDistribution.EASY || 0) * (formData.scorePerDifficulty.EASY || 1)) +
                            ((formData.difficultyDistribution.MEDIUM || 0) * (formData.scorePerDifficulty.MEDIUM || 2)) +
                            ((formData.difficultyDistribution.HARD || 0) * (formData.scorePerDifficulty.HARD || 3))
                          } points
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Question Bank Preview (Batch mode only) */}
            {generationMode === 'batch' && (
              <QuestionPreview
                courseId={formData.courseId}
                moduleIds={formData.moduleIds}
                numberOfQuestions={formData.useDifficultyDistribution 
                  ? (formData.difficultyDistribution.EASY || 0) + 
                    (formData.difficultyDistribution.MEDIUM || 0) + 
                    (formData.difficultyDistribution.HARD || 0)
                  : formData.numberOfQuestions}
                difficultyLevel={formData.useDifficultyDistribution 
                  ? undefined 
                  : (formData.difficulty === 'ANY' ? undefined : formData.difficulty)}
              />
            )}
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-4 mt-6">
          {generationMode === 'single' ? (
            <>
              <Button onClick={handleSubmit} disabled={loading || generating}>
                {loading && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                Create Exam
              </Button>
              {canUseAI && (
                <Button onClick={handleGenerateWithAI} disabled={loading || generating} variant="default">
                  {generating ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : 
                   <Sparkles className="h-4 w-4 mr-2" />}
                  Create & Generate with AI
                </Button>
              )}
            </>
          ) : (
            <Button 
              onClick={handleBatchGenerate} 
              disabled={loading || generating || selectedSectionIds.length === 0 || formData.moduleIds.length === 0}
              className="min-w-[200px]"
            >
              {generating ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Generating...
                </>
              ) : (
                <>
                  <FileStack className="h-4 w-4 mr-2" />
                  Generate {selectedSectionIds.length} Paper{selectedSectionIds.length !== 1 ? 's' : ''}
                </>
              )}
            </Button>
          )}
        </div>
      </Tabs>
    </div>
  )
}
