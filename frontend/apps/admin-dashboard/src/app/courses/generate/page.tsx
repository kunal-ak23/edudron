'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@edudron/shared-utils'
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
import { Checkbox } from '@/components/ui/checkbox'
import { Sparkles, Loader2, ArrowLeft, FileText, X, AlertCircle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { coursesApi, courseGenerationIndexApi } from '@/lib/api'
import type { GenerateCourseRequest, CourseGenerationIndex } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

export default function GenerateCoursePage() {
  const router = useRouter()
  const { toast } = useToast()
  const [prompt, setPrompt] = useState('')
  const [generating, setGenerating] = useState(false)
  const [pdfFile, setPdfFile] = useState<File | null>(null)
  const [generationProgress, setGenerationProgress] = useState<{ progress: number; message: string } | null>(null)
  const [referenceIndexes, setReferenceIndexes] = useState<CourseGenerationIndex[]>([])
  const [selectedReferenceIds, setSelectedReferenceIds] = useState<string[]>([])
  const [writingFormats, setWritingFormats] = useState<CourseGenerationIndex[]>([])
  const [selectedWritingFormatId, setSelectedWritingFormatId] = useState<string>('')
  const [customWritingFormat, setCustomWritingFormat] = useState('')
  const [options, setOptions] = useState({
    categoryId: '',
    difficultyLevel: 'AUTO',
    language: 'en',
    tags: '',
    certificateEligible: false,
    maxCompletionDays: ''
  })

  // Load indexes on mount
  useEffect(() => {
    loadIndexes()
  }, [])

  // Warn user if they try to navigate away during generation
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (generating) {
        e.preventDefault()
        e.returnValue = 'Course generation is in progress. Are you sure you want to leave?'
        return e.returnValue
      }
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    }
  }, [generating])

  const loadIndexes = async () => {
    try {
      const [refs, formats] = await Promise.all([
        courseGenerationIndexApi.getIndexesByType('REFERENCE_CONTENT'),
        courseGenerationIndexApi.getIndexesByType('WRITING_FORMAT')
      ])
      setReferenceIndexes(refs)
      setWritingFormats(formats)
    } catch (error) {
      console.error('Failed to load indexes:', error)
    }
  }

  const handleGenerate = async () => {
    // Prevent duplicate submissions
    if (generating) {
      toast({
        variant: 'default',
        title: 'Generation in Progress',
        description: 'Please wait for the current course generation to complete.',
      })
      return
    }

    if (!prompt.trim() && !pdfFile) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Please enter a course description prompt or upload a PDF file',
      })
      return
    }

    setGenerating(true)
    setGenerationProgress({ progress: 0, message: 'Starting course generation...' })
    try {
      const request = {
        prompt: prompt.trim() || '', // Allow empty prompt if PDF is provided
        referenceIndexIds: selectedReferenceIds.length > 0 ? selectedReferenceIds : undefined,
        writingFormatId: selectedWritingFormatId || undefined,
        writingFormat: customWritingFormat.trim() || undefined,
        categoryId: options.categoryId || undefined,
        difficultyLevel: options.difficultyLevel && options.difficultyLevel !== 'AUTO' ? options.difficultyLevel : undefined,
        language: options.language || undefined,
        tags: options.tags ? options.tags.split(',').map(t => t.trim()) : undefined,
        certificateEligible: options.certificateEligible || undefined,
        maxCompletionDays: options.maxCompletionDays ? parseInt(options.maxCompletionDays) : undefined,
        pdfFile: pdfFile || undefined,
      } as GenerateCourseRequest & { pdfFile?: File }

      const course = await (coursesApi.generateCourse as any)(request, (progress: number, message: string) => {
        setGenerationProgress({ progress, message })
      })
      
      setGenerationProgress(null)
      toast({
        title: 'Course Generated',
        description: 'The course has been generated successfully.',
      })
      router.push(`/courses/${course.id}`)
    } catch (error: any) {
      console.error('Failed to generate course:', error)
      setGenerationProgress(null)
      toast({
        variant: 'destructive',
        title: 'Failed to generate course',
        description: extractErrorMessage(error) || 'Failed to generate course. Please try again.',
      })
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div>

          {/* Generation in Progress Alert */}
          {generating && (
            <Alert className="mb-3 border-blue-200 bg-blue-50">
              <AlertCircle className="h-4 w-4 text-blue-600" />
              <AlertTitle className="text-blue-900">Course Generation in Progress</AlertTitle>
              <AlertDescription className="text-blue-800">
                Your course is being generated. Please do not navigate away or create another course until this process completes.
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
              <CardTitle>Course Generation</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                <div className="space-y-2">
                  <Label>
                    Course Description Prompt {!pdfFile && <span className="text-destructive">*</span>}
                  </Label>
                  <Textarea
                    value={prompt}
                    onChange={(e) => setPrompt(e.target.value)}
                    placeholder="e.g., Create a 5-module course on Python programming for beginners. Include basics, data structures, functions, OOP, and file handling. Each module should have 4-5 lectures. Or leave empty if uploading a PDF with course structure."
                    rows={6}
                    disabled={generating}
                    className={generating ? 'opacity-60 cursor-not-allowed' : ''}
                  />
                  <p className="text-sm text-muted-foreground">
                    Be specific about course length, modules, topics, difficulty level, etc. You can also upload a PDF with the course structure.
                  </p>
                </div>

                {/* PDF Upload */}
                <div className="space-y-2">
                  <Label>Course Structure PDF (Optional)</Label>
                  {!pdfFile ? (
                    <div className="border-2 border-dashed border-gray-300 rounded-lg p-4 text-center hover:border-gray-400 transition-colors">
                      <input
                        type="file"
                        id="pdf-upload"
                        accept=".pdf"
                        onChange={(e) => {
                          const file = e.target.files?.[0]
                          if (file) {
                            if (file.type !== 'application/pdf') {
                              toast({
                                variant: 'destructive',
                                title: 'Invalid File Type',
                                description: 'Please upload a PDF file',
                              })
                              return
                            }
                            if (file.size > 50 * 1024 * 1024) {
                              toast({
                                variant: 'destructive',
                                title: 'File Too Large',
                                description: 'PDF file must be less than 50MB',
                              })
                              return
                            }
                            setPdfFile(file)
                          }
                        }}
                        className="hidden"
                        disabled={generating}
                      />
                      <label 
                        htmlFor="pdf-upload" 
                        className={generating ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}
                      >
                        <FileText className="h-8 w-8 mx-auto mb-2 text-gray-400" />
                        <p className="text-sm text-gray-600">
                          {generating ? 'Upload disabled during generation' : 'Click to upload a PDF file with course structure'}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">
                          PDF files up to 50MB are supported
                        </p>
                      </label>
                    </div>
                  ) : (
                    <div className="border rounded-lg p-4 flex items-center justify-between bg-gray-50">
                      <div className="flex items-center space-x-3">
                        <FileText className="h-5 w-5 text-blue-600" />
                        <div>
                          <p className="text-sm font-medium">{pdfFile.name}</p>
                          <p className="text-xs text-gray-500">
                            {(pdfFile.size / 1024 / 1024).toFixed(2)} MB
                          </p>
                        </div>
                      </div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => setPdfFile(null)}
                        disabled={generating}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                  <p className="text-sm text-muted-foreground">
                    Upload a PDF file containing the course structure. The AI will extract the content and generate the course based on it. You can also add additional instructions in the prompt above.
                  </p>
                </div>

                {/* Reference Content */}
                {referenceIndexes.length > 0 && (
                  <div className={`space-y-2 ${generating ? 'opacity-60 pointer-events-none' : ''}`}>
                    <Label>Reference Content (Optional)</Label>
                    <div className="space-y-2 max-h-40 overflow-y-auto border rounded-md p-3">
                      {referenceIndexes.map((index) => (
                        <div key={index.id} className="flex items-start space-x-2">
                          <Checkbox
                            id={`ref-${index.id}`}
                            checked={selectedReferenceIds.includes(index.id)}
                            onCheckedChange={(checked) => {
                              if (checked) {
                                setSelectedReferenceIds([...selectedReferenceIds, index.id])
                              } else {
                                setSelectedReferenceIds(selectedReferenceIds.filter(id => id !== index.id))
                              }
                            }}
                            disabled={generating}
                          />
                          <label
                            htmlFor={`ref-${index.id}`}
                            className="flex-1 cursor-pointer"
                          >
                            <div className="text-sm font-medium">{index.title}</div>
                            {index.description && (
                              <div className="text-xs text-muted-foreground">{index.description}</div>
                            )}
                          </label>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Writing Format */}
                <div className={`space-y-2 ${generating ? 'opacity-60 pointer-events-none' : ''}`}>
                  <Label>Writing Format (Optional)</Label>
                  {writingFormats.length > 0 && (
                    <Select
                      value={selectedWritingFormatId}
                      onValueChange={(value) => {
                        setSelectedWritingFormatId(value)
                        if (value) setCustomWritingFormat('')
                      }}
                      disabled={generating}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select a saved format..." />
                      </SelectTrigger>
                      <SelectContent>
                        {writingFormats.map((format) => (
                          <SelectItem key={format.id} value={format.id}>
                            {format.title}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                  <Textarea
                    value={customWritingFormat}
                    onChange={(e) => {
                      setCustomWritingFormat(e.target.value)
                      if (e.target.value) setSelectedWritingFormatId('')
                    }}
                    placeholder="Or enter a custom writing format/style template..."
                    rows={4}
                    disabled={generating}
                  />
                  <p className="text-sm text-muted-foreground">
                    Specify the writing style, tone, and format you want the AI to follow
                  </p>
                </div>

                {/* Advanced Options */}
                <details className={`border rounded-md p-3 ${generating ? 'opacity-60 pointer-events-none' : ''}`}>
                  <summary className={`text-sm font-medium ${generating ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                    Advanced Options
                  </summary>
                  <div className="mt-4 space-y-3">
                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <Label className="text-xs">Category ID</Label>
                        <Input
                          type="text"
                          value={options.categoryId}
                          onChange={(e) => setOptions({...options, categoryId: e.target.value})}
                          className="text-sm"
                          disabled={generating}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label className="text-xs">Difficulty</Label>
                        <Select
                          value={options.difficultyLevel}
                          onValueChange={(value) => setOptions({...options, difficultyLevel: value})}
                          disabled={generating}
                        >
                          <SelectTrigger className="text-sm">
                            <SelectValue placeholder="Auto" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="AUTO">Auto</SelectItem>
                            <SelectItem value="BEGINNER">Beginner</SelectItem>
                            <SelectItem value="INTERMEDIATE">Intermediate</SelectItem>
                            <SelectItem value="ADVANCED">Advanced</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2">
                        <Label className="text-xs">Language</Label>
                        <Input
                          type="text"
                          value={options.language}
                          onChange={(e) => setOptions({...options, language: e.target.value})}
                          className="text-sm"
                          disabled={generating}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label className="text-xs">Max Completion Days</Label>
                        <Input
                          type="number"
                          value={options.maxCompletionDays}
                          onChange={(e) => setOptions({...options, maxCompletionDays: e.target.value})}
                          className="text-sm"
                          disabled={generating}
                        />
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Label className="text-xs">Tags (comma-separated)</Label>
                      <Input
                        type="text"
                        value={options.tags}
                        onChange={(e) => setOptions({...options, tags: e.target.value})}
                        className="text-sm"
                        disabled={generating}
                      />
                    </div>
                    <div className="flex items-center space-x-2">
                      <Checkbox
                        id="certificate"
                        checked={options.certificateEligible}
                        onCheckedChange={(checked) => setOptions({...options, certificateEligible: checked as boolean})}
                        disabled={generating}
                      />
                      <Label htmlFor="certificate" className="text-sm font-normal cursor-pointer">
                        Certificate Eligible
                      </Label>
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
                    description: 'Please wait for the course generation to complete before leaving.',
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
              disabled={generating || (!prompt.trim() && !pdfFile)}
              className="min-w-[160px]"
            >
              {generating ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Generating...
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4 mr-2" />
                  Generate Course
                </>
              )}
            </Button>
          </div>
      </div>
  )
}

