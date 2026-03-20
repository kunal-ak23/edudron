'use client'

import { useState, useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ArrowLeft, Loader2 } from 'lucide-react'
import { projectQuestionsApi, coursesApi } from '@/lib/api'
import type { Course } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

export default function NewProjectQuestionPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const editId = searchParams.get('editId')

  const [courseId, setCourseId] = useState<string>('')
  const [projectNumber, setProjectNumber] = useState('')
  const [title, setTitle] = useState('')
  const [problemStatement, setProblemStatement] = useState('')
  const [keyTechnologies, setKeyTechnologies] = useState('')
  const [tags, setTags] = useState('')
  const [difficulty, setDifficulty] = useState<string>('')

  const [courses, setCourses] = useState<Course[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [loadingEdit, setLoadingEdit] = useState(false)

  // Load courses
  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await coursesApi.listCourses()
        setCourses(data)
      } catch { /* Non-critical */ }
    }
    loadCourses()
  }, [])

  // Load existing question for editing
  useEffect(() => {
    if (!editId) return
    const loadQuestion = async () => {
      setLoadingEdit(true)
      try {
        const question = await projectQuestionsApi.getQuestion(editId)
        setCourseId(question.courseId || '')
        setProjectNumber(question.projectNumber || '')
        setTitle(question.title)
        setProblemStatement(question.problemStatement)
        setKeyTechnologies(question.keyTechnologies?.join(', ') || '')
        setTags(question.tags?.join(', ') || '')
        setDifficulty(question.difficulty || '')
      } catch (error) {
        toast({
          variant: 'destructive',
          title: 'Failed to load question',
          description: extractErrorMessage(error),
        })
      } finally {
        setLoadingEdit(false)
      }
    }
    loadQuestion()
  }, [editId, toast])

  const handleSubmit = async () => {
    if (!courseId || !title.trim() || !problemStatement.trim()) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Please fill in course, title, and problem statement.',
      })
      return
    }

    setSubmitting(true)
    try {
      const data = {
        courseId,
        projectNumber: projectNumber.trim() || undefined,
        title: title.trim(),
        problemStatement: problemStatement.trim(),
        keyTechnologies: keyTechnologies
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean),
        tags: tags
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean),
        difficulty: difficulty || undefined,
        isActive: true,
      }

      if (editId) {
        await projectQuestionsApi.updateQuestion(editId, data)
        toast({ title: 'Question Updated' })
      } else {
        await projectQuestionsApi.createQuestion(data)
        toast({ title: 'Question Created' })
      }
      router.push('/project-questions')
    } catch (error) {
      toast({
        variant: 'destructive',
        title: editId ? 'Failed to update question' : 'Failed to create question',
        description: extractErrorMessage(error),
      })
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingEdit) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  return (
    <div>
      <Card className="mb-3">
        <CardHeader>
          <CardTitle>{editId ? 'Edit Question' : 'Add Project Question'}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {/* Course */}
            <div className="space-y-2">
              <Label>
                Course <span className="text-destructive">*</span>
              </Label>
              <Select value={courseId} onValueChange={setCourseId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a course..." />
                </SelectTrigger>
                <SelectContent>
                  {courses.map((course) => (
                    <SelectItem key={course.id} value={course.id}>
                      {course.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Project Number */}
            <div className="space-y-2">
              <Label>Project Number</Label>
              <Input
                value={projectNumber}
                onChange={(e) => setProjectNumber(e.target.value)}
                placeholder="e.g. DA-01, AAI-01"
              />
              <p className="text-xs text-muted-foreground">Optional identifier for the project (e.g., DA-01).</p>
            </div>

            {/* Title */}
            <div className="space-y-2">
              <Label>
                Title <span className="text-destructive">*</span>
              </Label>
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Brief title for the problem statement"
              />
            </div>

            {/* Problem Statement */}
            <div className="space-y-2">
              <Label>
                Problem Statement <span className="text-destructive">*</span>
              </Label>
              <Textarea
                value={problemStatement}
                onChange={(e) => setProblemStatement(e.target.value)}
                placeholder="Detailed description of the project problem..."
                rows={6}
              />
            </div>

            {/* Key Technologies */}
            <div className="space-y-2">
              <Label>Key Technologies</Label>
              <Input
                value={keyTechnologies}
                onChange={(e) => setKeyTechnologies(e.target.value)}
                placeholder="React, Node.js, PostgreSQL (comma-separated)"
              />
              <p className="text-xs text-muted-foreground">Separate multiple technologies with commas.</p>
            </div>

            {/* Tags */}
            <div className="space-y-2">
              <Label>Tags</Label>
              <Input
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="web, backend, database (comma-separated)"
              />
            </div>

            {/* Difficulty */}
            <div className="space-y-2">
              <Label>Difficulty</Label>
              <Select value={difficulty} onValueChange={setDifficulty}>
                <SelectTrigger>
                  <SelectValue placeholder="Select difficulty..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="EASY">Easy</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end space-x-3">
        <Button variant="outline" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          disabled={submitting || !courseId || !title.trim() || !problemStatement.trim()}
        >
          {submitting ? (
            <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> {editId ? 'Updating...' : 'Creating...'}</>
          ) : (
            editId ? 'Update Question' : 'Create Question'
          )}
        </Button>
      </div>
    </div>
  )
}
