'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { ArrowLeft, Upload, Download, Loader2, FileText, CheckCircle, XCircle, AlertCircle, CheckCircle2, RefreshCw } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, coursesApi } from '@/lib/api'
import { CourseStructureTree } from '@/components/CourseStructureTree'

interface Course {
  id: string
  title: string
}

interface Section {
  id: string
  title: string
}

interface QuestionImportRowResult {
  rowNumber: number
  questionText: string
  success: boolean
  questionId?: string
  errorMessage?: string
  updated?: boolean
}

interface ImportResult {
  totalRows: number
  successfulRows: number
  failedRows: number
  createdRows: number
  updatedRows: number
  rowResults?: QuestionImportRowResult[]
  // For backward compatibility
  successCount?: number
  errorCount?: number
}

export const dynamic = 'force-dynamic'

export default function QuestionBankImportPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const { user } = useAuth()
  const fileInputRef = useRef<HTMLInputElement>(null)
  
  const [courses, setCourses] = useState<Course[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [selectedCourse, setSelectedCourse] = useState<string>('')
  const [selectedModule, setSelectedModule] = useState<string>('')
  const [loading, setLoading] = useState(false)
  const [loadingSections, setLoadingSections] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [options, setOptions] = useState({
    upsertExisting: false,
  })
  
  const canImport = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'

  // Initialize from URL params
  useEffect(() => {
    const courseIdParam = searchParams.get('courseId')
    const moduleIdParam = searchParams.get('moduleId')
    if (courseIdParam) {
      setSelectedCourse(courseIdParam)
    }
    if (moduleIdParam) {
      setSelectedModule(moduleIdParam)
    }
  }, [searchParams])

  // Load courses
  const loadCourses = useCallback(async () => {
    setLoading(true)
    try {
      const courses = await coursesApi.listCourses()
      if (Array.isArray(courses)) {
        setCourses(courses)
      }
    } catch (error) {
      console.error('Failed to load courses:', error)
      toast({
        title: 'Error',
        description: 'Failed to load courses',
        variant: 'destructive'
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadCourses()
  }, [loadCourses])

  // Load sections when course changes
  const loadSections = useCallback(async (courseId: string) => {
    if (!courseId) {
      setSections([])
      return
    }
    setLoadingSections(true)
    try {
      // Use getSections API to fetch sections separately
      const courseSections = await coursesApi.getSections(courseId)
      if (Array.isArray(courseSections)) {
        setSections(courseSections.map(s => ({ id: s.id, title: s.title })))
      } else {
        setSections([])
      }
    } catch (error) {
      console.error('Failed to load sections:', error)
      setSections([])
    } finally {
      setLoadingSections(false)
    }
  }, [])

  useEffect(() => {
    if (selectedCourse) {
      loadSections(selectedCourse)
    } else {
      setSections([])
      setSelectedModule('')
    }
  }, [selectedCourse, loadSections])

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      // Validate file type
      const validTypes = [
        'text/csv',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      ]
      const extension = file.name.toLowerCase().split('.').pop()
      
      if (!validTypes.includes(file.type) && !['csv', 'xlsx', 'xls'].includes(extension || '')) {
        toast({
          title: 'Invalid file type',
          description: 'Please upload a CSV or Excel file (.csv, .xlsx, .xls)',
          variant: 'destructive'
        })
        return
      }
      
      // Validate file size (max 10MB)
      if (file.size > 10 * 1024 * 1024) {
        toast({
          title: 'File too large',
          description: 'Please upload a file smaller than 10MB',
          variant: 'destructive'
        })
        return
      }
      
      setSelectedFile(file)
      setImportResult(null)
    }
  }

  const handleDownloadTemplate = async () => {
    try {
      const blob = await apiClient.downloadFile('/api/question-bank/import/template')
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'question-bank-template.csv'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Failed to download template:', error)
      toast({
        title: 'Error',
        description: 'Failed to download template',
        variant: 'destructive'
      })
    }
  }

  const handleImport = async () => {
    if (!selectedCourse) {
      toast({
        title: 'Error',
        description: 'Please select a course',
        variant: 'destructive'
      })
      return
    }
    
    if (!selectedFile) {
      toast({
        title: 'Error',
        description: 'Please select a file to import',
        variant: 'destructive'
      })
      return
    }
    
    setUploading(true)
    setImportResult(null)
    
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      formData.append('courseId', selectedCourse)
      if (selectedModule) {
        formData.append('moduleId', selectedModule)
      }
      formData.append('upsertExisting', options.upsertExisting.toString())
      
      const response = await apiClient.postForm<ImportResult>('/api/question-bank/import', formData)
      
      const result = response.data
      setImportResult(result)
      
      const successCount = result.successfulRows || result.successCount || 0
      const failedCount = result.failedRows || result.errorCount || 0
      const createdCount = result.createdRows || 0
      const updatedCount = result.updatedRows || 0
      
      if (successCount > 0 && failedCount === 0) {
        toast({
          title: 'Import completed successfully',
          description: updatedCount > 0 
            ? `Created ${createdCount} and updated ${updatedCount} questions`
            : `Successfully imported ${successCount} questions`
        })
      } else if (successCount > 0 && failedCount > 0) {
        toast({
          title: 'Import completed with errors',
          description: `${successCount} successful, ${failedCount} failed. See details below.`,
          variant: 'destructive'
        })
      } else if (failedCount > 0) {
        toast({
          title: 'Import failed',
          description: `All ${failedCount} rows had errors. See details below.`,
          variant: 'destructive'
        })
      }
    } catch (error: any) {
      console.error('Failed to import questions:', error)
      const errorMessage = error?.response?.data?.error || error?.message || 'Failed to import questions'
      toast({
        title: 'Import failed',
        description: errorMessage,
        variant: 'destructive'
      })
    } finally {
      setUploading(false)
    }
  }

  const handleClearFile = () => {
    setSelectedFile(null)
    setImportResult(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  if (!canImport) {
    return (
      <div className="p-4">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Access Denied</AlertTitle>
          <AlertDescription>
            You do not have permission to import questions. Only System Admins and Tenant Admins can import questions.
          </AlertDescription>
        </Alert>
      </div>
    )
  }

  return (
    <div className="p-4 space-y-4">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="ghost" onClick={() => {
          const params = new URLSearchParams()
          if (selectedCourse) params.set('courseId', selectedCourse)
          if (selectedModule) params.set('moduleId', selectedModule)
          const queryString = params.toString()
          router.push(`/question-bank${queryString ? '?' + queryString : ''}`)
        }}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div>
          <h1 className="text-2xl font-bold">Import Questions</h1>
          <p className="text-gray-500">Upload questions from CSV or Excel file</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Import Form */}
        <Card>
          <CardHeader>
            <CardTitle>Upload File</CardTitle>
            <CardDescription>
              Upload a CSV or Excel file to import questions into the Question Bank
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Course Selection */}
            <div>
              <Label>Select Course</Label>
              <Select value={selectedCourse} onValueChange={(v) => {
                setSelectedCourse(v)
                setSelectedModule('') // Reset module when course changes
              }} disabled={loading}>
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

            {/* Module Selection (optional) */}
            {selectedCourse && (
              <div>
                <Label>Module (optional)</Label>
                <Select 
                  value={selectedModule || '_none'} 
                  onValueChange={(v) => setSelectedModule(v === '_none' ? '' : v)} 
                  disabled={loadingSections}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="All modules" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="_none">All modules</SelectItem>
                    {sections.map(section => (
                      <SelectItem key={section.id} value={section.id}>
                        {section.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-gray-500 mt-1">
                  If selected, imported questions will be linked to this module
                </p>
              </div>
            )}

            {/* Import Options */}
            <div className="space-y-3 p-3 bg-gray-50 rounded-lg">
              <Label className="text-sm font-medium">Import Options</Label>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="upsertExisting"
                  checked={options.upsertExisting}
                  onCheckedChange={(checked) =>
                    setOptions({ ...options, upsertExisting: checked as boolean })
                  }
                />
                <Label htmlFor="upsertExisting" className="text-sm font-normal cursor-pointer">
                  Update existing questions (match by ID from export)
                </Label>
              </div>
              <p className="text-xs text-gray-500">
                If enabled, questions with an ID in the file will be updated instead of creating new ones.
                Export questions first to get their IDs.
              </p>
            </div>

            {/* File Upload */}
            <div>
              <Label>File</Label>
              <div className="mt-2">
                {!selectedFile ? (
                  <div
                    className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center cursor-pointer hover:border-primary transition-colors"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Upload className="h-10 w-10 mx-auto text-gray-400 mb-4" />
                    <p className="text-sm text-gray-600 mb-2">
                      Click to upload or drag and drop
                    </p>
                    <p className="text-xs text-gray-500">
                      CSV, XLSX, or XLS (max 10MB)
                    </p>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept=".csv,.xlsx,.xls"
                      onChange={handleFileSelect}
                      className="hidden"
                    />
                  </div>
                ) : (
                  <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border">
                    <div className="flex items-center gap-3">
                      <FileText className="h-8 w-8 text-blue-600" />
                      <div>
                        <p className="font-medium">{selectedFile.name}</p>
                        <p className="text-sm text-gray-500">
                          {(selectedFile.size / 1024).toFixed(1)} KB
                        </p>
                      </div>
                    </div>
                    <Button variant="ghost" size="sm" onClick={handleClearFile}>
                      Remove
                    </Button>
                  </div>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2">
              <Button
                onClick={handleImport}
                disabled={!selectedCourse || !selectedFile || uploading}
                className="flex-1"
              >
                {uploading && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                Import Questions
              </Button>
              <Button variant="outline" onClick={handleDownloadTemplate}>
                <Download className="h-4 w-4 mr-2" />
                Template
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Course Structure & Results */}
        <div className="space-y-4">
          {/* Course Structure Tree - shows when course is selected */}
          {selectedCourse && (
            <CourseStructureTree courseId={selectedCourse} />
          )}

          {/* Import Results */}
          {importResult && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  {(importResult.failedRows || importResult.errorCount || 0) === 0 ? (
                    <CheckCircle className="h-5 w-5 text-green-600" />
                  ) : (
                    <AlertCircle className="h-5 w-5 text-yellow-600" />
                  )}
                  Import Results
                </CardTitle>
              </CardHeader>
              <CardContent>
                {/* Summary Stats */}
                <div className="grid grid-cols-4 gap-3 mb-4">
                  <div className="text-center p-3 bg-gray-50 rounded-lg">
                    <div className="text-2xl font-bold text-gray-900">
                      {importResult.totalRows}
                    </div>
                    <div className="text-sm text-gray-600">Total</div>
                  </div>
                  <div className="text-center p-3 bg-green-50 rounded-lg">
                    <div className="text-2xl font-bold text-green-600">
                      {importResult.createdRows || 0}
                    </div>
                    <div className="text-sm text-green-700">Created</div>
                  </div>
                  <div className="text-center p-3 bg-blue-50 rounded-lg">
                    <div className="text-2xl font-bold text-blue-600">
                      {importResult.updatedRows || 0}
                    </div>
                    <div className="text-sm text-blue-700">Updated</div>
                  </div>
                  <div className="text-center p-3 bg-red-50 rounded-lg">
                    <div className="text-2xl font-bold text-red-600">
                      {importResult.failedRows || importResult.errorCount || 0}
                    </div>
                    <div className="text-sm text-red-700">Failed</div>
                  </div>
                </div>

                {/* Detailed Row Results */}
                {importResult.rowResults && importResult.rowResults.length > 0 && (
                  <div>
                    <h4 className="font-medium mb-2">Detailed Results:</h4>
                    <div className="border rounded-lg overflow-hidden max-h-72 overflow-y-auto">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead className="w-16">Row</TableHead>
                            <TableHead>Question</TableHead>
                            <TableHead className="w-24">Status</TableHead>
                            <TableHead>Details</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {importResult.rowResults.map((row, index) => (
                            <TableRow key={index} className={row.success ? '' : 'bg-red-50'}>
                              <TableCell className="font-medium">{row.rowNumber}</TableCell>
                              <TableCell className="max-w-[200px]">
                                <span className="truncate block" title={row.questionText}>
                                  {row.questionText || '-'}
                                </span>
                              </TableCell>
                              <TableCell>
                                {row.success ? (
                                  row.updated ? (
                                    <Badge className="bg-blue-500">
                                      <RefreshCw className="mr-1 h-3 w-3" />
                                      Updated
                                    </Badge>
                                  ) : (
                                    <Badge className="bg-green-500">
                                      <CheckCircle2 className="mr-1 h-3 w-3" />
                                      Created
                                    </Badge>
                                  )
                                ) : (
                                  <Badge variant="destructive">
                                    <XCircle className="mr-1 h-3 w-3" />
                                    Failed
                                  </Badge>
                                )}
                              </TableCell>
                              <TableCell className="text-sm">
                                {row.success ? (
                                  <span className="text-gray-600">ID: {row.questionId}</span>
                                ) : (
                                  <span className="text-red-600 flex items-center gap-1">
                                    <AlertCircle className="h-4 w-4 flex-shrink-0" />
                                    {row.errorMessage}
                                  </span>
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Instructions */}
          <Card>
            <CardHeader>
              <CardTitle>File Format Instructions</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <h4 className="font-medium mb-2">Columns (in order):</h4>
                <ul className="text-sm text-gray-600 space-y-1 list-disc list-inside">
                  <li><code className="bg-gray-100 px-1">id</code> - Question ID (leave empty for new, fill for update)</li>
                  <li><code className="bg-gray-100 px-1">questionType</code> - MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, or ESSAY</li>
                  <li><code className="bg-gray-100 px-1">questionText</code> - The question text</li>
                  <li><code className="bg-gray-100 px-1">points</code> - Points (default: 1)</li>
                  <li><code className="bg-gray-100 px-1">difficultyLevel</code> - EASY, MEDIUM, or HARD</li>
                  <li><code className="bg-gray-100 px-1">moduleIds</code> - Module IDs (comma-separated for multiple)</li>
                  <li><code className="bg-gray-100 px-1">lectureId</code> - Optional lecture/sub-module ID</li>
                  <li><code className="bg-gray-100 px-1">explanation</code> - Answer explanation</li>
                  <li><code className="bg-gray-100 px-1">tentativeAnswer</code> - Expected answer (for TRUE_FALSE use "true"/"false")</li>
                  <li><code className="bg-gray-100 px-1">option1-10</code> - Answer options (up to 10)</li>
                  <li><code className="bg-gray-100 px-1">option1Correct-option10Correct</code> - true/false for each option</li>
                </ul>
              </div>

              <div>
                <h4 className="font-medium mb-2">Tips:</h4>
                <ul className="text-sm text-gray-600 space-y-1 list-disc list-inside">
                  <li>Download the template for the correct format</li>
                  <li>Export existing questions to get their IDs for updating</li>
                  <li>Use double quotes for text containing commas</li>
                  <li>For multiple modules, separate IDs with commas</li>
                  <li>Mark correct options as "true" or "1"</li>
                  <li>Enable "Update existing" to modify questions by ID</li>
                </ul>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
