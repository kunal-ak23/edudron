'use client'

import { useEffect, useState, useCallback, useMemo } from 'react'
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
  CheckSquare,
  AlertTriangle,
  ShieldCheck,
  Camera
} from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient, enrollmentsApi } from '@/lib/api'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@kunal-ak23/edudron-shared-utils'
import { ProctoringReport } from '@/components/exams/ProctoringReport'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

/** Eligible student (section/class have name+email; course-wide may have id only) */
interface EligibleStudent {
  id: string
  name?: string
  email?: string
}

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
  completedAt?: string | null
  answersJson: any
  markedAsCheating?: boolean
}

interface Exam {
  id: string
  title: string
  description?: string
  reviewMethod?: string
  isMcqOnly?: boolean
  courseId?: string
  sectionId?: string
  classId?: string
  enableProctoring?: boolean
}

export const dynamic = 'force-dynamic'

export default function ExamSubmissionsPage() {
  const params = useParams()
  const router = useRouter()
  const examId = params.id as string
  const { toast } = useToast()

  const [exam, setExam] = useState<Exam | null>(null)
  const [submissions, setSubmissions] = useState<Submission[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedSubmissions, setSelectedSubmissions] = useState<Set<string>>(new Set())
  const [regrading, setRegrading] = useState(false)
  const [gradingMcq, setGradingMcq] = useState(false)
  const [regradingSubmissions, setRegradingSubmissions] = useState<Set<string>>(new Set())
  const [markingCheatingSubmissions, setMarkingCheatingSubmissions] = useState<Set<string>>(new Set())
  const [resettingSubmissions, setResettingSubmissions] = useState<Set<string>>(new Set())
  const [resettingBulk, setResettingBulk] = useState(false)
  const [resetDialogOpen, setResetDialogOpen] = useState(false)
  const [resetTargetSubmissionId, setResetTargetSubmissionId] = useState<string | null>(null)
  const [bulkResetDialogOpen, setBulkResetDialogOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [pendingSearchQuery, setPendingSearchQuery] = useState('')
  const [eligibleStudents, setEligibleStudents] = useState<EligibleStudent[] | null>(null)
  const [showProctoringDialog, setShowProctoringDialog] = useState(false)
  const [proctoringSubmissionId, setProctoringSubmissionId] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      setEligibleStudents(null)

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

      // Fetch eligible students (section / class / course) for appeared vs pending
      if (examData?.sectionId) {
        try {
          const sectionStudents = await enrollmentsApi.getStudentsBySection(examData.sectionId)
          setEligibleStudents(
            (sectionStudents || []).map((s: { id: string; name?: string; email?: string }) => ({
              id: s.id,
              name: s.name,
              email: s.email
            }))
          )
        } catch {
          setEligibleStudents(null)
        }
      } else if (examData?.classId) {
        try {
          const classStudents = await enrollmentsApi.getStudentsByClass(examData.classId)
          setEligibleStudents(
            (classStudents || []).map((s: { id: string; name?: string; email?: string }) => ({
              id: s.id,
              name: s.name,
              email: s.email
            }))
          )
        } catch {
          setEligibleStudents(null)
        }
      } else if (examData?.courseId) {
        try {
          const { content } = await enrollmentsApi.listAllEnrollmentsPaginated(0, 500, {
            courseId: examData.courseId
          })
          const byId = new Map<string, EligibleStudent>()
            ; (content || []).forEach((e: { studentId: string; studentEmail?: string }) => {
              if (e.studentId && !byId.has(e.studentId)) {
                byId.set(e.studentId, {
                  id: e.studentId,
                  email: e.studentEmail
                })
              }
            })
          setEligibleStudents(Array.from(byId.values()))
        } catch {
          setEligibleStudents(null)
        }
      }
    } catch (error) {
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

  const filteredSubmissions = useMemo(() => {
    let list = submissions
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase()
      list = list.filter(
        (s) =>
          s.studentId.toLowerCase().includes(query) ||
          (s.studentName?.toLowerCase().includes(query)) ||
          (s.studentEmail?.toLowerCase().includes(query))
      )
    }
    return list
  }, [submissions, searchQuery])

  const appearedSet = useMemo(
    () => new Set(submissions.map((s) => s.studentId)),
    [submissions]
  )
  const pendingList = useMemo(
    () =>
      eligibleStudents
        ? eligibleStudents.filter((e) => !appearedSet.has(e.id))
        : [],
    [eligibleStudents, appearedSet]
  )
  const filteredPendingList = useMemo(() => {
    if (!pendingSearchQuery.trim()) return pendingList
    const q = pendingSearchQuery.toLowerCase().trim()
    return pendingList.filter(
      (s) =>
        (s.name?.toLowerCase().includes(q)) ||
        (s.email?.toLowerCase().includes(q)) ||
        s.id.toLowerCase().includes(q)
    )
  }, [pendingList, pendingSearchQuery])
  const eligibleCount = eligibleStudents?.length ?? 0
  const appearedCount = appearedSet.size
  const showEligiblePending = eligibleStudents != null

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
      toast({
        title: 'Error',
        description: 'Failed to re-grade submissions',
        variant: 'destructive'
      })
    } finally {
      setRegrading(false)
    }
  }

  const openBulkResetDialog = () => {
    if (selectedSubmissions.size === 0) {
      toast({
        title: 'No submissions selected',
        description: 'Please select submissions to reset',
        variant: 'destructive'
      })
      return
    }
    setBulkResetDialogOpen(true)
  }

  const handleBulkResetConfirmed = async () => {
    if (selectedSubmissions.size === 0) {
      setBulkResetDialogOpen(false)
      return
    }
    setResettingBulk(true)
    try {
      const response = await apiClient.post(`/api/exams/${examId}/submissions/reset-bulk`, {
        submissionIds: Array.from(selectedSubmissions)
      })

      const result = (response as any)?.data ?? (response as any)
      const successCount = result?.successCount ?? 0
      const failureCount = result?.failureCount ?? 0
      toast({
        title: 'Reset complete',
        description: `Reset ${successCount} submission(s).${failureCount > 0 ? ` ${failureCount} failed or not found.` : ''}`
      })
      setSelectedSubmissions(new Set())
      await loadData()
    } catch (error: any) {
      const msg = error?.response?.data?.error || error?.message || 'Failed to reset submissions'
      toast({
        title: 'Error',
        description: msg,
        variant: 'destructive'
      })
    } finally {
      setResettingBulk(false)
      setBulkResetDialogOpen(false)
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

  const handleMarkAsCheating = async (submissionId: string, markedAsCheating: boolean) => {
    setMarkingCheatingSubmissions(prev => new Set(prev).add(submissionId))
    try {
      await apiClient.put(`/api/exams/${examId}/submissions/${submissionId}/mark-cheating`, {
        markedAsCheating
      })
      toast({
        title: 'Success',
        description: markedAsCheating ? 'Submission marked as cheating' : 'Cheating flag removed'
      })
      await loadData()
    } catch (error) {
      toast({
        title: 'Error',
        description: 'Failed to update cheating flag',
        variant: 'destructive'
      })
    } finally {
      setMarkingCheatingSubmissions(prev => {
        const newSet = new Set(prev)
        newSet.delete(submissionId)
        return newSet
      })
    }
  }

  const openResetDialog = (submissionId: string) => {
    setResetTargetSubmissionId(submissionId)
    setResetDialogOpen(true)
  }

  const handleResetSubmission = async () => {
    if (!resetTargetSubmissionId) {
      setResetDialogOpen(false)
      return
    }
    const submissionId = resetTargetSubmissionId
    setResettingSubmissions(prev => new Set(prev).add(submissionId))
    try {
      await apiClient.delete(`/api/exams/${examId}/submissions/${submissionId}`)
      toast({
        title: 'Success',
        description: 'Test reset. Student can take the test again.'
      })
      await loadData()
    } catch (error: any) {
      const msg = error?.response?.data?.error || error?.message || 'Failed to reset test'
      toast({
        title: 'Error',
        description: msg,
        variant: 'destructive'
      })
    } finally {
      setResettingSubmissions(prev => {
        const newSet = new Set(prev)
        newSet.delete(submissionId)
        return newSet
      })
      setResetTargetSubmissionId(null)
      setResetDialogOpen(false)
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
      'Attempt',
      'Status',
      'Review Status',
      'Marked as cheating',
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
        submission.completedAt == null ? 'In progress' : 'Submitted',
        submission.isPassed ? 'Passed' : 'Failed',
        submission.reviewStatus,
        submission.markedAsCheating ? 'Yes' : 'No',
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
          {selectedSubmissions.size > 0 && (
            <Button
              onClick={openBulkResetDialog}
              disabled={resettingBulk}
              variant="outline"
              title="Remove selected attempts so students can take the test again (max 500)"
            >
              {resettingBulk ? (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <RefreshCw className="h-4 w-4 mr-2" />
              )}
              Reset selected ({selectedSubmissions.size})
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

      {showEligiblePending && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-500">
                Eligible
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{eligibleCount}</div>
              <p className="text-xs text-muted-foreground mt-1">Students who can take this exam</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-500">
                Appeared
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{appearedCount}</div>
              <p className="text-xs text-muted-foreground mt-1">At least one submission (in progress or submitted)</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-500">
                Pending
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{pendingList.length}</div>
              <p className="text-xs text-muted-foreground mt-1">Eligible but not yet appeared</p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Search */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1 max-w-md">
              <Label htmlFor="search">Search by student ID, name or email</Label>
              <Input
                id="search"
                placeholder="Student ID, name or email..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Submissions Table */}
      <Card>
        <CardContent className="p-0">
          {filteredSubmissions.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              {searchQuery ? 'No submissions found matching your search.' : 'No submissions yet.'}
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
                  <TableHead>Attempt</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Review Status</TableHead>
                  <TableHead>Cheating</TableHead>
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
                      <div className="flex flex-col" title={submission.studentId}>
                        {(submission.studentName || submission.studentEmail) ? (
                          <>
                            {submission.studentName && <span className="font-medium">{submission.studentName}</span>}
                            {submission.studentEmail && (
                              <span className="text-muted-foreground text-xs">{submission.studentEmail}</span>
                            )}
                          </>
                        ) : (
                          <span className="text-muted-foreground">{submission.studentId?.substring(0, 12)}...</span>
                        )}
                      </div>
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
                      {submission.completedAt == null ? (
                        <Badge variant="secondary" className="gap-1">
                          <Clock className="h-3 w-3" />
                          In progress
                        </Badge>
                      ) : (
                        <Badge variant="outline" className="gap-1">
                          <CheckCircle className="h-3 w-3" />
                          Submitted
                        </Badge>
                      )}
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
                    <TableCell>
                      {submission.markedAsCheating ? (
                        <Badge variant="destructive" className="gap-1">
                          <AlertTriangle className="h-3 w-3" />
                          Flagged
                        </Badge>
                      ) : (
                        <span className="text-muted-foreground text-sm">No</span>
                      )}
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
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openResetDialog(submission.id)}
                          disabled={resettingSubmissions.has(submission.id)}
                          title="Reset test for this student so they can take it again"
                        >
                          {resettingSubmissions.has(submission.id) ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <>
                              <RefreshCw className="h-4 w-4 mr-1" />
                              Reset test
                            </>
                          )}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleMarkAsCheating(submission.id, !submission.markedAsCheating)}
                          disabled={markingCheatingSubmissions.has(submission.id)}
                          title={submission.markedAsCheating ? 'Unmark cheating' : 'Mark as cheating'}
                        >
                          {markingCheatingSubmissions.has(submission.id) ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : submission.markedAsCheating ? (
                            <>
                              <ShieldCheck className="h-4 w-4 mr-1" />
                              Unmark
                            </>
                          ) : (
                            <>
                              <AlertTriangle className="h-4 w-4 mr-1" />
                              Mark cheating
                            </>
                          )}
                        </Button>
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
                        {exam?.enableProctoring && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setProctoringSubmissionId(submission.id)
                              setShowProctoringDialog(true)
                            }}
                            title="View proctoring report"
                          >
                            <Camera className="h-4 w-4 mr-1" />
                            Proctoring
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {showEligiblePending && (
        <Card>
          <CardHeader>
            <CardTitle>Pending students</CardTitle>
            <p className="text-sm text-muted-foreground mt-1">
              Eligible for this exam but have not yet appeared (no submission)
            </p>
          </CardHeader>
          <CardContent className="space-y-4">
            {pendingList.length > 0 && (
              <div className="max-w-md">
                <Label htmlFor="pending-search">Search by name, email or student ID</Label>
                <Input
                  id="pending-search"
                  placeholder="Name, email or student ID..."
                  value={pendingSearchQuery}
                  onChange={(e) => setPendingSearchQuery(e.target.value)}
                  className="mt-1"
                />
              </div>
            )}
            {pendingList.length === 0 ? (
              <p className="text-muted-foreground text-center py-6">
                No pending students. All eligible students have at least one submission.
              </p>
            ) : filteredPendingList.length === 0 ? (
              <p className="text-muted-foreground text-center py-6">
                No pending students match your search.
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Student ID</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredPendingList.map((student) => (
                    <TableRow key={student.id}>
                      <TableCell className="text-sm font-medium">
                        {student.name ?? '—'}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {student.email ?? '—'}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground font-mono">
                        {student.id}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}

      <ConfirmationDialog
        isOpen={bulkResetDialogOpen}
        onClose={() => setBulkResetDialogOpen(false)}
        onConfirm={handleBulkResetConfirmed}
        title={`Reset ${selectedSubmissions.size} attempt${selectedSubmissions.size === 1 ? '' : 's'}`}
        description="These submissions will be removed and the students will be able to take the test again. Max 500 resets per request."
        confirmText="Reset attempts"
        cancelText="Cancel"
        variant="destructive"
        isLoading={resettingBulk}
      />

      <ConfirmationDialog
        isOpen={resetDialogOpen && !!resetTargetSubmissionId}
        onClose={() => {
          if (!resettingSubmissions.size) {
            setResetDialogOpen(false)
            setResetTargetSubmissionId(null)
          }
        }}
        onConfirm={handleResetSubmission}
        title="Reset attempt"
        description="This submission will be removed and the student will be able to take the test again."
        confirmText="Reset attempt"
        cancelText="Cancel"
        variant="destructive"
        isLoading={resetTargetSubmissionId ? resettingSubmissions.has(resetTargetSubmissionId) : false}
      />

      {/* Proctoring Report Dialog */}
      <Dialog
        open={showProctoringDialog}
        onOpenChange={(open) => {
          setShowProctoringDialog(open)
          if (!open) setProctoringSubmissionId(null)
        }}
      >
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Proctoring report</DialogTitle>
            <DialogDescription>
              {proctoringSubmissionId && `Submission: ${proctoringSubmissionId}`}
            </DialogDescription>
          </DialogHeader>
          {proctoringSubmissionId && examId && (
            <ProctoringReport examId={examId} submissionId={proctoringSubmissionId} />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
