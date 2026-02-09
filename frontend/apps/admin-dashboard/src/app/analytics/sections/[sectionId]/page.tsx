'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, ArrowLeft, TrendingUp, Users, BookOpen, Clock, AlertTriangle, GraduationCap } from 'lucide-react'
import { analyticsApi } from '@/lib/api'
import type { SectionAnalytics } from '@kunal-ak23/edudron-shared-utils'
import { ActivityTimelineChart } from '@/components/analytics/ActivityTimelineChart'
import { CompletionRateChart } from '@/components/analytics/CompletionRateChart'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export default function SectionAnalyticsPage() {
  const router = useRouter()
  const params = useParams()
  const sectionId = params.sectionId as string
  const { isAuthenticated } = useAuth()
  const [analytics, setAnalytics] = useState<SectionAnalytics | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    const loadData = async () => {
      try {
        setLoading(true)
        const analyticsData = await analyticsApi.getSectionAnalytics(sectionId)
        setAnalytics(analyticsData)
      } catch (error) {
        console.error('Failed to load section analytics:', error)
      } finally {
        setLoading(false)
      }
    }
    loadData()
  }, [isAuthenticated, router, sectionId])

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
        <Button variant="ghost" onClick={() => router.push('/analytics/sections')}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div>
          <h1 className="text-3xl font-bold">
            {analytics?.sectionName || 'Section Analytics'}
          </h1>
          <p className="text-gray-600 mt-2">
            Analytics aggregated across {analytics?.totalCourses || 0} course(s)
            {analytics?.className && ` • Class: ${analytics.className}`}
          </p>
        </div>
      </div>

      {analytics ? (
        <>
          {/* Overview Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Courses</CardTitle>
                <BookOpen className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.totalCourses}</div>
                <p className="text-xs text-muted-foreground">Assigned courses</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Sessions</CardTitle>
                <TrendingUp className="h-4 w-4 text-muted-foreground" />
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
                <div className="text-2xl font-bold">{analytics.uniqueStudentsEngaged}</div>
                <p className="text-xs text-muted-foreground">Engaged students</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Avg Time/Lecture</CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{formatDuration(analytics.averageTimePerLectureSeconds)}</div>
                <p className="text-xs text-muted-foreground">Per lecture</p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Completion Rate</CardTitle>
                <GraduationCap className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{analytics.overallCompletionRate.toFixed(1)}%</div>
                <p className="text-xs text-muted-foreground">Overall</p>
              </CardContent>
            </Card>
          </div>

          {/* Course Breakdown */}
          {analytics.courseBreakdown && analytics.courseBreakdown.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Course Breakdown</CardTitle>
                <p className="text-sm text-muted-foreground">
                  Performance metrics for each course in this section
                </p>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Course</TableHead>
                      <TableHead className="text-right">Sessions</TableHead>
                      <TableHead className="text-right">Students</TableHead>
                      <TableHead className="text-right">Completion Rate</TableHead>
                      <TableHead className="text-right">Avg Time</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {analytics.courseBreakdown.map((course) => (
                      <TableRow key={course.courseId}>
                        <TableCell className="font-medium">{course.courseTitle}</TableCell>
                        <TableCell className="text-right">{course.totalSessions.toLocaleString()}</TableCell>
                        <TableCell className="text-right">{course.uniqueStudents}</TableCell>
                        <TableCell className="text-right">
                          <Badge variant={course.completionRate >= 70 ? 'default' : 'secondary'}>
                            {course.completionRate.toFixed(1)}%
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">{formatDuration(course.averageTimeSpentSeconds)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Activity Timeline */}
          {analytics.activityTimeline && analytics.activityTimeline.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Activity Timeline</CardTitle>
                <p className="text-sm text-muted-foreground">
                  Daily engagement trends across all courses
                </p>
              </CardHeader>
              <CardContent>
                <ActivityTimelineChart data={analytics.activityTimeline} />
              </CardContent>
            </Card>
          )}

          {/* Lecture Engagement */}
          {analytics.lectureEngagements && analytics.lectureEngagements.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle>Lecture Engagement</CardTitle>
                <p className="text-sm text-muted-foreground">
                  Top lectures by views (across all courses)
                </p>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Lecture</TableHead>
                      <TableHead className="text-right">Views</TableHead>
                      <TableHead className="text-right">Unique Viewers</TableHead>
                      <TableHead className="text-right">Avg Duration</TableHead>
                      <TableHead className="text-right">Completion</TableHead>
                      <TableHead className="text-right">Skip Rate</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {analytics.lectureEngagements.slice(0, 10).map((lecture) => (
                      <TableRow key={lecture.lectureId}>
                        <TableCell className="font-medium">{lecture.lectureTitle}</TableCell>
                        <TableCell className="text-right">{lecture.totalViews}</TableCell>
                        <TableCell className="text-right">{lecture.uniqueViewers}</TableCell>
                        <TableCell className="text-right">{formatDuration(lecture.averageDurationSeconds)}</TableCell>
                        <TableCell className="text-right">
                          <Badge variant={lecture.completionRate >= 70 ? 'default' : 'secondary'}>
                            {lecture.completionRate.toFixed(1)}%
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          {lecture.skipRate && lecture.skipRate > 50 ? (
                            <Badge variant="destructive">{lecture.skipRate.toFixed(1)}%</Badge>
                          ) : (
                            <span>{lecture.skipRate?.toFixed(1) || 0}%</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Skipped Lectures */}
          {analytics.skippedLectures && analytics.skippedLectures.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertTriangle className="h-5 w-5 text-yellow-500" />
                  Skipped Lectures
                </CardTitle>
                <p className="text-sm text-muted-foreground">
                  Lectures with high skip rates (across all courses)
                </p>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Lecture</TableHead>
                      <TableHead className="text-right">Duration</TableHead>
                      <TableHead className="text-right">Total Sessions</TableHead>
                      <TableHead className="text-right">Skipped</TableHead>
                      <TableHead className="text-right">Skip Rate</TableHead>
                      <TableHead>Reason</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {analytics.skippedLectures.map((lecture) => (
                      <TableRow key={lecture.lectureId}>
                        <TableCell className="font-medium">{lecture.lectureTitle}</TableCell>
                        <TableCell className="text-right">{formatDuration(lecture.lectureDurationSeconds)}</TableCell>
                        <TableCell className="text-right">{lecture.totalSessions}</TableCell>
                        <TableCell className="text-right">{lecture.skippedSessions}</TableCell>
                        <TableCell className="text-right">
                          <Badge variant="destructive">{lecture.skipRate.toFixed(1)}%</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{lecture.skipReason.replace('_', ' ')}</Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </>
      ) : (
        <Card>
          <CardContent className="py-8">
            <p className="text-center text-muted-foreground">No analytics data available</p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
