'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import { Sparkles, Loader2, ArrowLeft } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, coursesApi } from '@/lib/api'
import type { Course, Section, Chapter } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

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
  const [formData, setFormData] = useState({
    courseId: '',
    title: '',
    description: '',
    instructions: '',
    moduleIds: [] as string[],
    reviewMethod: 'INSTRUCTOR' as 'INSTRUCTOR' | 'AI' | 'BOTH',
    numberOfQuestions: 10,
    difficulty: 'INTERMEDIATE'
  })

  useEffect(() => {
    loadCourses()
  }, [])

  useEffect(() => {
    if (formData.courseId) {
      loadSections(formData.courseId)
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

  const handleSubmit = async () => {
    if (!formData.courseId || !formData.title) {
      toast({
        title: 'Validation Error',
        description: 'Please fill in all required fields',
        variant: 'destructive'
      })
      return
    }

    // Modules are optional - only required for AI generation

    setLoading(true)
    try {
      const response = await apiClient.post<any>('/api/exams', {
        courseId: formData.courseId,
        title: formData.title,
        description: formData.description,
        instructions: formData.instructions,
        moduleIds: formData.moduleIds,
        reviewMethod: formData.reviewMethod
      })
      const exam = (response as any)?.data || response
      
      if (!exam || !exam.id) {
        console.error('Invalid exam response:', response)
        throw new Error(`Failed to get exam ID from response. Response: ${JSON.stringify(response)}`)
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

    // First create the exam, then generate questions
    setGenerating(true)
    try {
      // Create exam
      const response = await apiClient.post<any>('/api/exams', {
        courseId: formData.courseId,
        title: formData.title,
        description: formData.description,
        instructions: formData.instructions,
        moduleIds: formData.moduleIds,
        reviewMethod: formData.reviewMethod
      })
      
      // Handle response - apiClient might return data directly or wrapped
      const exam = (response as any)?.data || response
      
      if (!exam || !exam.id) {
        console.error('Invalid exam response:', { response, exam })
        throw new Error(`Failed to get exam ID from response. Response: ${JSON.stringify(response)}`)
      }

      // Generate questions with AI
      await apiClient.post(`/api/exams/${exam.id}/generate`, {
        numberOfQuestions: formData.numberOfQuestions,
        difficulty: formData.difficulty
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

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-4">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
      </div>

        <div className="grid gap-6 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Exam Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <Label htmlFor="course">Course *</Label>
                <Select value={formData.courseId} onValueChange={(value) => 
                  setFormData(prev => ({ ...prev, courseId: value, moduleIds: [] }))}>
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
                  placeholder="Enter exam title"
                />
              </div>

              <div>
                <Label htmlFor="description">Description</Label>
                <Textarea
                  id="description"
                  value={formData.description}
                  onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                  placeholder="Enter exam description"
                  rows={3}
                />
              </div>

              <div>
                <Label htmlFor="instructions">Instructions</Label>
                <Textarea
                  id="instructions"
                  value={formData.instructions}
                  onChange={(e) => setFormData(prev => ({ ...prev, instructions: e.target.value }))}
                  placeholder="Enter exam instructions"
                  rows={3}
                />
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
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Select Modules (Optional)</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-gray-600 mb-4">
                Modules are only required if you plan to generate questions with AI. You can create an empty exam and add questions manually.
              </p>
              {!formData.courseId ? (
                <p className="text-gray-500 text-sm">Please select a course first</p>
              ) : sections.length === 0 ? (
                <p className="text-gray-500 text-sm">No modules found for this course</p>
              ) : (
                <div className="space-y-2 max-h-96 overflow-y-auto">
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
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>AI Generation Options</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="numberOfQuestions">Number of Questions</Label>
                <Input
                  id="numberOfQuestions"
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
                <Label htmlFor="difficulty">Difficulty</Label>
                <Select value={formData.difficulty} onValueChange={(value) => 
                  setFormData(prev => ({ ...prev, difficulty: value }))}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="BEGINNER">Beginner</SelectItem>
                    <SelectItem value="INTERMEDIATE">Intermediate</SelectItem>
                    <SelectItem value="ADVANCED">Advanced</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-4">
          <Button onClick={handleSubmit} disabled={loading || generating}>
            {loading ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
            Create Exam
          </Button>
          {canUseAI && (
            <Button onClick={handleGenerateWithAI} disabled={loading || generating} variant="default">
              {generating ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : 
               <Sparkles className="h-4 w-4 mr-2" />}
              Create & Generate with AI
            </Button>
          )}
        </div>
    </div>
  )
}
