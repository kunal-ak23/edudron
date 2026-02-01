'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, TrendingUp, Users, BookOpen, Clock, AlertTriangle } from 'lucide-react'
import { coursesApi, analyticsApi } from '@/lib/api'
import type { CourseAnalytics, SkippedLecture } from '@kunal-ak23/edudron-shared-utils'
import { ActivityTimelineChart } from '@/components/analytics/ActivityTimelineChart'
import { CompletionRateChart } from '@/components/analytics/CompletionRateChart'
import { DurationDistributionChart } from '@/components/analytics/DurationDistributionChart'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export default function CourseAnalyticsPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.courseId as string
  const { isAuthenticated } = useAuth()
  const [course, setCourse] = useState<any>(null)
  const [analytics, setAnalytics] = useState<CourseAnalytics | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    const loadData = async () => {
      try {
        setLoading(true)
        const [courseData, analyticsData] = await Promise.all([
          coursesApi.getCourse(courseId).catch(() => null),
          analyticsApi.getCourseAnalytics(courseId).catch(() => null)
        ])
        setCourse(courseData)
        setAnalytics(analyticsData)
      } catch (error) {
        console.error('Failed to load data:', error)
      } finally {
        setLoading(false)
      }
    }
    loadData()
  }, [isAuthenticated, router, courseId])

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    return `${mins}m`
  }

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
        <Button variant="ghost" onClick={() => router.push('/analytics')}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div>
          <h1 className="text-3xl font-bold">
            {analytics?.courseTitle || course?.title || 'Course Analytics'}
          </h1>
          <p className="text-gray-600 mt-2">Detailed engagement analytics</p>
        </div>
      </div>

      {analytics ? (
        <>
          {/* Overview Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Sessions</CardTitle>
                <BookOpen className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.totalViewingSessions.toLocaleString()}</div>
                <p className="text-xs text-muted-foreground">Viewing sessions</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Active Students</CardTitle>
                <Users className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.uniqueStudentsEngaged.toLocaleString()}</div>
                <p className="text-xs text-muted-foreground">Unique students</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Completion Rate</CardTitle>
                <TrendingUp className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.overallCompletionRate.toFixed(1)}%</div>
                <p className="text-xs text-muted-foreground">Overall completion</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Avg Time/Lecture</CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {formatDuration(analytics.averageTimePerLectureSeconds)}
                </div>
                <p className="text-xs text-muted-foreground">Average duration</p>
              </CardContent>
            </Card>
          </div>

          {/* Activity Timeline */}
          {analytics.activityTimeline && analytics.activityTimeline.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Activity Timeline</CardTitle>
              </CardHeader>
              <CardContent>
                <ActivityTimelineChart data={analytics.activityTimeline} />
              </CardContent>
            </Card>
          )}

          {/* Completion Rates */}
          {analytics.lectureEngagements && analytics.lectureEngagements.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Lecture Completion Rates</CardTitle>
              </CardHeader>
              <CardContent>
                <CompletionRateChart data={analytics.lectureEngagements} />
              </CardContent>
            </Card>
          )}

          {/* Duration Distribution */}
          {analytics.lectureEngagements && analytics.lectureEngagements.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Average Time Spent per Lecture</CardTitle>
              </CardHeader>
              <CardContent>
                <DurationDistributionChart data={analytics.lectureEngagements} />
              </CardContent>
            </Card>
          )}

          {/* Lecture Engagement Table */}
          {analytics.lectureEngagements && analytics.lectureEngagements.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Lecture Engagement Summary</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Lecture</TableHead>
                        <TableHead>Total Views</TableHead>
                        <TableHead>Unique Viewers</TableHead>
                        <TableHead>Avg Duration</TableHead>
                        <TableHead>Completion Rate</TableHead>
                        <TableHead>Skip Rate</TableHead>
                        <TableHead>Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {analytics.lectureEngagements.map((lecture) => (
                        <TableRow key={lecture.lectureId}>
                          <TableCell className="font-medium">{lecture.lectureTitle}</TableCell>
                          <TableCell>{lecture.totalViews}</TableCell>
                          <TableCell>{lecture.uniqueViewers}</TableCell>
                          <TableCell>{formatDuration(lecture.averageDurationSeconds)}</TableCell>
                          <TableCell>{(lecture.completionRate ?? 0).toFixed(1)}%</TableCell>
                          <TableCell>
                            <Badge variant={(lecture.skipRate ?? 0) > 50 ? 'destructive' : 'secondary'}>
                              {(lecture.skipRate ?? 0).toFixed(1)}%
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => router.push(`/analytics/courses/${courseId}/lectures/${lecture.lectureId}`)}
                            >
                              View Details
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Skipped Lectures */}
          {analytics.skippedLectures && analytics.skippedLectures.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertTriangle className="h-5 w-5 text-orange-500" />
                  Skipped Lectures
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Lecture</TableHead>
                        <TableHead>Duration</TableHead>
                        <TableHead>Total Sessions</TableHead>
                        <TableHead>Skipped Sessions</TableHead>
                        <TableHead>Skip Rate</TableHead>
                        <TableHead>Avg Duration</TableHead>
                        <TableHead>Reason</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {analytics.skippedLectures.map((lecture: SkippedLecture) => (
                        <TableRow key={lecture.lectureId}>
                          <TableCell className="font-medium">{lecture.lectureTitle}</TableCell>
                          <TableCell>{formatDuration(lecture.lectureDurationSeconds)}</TableCell>
                          <TableCell>{lecture.totalSessions}</TableCell>
                          <TableCell>{lecture.skippedSessions}</TableCell>
                          <TableCell>
                            <Badge variant="destructive">{(lecture.skipRate ?? 0).toFixed(1)}%</Badge>
                          </TableCell>
                          <TableCell>{formatDuration(lecture.averageDurationSeconds)}</TableCell>
                          <TableCell>
                            <Badge variant="outline">
                              {lecture.skipReason === 'QUICK_COMPLETION' ? 'Quick Completion' : 'Short Duration'}
                            </Badge>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          )}
        </>
      ) : (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            No analytics data available for this course yet.
          </CardContent>
        </Card>
      )}
    </div>
  )
}
