'use client'

import { useState, useEffect, useRef } from 'react'
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
import { Sparkles, Loader2, ArrowLeft, AlertCircle, BookOpen } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { simulationsApi, coursesApi } from '@/lib/api'
import type { Course, Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

interface LectureOption {
  id: string
  title: string
  sectionTitle: string
}

export default function GenerateSimulationPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()

  const [courseId, setCourseId] = useState<string>(searchParams.get('courseId') || '')
  const [selectedLectureIds, setSelectedLectureIds] = useState<string[]>([])
  const [concept, setConcept] = useState('')
  const [subject, setSubject] = useState('')
  const [audience, setAudience] = useState<string>('')
  const [description, setDescription] = useState('')
  const [targetYears, setTargetYears] = useState(5)
  const [decisionsPerYear, setDecisionsPerYear] = useState(6)

  const [courses, setCourses] = useState<Course[]>([])
  const [lectures, setLectures] = useState<LectureOption[]>([])
  const [loadingLectures, setLoadingLectures] = useState(false)
  const [suggesting, setSuggesting] = useState(false)
  const [existingSimulations, setExistingSimulations] = useState<Array<{ id: string; title: string; concept: string; status: string }>>([])
  const [generating, setGenerating] = useState(false)
  const [generationProgress, setGenerationProgress] = useState<{ progress: number; message: string } | null>(null)

  const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

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

  // Load lectures when course changes
  useEffect(() => {
    if (!courseId || courseId === 'none') {
      setLectures([])
      setSelectedLectureIds([])
      return
    }
    const loadLectures = async () => {
      setLoadingLectures(true)
      try {
        const sections = await coursesApi.getSections(courseId)
        const lectureOptions: LectureOption[] = []
        for (const section of sections) {
          if (section.lectures) {
            for (const lecture of section.lectures) {
              lectureOptions.push({
                id: lecture.id,
                title: lecture.title,
                sectionTitle: section.title,
              })
            }
          }
        }
        setLectures(lectureOptions)
      } catch {
        setLectures([])
      } finally {
        setLoadingLectures(false)
      }
    }
    loadLectures()
  }, [courseId])

  // Warn user if they try to navigate away during generation
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (generating) {
        e.preventDefault()
        e.returnValue = 'Simulation generation is in progress. Are you sure you want to leave?'
        return e.returnValue
      }
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [generating])

  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current)
    }
  }, [])

  const handleSmartSuggest = async () => {
    if (!courseId || courseId === 'none') return
    setSuggesting(true)
    try {
      const response = await simulationsApi.suggestFromCourse({
        courseId,
        lectureIds: selectedLectureIds.length > 0 ? selectedLectureIds : undefined,
      })
      setConcept(response.concept)
      setSubject(response.subject)
      setAudience(response.audience)
      setDescription(response.description)
      setExistingSimulations(response.existingSimulations)
      toast({
        title: 'Smart Suggest Complete',
        description: 'Form fields have been populated. Review and adjust as needed.',
      })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Smart Suggest Failed',
        description: extractErrorMessage(error) || 'Failed to analyze course content. Please fill in fields manually.',
      })
    } finally {
      setSuggesting(false)
    }
  }

  const toggleLecture = (lectureId: string) => {
    setSelectedLectureIds(prev =>
      prev.includes(lectureId)
        ? prev.filter(id => id !== lectureId)
        : [...prev, lectureId]
    )
  }

  const handleGenerate = async () => {
    if (generating) return

    if (!concept.trim() || !subject.trim() || !audience) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Please fill in Concept, Subject, and Audience.',
      })
      return
    }

    setGenerating(true)
    setGenerationProgress({ progress: 0, message: 'Starting simulation generation...' })

    try {
      const request = {
        concept: concept.trim(),
        subject: subject.trim(),
        audience: audience as 'UNDERGRADUATE' | 'MBA' | 'GRADUATE',
        courseId: (courseId && courseId !== 'none') ? courseId : undefined,
        description: description.trim() || undefined,
        targetYears,
        decisionsPerYear,
      }

      const { jobId, simulationId } = await simulationsApi.generateSimulation(request)

      pollIntervalRef.current = setInterval(async () => {
        try {
          const job = await simulationsApi.getGenerationJobStatus(jobId)
          const progress = job.progress ?? 0
          const message = job.message || `Status: ${job.status}`
          setGenerationProgress({ progress, message })

          if (job.status === 'COMPLETED') {
            if (pollIntervalRef.current) clearInterval(pollIntervalRef.current)
            pollIntervalRef.current = null
            setGenerationProgress(null)
            setGenerating(false)
            toast({ title: 'Simulation Generated', description: 'The simulation has been generated successfully.' })
            router.push(`/simulations/${simulationId}`)
          } else if (job.status === 'FAILED') {
            if (pollIntervalRef.current) clearInterval(pollIntervalRef.current)
            pollIntervalRef.current = null
            setGenerationProgress(null)
            setGenerating(false)
            toast({ variant: 'destructive', title: 'Generation Failed', description: job.error || 'Please try again.' })
          }
        } catch (pollError) {
          console.error('Poll error:', pollError)
        }
      }, 3000)
    } catch (error) {
      setGenerationProgress(null)
      setGenerating(false)
      toast({
        variant: 'destructive',
        title: 'Failed to start generation',
        description: extractErrorMessage(error) || 'Please try again.',
      })
    }
  }

  return (
    <div>
      {/* Generation in Progress Alert */}
      {generating && (
        <Alert className="mb-3 border-blue-200 bg-blue-50">
          <AlertCircle className="h-4 w-4 text-blue-600" />
          <AlertTitle className="text-blue-900">Simulation Generation in Progress</AlertTitle>
          <AlertDescription className="text-blue-800">
            Your simulation is being generated. Please do not navigate away until this process completes.
            {generationProgress && (
              <span className="block mt-1 text-sm">
                {generationProgress.message} ({generationProgress.progress}%)
              </span>
            )}
          </AlertDescription>
        </Alert>
      )}

      <Card className="mb-3">
        <CardHeader>
          <CardTitle>Generate Simulation</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {/* 1. Link to Course (moved to top) */}
            <div className="space-y-2">
              <Label>
                <BookOpen className="h-4 w-4 inline mr-1" />
                Link to Course
              </Label>
              <Select
                value={courseId}
                onValueChange={(val) => { setCourseId(val); setSelectedLectureIds([]); setExistingSimulations([]) }}
                disabled={generating}
              >
                <SelectTrigger className={generating ? 'opacity-60 cursor-not-allowed' : ''}>
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

            {/* 2. Lecture multi-select (shown when course selected) */}
            {courseId && courseId !== 'none' && (
              <div className="space-y-2">
                <Label className="text-sm">Select Lectures (optional — focus the simulation on specific topics)</Label>
                {loadingLectures ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading lectures...
                  </div>
                ) : lectures.length > 0 ? (
                  <div className="max-h-48 overflow-y-auto border rounded-md p-2 space-y-1">
                    {lectures.map((lecture) => (
                      <label
                        key={lecture.id}
                        className="flex items-center gap-2 text-sm py-1 px-2 rounded hover:bg-gray-50 cursor-pointer"
                      >
                        <input
                          type="checkbox"
                          checked={selectedLectureIds.includes(lecture.id)}
                          onChange={() => toggleLecture(lecture.id)}
                          disabled={generating}
                          className="rounded border-gray-300"
                        />
                        <span className="text-gray-700">{lecture.title}</span>
                        <span className="text-xs text-muted-foreground ml-auto">{lecture.sectionTitle}</span>
                      </label>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground py-2">No lectures found for this course.</p>
                )}

                {/* Selected count */}
                {selectedLectureIds.length > 0 && (
                  <p className="text-xs text-muted-foreground">{selectedLectureIds.length} lecture(s) selected</p>
                )}
              </div>
            )}

            {/* 3. Smart Suggest button */}
            {courseId && courseId !== 'none' && (
              <Button
                variant="outline"
                onClick={handleSmartSuggest}
                disabled={suggesting || generating}
                className="w-full"
              >
                {suggesting ? (
                  <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Analyzing course content...</>
                ) : (
                  <><Sparkles className="h-4 w-4 mr-2" /> Smart Suggest</>
                )}
              </Button>
            )}

            {/* 4. Existing simulations warning */}
            {existingSimulations.length > 0 && (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm">
                <p className="font-medium text-amber-800">
                  {existingSimulations.length} simulation(s) already exist for this course:
                </p>
                {existingSimulations.map(s => (
                  <p key={s.id} className="text-amber-700 mt-1">
                    &bull; {s.title} <span className="text-xs">({s.status})</span>
                  </p>
                ))}
              </div>
            )}

            {/* 5. Concept */}
            <div className="space-y-2">
              <Label>
                Concept <span className="text-destructive">*</span>
              </Label>
              <Input
                value={concept}
                onChange={(e) => setConcept(e.target.value)}
                placeholder="What concept should the simulation teach?"
                disabled={generating}
                className={generating ? 'opacity-60 cursor-not-allowed' : ''}
              />
            </div>

            {/* 6. Subject */}
            <div className="space-y-2">
              <Label>
                Subject <span className="text-destructive">*</span>
              </Label>
              <Input
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="e.g. Economics, Product Strategy, Physics"
                disabled={generating}
                className={generating ? 'opacity-60 cursor-not-allowed' : ''}
              />
            </div>

            {/* 7. Audience */}
            <div className="space-y-2">
              <Label>
                Audience <span className="text-destructive">*</span>
              </Label>
              <Select
                value={audience}
                onValueChange={setAudience}
                disabled={generating}
              >
                <SelectTrigger className={generating ? 'opacity-60 cursor-not-allowed' : ''}>
                  <SelectValue placeholder="Select audience level" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNDERGRADUATE">Undergraduate</SelectItem>
                  <SelectItem value="MBA">MBA</SelectItem>
                  <SelectItem value="GRADUATE">Graduate</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* 8. Description */}
            <div className="space-y-2">
              <Label>Description</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Student-facing description of the simulation experience"
                rows={3}
                disabled={generating}
                className={generating ? 'opacity-60 cursor-not-allowed' : ''}
              />
            </div>

            {/* 9. Advanced Options */}
            <details className={`border rounded-md p-3 ${generating ? 'opacity-60 pointer-events-none' : ''}`}>
              <summary className={`text-sm font-medium ${generating ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                Advanced Options
              </summary>
              <div className="mt-4 space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label className="text-xs">Number of Years (3-7)</Label>
                    <Input
                      type="number"
                      min={3}
                      max={7}
                      value={targetYears}
                      onChange={(e) => setTargetYears(Math.min(7, Math.max(3, parseInt(e.target.value) || 5)))}
                      className="text-sm"
                      disabled={generating}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label className="text-xs">Decisions per Year (4-8)</Label>
                    <Input
                      type="number"
                      min={4}
                      max={8}
                      value={decisionsPerYear}
                      onChange={(e) => setDecisionsPerYear(Math.min(8, Math.max(4, parseInt(e.target.value) || 6)))}
                      className="text-sm"
                      disabled={generating}
                    />
                  </div>
                </div>
              </div>
            </details>
          </div>
        </CardContent>
      </Card>

      {/* Progress Indicator */}
      {generationProgress && (
        <Card className="mb-3">
          <CardContent className="pt-6">
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">{generationProgress.message}</span>
                <span className="text-muted-foreground">{generationProgress.progress}%</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                  style={{ width: `${generationProgress.progress}%` }}
                />
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="flex justify-end space-x-3">
        <Button
          variant="outline"
          onClick={() => {
            if (generating) {
              toast({ variant: 'default', title: 'Generation in Progress', description: 'Please wait for completion.' })
            } else {
              router.back()
            }
          }}
          disabled={generating}
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          {generating ? 'Generation in Progress...' : 'Cancel'}
        </Button>
        <Button
          onClick={handleGenerate}
          disabled={generating || !concept.trim() || !subject.trim() || !audience}
          className="min-w-[200px]"
        >
          {generating ? (
            <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Generating...</>
          ) : (
            <><Sparkles className="h-4 w-4 mr-2" /> Generate Simulation</>
          )}
        </Button>
      </div>
    </div>
  )
}
