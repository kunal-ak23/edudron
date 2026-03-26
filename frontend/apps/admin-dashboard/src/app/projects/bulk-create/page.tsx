'use client'

import { useState, useEffect, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ArrowLeft, ArrowRight, Loader2, AlertTriangle, CheckCircle2 } from 'lucide-react'
import { projectsApi, projectQuestionsApi, coursesApi, sectionsApi } from '@/lib/api'
import type { Course, ProjectQuestionDTO } from '@kunal-ak23/edudron-shared-utils'
import type { Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

const TOTAL_STEPS = 5

interface SectionInfo {
  id: string
  name: string
  studentCount: number
  isBacklog: boolean
  classId: string
}

export default function BulkCreateProjectPage() {
  const router = useRouter()
  const { toast } = useToast()

  // Wizard state
  const [step, setStep] = useState(1)

  // Step 1: Course
  const [courses, setCourses] = useState<Course[]>([])
  const [courseId, setCourseId] = useState('')
  const [loadingCourses, setLoadingCourses] = useState(true)
  const [questionCount, setQuestionCount] = useState<number | null>(null)
  const [loadingQuestions, setLoadingQuestions] = useState(false)

  // Step 2: Sections
  const [sectionInfos, setSectionInfos] = useState<SectionInfo[]>([])
  const [selectedSectionIds, setSelectedSectionIds] = useState<Set<string>>(new Set())
  const [loadingSections, setLoadingSections] = useState(false)

  // Step 3: Group Config
  const [groupSize, setGroupSize] = useState(3)

  // Step 4: Project Details
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [maxMarks, setMaxMarks] = useState(100)
  const [submissionCutoff, setSubmissionCutoff] = useState('')
  const [lateSubmissionAllowed, setLateSubmissionAllowed] = useState(false)

  // Step 5: Submit
  const [submitting, setSubmitting] = useState(false)

  // Derived values
  const selectedSections = useMemo(
    () => sectionInfos.filter((s) => selectedSectionIds.has(s.id)),
    [sectionInfos, selectedSectionIds]
  )

  const totalStudents = useMemo(
    () => selectedSections.reduce((sum, s) => sum + (s.studentCount || 0), 0),
    [selectedSections]
  )

  const totalGroups = useMemo(
    () => (groupSize > 0 ? Math.ceil(totalStudents / groupSize) : 0),
    [totalStudents, groupSize]
  )

  const selectedCourse = useMemo(
    () => courses.find((c) => c.id === courseId),
    [courses, courseId]
  )

  // Load courses on mount
  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await coursesApi.listCourses()
        setCourses(data)
      } catch {
        toast({ variant: 'destructive', title: 'Failed to load courses' })
      } finally {
        setLoadingCourses(false)
      }
    }
    loadCourses()
  }, [])

  // Load question count when course changes
  useEffect(() => {
    if (!courseId) {
      setQuestionCount(null)
      return
    }
    const loadQuestions = async () => {
      setLoadingQuestions(true)
      try {
        const result = await projectQuestionsApi.listQuestions({ courseId })
        setQuestionCount(result.totalElements)
      } catch {
        setQuestionCount(0)
      } finally {
        setLoadingQuestions(false)
      }
    }
    loadQuestions()
  }, [courseId])

  // Load sections when entering step 2
  const loadSections = async () => {
    if (!courseId) return
    setLoadingSections(true)
    setSectionInfos([])
    setSelectedSectionIds(new Set())
    try {
      const sectionIds = await projectsApi.getSectionsByCourse(courseId)
      const infos: SectionInfo[] = []
      for (const id of sectionIds) {
        try {
          const section = await sectionsApi.getSection(id)
          infos.push({
            id: section.id,
            name: section.name || section.id,
            studentCount: section.studentCount || 0,
            isBacklog: section.isBacklog || false,
            classId: section.classId || '',
          })
        } catch {
          // Skip sections that fail to load
        }
      }
      setSectionInfos(infos)
      // Auto-select all sections
      setSelectedSectionIds(new Set(infos.map((s) => s.id)))
    } catch {
      setSectionInfos([])
    } finally {
      setLoadingSections(false)
    }
  }

  const handleNext = () => {
    if (step === 1 && !courseId) {
      toast({ variant: 'destructive', title: 'Please select a course' })
      return
    }
    if (step === 2 && selectedSectionIds.size === 0) {
      toast({ variant: 'destructive', title: 'Please select at least one section' })
      return
    }
    if (step === 3 && groupSize < 1) {
      toast({ variant: 'destructive', title: 'Group size must be at least 1' })
      return
    }
    if (step === 4 && !title.trim()) {
      toast({ variant: 'destructive', title: 'Please enter a project title' })
      return
    }

    const nextStep = step + 1
    if (nextStep === 2 && sectionInfos.length === 0) {
      loadSections()
    }
    setStep(nextStep)
  }

  const handleBack = () => {
    if (step > 1) setStep(step - 1)
  }

  const toggleSection = (sectionId: string) => {
    setSelectedSectionIds((prev) => {
      const next = new Set(prev)
      if (next.has(sectionId)) {
        next.delete(sectionId)
      } else {
        next.add(sectionId)
      }
      return next
    })
  }

  const toggleAllSections = () => {
    if (selectedSectionIds.size === sectionInfos.length) {
      setSelectedSectionIds(new Set())
    } else {
      setSelectedSectionIds(new Set(sectionInfos.map((s) => s.id)))
    }
  }

  const handleSubmit = async () => {
    setSubmitting(true)
    try {
      const project = await projectsApi.bulkSetup({
        courseId,
        sectionIds: Array.from(selectedSectionIds),
        groupSize,
        title: title.trim(),
        description: description.trim() || undefined,
        maxMarks,
        submissionCutoff: submissionCutoff ? new Date(submissionCutoff).toISOString() : undefined,
        lateSubmissionAllowed,
      })
      toast({
        title: 'Project Created',
        description: 'Bulk project setup completed successfully.',
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

  const canProceed = () => {
    switch (step) {
      case 1: return !!courseId
      case 2: return selectedSectionIds.size > 0
      case 3: return groupSize >= 1
      case 4: return !!title.trim()
      case 5: return true
      default: return false
    }
  }

  return (
    <div>
      <Card className="mb-3">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Bulk Project Setup</CardTitle>
            <Badge variant="outline">
              Step {step} of {TOTAL_STEPS}
            </Badge>
          </div>
          {/* Step indicator */}
          <div className="flex gap-1.5 mt-3">
            {Array.from({ length: TOTAL_STEPS }, (_, i) => (
              <div
                key={i}
                className={`h-1.5 flex-1 rounded-full transition-colors ${
                  i + 1 <= step ? 'bg-primary' : 'bg-muted'
                }`}
              />
            ))}
          </div>
          <p className="text-sm text-muted-foreground mt-2">
            {step === 1 && 'Select the course for this project'}
            {step === 2 && 'Choose which sections to include'}
            {step === 3 && 'Configure student group sizes'}
            {step === 4 && 'Enter project details'}
            {step === 5 && 'Review and create the project'}
          </p>
        </CardHeader>

        <CardContent>
          {/* Step 1: Course Selection */}
          {step === 1 && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>Course <span className="text-destructive">*</span></Label>
                {loadingCourses ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading courses...
                  </div>
                ) : (
                  <Select value={courseId} onValueChange={(val) => {
                    setCourseId(val)
                    setSectionInfos([])
                    setSelectedSectionIds(new Set())
                  }}>
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
                )}
              </div>

              {courseId && (
                <div className="rounded-md border p-3">
                  {loadingQuestions ? (
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" /> Checking problem statements...
                    </div>
                  ) : questionCount !== null && (
                    <div className="flex items-center gap-2 text-sm">
                      {questionCount > 0 ? (
                        <>
                          <CheckCircle2 className="h-4 w-4 text-green-600" />
                          <span>
                            <strong>{questionCount}</strong> problem statement{questionCount !== 1 ? 's' : ''} available for this course
                          </span>
                        </>
                      ) : (
                        <>
                          <AlertTriangle className="h-4 w-4 text-amber-500" />
                          <span className="text-amber-700">
                            No problem statements found for this course. Groups will be created but no statements will be assigned.
                          </span>
                        </>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Step 2: Section Selection */}
          {step === 2 && (
            <div className="space-y-4">
              {loadingSections ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading sections with enrolled students...
                </div>
              ) : sectionInfos.length === 0 ? (
                <div className="rounded-md border border-amber-200 bg-amber-50 p-4">
                  <div className="flex items-center gap-2 text-sm text-amber-800">
                    <AlertTriangle className="h-4 w-4" />
                    No sections with enrolled students found for this course.
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex items-center justify-between">
                    <button
                      type="button"
                      onClick={toggleAllSections}
                      className="text-sm text-primary hover:underline"
                    >
                      {selectedSectionIds.size === sectionInfos.length ? 'Deselect all' : 'Select all'}
                    </button>
                    <p className="text-sm text-muted-foreground">
                      <strong>{totalStudents}</strong> student{totalStudents !== 1 ? 's' : ''} across{' '}
                      <strong>{selectedSectionIds.size}</strong> section{selectedSectionIds.size !== 1 ? 's' : ''} selected
                    </p>
                  </div>

                  <div className="space-y-2">
                    {sectionInfos.map((section) => (
                      <div
                        key={section.id}
                        className="flex items-center gap-3 rounded-md border p-3 hover:bg-muted/50 transition-colors"
                      >
                        <Checkbox
                          checked={selectedSectionIds.has(section.id)}
                          onCheckedChange={() => toggleSection(section.id)}
                        />
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-sm">{section.name}</span>
                            {section.isBacklog && (
                              <Badge variant="secondary" className="text-xs">Backlog</Badge>
                            )}
                          </div>
                        </div>
                        <span className="text-sm text-muted-foreground">
                          {section.studentCount} student{section.studentCount !== 1 ? 's' : ''}
                        </span>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          )}

          {/* Step 3: Group Config */}
          {step === 3 && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>Group Size <span className="text-destructive">*</span></Label>
                <Input
                  type="number"
                  min={1}
                  value={groupSize}
                  onChange={(e) => setGroupSize(Math.max(1, parseInt(e.target.value) || 1))}
                  className="max-w-[200px]"
                />
                <p className="text-xs text-muted-foreground">
                  Number of students per group
                </p>
              </div>

              <div className="rounded-md border p-4 space-y-3">
                <h4 className="font-medium text-sm">Group Summary</h4>
                <div className="grid grid-cols-2 gap-y-2 text-sm">
                  <span className="text-muted-foreground">Total students:</span>
                  <span className="font-medium">{totalStudents}</span>
                  <span className="text-muted-foreground">Group size:</span>
                  <span className="font-medium">{groupSize}</span>
                  <span className="text-muted-foreground">Estimated groups:</span>
                  <span className="font-medium">{totalGroups}</span>
                  <span className="text-muted-foreground">Problem statements:</span>
                  <span className="font-medium">{questionCount ?? '...'}</span>
                </div>

                {questionCount !== null && questionCount > 0 && totalGroups > 0 && (
                  <div className="rounded-md bg-muted p-3 text-sm">
                    {totalGroups <= questionCount ? (
                      <p>
                        Problem statements will be assigned round-robin across {totalGroups} group{totalGroups !== 1 ? 's' : ''}. Each group gets a unique statement.
                      </p>
                    ) : (
                      <p>
                        There are more groups ({totalGroups}) than problem statements ({questionCount}). Statements will be reused across groups via round-robin assignment.
                      </p>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Step 4: Project Details */}
          {step === 4 && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>Title <span className="text-destructive">*</span></Label>
                <Input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Enter project title"
                />
              </div>

              <div className="space-y-2">
                <Label>Description</Label>
                <Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Project description (optional)"
                  rows={3}
                />
              </div>

              <div className="space-y-2">
                <Label>Max Marks</Label>
                <Input
                  type="number"
                  min={1}
                  value={maxMarks}
                  onChange={(e) => setMaxMarks(parseInt(e.target.value) || 100)}
                  className="max-w-[200px]"
                />
              </div>

              <div className="space-y-2">
                <Label>Submission Cutoff</Label>
                <Input
                  type="datetime-local"
                  value={submissionCutoff}
                  onChange={(e) => setSubmissionCutoff(e.target.value)}
                  className="max-w-[300px]"
                />
              </div>

              <div className="flex items-center space-x-3">
                <Switch
                  checked={lateSubmissionAllowed}
                  onCheckedChange={setLateSubmissionAllowed}
                />
                <Label>Allow Late Submission</Label>
              </div>
            </div>
          )}

          {/* Step 5: Review & Create */}
          {step === 5 && (
            <div className="space-y-4">
              <div className="rounded-md border p-4 space-y-4">
                <div>
                  <h4 className="font-medium text-sm text-muted-foreground mb-1">Course</h4>
                  <p className="text-sm">{selectedCourse?.title || courseId}</p>
                </div>

                <div>
                  <h4 className="font-medium text-sm text-muted-foreground mb-1">Sections</h4>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedSections.map((s) => (
                      <Badge key={s.id} variant="secondary">
                        {s.name} ({s.studentCount})
                      </Badge>
                    ))}
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    {totalStudents} students across {selectedSectionIds.size} sections
                  </p>
                </div>

                <div>
                  <h4 className="font-medium text-sm text-muted-foreground mb-1">Groups</h4>
                  <p className="text-sm">
                    Group size: {groupSize} | Estimated groups: {totalGroups} | Problem statements: {questionCount ?? 0}
                  </p>
                </div>

                <div>
                  <h4 className="font-medium text-sm text-muted-foreground mb-1">Project Details</h4>
                  <div className="grid grid-cols-2 gap-y-1.5 text-sm">
                    <span className="text-muted-foreground">Title:</span>
                    <span>{title}</span>
                    {description && (
                      <>
                        <span className="text-muted-foreground">Description:</span>
                        <span>{description}</span>
                      </>
                    )}
                    <span className="text-muted-foreground">Max Marks:</span>
                    <span>{maxMarks}</span>
                    <span className="text-muted-foreground">Submission Cutoff:</span>
                    <span>{submissionCutoff ? new Date(submissionCutoff).toLocaleString() : 'Not set'}</span>
                    <span className="text-muted-foreground">Late Submission:</span>
                    <span>{lateSubmissionAllowed ? 'Allowed' : 'Not allowed'}</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Navigation buttons */}
      <div className="flex justify-between">
        <Button
          variant="outline"
          onClick={step === 1 ? () => router.back() : handleBack}
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          {step === 1 ? 'Cancel' : 'Back'}
        </Button>

        {step < TOTAL_STEPS ? (
          <Button onClick={handleNext} disabled={!canProceed()}>
            Next
            <ArrowRight className="h-4 w-4 ml-2" />
          </Button>
        ) : (
          <Button
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting ? (
              <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Creating...</>
            ) : (
              'Create Project'
            )}
          </Button>
        )}
      </div>
    </div>
  )
}
