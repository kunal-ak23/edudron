'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ArrowLeft, Loader2 } from 'lucide-react'
import { projectsApi, coursesApi, sectionsApi } from '@/lib/api'
import type { Course } from '@kunal-ak23/edudron-shared-utils'
import type { Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

export default function CreateProjectPage() {
  const router = useRouter()
  const { toast } = useToast()

  const [courseId, setCourseId] = useState<string>('')
  const [sectionId, setSectionId] = useState<string>('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [maxMarks, setMaxMarks] = useState(100)
  const [submissionCutoff, setSubmissionCutoff] = useState('')
  const [lateSubmissionAllowed, setLateSubmissionAllowed] = useState(false)

  const [courses, setCourses] = useState<Course[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [loadingSections, setLoadingSections] = useState(false)
  const [submitting, setSubmitting] = useState(false)

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

  // Load sections when course changes
  useEffect(() => {
    if (!courseId || courseId === 'none') {
      setSections([])
      setSectionId('')
      return
    }
    const loadSections = async () => {
      setLoadingSections(true)
      try {
        const data = await coursesApi.getSections(courseId)
        setSections(data)
      } catch {
        setSections([])
      } finally {
        setLoadingSections(false)
      }
    }
    loadSections()
  }, [courseId])

  const handleSubmit = async () => {
    if (!sectionId || !title.trim()) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Please select a section and provide a title.',
      })
      return
    }

    setSubmitting(true)
    try {
      const project = await projectsApi.createProject({
        courseId: courseId && courseId !== 'none' ? courseId : undefined,
        sectionId,
        title: title.trim(),
        description: description.trim() || undefined,
        maxMarks,
        submissionCutoff: submissionCutoff || undefined,
        lateSubmissionAllowed,
      })
      toast({
        title: 'Project Created',
        description: 'The project has been created successfully.',
      })
      router.push(`/projects/${project.id}`)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to create project',
        description: extractErrorMessage(error),
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <Card className="mb-3">
        <CardHeader>
          <CardTitle>Create Project</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {/* Course */}
            <div className="space-y-2">
              <Label>Course</Label>
              <Select value={courseId} onValueChange={(val) => { setCourseId(val); setSectionId('') }}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a course..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">None</SelectItem>
                  {courses.map((course) => (
                    <SelectItem key={course.id} value={course.id}>
                      {course.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Section */}
            <div className="space-y-2">
              <Label>
                Section <span className="text-destructive">*</span>
              </Label>
              {loadingSections ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading sections...
                </div>
              ) : (
                <Select value={sectionId} onValueChange={setSectionId}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select a section..." />
                  </SelectTrigger>
                  <SelectContent>
                    {sections.map((section) => (
                      <SelectItem key={section.id} value={section.id}>
                        {section.title || section.name || section.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {!courseId && (
                <p className="text-xs text-muted-foreground">Select a course first to see available sections.</p>
              )}
            </div>

            {/* Title */}
            <div className="space-y-2">
              <Label>
                Title <span className="text-destructive">*</span>
              </Label>
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Enter project title"
              />
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label>Description</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Project description (optional)"
                rows={3}
              />
            </div>

            {/* Max Marks */}
            <div className="space-y-2">
              <Label>Max Marks</Label>
              <Input
                type="number"
                min={1}
                value={maxMarks}
                onChange={(e) => setMaxMarks(parseInt(e.target.value) || 100)}
              />
            </div>

            {/* Submission Cutoff */}
            <div className="space-y-2">
              <Label>Submission Cutoff</Label>
              <Input
                type="datetime-local"
                value={submissionCutoff}
                onChange={(e) => setSubmissionCutoff(e.target.value)}
              />
            </div>

            {/* Late Submission */}
            <div className="flex items-center space-x-3">
              <Switch
                checked={lateSubmissionAllowed}
                onCheckedChange={setLateSubmissionAllowed}
              />
              <Label>Allow Late Submission</Label>
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
          disabled={submitting || !sectionId || !title.trim()}
        >
          {submitting ? (
            <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Creating...</>
          ) : (
            'Create Project'
          )}
        </Button>
      </div>
    </div>
  )
}
