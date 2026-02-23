'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import { Loader2, ArrowLeft, PlayCircle, Clock, CheckCircle2, GraduationCap, FileText } from 'lucide-react'
import { apiClient } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// Data types based on backend DTOs
interface StudentLectureEngagementDTO {
    studentId: string
    studentEmail: string
    totalSessions: number
    totalDurationSeconds: number
    averageSessionDurationSeconds: number
    firstViewAt: string | null
    lastViewAt: string | null
    isCompleted: boolean | null
    completionPercentage: number
    lectureId: string
    lectureTitle: string
    lectureDurationSeconds: number | null
}

interface StudentCourseAnalyticsDTO {
    studentId: string
    studentEmail: string
    courseId: string
    courseTitle: string
    totalViewingSessions: number
    totalDurationSeconds: number
    averageSessionDurationSeconds: number
    completionPercentage: number
    lectureActivity: StudentLectureEngagementDTO[]
}

interface StudentSubmission {
    id: string
    assessmentId: string
    courseId?: string
    score: number | null
    maxScore: number | null
    percentage: number | null
    isPassed: boolean | null
    submittedAt: string | null
    completedAt?: string | null
}

const formatDuration = (seconds?: number | null) => {
    if (seconds == null || isNaN(seconds)) return '0m'
    const hours = Math.floor(seconds / 3600)
    const mins = Math.floor((seconds % 3600) / 60)
    if (hours > 0) return `${hours}h ${mins}m`
    return `${mins}m`
}

