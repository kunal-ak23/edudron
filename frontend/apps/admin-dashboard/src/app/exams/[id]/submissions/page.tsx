'use client'

import { useEffect, useState, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { 
  Table, 
  TableBody, 
  TableCell, 
  TableHead, 
  TableHeader, 
  TableRow 
} from '@/components/ui/table'
import { 
  Loader2, 
  ArrowLeft, 
  RefreshCw, 
  CheckCircle, 
  XCircle,
  Clock,
  Download,
  CheckSquare
} from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient } from '@/lib/api'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@kunal-ak23/edudron-shared-utils'

interface Submission {
  id: string
  studentId: string
  studentName?: string
  studentEmail?: string
  score: number | null
  maxScore: number | null
  percentage: number | null
  isPassed: boolean
  reviewStatus: string
  submittedAt: string
  gradedAt?: string
  answersJson: any
}

interface Exam {
  id: string
  title: string
  description?: string
  reviewMethod?: string
  isMcqOnly?: boolean
}

export const dynamic = 'force-dynamic'

export default function ExamSubmissionsPage() {
  const params = useParams()
  const router = useRouter()
  const examId = params.id as string
  const { toast } = useToast()
  
  const [exam, setExam] = useState<Exam | null>(null)
  const [submissions, setSubmissions] = useState<Submission[]>([])
  const [filteredSubmissions, setFilteredSubmissions] = useState<Submission[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedSubmissions, setSelectedSubmissions] = useState<Set<string>>(new Set())
  const [regrading, setRegrading] = useState(false)
  const [gradingMcq, setGradingMcq] = useState(false)
  const [regradingSubmissions, setRegradingSubmissions] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      
      // Load exam details
      const examResponse = await apiClient.get<any>(`/api/exams/${examId}`)
      const examData = (examResponse as any)?.data || examResponse
      setExam(examData)
      
      // Load submissions
      const submissionsResponse = await apiClient.get<any>(`/api/exams/${examId}/submissions`)
      const submissionsData = Array.isArray(submissionsResponse) 
        ? submissionsResponse 
        : (submissionsResponse as any)?.data || []
      
      setSubmissions(submissionsData)
      setFilteredSubmissions(submissionsData)
    } catch (error) {
      console.error('Failed to load exam submissions:', error)
      toast({
        title: 'Error',
        description: 'Failed to load submissions',
        variant: 'destructive'
      })
    } finally {
      setLoading(false)
    }
  }, [examId, toast])

  useEffect(() => {
    loadData()
  }, [loadData])

  useEffect(() => {
    // Filter submissions by search query (student ID, name or email)
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase()
      setFilteredSubmissions(
        submissions.filter(s =>
          s.studentId.toLowerCase().includes(query) ||
          (s.studentName?.toLowerCase().includes(query)) ||
          (s.studentEmail?.toLowerCase().includes(query))
        )
      )
    } else {
      setFilteredSubmissions(submissions)
    }
  }, [submissions, searchQuery])

  const toggleSubmission = (submissionId: string) => {
    const newSelected = new Set(selectedSubmissions)
    if (newSelected.has(submissionId)) {
      newSelected.delete(submissionId)
    } else {
      newSelected.add(submissionId)
    }
    setSelectedSubmissions(newSelected)
  }

  const toggleAllSubmissions = (checked: boolean) => {
    if (checked) {
      setSelectedSubmissions(new Set(filteredSubmissions.map(s => s.id)))
    } else {
      setSelectedSubmissions(new Set())
    }
  }

  const handleBulkRegrade = async () => {
    if (selectedSubmissions.size === 0) {
      toast({
        title: 'No submissions selected',
        description: 'Please select submissions to re-grade',
        variant: 'destructive'
      })
      return
    }

    setRegrading(true)
    try {
      const response = await apiClient.post(`/api/exams/${examId}/submissions/regrade-bulk`, {
        submissionIds: Array.from(selectedSubmissions)
      })
      
      const result = response as any
      
      toast({
        title: 'Re-grading Complete',
        description: `Successfully re-graded ${result.successCount || 0} submission(s). ${result.failureCount > 0 ? `${result.failureCount} failed.` : ''}`
      })
      
      setSelectedSubmissions(new Set())
      await loadData()
    } catch (error) {
      console.error('Failed to bulk re-grade:', error)
      toast({
        title: 'Error',
        description: 'Failed to re-grade submissions',
        variant: 'destructive'
      })
    } finally {
      setRegrading(false)
    }
  }

  const handleGradeAllMcq = async () => {
    setGradingMcq(true)
    try {
      const response = await apiClient.post(`/api/exams/${examId}/submissions/bulk-grade-mcq`, {})
      const result = response as any
      const body = result?.data ?? result
      const gradedCount = body?.gradedCount ?? 0
      const skippedCount = body?.skippedCount ?? 0
      const errors = body?.errors ?? []
      const errorCount = Array.isArray(errors) ? errors.length : 0
      let description = `Graded ${gradedCount} submission(s).`
      if (skippedCount > 0) description += ` Skipped ${skippedCount}.`
      if (errorCount > 0) description += ` ${errorCount} error(s).`
      toast({
        title: 'Grade All MCQ Complete',
        description
      })
      await loadData()
    } catch (error) {
      console.error('Failed to grade all MCQ:', error)
      toast({
        title: 'Error',
        description: 'Failed to grade submissions',
        variant: 'destructive'
      })
    } finally {
      setGradingMcq(false)
    }
  }

  const handleRegradeSubmission = async (submissionId: string) => {
    setRegradingSubmissions(prev => new Set(prev).add(submissionId))
    
    try {
      await apiClient.post(`/api/exams/${examId}/submissions/${submissionId}/regrade`, {})
      
      toast({
        title: 'Success',
        description: 'Submission re-graded successfully'
      })
      
      await loadData()
    } catch (error) {
      console.error('Failed to re-grade submission:', error)
      toast({
        title: 'Error',
        description: 'Failed to re-grade submission',
        variant: 'destructive'
      })
    } finally {
      setRegradingSubmissions(prev => {
        const newSet = new Set(prev)
        newSet.delete(submissionId)
        return newSet
      })
    }
  }

  const exportToCSV = () => {
    const csvRows = []
    
    // Header
    csvRows.push([
      'Student ID',
      'Student Name',
      'Student Email',
      'Score',
      'Max Score',
      'Percentage',
      'Status',
      'Review Status',
      'Submitted At',
      'Graded At'
    ].join(','))
    
    // Data rows
    for (const submission of filteredSubmissions) {
      csvRows.push([
        submission.studentId,
        submission.studentName ?? '',
        submission.studentEmail ?? '',
        submission.score !== null ? submission.score : 'N/A',
        submission.maxScore !== null ? submission.maxScore : 'N/A',
        submission.percentage !== null ? submission.percentage.toFixed(2) + '%' : 'N/A',
        submission.isPassed ? 'Passed' : 'Failed',
        submission.reviewStatus,
        submission.submittedAt ? new Date(submission.submittedAt).toLocaleString() : 'N/A',
        submission.gradedAt ? new Date(submission.gradedAt).toLocaleString() : 'N/A'
      ].join(','))
    }
    
    const csvContent = csvRows.join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `exam-${examId}-submissions-${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    
    toast({
      title: 'Success',
      description: 'Submissions exported to CSV'
    })
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" onClick={() => router.push(`/exams/${examId}`)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Exam
        </Button>
      </div>

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{exam?.title || 'Exam'} - Submissions</h1>
          <p className="text-gray-600 mt-1">
            Manage and re-grade exam submissions
          </p>
        </div>
        <div className="flex gap-2">
          {exam?.isMcqOnly && (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    onClick={handleGradeAllMcq}
                    disabled={gradingMcq}
                    variant="outline"
                  >
                    {gradingMcq ? (
                      <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    ) : (
                      <CheckSquare className="h-4 w-4 mr-2" />
                    )}
                    Grade All MCQ
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  Grade all completed submissions (MCQ/True-False only). Works for any review method.
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}
          {selectedSubmissions.size > 0 && (exam?.reviewMethod === 'AI' || exam?.reviewMethod === 'BOTH') && (
            <Button 
              onClick={handleBulkRegrade} 
              disabled={regrading}
              variant="outline"
            >
              {regrading ? (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <RefreshCw className="h-4 w-4 mr-2" />
              )}
              Re-grade Selected ({selectedSubmissions.size})
            </Button>
          )}
          <Button onClick={exportToCSV} variant="outline">
            <Download className="h-4 w-4 mr-2" />
            Export CSV
          </Button>
        </div>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              Total Submissions
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{submissions.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              Graded
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {submissions.filter(s => s.score !== null).length}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              Average Score
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {submissions.length > 0 && submissions.filter(s => s.percentage !== null).length > 0
                ? (
                    submissions
                      .filter(s => s.percentage !== null)
                      .reduce((acc, s) => acc + (s.percentage || 0), 0) /
                    submissions.filter(s => s.percentage !== null).length
                  ).toFixed(1)
                : '0'}
              %
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              Pass Rate
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {submissions.length > 0
                ? ((submissions.filter(s => s.isPassed).length / submissions.length) * 100).toFixed(1)
                : '0'}
              %
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Search Filter */}
      <Card>
        <CardContent className="pt-6">
          <div className="max-w-md">
            <Label htmlFor="search">Search by student ID, name or email</Label>
            <Input
              id="search"
              placeholder="Student ID, name or email..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      {/* Submissions Table */}
      <Card>
        <CardContent className="p-0">
          {filteredSubmissions.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              {searchQuery ? 'No submissions found matching your search' : 'No submissions yet'}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">
                    <input
                      type="checkbox"
                      checked={
                        filteredSubmissions.length > 0 &&
                        filteredSubmissions.every(s => selectedSubmissions.has(s.id))
                      }
                      onChange={(e) => toggleAllSubmissions(e.target.checked)}
                    />
                  </TableHead>
                  <TableHead>Student</TableHead>
                  <TableHead>Score</TableHead>
                  <TableHead>Percentage</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Review Status</TableHead>
                  <TableHead>Submitted</TableHead>
                  <TableHead>Graded</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredSubmissions.map((submission) => (
                  <TableRow key={submission.id}>
                    <TableCell>
                      <input
                        type="checkbox"
                        checked={selectedSubmissions.has(submission.id)}
                        onChange={() => toggleSubmission(submission.id)}
                      />
                    </TableCell>
                    <TableCell className="text-sm">
                      <span title={submission.studentId}>
                        {submission.studentName || submission.studentEmail || `${submission.studentId.substring(0, 12)}...`}
                      </span>
                    </TableCell>
                    <TableCell>
                      {submission.score !== null && submission.score !== undefined
                        ? `${submission.score} / ${submission.maxScore}`
                        : 'Not graded'}
                    </TableCell>
                    <TableCell>
                      {submission.percentage !== null && submission.percentage !== undefined
                        ? `${submission.percentage.toFixed(1)}%`
                        : '-'}
                    </TableCell>
                    <TableCell>
                      {submission.score !== null && submission.score !== undefined ? (
                        submission.isPassed ? (
                          <Badge variant="default" className="gap-1">
                            <CheckCircle className="h-3 w-3" />
                            Passed
                          </Badge>
                        ) : (
                          <Badge variant="destructive" className="gap-1">
                            <XCircle className="h-3 w-3" />
                            Failed
                          </Badge>
                        )
                      ) : (
                        <Badge variant="outline">Not Graded</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge 
                        variant={
                          submission.reviewStatus === 'COMPLETED' || 
                          submission.reviewStatus === 'AI_REVIEWED' ||
                          submission.reviewStatus === 'INSTRUCTOR_REVIEWED'
                            ? 'default' 
                            : 'outline'
                        }
                        className="gap-1"
                      >
                        {submission.reviewStatus === 'PENDING' && (
                          <Clock className="h-3 w-3" />
                        )}
                        {submission.reviewStatus || 'PENDING'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm">
                      {submission.submittedAt
                        ? new Date(submission.submittedAt).toLocaleDateString()
                        : '-'}
                    </TableCell>
                    <TableCell className="text-sm">
                      {submission.gradedAt
                        ? new Date(submission.gradedAt).toLocaleDateString()
                        : '-'}
                    </TableCell>
                    <TableCell className="text-right">
                      {(exam?.reviewMethod === 'AI' || exam?.reviewMethod === 'BOTH') && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleRegradeSubmission(submission.id)}
                          disabled={regradingSubmissions.has(submission.id)}
                          title="Re-grade this submission"
                        >
                          {regradingSubmissions.has(submission.id) ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <>
                              <RefreshCw className="h-4 w-4 mr-1" />
                              Re-grade
                            </>
                          )}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
