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
import { Sparkles, Loader2, ArrowLeft, AlertCircle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { simulationsApi, coursesApi } from '@/lib/api'
import type { Course } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

export default function GenerateSimulationPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()

  const [concept, setConcept] = useState('')
  const [subject, setSubject] = useState('')
  const [audience, setAudience] = useState<string>('')
  const [courseId, setCourseId] = useState<string>(searchParams.get('courseId') || '')
  const [description, setDescription] = useState('')
  const [targetDepth, setTargetDepth] = useState(15)
  const [choicesPerNode, setChoicesPerNode] = useState('3')

  const [courses, setCourses] = useState<Course[]>([])
  const [generating, setGenerating] = useState(false)
  const [generationProgress, setGenerationProgress] = useState<{ progress: number; message: string } | null>(null)

  const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Load courses for the optional link dropdown
  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await coursesApi.listCourses()
        setCourses(data)
      } catch {
        // Non-critical — dropdown will just be empty
      }
    }
    loadCourses()
  }, [])

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
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    }
  }, [generating])

  // Cleanup poll on unmount
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current)
      }
    }
  }, [])

  const handleGenerate = async () => {
    if (generating) {
      toast({
        variant: 'default',
        title: 'Generation in Progress',
        description: 'Please wait for the current simulation generation to complete.',
      })
      return
    }

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
        targetDepth,
        choicesPerNode: parseInt(choicesPerNode),
      }

      const { jobId, simulationId } = await simulationsApi.generateSimulation(request)

      // Poll for job status
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
            toast({
              title: 'Simulation Generated',
              description: 'The simulation has been generated successfully.',
            })
            router.push(`/simulations/${simulationId}`)
          } else if (job.status === 'FAILED') {
            if (pollIntervalRef.current) clearInterval(pollIntervalRef.current)
            pollIntervalRef.current = null
            setGenerationProgress(null)
            setGenerating(false)
            toast({
              variant: 'destructive',
              title: 'Generation Failed',
              description: job.error || 'Simulation generation failed. Please try again.',
            })
          }
        } catch (pollError) {
          // Polling error — keep trying unless it's a fatal error
          console.error('Poll error:', pollError)
        }
      }, 3000)
    } catch (error) {
      setGenerationProgress(null)
      setGenerating(false)
      toast({
        variant: 'destructive',
        title: 'Failed to start generation',
        description: extractErrorMessage(error) || 'Failed to start simulation generation. Please try again.',
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
            {/* Concept */}
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

            {/* Subject */}
            <div className="space-y-2">
              <Label>
                Subject / Course <span className="text-destructive">*</span>
              </Label>
              <Input
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="e.g. Economics, Product Strategy, Physics"
                disabled={generating}
                className={generating ? 'opacity-60 cursor-not-allowed' : ''}
              />
            </div>

            {/* Audience */}
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

            {/* Link to Course */}
            <div className="space-y-2">
              <Label>Link to Course (Optional)</Label>
              <Select
                value={courseId}
                onValueChange={setCourseId}
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

            {/* Description */}
            <div className="space-y-2">
              <Label>Description (Optional)</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Student-facing description of the simulation"
                rows={3}
                disabled={generating}
                className={generating ? 'opacity-60 cursor-not-allowed' : ''}
              />
            </div>

            {/* Advanced Options */}
            <details className={`border rounded-md p-3 ${generating ? 'opacity-60 pointer-events-none' : ''}`}>
              <summary className={`text-sm font-medium ${generating ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                Advanced Options
              </summary>
              <div className="mt-4 space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label className="text-xs">Target Depth (10-30)</Label>
                    <Input
                      type="number"
                      min={10}
                      max={30}
                      value={targetDepth}
                      onChange={(e) => setTargetDepth(Math.min(30, Math.max(10, parseInt(e.target.value) || 15)))}
                      className="text-sm"
                      disabled={generating}
                    />
                    <p className="text-xs text-muted-foreground">
                      Number of decision nodes in the simulation tree
                    </p>
                  </div>
                  <div className="space-y-2">
                    <Label className="text-xs">Choices per Node</Label>
                    <Select
                      value={choicesPerNode}
                      onValueChange={setChoicesPerNode}
                      disabled={generating}
                    >
                      <SelectTrigger className="text-sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="2">2</SelectItem>
                        <SelectItem value="3">3</SelectItem>
                        <SelectItem value="4">4</SelectItem>
                      </SelectContent>
                    </Select>
                    <p className="text-xs text-muted-foreground">
                      Number of choices at each decision point
                    </p>
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
              toast({
                variant: 'default',
                title: 'Generation in Progress',
                description: 'Please wait for the simulation generation to complete before leaving.',
              })
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
            <>
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              Generating...
            </>
          ) : (
            <>
              <Sparkles className="h-4 w-4 mr-2" />
              Generate Simulation
            </>
          )}
        </Button>
      </div>
    </div>
  )
}