export default function StudentCourseAnalyticsPage() {
    const router = useRouter()
    const params = useParams()
    const studentId = params?.id as string
    const courseId = params?.courseId as string
    const { toast } = useToast()
    const { user, isAuthenticated } = useAuth()

    const [analytics, setAnalytics] = useState<StudentCourseAnalyticsDTO | null>(null)
    const [submissions, setSubmissions] = useState<StudentSubmission[]>([])
    const [examTitles, setExamTitles] = useState<Record<string, string>>({})
    const [loading, setLoading] = useState(true)

    const loadData = useCallback(async () => {
        if (!studentId || !courseId) return
        try {
            setLoading(true)

            // 1. Fetch Student Course Analytics
            const analyticsRes = await apiClient.get<StudentCourseAnalyticsDTO>(
                `/api/students/${studentId}/courses/${courseId}/analytics`
            )
            setAnalytics(analyticsRes.data)

            // 2. Fetch Student Submissions (filter by courseId)
            // Note: In reality we might not have courseId strictly on all submissions,
            // but we will try to filter it if the backend returns it. Best we can do is fetch all
            // and filter locally if possible, or if backend has it.
            const submissionsRes = await apiClient.get<StudentSubmission[] | { content?: StudentSubmission[] }>(
                `/api/students/${studentId}/submissions`
            )
            const raw = submissionsRes.data
            let list = Array.isArray(raw) ? raw : (raw as { content?: StudentSubmission[] })?.content ?? []

            // For now, if courseId is present on submission, we filter.
            // If none have courseId, we might show everything, but we should try to match assessments to this course.
            list = list.filter(sub => sub.courseId === courseId)
            setSubmissions(list)

            // Fetch exam titles
            const assessmentIds = [...new Set(list.map((s) => s.assessmentId).filter(Boolean))]
            const titles: Record<string, string> = {}
            await Promise.allSettled(
                assessmentIds.map(async (assessmentId) => {
                    try {
                        const examRes = await apiClient.get<{ title?: string; courseId?: string }>(`/api/exams/${assessmentId}`)
                        const examData = examRes.data as any
                        const title = examData?.title ?? assessmentId
                        titles[assessmentId] = title
                    } catch {
                        titles[assessmentId] = assessmentId
                    }
                })
            )
            setExamTitles(titles)

        } catch (err: any) {
            console.error('Failed to load data:', err)
            toast({
                variant: 'destructive',
                title: 'Error loading analytics',
                description: extractErrorMessage(err)
            })
        } finally {
            setLoading(false)
        }
    }, [studentId, courseId, toast])

    useEffect(() => {
        if (!isAuthenticated() || !user) {
            router.push('/login')
            return
        }
        const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'INSTRUCTOR']
        if (!allowedRoles.includes(user.role)) {
            router.push('/unauthorized')
            return
        }
        loadData()
    }, [user, isAuthenticated, router, loadData])

    if (!user || !isAuthenticated()) return null
    const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'INSTRUCTOR']
    if (!allowedRoles.includes(user.role)) return null

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <Loader2 className="h-8 w-8 animate-spin text-primary" />
            </div>
        )
    }

    if (!analytics) {
        return (
            <div className="space-y-4">
                <Link href={`/students/${studentId}`}>
                    <Button variant="ghost">
                        <ArrowLeft className="h-4 w-4 mr-2" />
                        Back to Student
                    </Button>
                </Link>
                <Card>
                    <CardContent className="py-12 text-center">
                        <p className="text-muted-foreground">Analytics data could not be loaded or is unavailable for this course.</p>
                    </CardContent>
                </Card>
            </div>
        )
    }

    const {
        courseTitle,
        totalViewingSessions,
        totalDurationSeconds,
        completionPercentage,
        lectureActivity
    } = analytics

    // Calculate completed lectures for summary
    const totalLectures = lectureActivity?.length || 0;
    const completedLectures = lectureActivity?.filter(l => l.isCompleted)?.length || 0;

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <Link href={`/students/${studentId}`}>
                        <Button variant="ghost" className="mb-2 -ml-4">
                            <ArrowLeft className="h-4 w-4 mr-2" />
                            Back to Student
                        </Button>
                    </Link>
                    <h1 className="text-2xl font-bold">{courseTitle || courseId}</h1>
                    <p className="text-muted-foreground">Detailed course analytics and engagement metrics</p>
                </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Overall Progress</CardTitle>
                        <CheckCircle2 className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{Math.round(completionPercentage)}%</div>
                        <Progress value={completionPercentage} className="w-full mt-2 h-2" />
                        <p className="text-xs text-muted-foreground mt-2">
                            Based on completed lectures
                        </p>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Lectures Completed</CardTitle>
                        <PlayCircle className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{completedLectures} / {totalLectures}</div>
                        <p className="text-xs text-muted-foreground mt-2">
                            Total viewing sessions: {totalViewingSessions}
                        </p>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Total Time Spent</CardTitle>
                        <Clock className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{formatDuration(totalDurationSeconds)}</div>
                        <p className="text-xs text-muted-foreground mt-2">
                            Across all viewing sessions
                        </p>
                    </CardContent>
                </Card>
            </div>

            <div className="grid gap-6 md:grid-cols-2">
                {/* Lecture Activity Table */}
                <Card className="md:col-span-2">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <FileText className="h-5 w-5" />
                            Lecture Activity
                        </CardTitle>
                        <CardDescription>Detailed breakdown of lecture engagement</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {!lectureActivity || lectureActivity.length === 0 ? (
                            <p className="text-muted-foreground">No lecture activity recorded yet.</p>
                        ) : (
                            <div className="rounded-md border">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Lecture Name</TableHead>
                                            <TableHead className="text-right">Sessions</TableHead>
                                            <TableHead className="text-right">Time Spent</TableHead>
                                            <TableHead>Last Viewed</TableHead>
                                            <TableHead>Status</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {lectureActivity.map((l) => (
                                            <TableRow key={l.lectureId}>
                                                <TableCell className="font-medium">
                                                    {l.lectureTitle || l.lectureId}
                                                </TableCell>
                                                <TableCell className="text-right">{l.totalSessions}</TableCell>
                                                <TableCell className="text-right">{formatDuration(l.totalDurationSeconds)}</TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {l.lastViewAt ? new Date(l.lastViewAt).toLocaleDateString() : '—'}
                                                </TableCell>
                                                <TableCell>
                                                    {l.isCompleted ? (
                                                        <Badge variant="default" className="bg-green-600 hover:bg-green-700">Completed</Badge>
                                                    ) : l.totalSessions > 0 ? (
                                                        <Badge variant="secondary" className="bg-amber-100 text-amber-800 hover:bg-amber-200">In Progress</Badge>
                                                    ) : (
                                                        <Badge variant="outline">Not Started</Badge>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>
                        )}
                    </CardContent>
                </Card>

                {/* Exams for this course */}
                <Card className="md:col-span-2">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <GraduationCap className="h-5 w-5" />
                            Course Exams
                        </CardTitle>
                        <CardDescription>Recent exam submissions for this course</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {submissions.length === 0 ? (
                            <p className="text-muted-foreground">No exam submissions for this course.</p>
                        ) : (
                            <div className="rounded-md border">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Exam Name</TableHead>
                                            <TableHead>Score</TableHead>
                                            <TableHead>Result</TableHead>
                                            <TableHead>Submitted On</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {submissions.map((sub) => (
                                            <TableRow key={sub.id}>
                                                <TableCell className="font-medium">
                                                    <Link
                                                        href={`/exams/${sub.assessmentId}`}
                                                        className="text-primary hover:underline"
                                                    >
                                                        {examTitles[sub.assessmentId] || sub.assessmentId}
                                                    </Link>
                                                </TableCell>
                                                <TableCell>
                                                    {sub.score != null && sub.maxScore != null
                                                        ? `${sub.score} / ${sub.maxScore}`
                                                        : sub.percentage != null
                                                            ? `${Math.round(Number(sub.percentage))}%`
                                                            : '—'}
                                                </TableCell>
                                                <TableCell>
                                                    {sub.isPassed != null ? (
                                                        <Badge variant={sub.isPassed ? 'default' : 'destructive'}>
                                                            {sub.isPassed ? 'Passed' : 'Failed'}
                                                        </Badge>
                                                    ) : (
                                                        <span className="text-muted-foreground">—</span>
                                                    )}
                                                </TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {sub.submittedAt || sub.completedAt
                                                        ? new Date(sub.submittedAt || sub.completedAt!).toLocaleString()
                                                        : '—'}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>
                        )}
                    </CardContent>
                </Card>

            </div>
        </div>
    )
}
