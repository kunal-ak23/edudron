'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { BarChart3, Loader2, TrendingUp, Users, BookOpen, Clock, Layers, GraduationCap, ArrowRight } from 'lucide-react'
import { coursesApi, analyticsApi, sectionsApi, enrollmentsApi } from '@/lib/api'
import type { Course, CourseAnalytics, Section, Batch } from '@kunal-ak23/edudron-shared-utils'

export default function AnalyticsPage() {
  const router = useRouter()
  const { isAuthenticated } = useAuth()
  const [courses, setCourses] = useState<Course[]>([])
  const [sections, setSections] = useState<Batch[]>([])
  const [selectedCourseId, setSelectedCourseId] = useState<string>('')
  const [analytics, setAnalytics] = useState<CourseAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingAnalytics, setLoadingAnalytics] = useState(false)

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
      return
    }
    loadCourses()
  }, [isAuthenticated, router])

  useEffect(() => {
    if (selectedCourseId) {
      loadAnalytics(selectedCourseId)
    }
  }, [selectedCourseId])

  const loadCourses = async () => {
    try {
      setLoading(true)
      const [coursesData, sectionsData] = await Promise.all([
        coursesApi.listCourses().catch(() => []),
        enrollmentsApi.listSections().catch(() => [])
      ])
      setCourses(coursesData.filter(c => c.isPublished && c.status !== 'ARCHIVED'))
      setSections(sectionsData.filter(s => s.isActive))
    } catch (error) {
    } finally {
      setLoading(false)
    }
  }

  const loadAnalytics = async (courseId: string) => {
    try {
      setLoadingAnalytics(true)
      const analyticsData = await analyticsApi.getCourseAnalytics(courseId)
      setAnalytics(analyticsData)
    } catch (error) {
    } finally {
      setLoadingAnalytics(false)
    }
  }

  const handleViewDetails = () => {
    if (selectedCourseId) {
      router.push(`/analytics/courses/${selectedCourseId}`)
    }
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
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Analytics Dashboard</h1>
          <p className="text-gray-600 mt-2">View engagement analytics by course, section, or class</p>
        </div>
      </div>

      {/* Quick Access Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Card className="hover:shadow-lg transition-shadow cursor-pointer border-2 border-blue-100">
          <CardHeader>
            <div className="flex items-center gap-2">
              <BookOpen className="h-5 w-5 text-blue-600" />
              <CardTitle>Course Analytics</CardTitle>
            </div>
            <CardDescription>
              View analytics for individual courses
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-3">
              {courses.length} published course{courses.length !== 1 ? 's' : ''} available
            </p>
            <div className="text-xs text-muted-foreground">
              Select a course below to view detailed analytics
            </div>
          </CardContent>
        </Card>

        <Card className="hover:shadow-lg transition-shadow cursor-pointer border-2 border-green-100">
          <CardHeader>
            <div className="flex items-center gap-2">
              <Layers className="h-5 w-5 text-green-600" />
              <CardTitle>Section Analytics</CardTitle>
            </div>
            <CardDescription>
              Analytics aggregated by section/batch
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-3">
              {sections.length} active section{sections.length !== 1 ? 's' : ''} available
            </p>
            {sections.length > 0 ? (
              <Select onValueChange={(id) => router.push(`/analytics/sections/${id}`)}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="Select a section" />
                </SelectTrigger>
                <SelectContent className="max-h-60 overflow-y-auto">
                  {sections.map(section => (
                    <SelectItem key={section.id} value={section.id}>
                      {section.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <p className="text-xs text-muted-foreground">No sections available</p>
            )}
          </CardContent>
        </Card>

        <Card className="hover:shadow-lg transition-shadow cursor-pointer border-2 border-purple-100">
          <CardHeader>
            <div className="flex items-center gap-2">
              <GraduationCap className="h-5 w-5 text-purple-600" />
              <CardTitle>Class Analytics</CardTitle>
            </div>
            <CardDescription>
              Compare sections within a class
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-3">
              View aggregated analytics by class
            </p>
            <Button
              variant="outline"
              size="sm"
              className="w-full"
              onClick={() => router.push('/classes')}
            >
              <ArrowRight className="h-3 w-3 mr-2" />
              Go to Classes
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Course Analytics</CardTitle>
          <CardDescription>Select a course to view quick overview</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex gap-4 items-end">
            <div className="flex-1">
              <Select value={selectedCourseId} onValueChange={setSelectedCourseId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a course to view analytics" />
                </SelectTrigger>
                <SelectContent>
                  {courses.map(course => (
                    <SelectItem key={course.id} value={course.id}>
                      {course.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {selectedCourseId && (
              <Button onClick={handleViewDetails}>
                View Detailed Analytics
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {selectedCourseId && (
        <>
          {loadingAnalytics ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="h-8 w-8 animate-spin text-primary" />
            </div>
          ) : analytics ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">Total Sessions</CardTitle>
                  <BarChart3 className="h-4 w-4 text-muted-foreground" />
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
                    {Math.floor(analytics.averageTimePerLectureSeconds / 60)}m
                  </div>
                  <p className="text-xs text-muted-foreground">Average duration</p>
                </CardContent>
              </Card>
            </div>
          ) : (
            <Card>
              <CardContent className="py-8 text-center text-gray-500">
                No analytics data available for this course yet.
              </CardContent>
            </Card>
          )}
        </>
      )}

      {!selectedCourseId && (
        <Card>
          <CardContent className="py-12 text-center">
            <BarChart3 className="mx-auto h-12 w-12 text-gray-400 mb-4" />
            <h3 className="text-lg font-semibold text-gray-900 mb-2">No Course Selected</h3>
            <p className="text-gray-500">Select a course from the dropdown above to view analytics</p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
