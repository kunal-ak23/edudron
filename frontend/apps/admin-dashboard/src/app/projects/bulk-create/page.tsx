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
import { ArrowLeft, ArrowRight, Loader2, AlertTriangle, CheckCircle2, Search, ChevronDown, ChevronUp, Plus, Trash2, Copy } from 'lucide-react'
import { projectsApi, projectQuestionsApi, coursesApi, sectionsApi, classesApi } from '@/lib/api'
import type { Course, ProjectQuestionDTO } from '@kunal-ak23/edudron-shared-utils'
import type { Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

const TOTAL_STEPS = 6

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
  const [questions, setQuestions] = useState<ProjectQuestionDTO[]>([])
  const [loadingQuestions, setLoadingQuestions] = useState(false)
  const [showQuestions, setShowQuestions] = useState(false)
  const [questionSearch, setQuestionSearch] = useState('')
  const [difficultyFilter, setDifficultyFilter] = useState<string>('all')
  const [selectedQuestionIds, setSelectedQuestionIds] = useState<Set<string>>(new Set())

  // Step 2: Sections
  const [sectionInfos, setSectionInfos] = useState<SectionInfo[]>([])
  const [selectedSectionIds, setSelectedSectionIds] = useState<Set<string>>(new Set())
  const [loadingSections, setLoadingSections] = useState(false)

  // Step 3: Group Config
  const [groupSize, setGroupSize] = useState(3)
  const [mixSections, setMixSections] = useState(false)

  // Step 4: Project Details
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [maxMarks, setMaxMarks] = useState(100)
  const [submissionCutoff, setSubmissionCutoff] = useState('')
  const [lateSubmissionAllowed, setLateSubmissionAllowed] = useState(false)

  // Step 5: Events (per-section)
  interface EventEntry {
    name: string
    dateTime: string
    zoomLink: string
    hasMarks: boolean
    maxMarks: number
  }
  const [eventsBySectionId, setEventsBySectionId] = useState<Record<string, EventEntry[]>>({})
  const [activeEventTab, setActiveEventTab] = useState<string>('_global')

  const getEventsForTab = (tabId: string): EventEntry[] => eventsBySectionId[tabId] || []

  const addEventToTab = (tabId: string) => {
    setEventsBySectionId((prev) => ({
      ...prev,
      [tabId]: [...(prev[tabId] || []), { name: '', dateTime: '', zoomLink: '', hasMarks: false, maxMarks: 10 }],
    }))
  }

  const removeEventFromTab = (tabId: string, index: number) => {
    setEventsBySectionId((prev) => ({
      ...prev,
      [tabId]: (prev[tabId] || []).filter((_, i) => i !== index),
    }))
  }

  const updateEventInTab = (tabId: string, index: number, field: keyof EventEntry, value: any) => {
    setEventsBySectionId((prev) => ({
      ...prev,
      [tabId]: (prev[tabId] || []).map((e, i) => i === index ? { ...e, [field]: value } : e),
    }))
  }

  const copyEventsToSection = (fromId: string, toId: string) => {
    const source = eventsBySectionId[fromId] || []
    setEventsBySectionId((prev) => ({
      ...prev,
      [toId]: source.map((e) => ({ ...e })), // deep copy
    }))
  }

  const copyEventsToAllSections = (fromId: string) => {
    const source = eventsBySectionId[fromId] || []
    const updated = { ...eventsBySectionId }
    selectedSections.forEach((s) => {
      if (s.id !== fromId) {
        updated[s.id] = source.map((e) => ({ ...e }))
      }
    })
    setEventsBySectionId(updated)
  }

  // Total events count across all sections
  const totalEventsCount = useMemo(
    () => Object.values(eventsBySectionId).reduce((sum, evts) => sum + evts.filter((e) => e.name.trim()).length, 0),
    [eventsBySectionId]
  )

  // Step 6: Submit
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

  // Compute optimal group distribution: fewer groups, redistribute remainder
  function computeDistribution(students: number, targetSize: number): {
    numGroups: number; label: string
  } {
    if (students <= 0 || targetSize <= 0) return { numGroups: 0, label: '' }
    const numGroups = Math.floor(students / targetSize) || 1
    const remainder = students - numGroups * targetSize
    if (remainder === 0) {
      return { numGroups, label: `${numGroups} group${numGroups !== 1 ? 's' : ''} of ${targetSize}` }
    }
    if (numGroups - remainder > 0) {
      return {
        numGroups,
        label: `${numGroups} groups (${remainder}×${targetSize + 1} + ${numGroups - remainder}×${targetSize})`,
      }
    }
    return {
      numGroups,
      label: `${numGroups} group${numGroups !== 1 ? 's' : ''} of ${targetSize + Math.ceil(remainder / numGroups)}`,
    }
  }

  const perSectionGroups = useMemo(
    () =>
      selectedSections.map((s) => {
        const dist = computeDistribution(s.studentCount, groupSize)
        return { ...s, groups: dist.numGroups, label: dist.label }
      }),
    [selectedSections, groupSize]
  )

  const totalGroups = useMemo(
    () => {
      if (groupSize <= 0) return 0
      if (mixSections || selectedSections.length <= 1) {
        return computeDistribution(totalStudents, groupSize).numGroups
      }
      return perSectionGroups.reduce((sum, s) => sum + s.groups, 0)
    },
    [totalStudents, groupSize, mixSections, selectedSections, perSectionGroups]
  )

  // Group sections by classId for Step 2 UI
  const classGroups = useMemo(() => {
    const groups: { classId: string; className: string; sections: SectionInfo[] }[] = []
    const classMap = new Map<string, SectionInfo[]>()
    const classNameMap = new Map<string, string>()

    sectionInfos.forEach((s) => {
      const cid = s.classId || '_ungrouped'
      if (!classMap.has(cid)) classMap.set(cid, [])
      classMap.get(cid)!.push(s)
      if (s.classId && !classNameMap.has(cid)) {
        // derive class name from section name prefix or use classId
        classNameMap.set(cid, cid)
      }
    })

    classMap.forEach((sections, classId) => {
      groups.push({
        classId,
        className: classNameMap.get(classId) || 'Ungrouped',
        sections,
      })
    })
    return groups
  }, [sectionInfos])

  // Load class names when sections are available
  useEffect(() => {
    if (classGroups.length === 0) return
    const classIds = classGroups.map((g) => g.classId).filter((id) => id !== '_ungrouped')
    if (classIds.length === 0) return

    // Fetch class names for display
    const fetchClassNames = async () => {
      for (const cg of classGroups) {
        if (cg.classId === '_ungrouped') continue
        try {
          // Try fetching class details - the sections already have classId
          // We'll use sectionsApi since we already have section data
          // Class name can be derived from section data pattern
        } catch { /* ignore */ }
      }
    }
    fetchClassNames()
  }, [classGroups])

  const toggleClassSections = (classId: string) => {
    const classSections = classGroups.find((g) => g.classId === classId)?.sections || []
    const classSectionIds = classSections.map((s) => s.id)
    const allSelected = classSectionIds.every((id) => selectedSectionIds.has(id))

    setSelectedSectionIds((prev) => {
      const next = new Set(prev)
      if (allSelected) {
        classSectionIds.forEach((id) => next.delete(id))
      } else {
        classSectionIds.forEach((id) => next.add(id))
      }
      return next
    })
  }

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

  // Load questions when course changes
  useEffect(() => {
    if (!courseId) {
      setQuestionCount(null)
      setQuestions([])
      setShowQuestions(false)
      setSelectedQuestionIds(new Set())
      return
    }
    const loadQuestions = async () => {
      setLoadingQuestions(true)
      try {
        const result = await projectQuestionsApi.listQuestions({ courseId })
        setQuestionCount(result.totalElements)
        setQuestions(result.content || [])
      } catch {
        setQuestionCount(0)
        setQuestions([])
      } finally {
        setLoadingQuestions(false)
      }
    }
    loadQuestions()
  }, [courseId])

  // Filtered questions for the browser
  const filteredQuestions = useMemo(() => {
    let filtered = questions
    if (questionSearch.trim()) {
      const term = questionSearch.toLowerCase()
      filtered = filtered.filter(
        (q) =>
          q.title?.toLowerCase().includes(term) ||
          q.problemStatement?.toLowerCase().includes(term) ||
          q.projectNumber?.toLowerCase().includes(term)
      )
    }
    if (difficultyFilter !== 'all') {
      filtered = filtered.filter((q) => q.difficulty?.toLowerCase() === difficultyFilter.toLowerCase())
    }
    return filtered
  }, [questions, questionSearch, difficultyFilter])

  const availableDifficulties = useMemo(() => {
    const set = new Set<string>()
    questions.forEach((q) => {
      if (q.difficulty) set.add(q.difficulty)
    })
    return Array.from(set).sort()
  }, [questions])

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
    if (step === 5) {
      const hasEmpty = Object.values(eventsBySectionId).some((evts) => evts.some((e) => !e.name.trim()))
      if (hasEmpty) {
        toast({ variant: 'destructive', title: 'Please fill in event names or remove empty events' })
        return
      }
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
      const sectionNameMap: Record<string, string> = {}
      sectionInfos.forEach((s) => { sectionNameMap[s.id] = s.name })

      // Build group count map from computed distribution
      const sectionGroupCountMap: Record<string, number> = {}
      perSectionGroups.forEach((s) => {
        sectionGroupCountMap[s.id] = s.groups
      })

      const project = await projectsApi.bulkSetup({
        courseId,
        sectionIds: Array.from(selectedSectionIds),
        groupSize,
        title: title.trim(),
        description: description.trim() || undefined,
        maxMarks,
        submissionCutoff: submissionCutoff ? new Date(submissionCutoff).toISOString() : undefined,
        lateSubmissionAllowed,
        mixSections,
        sectionNames: sectionNameMap,
        sectionGroupCounts: !mixSections ? sectionGroupCountMap : undefined,
        totalGroupCount: mixSections ? computeDistribution(totalStudents, groupSize).numGroups : undefined,
        selectedQuestionIds: selectedQuestionIds.size > 0 ? Array.from(selectedQuestionIds) : undefined,
        eventsBySectionId: Object.keys(eventsBySectionId).length > 0
          ? Object.fromEntries(
              Object.entries(eventsBySectionId)
                .filter(([_, evts]) => evts.some((e) => e.name.trim()))
                .map(([sid, evts]) => [
                  sid,
                  evts.filter((e) => e.name.trim()).map((e, idx) => ({
                    name: e.name.trim(),
                    dateTime: e.dateTime ? new Date(e.dateTime).toISOString() : undefined,
                    zoomLink: e.zoomLink.trim() || undefined,
                    hasMarks: e.hasMarks,
                    maxMarks: e.hasMarks ? e.maxMarks : undefined,
                    sequence: idx + 1,
                  })),
                ])
            )
          : undefined,
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
      case 5: return true // events are optional
      case 6: return true
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
            {step === 5 && 'Add project events (presentations, reviews, etc.)'}
            {step === 6 && 'Review and create the project'}
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
                <div className="rounded-md border p-3 space-y-3">
                  {loadingQuestions ? (
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" /> Checking problem statements...
                    </div>
                  ) : questionCount !== null && (
                    <>
                      <div className="flex items-center justify-between">
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
                                No problem statements found. Groups will be created but no statements will be assigned.
                              </span>
                            </>
                          )}
                        </div>
                        {questionCount > 0 && (
                          <button
                            type="button"
                            onClick={() => setShowQuestions(!showQuestions)}
                            className="flex items-center gap-1 text-xs text-primary hover:underline"
                          >
                            {showQuestions ? 'Hide' : 'Browse'}
                            {showQuestions ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
                          </button>
                        )}
                      </div>

                      {showQuestions && questionCount > 0 && (
                        <div className="space-y-2">
                          {/* Filters */}
                          <div className="flex gap-2">
                            <div className="relative flex-1">
                              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
                              <Input
                                placeholder="Search by name..."
                                value={questionSearch}
                                onChange={(e) => setQuestionSearch(e.target.value)}
                                className="pl-8 h-8 text-sm"
                              />
                            </div>
                            <Select value={difficultyFilter} onValueChange={setDifficultyFilter}>
                              <SelectTrigger className="w-[140px] h-8 text-sm">
                                <SelectValue placeholder="Difficulty" />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="all">All Levels</SelectItem>
                                {availableDifficulties.map((d) => (
                                  <SelectItem key={d} value={d.toLowerCase()}>{d}</SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>

                          {/* Selection controls */}
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <button
                                type="button"
                                onClick={() => {
                                  if (selectedQuestionIds.size === filteredQuestions.length) {
                                    // Deselect all visible
                                    const newSet = new Set(selectedQuestionIds)
                                    filteredQuestions.forEach((q) => newSet.delete(q.id))
                                    setSelectedQuestionIds(newSet)
                                  } else {
                                    // Select all visible
                                    const newSet = new Set(selectedQuestionIds)
                                    filteredQuestions.forEach((q) => newSet.add(q.id))
                                    setSelectedQuestionIds(newSet)
                                  }
                                }}
                                className="text-xs text-primary hover:underline"
                              >
                                {filteredQuestions.length > 0 && filteredQuestions.every((q) => selectedQuestionIds.has(q.id))
                                  ? 'Deselect all visible'
                                  : 'Select all visible'}
                              </button>
                              {selectedQuestionIds.size > 0 && (
                                <button
                                  type="button"
                                  onClick={() => setSelectedQuestionIds(new Set())}
                                  className="text-xs text-muted-foreground hover:underline"
                                >
                                  Clear selection
                                </button>
                              )}
                            </div>
                            <p className="text-xs text-muted-foreground">
                              {selectedQuestionIds.size > 0 ? (
                                <><strong>{selectedQuestionIds.size}</strong> selected of {questionCount}</>
                              ) : (
                                <>Showing {filteredQuestions.length} of {questionCount} &middot; none selected (all will be used)</>
                              )}
                            </p>
                          </div>

                          {/* Question list */}
                          <div className="max-h-[280px] overflow-y-auto space-y-1.5 pr-1">
                            {filteredQuestions.length === 0 ? (
                              <p className="text-sm text-muted-foreground py-3 text-center">
                                No problem statements match your filters.
                              </p>
                            ) : (
                              filteredQuestions.map((q) => (
                                <div
                                  key={q.id}
                                  onClick={() => {
                                    setSelectedQuestionIds((prev) => {
                                      const next = new Set(prev)
                                      if (next.has(q.id)) {
                                        next.delete(q.id)
                                      } else {
                                        next.add(q.id)
                                      }
                                      return next
                                    })
                                  }}
                                  className={`rounded-md border px-3 py-2 cursor-pointer transition-colors ${
                                    selectedQuestionIds.has(q.id)
                                      ? 'border-primary bg-primary/5'
                                      : 'hover:bg-muted/50'
                                  }`}
                                >
                                  <div className="flex items-start gap-2.5">
                                    <Checkbox
                                      checked={selectedQuestionIds.has(q.id)}
                                      onCheckedChange={() => {
                                        setSelectedQuestionIds((prev) => {
                                          const next = new Set(prev)
                                          if (next.has(q.id)) {
                                            next.delete(q.id)
                                          } else {
                                            next.add(q.id)
                                          }
                                          return next
                                        })
                                      }}
                                      className="mt-0.5 shrink-0"
                                      onClick={(e) => e.stopPropagation()}
                                    />
                                    <div className="flex-1 min-w-0">
                                      <div className="flex items-center gap-2">
                                        {q.projectNumber && (
                                          <span className="text-xs font-mono text-muted-foreground shrink-0">
                                            #{q.projectNumber}
                                          </span>
                                        )}
                                        <span className="text-sm font-medium truncate">{q.title}</span>
                                      </div>
                                      {q.keyTechnologies && q.keyTechnologies.length > 0 && (
                                        <div className="flex flex-wrap gap-1 mt-1">
                                          {q.keyTechnologies.slice(0, 4).map((tech, i) => (
                                            <Badge key={i} variant="secondary" className="text-[10px] px-1.5 py-0">
                                              {tech}
                                            </Badge>
                                          ))}
                                          {q.keyTechnologies.length > 4 && (
                                            <span className="text-[10px] text-muted-foreground">
                                              +{q.keyTechnologies.length - 4}
                                            </span>
                                          )}
                                        </div>
                                      )}
                                    </div>
                                    {q.difficulty && (
                                      <Badge
                                        variant="outline"
                                        className={`text-[10px] shrink-0 ${
                                          q.difficulty.toLowerCase() === 'easy' ? 'border-green-300 text-green-700' :
                                          q.difficulty.toLowerCase() === 'medium' ? 'border-amber-300 text-amber-700' :
                                          q.difficulty.toLowerCase() === 'hard' ? 'border-red-300 text-red-700' :
                                          ''
                                        }`}
                                      >
                                        {q.difficulty}
                                      </Badge>
                                    )}
                                  </div>
                                </div>
                              ))
                            )}
                          </div>
                        </div>
                      )}
                    </>
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

                  <div className="space-y-3">
                    {classGroups.length > 1 ? (
                      // Multi-class: show grouped by class
                      classGroups.map((cg) => {
                        const classSectionIds = cg.sections.map((s) => s.id)
                        const allSelected = classSectionIds.every((id) => selectedSectionIds.has(id))
                        const someSelected = classSectionIds.some((id) => selectedSectionIds.has(id))
                        const classStudents = cg.sections.reduce((sum, s) => sum + s.studentCount, 0)

                        return (
                          <div key={cg.classId} className="rounded-md border">
                            <div
                              className="flex items-center gap-3 p-3 bg-muted/30 cursor-pointer hover:bg-muted/50 transition-colors"
                              onClick={() => toggleClassSections(cg.classId)}
                            >
                              <Checkbox
                                checked={allSelected}
                                // @ts-ignore - indeterminate is valid
                                data-state={someSelected && !allSelected ? 'indeterminate' : undefined}
                                onCheckedChange={() => toggleClassSections(cg.classId)}
                              />
                              <div className="flex-1">
                                <span className="font-medium text-sm">
                                  Class: {cg.sections[0]?.name?.split(' ')[0] || cg.classId}
                                </span>
                                <span className="text-xs text-muted-foreground ml-2">
                                  {cg.sections.length} section{cg.sections.length !== 1 ? 's' : ''}, {classStudents} students
                                </span>
                              </div>
                            </div>
                            <div className="space-y-0 border-t">
                              {cg.sections.map((section) => (
                                <div
                                  key={section.id}
                                  className="flex items-center gap-3 px-3 py-2 pl-8 hover:bg-muted/30 transition-colors"
                                >
                                  <Checkbox
                                    checked={selectedSectionIds.has(section.id)}
                                    onCheckedChange={() => toggleSection(section.id)}
                                  />
                                  <div className="flex-1">
                                    <div className="flex items-center gap-2">
                                      <span className="text-sm">{section.name}</span>
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
                          </div>
                        )
                      })
                    ) : (
                      // Single class or no class: flat list
                      sectionInfos.map((section) => (
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
                      ))
                    )}
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

              {selectedSectionIds.size > 1 && (
                <div className="flex items-center space-x-3 rounded-md border p-3">
                  <Switch
                    checked={mixSections}
                    onCheckedChange={setMixSections}
                  />
                  <div>
                    <Label className="text-sm font-medium">Mix students across sections</Label>
                    <p className="text-xs text-muted-foreground">
                      {mixSections
                        ? 'Students from all sections will be pooled and shuffled together'
                        : 'Groups will be created within each section separately (default)'}
                    </p>
                  </div>
                </div>
              )}

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
                  <span className="font-medium">
                    {selectedQuestionIds.size > 0
                      ? `${selectedQuestionIds.size} selected`
                      : `${questionCount ?? '...'} (all)`}
                  </span>
                </div>

                {/* Per-section breakdown (default mode) */}
                {!mixSections && selectedSectionIds.size > 1 && totalStudents > 0 && (
                  <div className="rounded-md border p-3 space-y-2">
                    <div>
                      <p className="text-sm font-medium">Groups per section</p>
                      <p className="text-xs text-muted-foreground">
                        Groups are created within each section. Students will not be mixed across sections.
                      </p>
                    </div>
                    <div className="space-y-1">
                      {perSectionGroups.map((s) => (
                        <div key={s.id} className="flex items-center justify-between text-sm rounded px-2 py-1.5 bg-muted/50">
                          <span className="font-medium">{s.name}</span>
                          <span className="text-muted-foreground">
                            {s.studentCount} students &rarr; {s.label}
                            <span className="text-xs ml-1">
                              ({s.name} Group 1{s.groups > 1 ? ` ... ${s.name} Group ${s.groups}` : ''})
                            </span>
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Cross-section mode info */}
                {mixSections && selectedSectionIds.size > 1 && totalStudents > 0 && (
                  <div className="rounded-md bg-amber-50 border border-amber-200 p-3 text-sm text-amber-800">
                    <p>
                      All {totalStudents} students from {selectedSectionIds.size} sections will be pooled and
                      shuffled &rarr; {computeDistribution(totalStudents, groupSize).label}
                    </p>
                  </div>
                )}

                {/* Single section */}
                {selectedSectionIds.size === 1 && totalStudents > 0 && (
                  <div className="rounded-md bg-muted p-3 text-sm">
                    <p>
                      {totalStudents} student{totalStudents !== 1 ? 's' : ''} from <strong>{selectedSections[0]?.name}</strong> &rarr;{' '}
                      {computeDistribution(totalStudents, groupSize).label}
                    </p>
                    <p className="text-xs text-muted-foreground mt-1">
                      {selectedSections[0]?.name} Group 1{totalGroups > 1 ? ` ... ${selectedSections[0]?.name} Group ${totalGroups}` : ''}
                    </p>
                  </div>
                )}

                {/* Problem statement assignment info */}
                {(() => {
                  const stmtCount = selectedQuestionIds.size > 0 ? selectedQuestionIds.size : (questionCount ?? 0)
                  if (stmtCount > 0 && totalGroups > 0) {
                    return (
                      <div className="rounded-md bg-muted p-3 text-sm">
                        {totalGroups <= stmtCount ? (
                          <p>
                            {stmtCount} problem statement{stmtCount !== 1 ? 's' : ''} will be assigned round-robin
                            across {totalGroups} group{totalGroups !== 1 ? 's' : ''}. Each group gets a unique statement.
                          </p>
                        ) : (
                          <p>
                            There are more groups ({totalGroups}) than problem statements ({stmtCount}).
                            Statements will be reused across groups via round-robin assignment.
                          </p>
                        )}
                      </div>
                    )
                  }
                  return null
                })()}
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

          {/* Step 5: Per-Section Events */}
          {step === 5 && (() => {
            const eventTabs = selectedSections.length > 1
              ? [{ id: '_global', name: 'All Sections' }, ...selectedSections.map((s) => ({ id: s.id, name: s.name }))]
              : [{ id: selectedSections[0]?.id || '_global', name: selectedSections[0]?.name || 'Events' }]
            const currentTab = eventTabs.find((t) => t.id === activeEventTab) || eventTabs[0]
            const currentTabId = currentTab.id
            const currentEvents = getEventsForTab(currentTabId)

            return (
              <div className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  Add events like presentations, reviews, or submissions. Events can also be added later.
                  {selectedSections.length > 1 && ' Use tabs to define different events per section, or "All Sections" for shared events.'}
                </p>

                {/* Section tabs */}
                {eventTabs.length > 1 && (
                  <div className="flex flex-wrap gap-1 border-b pb-2">
                    {eventTabs.map((tab) => {
                      const tabEvents = getEventsForTab(tab.id)
                      return (
                        <button
                          key={tab.id}
                          type="button"
                          onClick={() => setActiveEventTab(tab.id)}
                          className={`px-3 py-1.5 text-sm rounded-t transition-colors ${
                            currentTabId === tab.id
                              ? 'bg-primary text-primary-foreground font-medium'
                              : 'hover:bg-muted text-muted-foreground'
                          }`}
                        >
                          {tab.name}
                          {tabEvents.length > 0 && (
                            <Badge variant="secondary" className="ml-1.5 text-[10px] px-1 py-0">
                              {tabEvents.length}
                            </Badge>
                          )}
                        </button>
                      )
                    })}
                  </div>
                )}

                {/* Copy events controls */}
                {selectedSections.length > 1 && currentEvents.length > 0 && currentTabId !== '_global' && (
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => copyEventsToAllSections(currentTabId)}
                      className="text-xs"
                    >
                      <Copy className="h-3 w-3 mr-1.5" /> Copy to all other sections
                    </Button>
                    {selectedSections.filter((s) => s.id !== currentTabId).map((s) => (
                      <Button
                        key={s.id}
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => copyEventsToSection(currentTabId, s.id)}
                        className="text-xs"
                      >
                        <Copy className="h-3 w-3 mr-1.5" /> Copy to {s.name}
                      </Button>
                    ))}
                  </div>
                )}

                {/* Event list for current tab */}
                {currentEvents.length === 0 ? (
                  <div className="rounded-md border border-dashed p-6 text-center">
                    <p className="text-sm text-muted-foreground mb-2">No events for {currentTab.name}</p>
                    <Button type="button" variant="outline" size="sm" onClick={() => addEventToTab(currentTabId)}>
                      <Plus className="h-3.5 w-3.5 mr-1.5" /> Add Event
                    </Button>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {currentEvents.map((event, idx) => (
                      <div key={idx} className="rounded-md border p-3 space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">Event {idx + 1}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeEventFromTab(currentTabId, idx)}
                            className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                          <div className="col-span-2 space-y-1.5">
                            <Label className="text-xs">Event Name <span className="text-destructive">*</span></Label>
                            <Input
                              value={event.name}
                              onChange={(e) => updateEventInTab(currentTabId, idx, 'name', e.target.value)}
                              placeholder="e.g. Proposal Presentation, Mid Review, Final Demo"
                              className="h-8 text-sm"
                            />
                          </div>

                          <div className="space-y-1.5">
                            <Label className="text-xs">Date & Time</Label>
                            <Input
                              type="datetime-local"
                              value={event.dateTime}
                              onChange={(e) => updateEventInTab(currentTabId, idx, 'dateTime', e.target.value)}
                              className="h-8 text-sm"
                            />
                          </div>

                          <div className="space-y-1.5">
                            <Label className="text-xs">Zoom / Meeting Link</Label>
                            <Input
                              value={event.zoomLink}
                              onChange={(e) => updateEventInTab(currentTabId, idx, 'zoomLink', e.target.value)}
                              placeholder="https://zoom.us/..."
                              className="h-8 text-sm"
                            />
                          </div>

                          <div className="flex items-center gap-3 col-span-2">
                            <div className="flex items-center space-x-2">
                              <Switch
                                checked={event.hasMarks}
                                onCheckedChange={(val) => updateEventInTab(currentTabId, idx, 'hasMarks', val)}
                              />
                              <Label className="text-xs">Has Marks</Label>
                            </div>
                            {event.hasMarks && (
                              <div className="flex items-center gap-2">
                                <Label className="text-xs">Max Marks:</Label>
                                <Input
                                  type="number"
                                  min={1}
                                  value={event.maxMarks}
                                  onChange={(e) => updateEventInTab(currentTabId, idx, 'maxMarks', parseInt(e.target.value) || 10)}
                                  className="h-7 w-20 text-sm"
                                />
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}

                    <Button type="button" variant="outline" size="sm" onClick={() => addEventToTab(currentTabId)} className="w-full">
                      <Plus className="h-3.5 w-3.5 mr-1.5" /> Add Another Event
                    </Button>
                  </div>
                )}
              </div>
            )
          })()}

          {/* Step 6: Review & Create */}
          {step === 6 && (
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
                    Group size: {groupSize} | Estimated groups: {totalGroups} | Problem statements:{' '}
                    {selectedQuestionIds.size > 0
                      ? `${selectedQuestionIds.size} selected`
                      : `${questionCount ?? 0} (all)`}
                  </p>
                  {selectedSectionIds.size > 1 && (
                    <p className="text-xs text-muted-foreground mt-1">
                      {mixSections
                        ? 'Students from all sections will be mixed together'
                        : 'Groups created within each section separately'}
                    </p>
                  )}
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

                {totalEventsCount > 0 && (
                  <div>
                    <h4 className="font-medium text-sm text-muted-foreground mb-1">Events ({totalEventsCount})</h4>
                    <div className="space-y-2">
                      {Object.entries(eventsBySectionId)
                        .filter(([_, evts]) => evts.some((e) => e.name.trim()))
                        .map(([sectionId, evts]) => {
                          const sectionName = sectionId === '_global'
                            ? 'All Sections'
                            : selectedSections.find((s) => s.id === sectionId)?.name || sectionId
                          return (
                            <div key={sectionId}>
                              {Object.keys(eventsBySectionId).length > 1 && (
                                <p className="text-xs font-medium text-muted-foreground mb-1">{sectionName}</p>
                              )}
                              {evts.filter((e) => e.name.trim()).map((ev, idx) => (
                                <div key={idx} className="flex items-center justify-between text-sm rounded px-2 py-1 bg-muted/50">
                                  <span className="font-medium">{ev.name}</span>
                                  <span className="text-muted-foreground text-xs">
                                    {ev.dateTime ? new Date(ev.dateTime).toLocaleString() : 'No date'}
                                    {ev.hasMarks && ` · ${ev.maxMarks} marks`}
                                  </span>
                                </div>
                              ))}
                            </div>
                          )
                        })}
                    </div>
                  </div>
                )}
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
