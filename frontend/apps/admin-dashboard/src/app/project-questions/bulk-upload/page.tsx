'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { ArrowLeft, Loader2, Upload, Eye, AlertCircle } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { projectQuestionsApi, coursesApi } from '@/lib/api'
import type { Course, ProjectQuestionDTO } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

interface ParsedQuestion {
  title: string
  problemStatement: string
  keyTechnologies?: string[]
  tags?: string[]
  difficulty?: string
}

export default function BulkUploadQuestionsPage() {
  const router = useRouter()
  const { toast } = useToast()

  const [courseId, setCourseId] = useState<string>('')
  const [jsonInput, setJsonInput] = useState('')
  const [courses, setCourses] = useState<Course[]>([])
  const [parsedQuestions, setParsedQuestions] = useState<ParsedQuestion[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [uploading, setUploading] = useState(false)

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

  const handlePreview = () => {
    setParseError(null)
    setParsedQuestions([])

    if (!jsonInput.trim()) {
      setParseError('Please enter JSON data.')
      return
    }

    try {
      const parsed = JSON.parse(jsonInput.trim())
      if (!Array.isArray(parsed)) {
        setParseError('JSON must be an array of question objects.')
        return
      }

      // Validate structure
      const validated: ParsedQuestion[] = []
      for (let i = 0; i < parsed.length; i++) {
        const item = parsed[i]
        if (!item.title || !item.problemStatement) {
          setParseError(`Item ${i + 1}: "title" and "problemStatement" are required fields.`)
          return
        }
        validated.push({
          title: item.title,
          problemStatement: item.problemStatement,
          keyTechnologies: Array.isArray(item.keyTechnologies) ? item.keyTechnologies : undefined,
          tags: Array.isArray(item.tags) ? item.tags : undefined,
          difficulty: item.difficulty || undefined,
        })
      }

      setParsedQuestions(validated)
      setPreviewing(true)
    } catch {
      setParseError('Invalid JSON format. Please check your input.')
    }
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      const text = event.target?.result as string

      if (file.name.endsWith('.csv')) {
        // Parse CSV
        try {
          const lines = text.split('\n').filter((l) => l.trim())
          if (lines.length < 2) {
            setParseError('CSV must have a header row and at least one data row.')
            return
          }
          const headers = lines[0].split(',').map((h) => h.trim().toLowerCase())
          const titleIdx = headers.indexOf('title')
          const stmtIdx = headers.indexOf('problemstatement') !== -1 ? headers.indexOf('problemstatement') : headers.indexOf('problem_statement')
          const techIdx = headers.indexOf('keytechnologies') !== -1 ? headers.indexOf('keytechnologies') : headers.indexOf('key_technologies')
          const tagsIdx = headers.indexOf('tags')
          const diffIdx = headers.indexOf('difficulty')

          if (titleIdx === -1 || stmtIdx === -1) {
            setParseError('CSV must have "title" and "problemStatement" columns.')
            return
          }

          const questions: ParsedQuestion[] = []
          for (let i = 1; i < lines.length; i++) {
            const cols = lines[i].split(',').map((c) => c.trim())
            questions.push({
              title: cols[titleIdx] || '',
              problemStatement: cols[stmtIdx] || '',
              keyTechnologies: techIdx !== -1 && cols[techIdx] ? cols[techIdx].split(';').map((t) => t.trim()) : undefined,
              tags: tagsIdx !== -1 && cols[tagsIdx] ? cols[tagsIdx].split(';').map((t) => t.trim()) : undefined,
              difficulty: diffIdx !== -1 ? cols[diffIdx] : undefined,
            })
          }
          setParsedQuestions(questions)
          setPreviewing(true)
          setParseError(null)
        } catch {
          setParseError('Failed to parse CSV file.')
        }
      } else {
        // Assume JSON
        setJsonInput(text)
      }
    }
    reader.readAsText(file)
    // Reset input
    e.target.value = ''
  }

  const handleUpload = async () => {
    if (!courseId) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Please select a course.',
      })
      return
    }

    setUploading(true)
    try {
      const questionsToUpload = parsedQuestions.map((q) => ({
        ...q,
        courseId,
        isActive: true,
      }))
      const result = await projectQuestionsApi.bulkUpload(questionsToUpload)
      toast({
        title: 'Upload Successful',
        description: `${result.length} question(s) uploaded.`,
      })
      router.push('/project-questions')
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Upload Failed',
        description: extractErrorMessage(error),
      })
    } finally {
      setUploading(false)
    }
  }

  return (
    <div>
      <Card className="mb-3">
        <CardHeader>
          <CardTitle>Bulk Upload Project Questions</CardTitle>
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

            {/* File Upload */}
            <div className="space-y-2">
              <Label>Upload File (JSON or CSV)</Label>
              <input
                type="file"
                accept=".json,.csv"
                onChange={handleFileUpload}
                className="block w-full text-sm text-muted-foreground file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-medium file:bg-primary file:text-primary-foreground hover:file:bg-primary/90"
              />
            </div>

            {/* JSON Input */}
            <div className="space-y-2">
              <Label>Or paste JSON array</Label>
              <Textarea
                value={jsonInput}
                onChange={(e) => { setJsonInput(e.target.value); setPreviewing(false) }}
                placeholder={`[\n  {\n    "title": "E-commerce Platform",\n    "problemStatement": "Build a full-stack e-commerce platform...",\n    "keyTechnologies": ["React", "Node.js"],\n    "difficulty": "MEDIUM"\n  }\n]`}
                rows={8}
                className="font-mono text-sm"
              />
            </div>

            {parseError && (
              <Alert variant="destructive">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>{parseError}</AlertDescription>
              </Alert>
            )}

            {!previewing && (
              <Button variant="outline" onClick={handlePreview} disabled={!jsonInput.trim()}>
                <Eye className="h-4 w-4 mr-2" /> Preview
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Preview Table */}
      {previewing && parsedQuestions.length > 0 && (
        <Card className="mb-3">
          <CardHeader>
            <CardTitle className="text-base">
              Preview ({parsedQuestions.length} question{parsedQuestions.length !== 1 ? 's' : ''})
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>#</TableHead>
                  <TableHead>Title</TableHead>
                  <TableHead>Problem Statement</TableHead>
                  <TableHead>Technologies</TableHead>
                  <TableHead>Difficulty</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {parsedQuestions.map((q, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{idx + 1}</TableCell>
                    <TableCell className="font-medium max-w-[200px] truncate">{q.title}</TableCell>
                    <TableCell className="max-w-[300px] truncate text-sm text-muted-foreground">
                      {q.problemStatement}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {q.keyTechnologies?.map((tech) => (
                          <Badge key={tech} variant="secondary" className="text-xs">
                            {tech}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell>
                      {q.difficulty && (
                        <Badge variant="outline" className="text-xs">
                          {q.difficulty}
                        </Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <div className="flex justify-end space-x-3">
        <Button variant="outline" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Cancel
        </Button>
        {previewing && parsedQuestions.length > 0 && (
          <Button onClick={handleUpload} disabled={uploading || !courseId}>
            {uploading ? (
              <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Uploading...</>
            ) : (
              <><Upload className="h-4 w-4 mr-2" /> Upload {parsedQuestions.length} Question{parsedQuestions.length !== 1 ? 's' : ''}</>
            )}
          </Button>
        )}
      </div>
    </div>
  )
}
