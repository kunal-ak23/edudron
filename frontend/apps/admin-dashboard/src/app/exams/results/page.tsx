'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
  TableRow 
} from '@/components/ui/table'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import { 
  Loader2, 
  Download, 
  ChevronDown, 
  ChevronRight,
  CheckCircle,
  XCircle,
  Clock,
  Sparkles,
  RefreshCw
} from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient } from '@/lib/api'

interface ExamResult {
  examId: string
  examTitle: string
  examDescription?: string
  courseId: string
  status: string
  startTime?: string
  endTime?: string
  reviewMethod: string
  createdAt: string
  statistics: {
    totalSubmissions: number
    gradedSubmissions: number
    pendingReviews: number
    avgScore: number
    avgMaxScore: number
    avgPercentage: number
    passRate: number
    passedCount: number
  }
  submissions: any[]
}

export const dynamic = 'force-dynamic'

export default function ExamResultsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [results, setResults] = useState<ExamResult[]>([])
  const [filteredResults, setFilteredResults] = useState<ExamResult[]>([])
  const [loading, setLoading] = useState(true)
  const [expandedExams, setExpandedExams] = useState<Set<string>>(new Set())
  const [selectedSubmissions, setSelectedSubmissions] = useState<Set<string>>(new Set())
  const [bulkReviewing, setBulkReviewing] = useState(false)
  const [regradingSubmissions, setRegradingSubmissions] = useState<Set<string>>(new Set())
  
  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [searchQuery, setSearchQuery] = useState('')

  const loadResults = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apiClient.get<ExamResult[]>('/api/exams/all-results')
      
      // Handle different response formats
      let resultsData: ExamResult[] = []
      if (Array.isArray(response)) {
        resultsData = response
      } else if (response && typeof response === 'object' && 'data' in response) {
        resultsData = (response as any).data
      }
      
      setResults(resultsData)
      setFilteredResults(resultsData)
    } catch (error) {
      console.error('Failed to load exam results:', error)
      toast({
        title: 'Error',
        description: 'Failed to load exam results',
        variant: 'destructive'
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadResults()
  }, [loadResults])

  useEffect(() => {
    // Apply filters
    let filtered = results

    // Status filter
    if (statusFilter !== 'all') {
      filtered = filtered.filter(r => r.status === statusFilter)
    }

    // Search filter (by exam title or description)
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase()
      filtered = filtered.filter(r => 
        r.examTitle.toLowerCase().includes(query) ||
        (r.examDescription && r.examDescription.toLowerCase().includes(query))
      )
    }

    setFilteredResults(filtered)
  }, [results, statusFilter, searchQuery])

  const toggleExam = (examId: string) => {
    const newExpanded = new Set(expandedExams)
    if (newExpanded.has(examId)) {
      newExpanded.delete(examId)
    } else {
      newExpanded.add(examId)
    }
    setExpandedExams(newExpanded)
  }

  const toggleSubmission = (submissionId: string) => {
    const newSelected = new Set(selectedSubmissions)
    if (newSelected.has(submissionId)) {
      newSelected.delete(submissionId)
    } else {
      newSelected.add(submissionId)
    }
    setSelectedSubmissions(newSelected)
  }

  const handleBulkReview = async () => {
    if (selectedSubmissions.size === 0) {
      toast({
        title: 'No submissions selected',
        description: 'Please select submissions to review',
        variant: 'destructive'
      })
      return
    }

    setBulkReviewing(true)
    try {
      await apiClient.post('/api/exams/bulk-review', {
        submissionIds: Array.from(selectedSubmissions)
      })
      
      toast({
        title: 'Success',
        description: `AI review triggered for ${selectedSubmissions.size} submission(s)`
      })
      
      setSelectedSubmissions(new Set())
      await loadResults()
    } catch (error) {
      console.error('Failed to trigger bulk review:', error)
      toast({
        title: 'Error',
        description: 'Failed to trigger bulk review',
        variant: 'destructive'
      })
    } finally {
      setBulkReviewing(false)
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

    // Find exam IDs for selected submissions (only exams with AI review enabled)
    const submissionsByExam = new Map<string, string[]>()
    
    for (const result of results) {
      if (result.reviewMethod !== 'AI' && result.reviewMethod !== 'BOTH') {
        continue
      }
      for (const submission of result.submissions) {
        if (selectedSubmissions.has(submission.id)) {
          if (!submissionsByExam.has(result.examId)) {
            submissionsByExam.set(result.examId, [])
          }
          submissionsByExam.get(result.examId)!.push(submission.id)
        }
      }
    }

    if (submissionsByExam.size === 0) {
      toast({
        title: 'Re-grade not available',
        description: 'Re-grade is only available for exams with AI review. Use Grade All MCQ on the exam submissions page for MCQ-only exams.',
        variant: 'destructive'
      })
      return
    }

    setBulkReviewing(true)
    let totalSuccess = 0
    let totalFailed = 0

    try {
      for (const [examId, submissionIds] of submissionsByExam.entries()) {
        try {
          const response = await apiClient.post(`/api/exams/${examId}/submissions/regrade-bulk`, {
            submissionIds
          })
          
          const result = response as any
          totalSuccess += result.successCount || 0
          totalFailed += result.failureCount || 0
        } catch (error) {
          console.error(`Failed to re-grade submissions for exam ${examId}:`, error)
          totalFailed += submissionIds.length
        }
      }
      
      toast({
        title: 'Re-grading Complete',
        description: `Successfully re-graded ${totalSuccess} submission(s). ${totalFailed > 0 ? `${totalFailed} failed.` : ''}`
      })
      
      setSelectedSubmissions(new Set())
      await loadResults()
    } catch (error) {
      console.error('Failed to trigger bulk re-grade:', error)
      toast({
        title: 'Error',
        description: 'Failed to trigger bulk re-grade',
        variant: 'destructive'
      })
    } finally {
      setBulkReviewing(false)
    }
  }

  const handleRegradeSubmission = async (examId: string, submissionId: string) => {
    setRegradingSubmissions(prev => new Set(prev).add(submissionId))
    
    try {
      await apiClient.post(`/api/exams/${examId}/submissions/${submissionId}/regrade`, {})
      
      toast({
        title: 'Success',
        description: 'Submission re-graded successfully'
      })
      
      await loadResults()
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
      'Exam Title',
      'Status',
      'Total Submissions',
      'Graded',
      'Pending Reviews',
      'Avg Score',
      'Avg Percentage',
      'Pass Rate',
      'Start Time',
      'End Time'
    ].join(','))
    
    // Data rows
    for (const result of filteredResults) {
      csvRows.push([
        `"${result.examTitle}"`,
        result.status,
        result.statistics.totalSubmissions,
        result.statistics.gradedSubmissions,
        result.statistics.pendingReviews,
        result.statistics.avgScore.toFixed(2),
        result.statistics.avgPercentage.toFixed(2) + '%',
        result.statistics.passRate.toFixed(2) + '%',
        result.startTime ? new Date(result.startTime).toLocaleString() : 'N/A',
        result.endTime ? new Date(result.endTime).toLocaleString() : 'N/A'
      ].join(','))
    }
    
    const csvContent = csvRows.join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `exam-results-${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    
    toast({
      title: 'Success',
      description: 'Results exported to CSV'
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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Exam Results</h1>
          <p className="text-gray-600 mt-1">View and analyze exam performance across all exams</p>
        </div>
        <div className="flex gap-2">
          {selectedSubmissions.size > 0 && (
            <>
              <Button 
                onClick={handleBulkReview} 
                disabled={bulkReviewing}
                variant="outline"
              >
                {bulkReviewing ? (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                  <Sparkles className="h-4 w-4 mr-2" />
                )}
                Review Selected ({selectedSubmissions.size})
              </Button>
              <Button 
                onClick={handleBulkRegrade} 
                disabled={bulkReviewing}
                variant="outline"
              >
                {bulkReviewing ? (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                  <RefreshCw className="h-4 w-4 mr-2" />
                )}
                Re-grade Selected ({selectedSubmissions.size})
              </Button>
            </>
          )}
          <Button onClick={exportToCSV} variant="outline">
            <Download className="h-4 w-4 mr-2" />
            Export CSV
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <Label htmlFor="search">Search</Label>
              <Input
                id="search"
                placeholder="Search by exam title..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="status">Status</Label>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger id="status">
                  <SelectValue placeholder="All statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Statuses</SelectItem>
                  <SelectItem value="DRAFT">Draft</SelectItem>
                  <SelectItem value="SCHEDULED">Scheduled</SelectItem>
                  <SelectItem value="LIVE">Live</SelectItem>
                  <SelectItem value="COMPLETED">Completed</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Results */}
      {filteredResults.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-gray-500">No exam results found</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {filteredResults.map((result) => (
            <Collapsible
              key={result.examId}
              open={expandedExams.has(result.examId)}
              onOpenChange={() => toggleExam(result.examId)}
            >
              <Card>
                <CardHeader>
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <CollapsibleTrigger asChild>
                        <button className="flex items-center gap-2 text-left hover:underline">
                          {expandedExams.has(result.examId) ? (
                            <ChevronDown className="h-4 w-4" />
                          ) : (
                            <ChevronRight className="h-4 w-4" />
                          )}
                          <CardTitle className="text-xl">{result.examTitle}</CardTitle>
                        </button>
                      </CollapsibleTrigger>
                      {result.examDescription && (
                        <p className="text-sm text-gray-600 mt-1 ml-6">
                          {result.examDescription}
                        </p>
                      )}
                    </div>
                    <Badge variant={result.status === 'COMPLETED' ? 'outline' : 'default'}>
                      {result.status}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                    <div>
                      <div className="text-sm text-gray-500">Total Submissions</div>
                      <div className="text-2xl font-bold">{result.statistics.totalSubmissions}</div>
                    </div>
                    <div>
                      <div className="text-sm text-gray-500">Avg Score</div>
                      <div className="text-2xl font-bold">
                        {result.statistics.avgPercentage.toFixed(1)}%
                      </div>
                    </div>
                    <div>
                      <div className="text-sm text-gray-500">Pass Rate</div>
                      <div className="text-2xl font-bold text-green-600">
                        {result.statistics.passRate.toFixed(1)}%
                      </div>
                    </div>
                    <div>
                      <div className="text-sm text-gray-500">Pending Reviews</div>
                      <div className="text-2xl font-bold text-orange-600">
                        {result.statistics.pendingReviews}
                      </div>
                    </div>
                  </div>

                  <CollapsibleContent>
                    {result.submissions.length > 0 ? (
                      <div className="mt-4 border rounded-lg overflow-hidden">
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead className="w-12">
                                <input
                                  type="checkbox"
                                  checked={result.submissions.every(s => 
                                    selectedSubmissions.has(s.id)
                                  )}
                                  onChange={(e) => {
                                    const newSelected = new Set(selectedSubmissions)
                                    if (e.target.checked) {
                                      result.submissions.forEach(s => newSelected.add(s.id))
                                    } else {
                                      result.submissions.forEach(s => newSelected.delete(s.id))
                                    }
                                    setSelectedSubmissions(newSelected)
                                  }}
                                />
                              </TableHead>
                              <TableHead>Student ID</TableHead>
                              <TableHead>Score</TableHead>
                              <TableHead>Percentage</TableHead>
                              <TableHead>Status</TableHead>
                              <TableHead>Review Status</TableHead>
                              <TableHead>Submitted</TableHead>
                              <TableHead>Actions</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {result.submissions.map((submission) => (
                              <TableRow key={submission.id}>
                                <TableCell>
                                  <input
                                    type="checkbox"
                                    checked={selectedSubmissions.has(submission.id)}
                                    onChange={() => toggleSubmission(submission.id)}
                                  />
                                </TableCell>
                                <TableCell className="font-mono text-sm">
                                  {submission.studentId?.substring(0, 8)}...
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
                                      submission.reviewStatus === 'AI_REVIEWED' 
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
                                <TableCell>
                                  <div className="flex gap-2">
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      onClick={() => router.push(`/exams/${result.examId}`)}
                                    >
                                      View
                                    </Button>
                                    {(result.reviewMethod === 'AI' || result.reviewMethod === 'BOTH') && (
                                      <Button
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => handleRegradeSubmission(result.examId, submission.id)}
                                        disabled={regradingSubmissions.has(submission.id)}
                                      >
                                        {regradingSubmissions.has(submission.id) ? (
                                          <Loader2 className="h-3 w-3 animate-spin" />
                                        ) : (
                                          <RefreshCw className="h-3 w-3" />
                                        )}
                                      </Button>
                                    )}
                                  </div>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </div>
                    ) : (
                      <div className="text-center py-8 text-gray-500">
                        No submissions yet
                      </div>
                    )}
                  </CollapsibleContent>
                </CardContent>
              </Card>
            </Collapsible>
          ))}
        </div>
      )}
    </div>
  )
}
