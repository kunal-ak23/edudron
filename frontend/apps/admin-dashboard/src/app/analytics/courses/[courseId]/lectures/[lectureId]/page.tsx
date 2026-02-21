'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, TrendingUp, Users, Eye, Clock, CheckCircle, XCircle } from 'lucide-react'
import { analyticsApi } from '@/lib/api'
import type { LectureAnalytics } from '@kunal-ak23/edudron-shared-utils'
import { ActivityTimelineChart } from '@/components/analytics/ActivityTimelineChart'
import { StudentEngagementTable } from '@/components/analytics/StudentEngagementTable'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

export default function LectureAnalyticsPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.courseId as string
  const lectureId = params.lectureId as string
  const { isAuthenticated } = useAuth()
  const [analytics, setAnalytics] = useState<LectureAnalytics | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    const loadData = async () => {
      try {
        setLoading(true)
        const analyticsData = await analyticsApi.getLectureAnalytics(lectureId)
        setAnalytics(analyticsData)
      } catch (error) {
      } finally {
        setLoading(false)
      }
    }
    loadData()
  }, [isAuthenticated, router, lectureId])

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}m ${secs}s`
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '—'
    return new Date(dateStr).toLocaleString()
  }

  // Prepare duration distribution data
  const durationDistributionData = analytics?.recentSessions
    ?.filter(s => s.durationSeconds > 0)
    .map(s => ({
      duration: Math.floor(s.durationSeconds / 60), // Convert to minutes
      count: 1
    }))
    .reduce((acc: any[], curr) => {
      const existing = acc.find(item => item.duration === curr.duration)
      if (existing) {
        existing.count++
      } else {
        acc.push(curr)
      }
      return acc
    }, [])
    .sort((a, b) => a.duration - b.duration) || []

  if (loading) {
    return (
      <div className="container mx-auto py-8 px-4">
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 px-4">
      <div className="mb-6 flex items-center gap-4">
        <Button variant="ghost" onClick={() => router.push(`/analytics/courses/${courseId}`)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Course Analytics
        </Button>
        <div>
          <h1 className="text-3xl font-bold">
            {analytics?.lectureTitle || 'Lecture Analytics'}
          </h1>
          <p className="text-gray-600 mt-2">Detailed engagement metrics</p>
        </div>
      </div>

      {analytics ? (
        <>
          {/* Overview Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Views</CardTitle>
                <Eye className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.totalViews.toLocaleString()}</div>
                <p className="text-xs text-muted-foreground">Viewing sessions</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Unique Viewers</CardTitle>
                <Users className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.uniqueViewers.toLocaleString()}</div>
                <p className="text-xs text-muted-foreground">Unique students</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Completion Rate</CardTitle>
                <CheckCircle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{(analytics.completionRate ?? 0).toFixed(1)}%</div>
                <p className="text-xs text-muted-foreground">Completed sessions</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Avg Duration</CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {formatDuration(analytics.averageSessionDurationSeconds)}
                </div>
                <p className="text-xs text-muted-foreground">Per session</p>
              </CardContent>
            </Card>
          </div>

          {/* Additional Metrics */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Skip Rate</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  <Badge variant={(analytics.skipRate ?? 0) > 50 ? 'destructive' : 'secondary'}>
                    {(analytics.skipRate ?? 0).toFixed(1)}%
                  </Badge>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">First View</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm">{formatDate(analytics.firstViewAt)}</div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-sm">Last View</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm">{formatDate(analytics.lastViewAt)}</div>
              </CardContent>
            </Card>
          </div>

          {/* Duration Distribution */}
          {durationDistributionData.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Session Duration Distribution</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={durationDistributionData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="duration" label={{ value: 'Minutes', position: 'insideBottom', offset: -5 }} />
                    <YAxis label={{ value: 'Count', angle: -90, position: 'insideLeft' }} />
                    <Tooltip formatter={(value) => {
                      const numValue = typeof value === 'number' ? value : (value ? Number(value) : 0);
                      return `${numValue} sessions`;
                    }} />
                    <Bar dataKey="count" fill="#8884d8" name="Sessions" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          )}

          {/* Activity Timeline */}
          {analytics.recentSessions && analytics.recentSessions.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Recent Viewing Sessions</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Student</TableHead>
                        <TableHead>Started</TableHead>
                        <TableHead>Ended</TableHead>
                        <TableHead>Duration</TableHead>
                        <TableHead>Progress Start</TableHead>
                        <TableHead>Progress End</TableHead>
                        <TableHead>Completed</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {(() => {
                        const emailMap = new Map(
                          (analytics.studentEngagements || []).map(e => [e.studentId, e.studentEmail])
                        )
                        return analytics.recentSessions.slice(0, 20).map((session) => (
                          <TableRow key={session.id}>
                            <TableCell className="font-medium">
                              {emailMap.get(session.studentId) || session.studentId}
                            </TableCell>
                            <TableCell>{formatDate(session.sessionStartedAt)}</TableCell>
                            <TableCell>{formatDate(session.sessionEndedAt)}</TableCell>
                            <TableCell>{formatDuration(session.durationSeconds)}</TableCell>
                            <TableCell>{session.progressAtStart.toFixed(0)}%</TableCell>
                            <TableCell>{session.progressAtEnd?.toFixed(0) || '—'}%</TableCell>
                            <TableCell>
                              {session.isCompletedInSession ? (
                                <CheckCircle className="h-4 w-4 text-green-600" />
                              ) : (
                                <XCircle className="h-4 w-4 text-gray-400" />
                              )}
                            </TableCell>
                          </TableRow>
                        ))
                      })()}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Student Engagement */}
          {analytics.studentEngagements && analytics.studentEngagements.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Student Engagement</CardTitle>
              </CardHeader>
              <CardContent>
                <StudentEngagementTable data={analytics.studentEngagements} />
              </CardContent>
            </Card>
          )}
        </>
      ) : (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            No analytics data available for this lecture yet.
          </CardContent>
        </Card>
      )}
    </div>
  )
}
