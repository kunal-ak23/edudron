'use client'

import { useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { projectsApi } from '@/lib/api'
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
} from 'lucide-react'
import type {
  ProjectDTO,
  ProjectGroupDTO,
  ProjectEventDTO,
  ProjectQuestionDTO,
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

  useEffect(() => {
    if (!enabled || !projectId) return
    loadData()
  }, [enabled, projectId])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)

      const [projectData, groupData, attendanceData] = await Promise.all([
        projectsApi.getProject(projectId),
        projectsApi.getMyGroup(projectId).catch(() => null),
        projectsApi.getMyAttendance(projectId).catch(() => []),
      ])

      setProject(projectData)
      setGroup(groupData)

      // attendance may be an array of records
      if (Array.isArray(attendanceData)) {
        setAttendance(attendanceData)
      } else if (attendanceData?.attendance) {
        setAttendance(attendanceData.attendance)
      } else if (attendanceData?.events) {
        setEvents(attendanceData.events)
        setAttendance(attendanceData.records || [])
      }

      // If the response includes events, store them
      if (attendanceData?.events) {
        setEvents(attendanceData.events)
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
    } catch (err) {
      console.error('Failed to load project details', err)
      setError('Failed to load project details')
    } finally {
      setLoading(false)
    }
  }

  async function handleSubmit() {
    if (!submissionUrl.trim() || !group) return
    try {
      setSubmitting(true)
      setSubmitError(null)
      const updated = await projectsApi.submitMyProject(projectId, {
        submissionUrl: submissionUrl.trim(),
      })
      setGroup(updated)
      setSubmissionUrl('')
    } catch (err: any) {
      console.error('Failed to submit project', err)
      setSubmitError(err?.response?.data?.error || 'Failed to submit project')
    } finally {
      setSubmitting(false)
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
                      Group {group.groupNumber}
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

            {/* Events Timeline */}
            {attendance.length > 0 && (
              <Card>
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <Calendar className="h-5 w-5 text-primary-600" />
                    <CardTitle className="text-lg">Events</CardTitle>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    {attendance.map((record, idx) => {
                      // Try to find matching event for zoom link / dateTime
                      const event = events.find((e) => e.id === record.eventId)
                      return (
                        <div
                          key={record.eventId || idx}
                          className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                        >
                          <div className="flex-1 min-w-0">
                            <p className="font-medium text-gray-900">
                              {record.eventName || event?.name || `Event ${idx + 1}`}
                            </p>
                            {event?.dateTime && (
                              <p className="text-sm text-gray-500">
                                {formatDateTime(event.dateTime)}
                              </p>
                            )}
                            {event?.zoomLink && (
                              <a
                                href={event.zoomLink}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 text-sm text-blue-600 hover:underline mt-1"
                              >
                                <Video className="h-3.5 w-3.5" />
                                Join Zoom
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            )}
                          </div>
                          <div className="flex items-center gap-3 shrink-0">
                            {/* Attendance badge */}
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
                            ) : (
                              <Badge variant="outline" className="text-gray-400">
                                <MinusCircle className="h-3.5 w-3.5 mr-1" />
                                Not recorded
                              </Badge>
                            )}
                            {/* Marks */}
                            {record.hasMarks && record.marks != null ? (
                              <span className="text-sm font-medium text-gray-700">
                                {record.marks} / {record.maxMarks ?? '?'}
                              </span>
                            ) : record.hasMarks ? (
                              <span className="text-sm text-gray-400">&mdash;</span>
                            ) : null}
                          </div>
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
                  {group.submittedAt ? (
                    // Already submitted
                    <div className="space-y-2">
                      <div className="flex items-center gap-2 text-green-700">
                        <CheckCircle2 className="h-5 w-5" />
                        <span className="font-medium">Submitted</span>
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
                            {group.submittedBy}
                          </p>
                        )}
                      </div>
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
