'use client'

import { useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { projectsApi, mediaApi } from '@/lib/api'
import { useProjectsFeature } from '@/hooks/useProjectsFeature'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Loader2,
  ArrowLeft,
  Users,
  Calendar,
  ExternalLink,
  Video,
  CheckCircle2,
  XCircle,
  MinusCircle,
  Send,
  Clock,
  AlertTriangle,
  FileDown,
  FileUp,
  Paperclip,
  X,
} from 'lucide-react'
import type {
  ProjectDTO,
  ProjectGroupDTO,
  ProjectEventDTO,
  ProjectQuestionDTO,
  AttachmentInfo,
  ProjectEventSubmissionDTO,
  ProjectEventFeedbackDTO,
} from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

interface AttendanceRecord {
  eventId: string
  eventName: string
  present?: boolean
  marks?: number
  maxMarks?: number
  hasMarks?: boolean
}

export default function ProjectDetailPage() {
  const params = useParams()
  const router = useRouter()
  const projectId = params.id as string
  const { enabled, loading: featureLoading } = useProjectsFeature()

  const [project, setProject] = useState<ProjectDTO | null>(null)
  const [group, setGroup] = useState<ProjectGroupDTO | null>(null)
  const [attendance, setAttendance] = useState<AttendanceRecord[]>([])
  const [events, setEvents] = useState<ProjectEventDTO[]>([])
  const [question, setQuestion] = useState<ProjectQuestionDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [submissionUrl, setSubmissionUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [submissionHistory, setSubmissionHistory] = useState<any[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [uploadingFiles, setUploadingFiles] = useState(false)
  const [pendingAttachments, setPendingAttachments] = useState<AttachmentInfo[]>([])
  const [eventSubmissions, setEventSubmissions] = useState<Record<string, ProjectEventSubmissionDTO | null>>({})
  const [eventSubmissionHistories, setEventSubmissionHistories] = useState<Record<string, ProjectEventSubmissionDTO[]>>({})
  const [eventFeedback, setEventFeedback] = useState<Record<string, ProjectEventFeedbackDTO[]>>({})
  const [expandedEvent, setExpandedEvent] = useState<string | null>(null)
  const [eventSubmissionUrl, setEventSubmissionUrl] = useState('')
  const [eventSubmissionText, setEventSubmissionText] = useState('')
  const [submittingEvent, setSubmittingEvent] = useState(false)

  useEffect(() => {
    if (!enabled || !projectId) return
    loadData()
  }, [enabled, projectId])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)

      const [projectData, groupData, attendanceData, eventsData] = await Promise.all([
        projectsApi.getProject(projectId),
        projectsApi.getMyGroup(projectId).catch(() => null),
        projectsApi.getMyAttendance(projectId).catch(() => []),
        projectsApi.getEvents(projectId).catch(() => []),
      ])

      setProject(projectData)
      setGroup(groupData)
      setEvents(Array.isArray(eventsData) ? eventsData : [])

      // Load submissions for events with hasSubmission
      const evtsData = Array.isArray(eventsData) ? eventsData : []
      for (const evt of evtsData) {
        if (evt.hasSubmission) {
          try {
            const [sub, history, fb] = await Promise.all([
              projectsApi.getMyEventSubmission(projectId, evt.id),
              projectsApi.getMyEventSubmissionHistory(projectId, evt.id),
              projectsApi.getMyEventFeedback(projectId, evt.id),
            ])
            setEventSubmissions(prev => ({ ...prev, [evt.id]: sub }))
            setEventSubmissionHistories(prev => ({ ...prev, [evt.id]: history }))
            setEventFeedback(prev => ({ ...prev, [evt.id]: fb }))
          } catch { /* ok */ }
        }
      }

      // attendance may be an array of records
      if (Array.isArray(attendanceData)) {
        setAttendance(attendanceData)
      } else if (attendanceData?.attendance) {
        setAttendance(attendanceData.attendance)
      } else if (attendanceData?.events) {
        setAttendance(attendanceData.records || [])
      }

      // If there's a problem statement, try to get question details
      if (groupData?.problemStatementId) {
        try {
          // The question endpoint may not be accessible to students, so catch errors
          const { ProjectQuestionsApi } = await import('@kunal-ak23/edudron-shared-utils')
          const questionsApi = new ProjectQuestionsApi((await import('@/lib/api')).getApiClient())
          const q = await questionsApi.getQuestion(groupData.problemStatementId)
          setQuestion(q)
        } catch {
          // Not accessible - that's fine
        }
      }
      // Load submission history
      if (groupData?.submittedAt) {
        try {
          const history = await projectsApi.getSubmissionHistory(projectId)
          setSubmissionHistory(history)
        } catch { /* Not critical */ }
      }
    } catch (err) {
      console.error('Failed to load project details', err)
      setError('Failed to load project details')
    } finally {
      setLoading(false)
    }
  }

  async function handleFileUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files
    if (!files || files.length === 0) return
    setUploadingFiles(true)
    try {
      const newAttachments: AttachmentInfo[] = []
      for (const file of Array.from(files)) {
        const url = await mediaApi.uploadImage(file, 'projects/submissions')
        newAttachments.push({
          fileUrl: url,
          fileName: file.name,
          fileSizeBytes: file.size,
          mimeType: file.type,
        })
      }
      setPendingAttachments((prev) => [...prev, ...newAttachments])
    } catch (err) {
      console.error('Upload failed', err)
    } finally {
      setUploadingFiles(false)
      e.target.value = '' // reset input
    }
  }

  async function handleSubmit() {
    if (!submissionUrl.trim() || !group) return
    try {
      setSubmitting(true)
      setSubmitError(null)
      const updated = await projectsApi.submitMyProject(projectId, {
        submissionUrl: submissionUrl.trim(),
        attachments: pendingAttachments.length > 0 ? pendingAttachments : undefined,
      })
      setGroup(updated)
      setSubmissionUrl('')
      setPendingAttachments([])
      setEditing(false)
      // Refresh submission history
      try {
        const history = await projectsApi.getSubmissionHistory(projectId)
        setSubmissionHistory(history)
      } catch { /* Not critical */ }
    } catch (err: any) {
      console.error('Failed to submit project', err)
      setSubmitError(err?.response?.data?.error || 'Failed to submit project')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleEventSubmit(eventId: string) {
    if (!eventSubmissionUrl.trim() && !eventSubmissionText.trim()) return
    setSubmittingEvent(true)
    try {
      const result = await projectsApi.submitToEvent(projectId, eventId, {
        submissionUrl: eventSubmissionUrl.trim() || undefined,
        submissionText: eventSubmissionText.trim() || undefined,
        attachments: pendingAttachments.length > 0 ? pendingAttachments : undefined,
      })
      setEventSubmissions(prev => ({ ...prev, [eventId]: result }))
      setEventSubmissionUrl('')
      setEventSubmissionText('')
      setPendingAttachments([])
      setExpandedEvent(null)
      // Refresh feedback
      const fb = await projectsApi.getMyEventFeedback(projectId, eventId)
      setEventFeedback(prev => ({ ...prev, [eventId]: fb }))
    } catch (err: any) {
      console.error('Submit failed', err)
    } finally {
      setSubmittingEvent(false)
    }
  }

  function formatDateTime(dateStr?: string): string {
    if (!dateStr) return '--'
    try {
      return new Date(dateStr).toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      })
    } catch {
      return dateStr
    }
  }

  function isCutoffPassed(): boolean {
    if (!project?.submissionCutoff) return false
    return new Date(project.submissionCutoff) < new Date()
  }

  function getCutoffCountdown(): string | null {
    if (!project?.submissionCutoff) return null
    const cutoff = new Date(project.submissionCutoff)
    const now = new Date()
    if (cutoff <= now) return null
    const diff = cutoff.getTime() - now.getTime()
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
    if (days > 7) return null // Only show countdown within a week
    if (days > 0) return `${days}d ${hours}h remaining`
    if (hours > 0) return `${hours}h remaining`
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    return `${minutes}m remaining`
  }

  const canSubmit = !group?.submittedAt && (!isCutoffPassed() || project?.lateSubmissionAllowed)

  if (featureLoading || loading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="flex justify-center items-center p-12">
            <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!enabled) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="p-12 text-center text-gray-500">
            Projects are not available for your institution.
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (error || !project) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="container mx-auto px-4 py-8">
            <Button
              variant="ghost"
              onClick={() => router.push('/projects')}
              className="mb-4"
            >
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Projects
            </Button>
            <div className="text-center p-12 text-gray-500">
              {error || 'Project not found'}
            </div>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  const countdown = getCutoffCountdown()

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8 max-w-4xl">
          {/* Back button + title */}
          <Button
            variant="ghost"
            onClick={() => router.push('/projects')}
            className="mb-4"
          >
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Projects
          </Button>

          <div className="flex items-start justify-between gap-4 mb-6">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">{project.title}</h1>
              {project.description && (
                <p className="text-gray-500 mt-1">{project.description}</p>
              )}
            </div>
            <Badge
              variant="outline"
              className={`shrink-0 ${
                project.status === 'COMPLETED'
                  ? 'border-green-500 text-green-700'
                  : project.status === 'ACTIVE'
                  ? 'border-blue-500 text-blue-700'
                  : 'border-gray-400 text-gray-600'
              }`}
            >
              {project.status}
            </Badge>
          </div>

          <div className="space-y-6">
            {/* Problem Statement */}
            {question && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Problem Statement</CardTitle>
                </CardHeader>
                <CardContent>
                  <h3 className="font-semibold text-gray-900 mb-2">{question.title}</h3>
                  <p className="text-gray-600 whitespace-pre-wrap mb-4">
                    {question.problemStatement}
                  </p>
                  {question.keyTechnologies && question.keyTechnologies.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {question.keyTechnologies.map((tech) => (
                        <Badge key={tech} variant="secondary">
                          {tech}
                        </Badge>
                      ))}
                    </div>
                  )}
                  {question.difficulty && (
                    <Badge variant="outline" className="mt-3">
                      {question.difficulty}
                    </Badge>
                  )}
                </CardContent>
              </Card>
            )}

            {/* Group Members */}
            {group && (
              <Card>
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <Users className="h-5 w-5 text-primary-600" />
                    <CardTitle className="text-lg">
                      {group.groupName || `Group ${group.groupNumber}`}
                    </CardTitle>
                  </div>
                </CardHeader>
                <CardContent>
                  {group.members && group.members.length > 0 ? (
                    <div className="border rounded-lg overflow-hidden">
                      <table className="w-full text-sm">
                        <thead className="bg-gray-50">
                          <tr>
                            <th className="text-left px-4 py-2 font-medium text-gray-700">
                              Name
                            </th>
                            <th className="text-left px-4 py-2 font-medium text-gray-700">
                              Email
                            </th>
                          </tr>
                        </thead>
                        <tbody className="divide-y">
                          {group.members.map((member, idx) => (
                            <tr key={member.studentId || idx}>
                              <td className="px-4 py-2 text-gray-900">
                                {member.name || '--'}
                              </td>
                              <td className="px-4 py-2 text-gray-500">
                                {member.email || '--'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className="text-gray-500">No group members assigned yet.</p>
                  )}
                </CardContent>
              </Card>
            )}

            {/* Statement Attachments */}
            {project.statementAttachments && project.statementAttachments.length > 0 && (
              <Card>
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <Paperclip className="h-5 w-5 text-primary-600" />
                    <CardTitle className="text-lg">Project Files</CardTitle>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    {project.statementAttachments.map((att: any) => (
                      <a
                        key={att.id}
                        href={att.fileUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-2 p-2 rounded-lg border hover:bg-gray-50 transition-colors"
                      >
                        <FileDown className="h-4 w-4 text-blue-600 shrink-0" />
                        <span className="text-sm text-blue-600 hover:underline truncate">
                          {att.fileName || 'Download'}
                        </span>
                        {att.fileSizeBytes && (
                          <span className="text-xs text-gray-400 shrink-0">
                            ({(att.fileSizeBytes / 1024).toFixed(0)} KB)
                          </span>
                        )}
                      </a>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Events Timeline */}
            {events.length > 0 && (
              <Card>
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <Calendar className="h-5 w-5 text-primary-600" />
                    <CardTitle className="text-lg">Events ({events.length})</CardTitle>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    {events.map((event, idx) => {
                      const record = attendance.find((a) => a.eventId === event.id)
                      return (
                        <div
                          key={event.id || idx}
                          className="p-3 bg-gray-50 rounded-lg"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex-1 min-w-0">
                              <p className="font-medium text-gray-900">
                                {event.name || `Event ${idx + 1}`}
                              </p>
                              {event.dateTime && (
                                <p className="text-sm text-gray-500">
                                  {formatDateTime(event.dateTime)}
                                </p>
                              )}
                              {event.zoomLink && (
                                <a
                                  href={event.zoomLink}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="inline-flex items-center gap-1 text-sm text-blue-600 hover:underline mt-1"
                                >
                                  <Video className="h-3.5 w-3.5" />
                                  Join Meeting
                                  <ExternalLink className="h-3 w-3" />
                                </a>
                              )}
                            </div>
                            <div className="flex items-center gap-3 shrink-0">
                              {record ? (
                                <>
                                  {record.present === true ? (
                                    <Badge className="bg-green-100 text-green-700 border-green-300">
                                      <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                                      Present
                                    </Badge>
                                  ) : record.present === false ? (
                                    <Badge className="bg-red-100 text-red-700 border-red-300">
                                      <XCircle className="h-3.5 w-3.5 mr-1" />
                                      Absent
                                    </Badge>
                                  ) : null}
                                  {record.hasMarks && record.marks != null ? (
                                    <span className="text-sm font-medium text-gray-700">
                                      {record.marks} / {record.maxMarks ?? '?'}
                                    </span>
                                  ) : record.hasMarks ? (
                                    <span className="text-sm text-gray-400">&mdash;</span>
                                  ) : null}
                                </>
                              ) : event.hasMarks ? (
                                <span className="text-xs text-gray-400">
                                  {event.maxMarks} marks
                                </span>
                              ) : null}
                            </div>
                          </div>
                          {event.hasSubmission && (() => {
                          const submission = eventSubmissions[event.id]
                          const feedback = eventFeedback[event.id] || []
                          const latestFeedback = feedback[0]
                          const isExpanded = expandedEvent === event.id

                          return (
                            <div className="mt-2 border-t pt-2 space-y-2">
                              {/* Status */}
                              <div className="flex items-center gap-2">
                                {submission ? (
                                  <Badge className={
                                    submission.status === 'APPROVED' || submission.status === 'REVIEWED'
                                      ? 'bg-green-100 text-green-700 border-green-300'
                                      : submission.status === 'NEEDS_REVISION'
                                      ? 'bg-amber-100 text-amber-700 border-amber-300'
                                      : 'bg-blue-100 text-blue-700 border-blue-300'
                                  }>
                                    {submission.status} (v{submission.version})
                                  </Badge>
                                ) : (
                                  <Badge variant="outline" className="text-gray-400">Not submitted</Badge>
                                )}
                                <button
                                  type="button"
                                  onClick={() => setExpandedEvent(isExpanded ? null : event.id)}
                                  className="text-xs text-blue-600 hover:underline"
                                >
                                  {isExpanded ? 'Hide' : submission ? 'View / Resubmit' : 'Submit'}
                                </button>
                              </div>

                              {/* Latest feedback */}
                              {latestFeedback && (
                                <div className={`text-sm p-2 rounded ${
                                  latestFeedback.status === 'NEEDS_REVISION' ? 'bg-amber-50 text-amber-800' : 'bg-green-50 text-green-800'
                                }`}>
                                  <p className="font-medium text-xs">{latestFeedback.status === 'NEEDS_REVISION' ? 'Revision needed' : 'Reviewed'}</p>
                                  <p>{latestFeedback.comment}</p>
                                  <p className="text-xs mt-1 opacity-60">— {latestFeedback.feedbackBy}</p>
                                </div>
                              )}

                              {/* Expanded: submission details + form */}
                              {isExpanded && (
                                <div className="space-y-3 pt-1">
                                  {/* Current submission details */}
                                  {submission && (
                                    <div className="text-sm space-y-1 p-2 bg-white rounded border">
                                      <p className="text-xs font-medium text-gray-500">Current submission (v{submission.version})</p>
                                      {submission.submissionUrl && (
                                        <p>
                                          <span className="text-gray-500">URL:</span>{' '}
                                          <a href={submission.submissionUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline inline-flex items-center gap-1">
                                            {submission.submissionUrl}
                                            <ExternalLink className="h-3 w-3" />
                                          </a>
                                        </p>
                                      )}
                                      {submission.submissionText && (
                                        <p><span className="text-gray-500">Notes:</span> {submission.submissionText}</p>
                                      )}
                                      {/* Submission attachments */}
                                      {submission.attachments && submission.attachments.length > 0 && (
                                        <div className="space-y-1 mt-1">
                                          <p className="text-xs text-gray-500">Attached files:</p>
                                          {submission.attachments.map((att: any) => (
                                            <a
                                              key={att.id}
                                              href={att.fileUrl}
                                              target="_blank"
                                              rel="noopener noreferrer"
                                              className="flex items-center gap-1.5 text-xs text-blue-600 hover:underline"
                                            >
                                              <FileDown className="h-3 w-3" />
                                              {att.fileName}
                                            </a>
                                          ))}
                                        </div>
                                      )}
                                      <p className="text-xs text-gray-400 mt-1">
                                        Submitted {new Date(submission.submittedAt).toLocaleString()}
                                      </p>
                                    </div>
                                  )}

                                  {/* Previous submissions */}
                                  {(() => {
                                    const history = eventSubmissionHistories[event.id] || []
                                    return history.length > 1 ? (
                                      <div className="space-y-1">
                                        <p className="text-xs font-medium text-gray-500">Previous versions</p>
                                        {history.slice(1).map((h: any) => (
                                          <div key={h.id} className="text-xs text-gray-500 p-1.5 bg-gray-50 rounded flex items-center justify-between">
                                            <span>
                                              v{h.version}
                                              {h.submissionUrl && (
                                                <> &middot; <a href={h.submissionUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">{h.submissionUrl}</a></>
                                              )}
                                              {h.submissionText && <> &middot; {h.submissionText}</>}
                                            </span>
                                            <span className="text-gray-400">{new Date(h.submittedAt).toLocaleDateString()}</span>
                                          </div>
                                        ))}
                                      </div>
                                    ) : null
                                  })()}

                                  {/* Resubmit form */}
                                  <div className="space-y-2">
                                    <Input
                                      placeholder="Submission URL (e.g., GitHub link, Google Doc)"
                                      value={eventSubmissionUrl}
                                      onChange={(e) => setEventSubmissionUrl(e.target.value)}
                                      className="text-sm"
                                    />
                                    <Input
                                      placeholder="Notes (optional)"
                                      value={eventSubmissionText}
                                      onChange={(e) => setEventSubmissionText(e.target.value)}
                                      className="text-sm"
                                    />
                                    {/* File attachments */}
                                    <div className="flex items-center gap-2">
                                      <label className="cursor-pointer">
                                        <input
                                          type="file"
                                          multiple
                                          className="hidden"
                                          onChange={handleFileUpload}
                                          accept=".pdf,.doc,.docx,.ppt,.pptx,.zip,.rar,.txt,.png,.jpg,.jpeg"
                                        />
                                        <span className="inline-flex items-center gap-1.5 text-xs text-blue-600 hover:underline">
                                          <FileUp className="h-3.5 w-3.5" />
                                          {uploadingFiles ? 'Uploading...' : 'Attach files'}
                                        </span>
                                      </label>
                                      {uploadingFiles && <Loader2 className="h-3 w-3 animate-spin text-blue-600" />}
                                    </div>
                                    {pendingAttachments.length > 0 && (
                                      <div className="space-y-1">
                                        {pendingAttachments.map((att, idx) => (
                                          <div key={idx} className="flex items-center gap-2 text-xs text-gray-600 bg-gray-100 rounded px-2 py-1">
                                            <Paperclip className="h-3 w-3 shrink-0" />
                                            <span className="truncate">{att.fileName}</span>
                                            <button
                                              type="button"
                                              onClick={() => setPendingAttachments((prev) => prev.filter((_, i) => i !== idx))}
                                              className="ml-auto text-gray-400 hover:text-red-500"
                                            >
                                              <X className="h-3 w-3" />
                                            </button>
                                          </div>
                                        ))}
                                      </div>
                                    )}
                                    <Button
                                      size="sm"
                                      onClick={() => handleEventSubmit(event.id)}
                                      disabled={submittingEvent || (!eventSubmissionUrl.trim() && !eventSubmissionText.trim() && pendingAttachments.length === 0)}
                                    >
                                      {submittingEvent ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" /> : <Send className="h-3.5 w-3.5 mr-1" />}
                                      {submission ? `Submit v${submission.version + 1}` : 'Submit'}
                                    </Button>
                                  </div>
                                </div>
                              )}
                            </div>
                          )
                        })()}
                        </div>
                      )
                    })}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Submission */}
            {group && (
              <Card>
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <Send className="h-5 w-5 text-primary-600" />
                    <CardTitle className="text-lg">Submission</CardTitle>
                  </div>
                </CardHeader>
                <CardContent>
                  {group.submittedAt && !editing ? (
                    // Already submitted — show details + edit button
                    <div className="space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-green-700">
                          <CheckCircle2 className="h-5 w-5" />
                          <span className="font-medium">Submitted</span>
                        </div>
                        {canSubmit && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => { setEditing(true); setSubmissionUrl(group.submissionUrl || '') }}
                          >
                            Edit Submission
                          </Button>
                        )}
                      </div>
                      <div className="text-sm text-gray-600">
                        <p>
                          <span className="text-gray-500">URL:</span>{' '}
                          <a
                            href={group.submissionUrl || '#'}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-blue-600 hover:underline inline-flex items-center gap-1"
                          >
                            {group.submissionUrl}
                            <ExternalLink className="h-3 w-3" />
                          </a>
                        </p>
                        <p className="mt-1">
                          <span className="text-gray-500">Submitted at:</span>{' '}
                          {formatDateTime(group.submittedAt)}
                        </p>
                        {group.submittedBy && (
                          <p className="mt-1">
                            <span className="text-gray-500">Submitted by:</span>{' '}
                            {group.members?.find((m: any) => m.studentId === group.submittedBy)?.name || group.members?.find((m: any) => m.studentId === group.submittedBy)?.email || group.submittedBy}
                          </p>
                        )}
                      </div>
                      {/* Submission Attachments */}
                      {group.submissionAttachments && group.submissionAttachments.length > 0 && (
                        <div className="mt-3 border-t pt-3">
                          <p className="text-xs font-medium text-gray-500 mb-2">Attachments</p>
                          <div className="space-y-1">
                            {group.submissionAttachments.map((att: any) => (
                              <a
                                key={att.id}
                                href={att.fileUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-2 text-sm text-blue-600 hover:underline"
                              >
                                <FileDown className="h-3.5 w-3.5" />
                                {att.fileName || 'Download'}
                              </a>
                            ))}
                          </div>
                        </div>
                      )}
                      {/* Submission History */}
                      {submissionHistory.length > 1 && (
                        <div className="mt-3 border-t pt-3">
                          <p className="text-xs font-medium text-gray-500 mb-2">Submission History</p>
                          <div className="space-y-2">
                            {submissionHistory.map((h: any, i: number) => (
                              <div key={h.id} className="flex items-start gap-2 text-xs text-gray-500">
                                <Badge variant="outline" className={`text-[10px] shrink-0 ${h.action === 'EDIT' ? 'border-amber-300 text-amber-600' : 'border-green-300 text-green-600'}`}>
                                  {h.action}
                                </Badge>
                                <div>
                                  <a href={h.submissionUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline break-all">
                                    {h.submissionUrl}
                                  </a>
                                  <span className="text-gray-400 ml-1">
                                    by {h.submittedByName || h.submittedBy} &middot; {formatDateTime(h.submittedAt)}
                                  </span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  ) : canSubmit ? (
                    // Can submit
                    <div className="space-y-3">
                      {countdown && (
                        <div className="flex items-center gap-2 text-amber-600 text-sm">
                          <AlertTriangle className="h-4 w-4" />
                          <span className="font-medium">{countdown}</span>
                        </div>
                      )}
                      {isCutoffPassed() && project?.lateSubmissionAllowed && (
                        <div className="flex items-center gap-2 text-amber-600 text-sm">
                          <AlertTriangle className="h-4 w-4" />
                          <span>Deadline has passed. Late submissions are allowed.</span>
                        </div>
                      )}
                      <div className="flex gap-2">
                        <Input
                          placeholder="Enter submission URL (e.g., GitHub repo link)"
                          value={submissionUrl}
                          onChange={(e) => setSubmissionUrl(e.target.value)}
                          className="flex-1"
                        />
                        <Button
                          onClick={handleSubmit}
                          disabled={!submissionUrl.trim() || submitting}
                        >
                          {submitting ? (
                            <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                          ) : (
                            <Send className="h-4 w-4 mr-1" />
                          )}
                          Submit
                        </Button>
                      </div>
                      {/* File attachments */}
                      <div className="space-y-2">
                        <div className="flex items-center gap-2">
                          <label className="cursor-pointer">
                            <input
                              type="file"
                              multiple
                              className="hidden"
                              onChange={handleFileUpload}
                              accept=".pdf,.doc,.docx,.ppt,.pptx,.zip,.rar,.txt,.png,.jpg,.jpeg"
                            />
                            <span className="inline-flex items-center gap-1.5 text-sm text-blue-600 hover:underline">
                              <FileUp className="h-4 w-4" />
                              {uploadingFiles ? 'Uploading...' : 'Attach files'}
                            </span>
                          </label>
                          {uploadingFiles && <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-600" />}
                        </div>
                        {pendingAttachments.length > 0 && (
                          <div className="space-y-1">
                            {pendingAttachments.map((att, idx) => (
                              <div key={idx} className="flex items-center gap-2 text-sm text-gray-600 bg-gray-50 rounded px-2 py-1">
                                <Paperclip className="h-3.5 w-3.5 shrink-0" />
                                <span className="truncate">{att.fileName}</span>
                                <span className="text-xs text-gray-400">({((att.fileSizeBytes || 0) / 1024).toFixed(0)} KB)</span>
                                <button
                                  type="button"
                                  onClick={() => setPendingAttachments((prev) => prev.filter((_, i) => i !== idx))}
                                  className="ml-auto text-gray-400 hover:text-red-500"
                                >
                                  <X className="h-3.5 w-3.5" />
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                      {submitError && (
                        <p className="text-sm text-red-600">{submitError}</p>
                      )}
                      {project?.submissionCutoff && (
                        <p className="text-xs text-gray-400">
                          Deadline: {formatDateTime(project.submissionCutoff)}
                        </p>
                      )}
                    </div>
                  ) : (
                    // Cannot submit - cutoff passed
                    <div className="flex items-center gap-2 text-gray-500">
                      <Clock className="h-5 w-5" />
                      <span>Submission deadline has passed.</span>
                    </div>
                  )}
                </CardContent>
              </Card>
            )}

            {!group && (
              <Card>
                <CardContent className="py-8 text-center text-gray-500">
                  You have not been assigned to a group for this project yet.
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
