'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { 
  BookOpen, 
  Users, 
  GraduationCap, 
  BarChart3, 
  Plus, 
  Sparkles, 
  FileText, 
  UserCog,
  List,
  Loader2,
  LogOut
} from 'lucide-react'
import { coursesApi, enrollmentsApi } from '@/lib/api'
import type { Course, Batch } from '@edudron/shared-utils'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

export default function DashboardPage() {
  const [mounted, setMounted] = useState(false)
  const router = useRouter()
  const [courses, setCourses] = useState<Course[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [user, setUser] = useState<any>(null)

  // Ensure we're on client side before using router
  useEffect(() => {
    setMounted(true)
  }, [])

  useEffect(() => {
    if (!mounted) return
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mounted])

  const loadData = async () => {
    try {
      const userStr = localStorage.getItem('user')
      if (userStr) {
        setUser(JSON.parse(userStr))
      }

      const [coursesData, batchesData] = await Promise.all([
        coursesApi.listCourses(),
        enrollmentsApi.listBatches()
      ])

      setCourses(coursesData)
      setBatches(batchesData)
    } catch (error) {
      console.error('Failed to load dashboard data:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleLogout = () => {
    localStorage.clear()
    router.push('/login')
  }

  const publishedCourses = courses.filter(c => c.isPublished)
  const draftCourses = courses.filter(c => !c.isPublished)
  const activeBatches = batches.filter(b => b.isActive)
  const totalStudents = batches.reduce((sum, batch) => sum + (batch.enrolledCount || 0), 0)

  if (!mounted) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER']}>
      <div>

          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-3">
            <Card className="bg-gradient-to-br from-blue-500 to-blue-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-blue-100">Total Courses</h3>
                  <BookOpen className="w-6 h-6 text-blue-200" />
                </div>
                <p className="text-3xl font-bold">{loading ? '...' : courses.length}</p>
                <p className="text-sm text-blue-100 mt-1">
                  {publishedCourses.length} published, {draftCourses.length} drafts
                </p>
              </CardContent>
            </Card>

            <Card className="bg-gradient-to-br from-green-500 to-green-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-green-100">Active Batches</h3>
                  <Users className="w-6 h-6 text-green-200" />
                </div>
                <p className="text-3xl font-bold">{loading ? '...' : activeBatches.length}</p>
                <p className="text-sm text-green-100 mt-1">Currently running</p>
              </CardContent>
            </Card>

            <Card className="bg-gradient-to-br from-purple-500 to-purple-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-purple-100">Total Students</h3>
                  <GraduationCap className="w-6 h-6 text-purple-200" />
                </div>
                <p className="text-3xl font-bold">{loading ? '...' : totalStudents.toLocaleString()}</p>
                <p className="text-sm text-purple-100 mt-1">Enrolled across all batches</p>
              </CardContent>
            </Card>

            <Card className="bg-gradient-to-br from-orange-500 to-orange-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-orange-100">Completion Rate</h3>
                  <BarChart3 className="w-6 h-6 text-orange-200" />
                </div>
                <p className="text-3xl font-bold">-</p>
                <p className="text-sm text-orange-100 mt-1">Average course completion</p>
              </CardContent>
            </Card>
          </div>

          {/* Main Content Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-3">
            {/* Recent Courses */}
            <div className="lg:col-span-2">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                  <CardTitle>Recent Courses</CardTitle>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => router.push('/courses')}
                  >
                    View All
                  </Button>
                </CardHeader>
                <CardContent>
                {loading ? (
                  <div className="space-y-4">
                    {[...Array(5)].map((_, i) => (
                      <div key={i} className="animate-pulse">
                        <div className="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
                        <div className="h-3 bg-gray-200 rounded w-1/2"></div>
                      </div>
                    ))}
                  </div>
                ) : courses.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    <p>No courses yet</p>
                    <Button
                      className="mt-4"
                      onClick={() => router.push('/courses/new')}
                    >
                      Create Your First Course
                    </Button>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {courses.slice(0, 5).map((course) => (
                      <div
                        key={course.id}
                        className="flex items-center justify-between p-4 border rounded-lg hover:bg-accent cursor-pointer transition-colors"
                        onClick={() => router.push(`/courses/${course.id}`)}
                      >
                        <div className="flex-1">
                          <h3 className="font-semibold mb-1">{course.title}</h3>
                          <div className="flex items-center space-x-4 text-sm text-muted-foreground">
                            <Badge variant={course.isPublished ? 'default' : 'secondary'}>
                              {course.isPublished ? 'Published' : 'Draft'}
                            </Badge>
                            {course.totalStudentsCount && (
                              <span>{course.totalStudentsCount} students</span>
                            )}
                            {course.totalLecturesCount && (
                              <span>{course.totalLecturesCount} lectures</span>
                            )}
                          </div>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation()
                            router.push(`/courses/${course.id}`)
                          }}
                        >
                          Edit
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
                </CardContent>
              </Card>
            </div>

            {/* Quick Actions */}
            <div>
              <Card>
                <CardHeader>
                  <CardTitle>Quick Actions</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    <Button
                      className="w-full justify-start"
                      onClick={() => router.push('/courses/new')}
                    >
                      <Plus className="w-5 h-5 mr-2" />
                      Create New Course
                    </Button>
                    <Button
                      className="w-full justify-start"
                      variant="outline"
                      onClick={() => router.push('/batches/new')}
                    >
                      <Users className="w-5 h-5 mr-2" />
                      Create New Batch
                    </Button>
                    <Button
                      className="w-full justify-start"
                      variant="outline"
                      onClick={() => router.push('/courses/generate')}
                    >
                      <Sparkles className="w-5 h-5 mr-2" />
                      Generate Course with AI
                    </Button>
                    <Button
                      className="w-full justify-start"
                      variant="outline"
                      onClick={() => router.push('/course-index')}
                    >
                      <FileText className="w-5 h-5 mr-2" />
                      Manage Course Index
                    </Button>
                    <Button
                      className="w-full justify-start"
                      variant="outline"
                      onClick={() => router.push('/users')}
                    >
                      <UserCog className="w-5 h-5 mr-2" />
                      Manage Users
                    </Button>
                    <Button
                      className="w-full justify-start"
                      variant="outline"
                      onClick={() => router.push('/courses')}
                    >
                      <List className="w-5 h-5 mr-2" />
                      View All Courses
                    </Button>
                  </div>
                </CardContent>
              </Card>

              {/* Recent Activity */}
              <Card className="mt-6">
                <CardHeader>
                  <CardTitle>Recent Activity</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3 text-sm">
                    <div className="flex items-start space-x-3">
                      <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
                      <div className="flex-1">
                        <p>New course created</p>
                        <p className="text-muted-foreground text-xs">2 hours ago</p>
                      </div>
                    </div>
                    <div className="flex items-start space-x-3">
                      <div className="w-2 h-2 bg-green-500 rounded-full mt-2"></div>
                      <div className="flex-1">
                        <p>Batch enrollment completed</p>
                        <p className="text-muted-foreground text-xs">5 hours ago</p>
                      </div>
                    </div>
                    <div className="flex items-start space-x-3">
                      <div className="w-2 h-2 bg-purple-500 rounded-full mt-2"></div>
                      <div className="flex-1">
                        <p>Course published</p>
                        <p className="text-muted-foreground text-xs">1 day ago</p>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
      </div>
    </ProtectedRoute>
  )
}
