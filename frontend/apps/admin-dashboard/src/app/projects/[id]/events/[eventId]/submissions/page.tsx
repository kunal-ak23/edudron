'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  ArrowLeft,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Clock,
  Send,
  ExternalLink,
  FileDown,
  Loader2,
  ChevronRight,
  History,
} from 'lucide-react'
import { projectsApi } from '@/lib/api'
import type {
  ProjectDTO,
  ProjectEventDTO,
  ProjectEventFeedbackDTO,
} from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// ---------- Types for the submissions endpoint response ----------

interface GroupSubmissionEntry {
  groupId: string
  groupName: string
  groupNumber: number
  submission: {
    id: string
    projectId: string
    eventId: string
    groupId: string
    submissionUrl?: string
    submissionText?: string
    submittedBy: string
    submittedAt: string
    version: number
    status: string
    attachments?: Array<{
      id: string
      fileUrl: string
      fileName: string
      fileSizeBytes?: number
      mimeType?: string
    }>
  } | null
  latestFeedback?: ProjectEventFeedbackDTO
  members?: Array<{ studentId: string; name?: string; email?: string }>
}

interface GroupDetailResponse {
  latest: GroupSubmissionEntry['submission']
  history: GroupSubmissionEntry['submission'][]
  feedback: ProjectEventFeedbackDTO[]
}

// ---------- Status badge helper ----------

function statusBadge(status: string | undefined) {
  if (!status) {
    return (
      <Badge variant="secondary" className="bg-gray-100 text-gray-600">
        <Clock className="mr-1 h-3 w-3" />
        Not Submitted
      </Badge>
    )
  }
  switch (status) {
    case 'SUBMITTED':
      return (
        <Badge className="bg-blue-100 text-blue-700 hover:bg-blue-100">
          <Send className="mr-1 h-3 w-3" />
          Submitted
        </Badge>
      )
    case 'REVIEWED':
    case 'APPROVED':
      return (
        <Badge className="bg-green-100 text-green-700 hover:bg-green-100">
          <CheckCircle2 className="mr-1 h-3 w-3" />
          {status === 'APPROVED' ? 'Approved' : 'Reviewed'}
        </Badge>
      )
    case 'NEEDS_REVISION':
      return (
        <Badge className="bg-amber-100 text-amber-700 hover:bg-amber-100">
          <AlertTriangle className="mr-1 h-3 w-3" />
          Needs Revision
        </Badge>
      )
    default:
      return (
        <Badge variant="secondary">
          {status}
        </Badge>
      )
  }
}

function formatDate(iso: string | undefined) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

// ---------- Main component ----------

export default function SubmissionsPage() {
  const params = useParams()
  const router = useRouter()
  const { toast } = useToast()
  const projectId = params.id as string
  const eventId = params.eventId as string

  const [project, setProject] = useState<ProjectDTO | null>(null)
  const [events, setEvents] = useState<ProjectEventDTO[]>([])
  const [currentEvent, setCurrentEvent] = useState<ProjectEventDTO | null>(null)
  const [entries, setEntries] = useState<GroupSubmissionEntry[]>([])
  const [loading, setLoading] = useState(true)

  // Review panel state
  const [expandedGroupId, setExpandedGroupId] = useState<string | null>(null)
  const [groupDetail, setGroupDetail] = useState<GroupDetailResponse | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [feedbackComment, setFeedbackComment] = useState('')
  const [feedbackSubmitting, setFeedbackSubmitting] = useState(false)
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false)
  const [historyGroupName, setHistoryGroupName] = useState('')
  // Approve dialog state
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [approveGroupId, setApproveGroupId] = useState('')
  const [approveGroupName, setApproveGroupName] = useState('')
  const [approveMembers, setApproveMembers] = useState<Array<{ studentId: string; name?: string; email?: string }>>([])
  const [approveAttendance, setApproveAttendance] = useState<Record<string, boolean>>({})
  const [approveGrades, setApproveGrades] = useState<Record<string, number>>({})
  const [approveSubmitting, setApproveSubmitting] = useState(false)
  const [advancingPhase, setAdvancingPhase] = useState(false)

  // ---------- Data loading ----------

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const [proj, evts, subs] = await Promise.all([
        projectsApi.getProject(projectId),
        projectsApi.getEvents(projectId),
        projectsApi.getEventSubmissions(projectId, eventId),
      ])
      setProject(proj)
      setEvents(evts)
      setCurrentEvent(evts.find((e) => e.id === eventId) || null)
      setEntries(subs as unknown as GroupSubmissionEntry[])
    } catch (err) {
      toast({
        title: 'Error',
        description: extractErrorMessage(err),
        variant: 'destructive',
      })
    } finally {
      setLoading(false)
    }
  }, [projectId, eventId, toast])

  useEffect(() => {
    loadData()
  }, [loadData])

  // ---------- Expand / collapse review panel ----------

  const toggleReviewPanel = async (groupId: string) => {
    if (expandedGroupId === groupId) {
      setExpandedGroupId(null)
      setGroupDetail(null)
      setFeedbackComment('')
      return
    }

    setExpandedGroupId(groupId)
    setGroupDetail(null)
    setFeedbackComment('')
    setDetailLoading(true)

    try {
      const detail = await projectsApi.getGroupEventSubmission(projectId, eventId, groupId)
      setGroupDetail(detail as GroupDetailResponse)
    } catch (err) {
      toast({
        title: 'Error loading submission details',
        description: extractErrorMessage(err),
        variant: 'destructive',
      })
    } finally {
      setDetailLoading(false)
    }
  }

  // ---------- Give feedback ----------

  const handleFeedback = async (groupId: string, status: 'REVIEWED' | 'NEEDS_REVISION') => {
    if (!feedbackComment.trim()) {
      toast({
        title: 'Comment required',
        description: 'Please enter a feedback comment before submitting.',
        variant: 'destructive',
      })
      return
    }

    setFeedbackSubmitting(true)
    try {
      await projectsApi.giveEventFeedback(projectId, eventId, groupId, {
        comment: feedbackComment.trim(),
        status,
      })
      toast({
        title: 'Feedback submitted',
        description: `Submission marked as ${status === 'REVIEWED' ? 'Approved' : 'Needs Revision'}.`,
      })
      setFeedbackComment('')
      setExpandedGroupId(null)
      setGroupDetail(null)
      await loadData()
    } catch (err) {
      toast({
        title: 'Error submitting feedback',
        description: extractErrorMessage(err),
        variant: 'destructive',
      })
    } finally {
      setFeedbackSubmitting(false)
    }
  }

  // ---------- Approve with attendance & grades ----------

  const openApproveDialog = async (groupId: string, groupName: string, members: any[]) => {
    setApproveGroupId(groupId)
    setApproveGroupName(groupName)
    setApproveMembers(members || [])

    // Pre-populate attendance and grades from existing data
    const attendance: Record<string, boolean> = {}
    const grades: Record<string, number> = {}
    members?.forEach((m: any) => {
      attendance[m.studentId] = true // default to present
      grades[m.studentId] = 0
    })

    // Load existing attendance & grades
    try {
      const [existingAtt, existingGrades] = await Promise.all([
        projectsApi.getAttendance(projectId, eventId),
        projectsApi.getGrades(projectId, eventId),
      ])
      existingAtt.forEach((a: any) => {
        if (attendance.hasOwnProperty(a.studentId)) {
          attendance[a.studentId] = a.present
        }
      })
      existingGrades.forEach((g: any) => {
        if (grades.hasOwnProperty(g.studentId)) {
          grades[g.studentId] = g.marks
        }
      })
    } catch { /* ok - no existing data */ }

    setApproveAttendance(attendance)
    setApproveGrades(grades)
    setApproveDialogOpen(true)
  }

  const handleApproveWithGrades = async () => {
    if (!feedbackComment.trim()) {
      toast({ title: 'Comment required', variant: 'destructive' })
      return
    }
    setApproveSubmitting(true)
    try {
      // 1. Give feedback (approve)
      await projectsApi.giveEventFeedback(projectId, eventId, approveGroupId, {
        comment: feedbackComment.trim(),
        status: 'REVIEWED',
      })

      // 2. Save attendance
      const attendanceEntries = Object.entries(approveAttendance).map(([studentId, present]) => ({
        studentId,
        present,
      }))
      await projectsApi.saveAttendance(projectId, eventId, attendanceEntries)

      // 3. Save grades (if event has marks)
      if (currentEvent?.hasMarks) {
        const gradeEntries = Object.entries(approveGrades).map(([studentId, marks]) => ({
          studentId,
          marks,
        }))
        await projectsApi.saveGrades(projectId, eventId, gradeEntries)
      }

      toast({ title: 'Approved with attendance & grades saved' })
      setFeedbackComment('')
      setApproveDialogOpen(false)
      setExpandedGroupId(null)
      setGroupDetail(null)
      await loadData()
    } catch (err) {
      toast({ title: 'Error', description: extractErrorMessage(err), variant: 'destructive' })
    } finally {
      setApproveSubmitting(false)
    }
  }

  // ---------- Advance phase ----------

  const handleAdvancePhase = async () => {
    if (!currentEvent || events.length === 0) return

    const sortedEvents = [...events].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0))
    const currentIndex = sortedEvents.findIndex((e) => e.id === eventId)
    const nextEvent = currentIndex >= 0 && currentIndex < sortedEvents.length - 1
      ? sortedEvents[currentIndex + 1]
      : null

    const confirmMsg = nextEvent
      ? `Advance to "${nextEvent.name}"? This will update the project's current phase.`
      : 'This is the last event. Advancing will complete the project phase progression.'

    if (!window.confirm(confirmMsg)) return

    setAdvancingPhase(true)
    try {
      await projectsApi.advancePhase(projectId, nextEvent?.id ?? null)
      toast({
        title: 'Phase advanced',
        description: nextEvent
          ? `Active phase is now "${nextEvent.name}".`
          : 'All phases completed.',
      })
      await loadData()
    } catch (err) {
      toast({
        title: 'Error advancing phase',
        description: extractErrorMessage(err),
        variant: 'destructive',
      })
    } finally {
      setAdvancingPhase(false)
    }
  }

  // ---------- Derived state ----------

  const isActiveEvent = project?.currentEventId === eventId
  const sortedEvents = [...events].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0))
  const currentIndex = sortedEvents.findIndex((e) => e.id === eventId)
  const hasNextEvent = currentIndex >= 0 && currentIndex < sortedEvents.length - 1
  const submittedCount = entries.filter((e) => e.submission != null).length
  const reviewedCount = entries.filter(
    (e) => e.submission?.status === 'REVIEWED' || e.submission?.status === 'APPROVED'
  ).length

  // ---------- Render ----------

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="space-y-6 p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.push(`/projects/${projectId}`)}
            className="mb-2 -ml-2"
          >
            <ArrowLeft className="mr-1 h-4 w-4" />
            Back to Project
          </Button>
          <h1 className="text-2xl font-semibold">
            {currentEvent?.name ?? 'Event'} &mdash; Submissions
          </h1>
          {project && (
            <p className="text-muted-foreground text-sm">
              {project.title}
            </p>
          )}
        </div>

        <div className="flex items-center gap-3 flex-shrink-0 pt-10">
          {isActiveEvent && (
            <Badge className="bg-indigo-100 text-indigo-700 hover:bg-indigo-100">
              Active Phase
            </Badge>
          )}
          {isActiveEvent && (
            <Button
              size="sm"
              onClick={handleAdvancePhase}
              disabled={advancingPhase}
            >
              {advancingPhase ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <ChevronRight className="mr-1 h-4 w-4" />
              )}
              {hasNextEvent ? 'Advance to Next Phase' : 'Complete Phases'}
            </Button>
          )}
        </div>
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4 pb-4">
            <p className="text-sm text-muted-foreground">Total Groups</p>
            <p className="text-2xl font-semibold">{entries.length}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-4">
            <p className="text-sm text-muted-foreground">Submitted</p>
            <p className="text-2xl font-semibold">
              {submittedCount}{' '}
              <span className="text-sm font-normal text-muted-foreground">
                / {entries.length}
              </span>
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-4">
            <p className="text-sm text-muted-foreground">Reviewed</p>
            <p className="text-2xl font-semibold">
              {reviewedCount}{' '}
              <span className="text-sm font-normal text-muted-foreground">
                / {submittedCount}
              </span>
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Group list */}
      {entries.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            No groups found for this project.
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {entries.map((entry) => {
            const sub = entry.submission
            const isExpanded = expandedGroupId === entry.groupId

            return (
              <div key={entry.groupId}>
                <Card
                  className={`transition-colors ${
                    isExpanded ? 'ring-2 ring-primary/30' : ''
                  }`}
                >
                  <CardContent className="py-4">
                    {/* Main row */}
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex-1 min-w-0 space-y-1">
                        <div className="flex items-center gap-3">
                          <span className="font-medium text-sm">
                            {entry.groupName || `Group ${entry.groupNumber}`}
                          </span>
                          {statusBadge(sub?.status)}
                        </div>

                        {sub && (
                          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                            <span>
                              Submitted by {entry.members?.find((m: any) => m.studentId === sub.submittedBy)?.name || entry.members?.find((m: any) => m.studentId === sub.submittedBy)?.email || sub.submittedBy} on{' '}
                              {formatDate(sub.submittedAt)}
                            </span>
                            <span>Version {sub.version}</span>
                            {sub.submissionUrl && (
                              <a
                                href={sub.submissionUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 text-blue-600 hover:underline"
                              >
                                <ExternalLink className="h-3 w-3" />
                                Submission Link
                              </a>
                            )}
                            {sub.attachments && sub.attachments.length > 0 && (
                              <span className="inline-flex items-center gap-1">
                                <FileDown className="h-3 w-3" />
                                {sub.attachments.length} file
                                {sub.attachments.length > 1 ? 's' : ''}
                              </span>
                            )}
                          </div>
                        )}

                        {!sub && (
                          <p className="text-xs text-muted-foreground">
                            No submission yet
                          </p>
                        )}
                      </div>

                      {sub && (
                        <Button
                          variant={isExpanded ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => toggleReviewPanel(entry.groupId)}
                        >
                          {isExpanded ? 'Close' : 'Review'}
                        </Button>
                      )}
                    </div>

                    {/* Expanded review panel */}
                    {isExpanded && (
                      <div className="mt-4 pt-4 border-t space-y-4">
                        {detailLoading ? (
                          <div className="flex items-center justify-center py-8">
                            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                          </div>
                        ) : (
                          <>
                            {/* Submission details */}
                            <div className="space-y-2">
                              <h4 className="text-sm font-medium">
                                Submission Details
                              </h4>
                              <div className="rounded-md border p-3 space-y-2 text-sm">
                                {groupDetail?.latest?.submissionUrl && (
                                  <div>
                                    <span className="text-muted-foreground">
                                      URL:{' '}
                                    </span>
                                    <a
                                      href={groupDetail.latest.submissionUrl}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-blue-600 hover:underline break-all"
                                    >
                                      {groupDetail.latest.submissionUrl}
                                    </a>
                                  </div>
                                )}
                                {groupDetail?.latest?.submissionText && (
                                  <div>
                                    <span className="text-muted-foreground">
                                      Text:{' '}
                                    </span>
                                    <p className="mt-1 whitespace-pre-wrap text-sm">
                                      {groupDetail.latest.submissionText}
                                    </p>
                                  </div>
                                )}
                                {groupDetail?.latest?.attachments &&
                                  groupDetail.latest.attachments.length > 0 && (
                                    <div>
                                      <span className="text-muted-foreground">
                                        Files:
                                      </span>
                                      <ul className="mt-1 space-y-1">
                                        {groupDetail.latest.attachments.map(
                                          (att) => (
                                            <li key={att.id}>
                                              <a
                                                href={att.fileUrl}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="inline-flex items-center gap-1 text-blue-600 hover:underline text-sm"
                                              >
                                                <FileDown className="h-3 w-3" />
                                                {att.fileName}
                                                {att.fileSizeBytes != null && (
                                                  <span className="text-muted-foreground ml-1">
                                                    (
                                                    {(
                                                      att.fileSizeBytes / 1024
                                                    ).toFixed(0)}
                                                    KB)
                                                  </span>
                                                )}
                                              </a>
                                            </li>
                                          )
                                        )}
                                      </ul>
                                    </div>
                                  )}
                                {groupDetail?.history &&
                                  groupDetail.history.length > 1 && (
                                    <button
                                      type="button"
                                      onClick={() => {
                                        setHistoryGroupName(entry.groupName || `Group ${entry.groupNumber}`)
                                        setHistoryDialogOpen(true)
                                      }}
                                      className="text-xs text-blue-600 hover:underline inline-flex items-center gap-1"
                                    >
                                      <History className="h-3 w-3" />
                                      View {groupDetail.history.length - 1} previous version{groupDetail.history.length > 2 ? 's' : ''}
                                    </button>
                                  )}
                              </div>
                            </div>

                            {/* Previous feedback */}
                            {groupDetail?.feedback &&
                              groupDetail.feedback.length > 0 && (
                                <div className="space-y-2">
                                  <h4 className="text-sm font-medium">
                                    Previous Feedback
                                  </h4>
                                  <div className="space-y-2 max-h-48 overflow-y-auto">
                                    {groupDetail.feedback.map((fb) => (
                                      <div
                                        key={fb.id}
                                        className="rounded-md border p-3 text-sm space-y-1"
                                      >
                                        <div className="flex items-center justify-between gap-2">
                                          <span className="font-medium text-xs">
                                            {fb.feedbackBy}
                                          </span>
                                          <div className="flex items-center gap-2">
                                            {statusBadge(fb.status)}
                                            <span className="text-xs text-muted-foreground">
                                              {formatDate(fb.feedbackAt)}
                                            </span>
                                          </div>
                                        </div>
                                        <p className="whitespace-pre-wrap text-sm text-muted-foreground">
                                          {fb.comment}
                                        </p>
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )}

                            {/* New feedback form */}
                            <div className="space-y-3">
                              <h4 className="text-sm font-medium">
                                New Feedback
                              </h4>
                              <Textarea
                                placeholder="Enter your feedback comment..."
                                value={feedbackComment}
                                onChange={(e) =>
                                  setFeedbackComment(e.target.value)
                                }
                                rows={3}
                                disabled={feedbackSubmitting}
                              />
                              <div className="flex items-center gap-2">
                                <Button
                                  size="sm"
                                  onClick={() =>
                                    openApproveDialog(
                                      entry.groupId,
                                      entry.groupName || `Group ${entry.groupNumber}`,
                                      entry.members || []
                                    )
                                  }
                                  disabled={
                                    feedbackSubmitting ||
                                    !feedbackComment.trim()
                                  }
                                >
                                  <CheckCircle2 className="mr-1 h-4 w-4" />
                                  Approve
                                </Button>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  className="border-amber-300 text-amber-700 hover:bg-amber-50"
                                  onClick={() =>
                                    handleFeedback(
                                      entry.groupId,
                                      'NEEDS_REVISION'
                                    )
                                  }
                                  disabled={
                                    feedbackSubmitting ||
                                    !feedbackComment.trim()
                                  }
                                >
                                  {feedbackSubmitting ? (
                                    <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                                  ) : (
                                    <XCircle className="mr-1 h-4 w-4" />
                                  )}
                                  Needs Revision
                                </Button>
                              </div>
                            </div>
                          </>
                        )}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </div>
            )
          })}
        </div>
      )}
      {/* Approve with Attendance & Grades Dialog */}
      <Dialog open={approveDialogOpen} onOpenChange={setApproveDialogOpen}>
        <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Approve — {approveGroupName}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Attendance */}
            <div>
              <Label className="text-sm font-medium">Attendance</Label>
              <div className="mt-2 space-y-2">
                {approveMembers.map((m) => (
                  <label key={m.studentId} className="flex items-center gap-2 text-sm cursor-pointer">
                    <Checkbox
                      checked={approveAttendance[m.studentId] ?? false}
                      onCheckedChange={(checked) =>
                        setApproveAttendance((prev) => ({ ...prev, [m.studentId]: !!checked }))
                      }
                    />
                    <span>{m.name || m.email || m.studentId}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* Grades (if event has marks) */}
            {currentEvent?.hasMarks && (
              <div>
                <Label className="text-sm font-medium">
                  Grades {currentEvent.maxMarks ? `(max ${currentEvent.maxMarks})` : ''}
                </Label>
                <div className="mt-2 space-y-2">
                  {approveMembers.map((m) => (
                    <div key={m.studentId} className="flex items-center gap-3">
                      <span className="text-sm flex-1 truncate">{m.name || m.email || m.studentId}</span>
                      <Input
                        type="number"
                        min={0}
                        max={currentEvent.maxMarks || undefined}
                        value={approveGrades[m.studentId] ?? 0}
                        onChange={(e) =>
                          setApproveGrades((prev) => ({ ...prev, [m.studentId]: parseInt(e.target.value) || 0 }))
                        }
                        className="w-20 h-8 text-sm"
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Feedback comment (inherited from the textarea) */}
            <div className="text-sm text-muted-foreground bg-muted/50 rounded p-2">
              <span className="font-medium">Feedback:</span> {feedbackComment || '(none)'}
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveDialogOpen(false)} disabled={approveSubmitting}>
              Cancel
            </Button>
            <Button onClick={handleApproveWithGrades} disabled={approveSubmitting}>
              {approveSubmitting ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle2 className="mr-1 h-4 w-4" />
              )}
              Approve & Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Version History Dialog */}
      <Dialog open={historyDialogOpen} onOpenChange={setHistoryDialogOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Submission History — {historyGroupName}</DialogTitle>
          </DialogHeader>
          {groupDetail?.history && groupDetail.history.length > 0 ? (
            <div className="space-y-3">
              {groupDetail.history.map((h: any, idx: number) => (
                <div key={h.id || idx} className={`rounded-md border p-3 space-y-2 ${idx === 0 ? 'border-blue-200 bg-blue-50/30' : ''}`}>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Badge variant={idx === 0 ? 'default' : 'secondary'} className="text-xs">
                        v{h.version}
                      </Badge>
                      {idx === 0 && <span className="text-xs text-blue-600 font-medium">Latest</span>}
                      {statusBadge(h.status)}
                    </div>
                    <span className="text-xs text-muted-foreground">{formatDate(h.submittedAt)}</span>
                  </div>
                  {h.submissionUrl && (
                    <p className="text-sm">
                      <span className="text-muted-foreground">URL:</span>{' '}
                      <a href={h.submissionUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline inline-flex items-center gap-1">
                        {h.submissionUrl} <ExternalLink className="h-3 w-3" />
                      </a>
                    </p>
                  )}
                  {h.submissionText && (
                    <p className="text-sm"><span className="text-muted-foreground">Notes:</span> {h.submissionText}</p>
                  )}
                  {h.attachments && h.attachments.length > 0 && (
                    <div className="space-y-1">
                      <p className="text-xs text-muted-foreground">Files:</p>
                      {h.attachments.map((att: any) => (
                        <a key={att.id} href={att.fileUrl} target="_blank" rel="noopener noreferrer"
                          className="flex items-center gap-1.5 text-xs text-blue-600 hover:underline">
                          <FileDown className="h-3 w-3" /> {att.fileName}
                        </a>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground py-4 text-center">No submission history</p>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
