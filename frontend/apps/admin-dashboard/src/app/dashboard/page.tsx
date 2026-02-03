'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
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
import { apiClient, coursesApi, enrollmentsApi } from '@/lib/api'
import type { Course, Batch } from '@kunal-ak23/edudron-shared-utils'

interface InstructorAccess {
  allowedClassIds: string[]
  allowedSectionIds: string[]
  allowedCourseIds: string[]
}

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

type ActivityDotColor = 'blue' | 'green' | 'purple' | 'orange'
interface ActivityItem {
  id: string
  dotColor: ActivityDotColor
  title: string
  subtitle: string
  timestampMs: number
}

function safeDateMs(dateStr?: string): number {
  if (!dateStr) return 0
  const ms = new Date(dateStr).getTime()
  return Number.isFinite(ms) ? ms : 0
}

function timeAgo(dateStr?: string): string {
  const ms = safeDateMs(dateStr)
  if (!ms) return '—'
  const diffMs = Date.now() - ms
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins} minute${diffMins === 1 ? '' : 's'} ago`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours} hour${diffHours === 1 ? '' : 's'} ago`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays} day${diffDays === 1 ? '' : 's'} ago`
}

function computeSeatUtilization(batches: Batch[]): number | null {
  const withCapacity = batches.filter((b) => {
    const cap = b.capacity ?? b.maxStudents ?? 0
    return typeof cap === 'number' && cap > 0
  })
  if (withCapacity.length === 0) return null
  const totalCapacity = withCapacity.reduce((sum, b) => sum + (b.capacity ?? b.maxStudents ?? 0), 0)
  if (totalCapacity <= 0) return null
  const totalEnrolled = withCapacity.reduce((sum, b) => sum + (b.enrolledCount ?? b.studentCount ?? 0), 0)
  return Math.max(0, Math.min(100, (totalEnrolled / totalCapacity) * 100))
}

export default function DashboardPage() {
  const [mounted, setMounted] = useState(false)
  const router = useRouter()
  const { user: authUser, isAuthenticated } = useAuth()
  const [courses, setCourses] = useState<Course[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [activeClassCount, setActiveClassCount] = useState<number | null>(null)
  const [activeSectionCount, setActiveSectionCount] = useState<number | null>(null)
  const [studentCount, setStudentCount] = useState<number | null>(null)
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
    setLoading(true)
    try {
      const userStr = localStorage.getItem('user')
      let parsedUser: { id?: string; name?: string; role?: string } | null = null
      if (userStr) {
        try {
          parsedUser = JSON.parse(userStr)
          setUser(parsedUser)
        } catch {
          setUser(null)
        }
      }

      const isInstructor = parsedUser?.role === 'INSTRUCTOR'
      const isSupportStaff = parsedUser?.role === 'SUPPORT_STAFF'
      const useInstructorScope = (isInstructor || isSupportStaff) && parsedUser?.id

      if (useInstructorScope) {
        // Instructor/scoped path: fetch access, then load and filter courses and batches
        let access: InstructorAccess | null = null
        try {
          const accessRes = await apiClient.get<InstructorAccess>(
            `/api/instructor-assignments/instructor/${parsedUser!.id}/access`
          )
          access = accessRes.data
        } catch (err) {
          console.error('Failed to load instructor access:', err)
          access = { allowedClassIds: [], allowedSectionIds: [], allowedCourseIds: [] }
        }

        const allowedCourseIds = new Set(access?.allowedCourseIds ?? [])
        const allowedSectionIds = new Set(access?.allowedSectionIds ?? [])
        const allowedClassIds = access?.allowedClassIds ?? []

        const [coursesRes, batchesRes] = await Promise.all([
          coursesApi.listCourses(),
          enrollmentsApi.listBatches(),
        ])

        const coursesData =
          allowedCourseIds.size > 0
            ? coursesRes.filter((c) => allowedCourseIds.has(c.id))
            : []
        const batchesData =
          allowedSectionIds.size > 0
            ? batchesRes.filter((b) => allowedSectionIds.has(b.id))
            : []

        const resolvedActiveClassCount = allowedClassIds.length
        const resolvedActiveSectionCount = allowedSectionIds.size
        const resolvedStudentCount = batchesData.reduce(
          (sum, b) => sum + (b.enrolledCount ?? b.studentCount ?? 0),
          0
        )

        setCourses(coursesData)
        setBatches(batchesData)
        setStudentCount(resolvedStudentCount)
        setActiveClassCount(resolvedActiveClassCount)
        setActiveSectionCount(resolvedActiveSectionCount)
      } else {
        // Non-instructor path: tenant-wide data
        const results = await Promise.allSettled([
          coursesApi.listCourses(),
          enrollmentsApi.listBatches(),
          apiClient.get<number>('/idp/users/role/STUDENT/count?active=true'),
          apiClient.get<number>('/api/classes/count?active=true'),
          apiClient.get<number>('/api/sections/count?active=true'),
        ])

        const coursesData = results[0].status === 'fulfilled' ? results[0].value : []
        const batchesData = results[1].status === 'fulfilled' ? results[1].value : []

        let resolvedStudentCount: number | null = null
        if (results[2].status === 'fulfilled') {
          const raw = results[2].value?.data
          if (typeof raw === 'number' && Number.isFinite(raw)) {
            resolvedStudentCount = raw
          }
        }

        let resolvedActiveClassCount: number | null = null
        if (results[3].status === 'fulfilled') {
          const raw = results[3].value?.data
          if (typeof raw === 'number' && Number.isFinite(raw)) {
            resolvedActiveClassCount = raw
          }
        }

        let resolvedActiveSectionCount: number | null = null
        if (results[4].status === 'fulfilled') {
          const raw = results[4].value?.data
          if (typeof raw === 'number' && Number.isFinite(raw)) {
            resolvedActiveSectionCount = raw
          }
        }

        setCourses(coursesData)
        setBatches(batchesData)
        setStudentCount(resolvedStudentCount)
        setActiveClassCount(resolvedActiveClassCount)
        setActiveSectionCount(resolvedActiveSectionCount)
      }
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

  // Role-based access control
  useEffect(() => {
    if (!isAuthenticated() || !authUser) {
      router.push('/login')
      return
    }
    
    const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER', 'INSTRUCTOR']
    if (!allowedRoles.includes(authUser.role)) {
      router.push('/unauthorized')
    }
  }, [authUser, isAuthenticated, router])

  if (!authUser || !isAuthenticated()) {
    return null
  }

  const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER', 'INSTRUCTOR']
  if (!allowedRoles.includes(authUser.role)) {
    return null
  }

  const isInstructorView = authUser.role === 'INSTRUCTOR' || authUser.role === 'SUPPORT_STAFF'

  const publishedCourses = courses.filter(c => c.isPublished)
  const draftCourses = courses.filter(c => !c.isPublished)
  // "Batches" in the org's terminology maps to "Sections" in the backend.
  const activeBatches = batches.filter(b => b.isActive)
  const totalBatches = batches.length
  const totalStudentsFromBatches = batches.reduce((sum, batch) => sum + (batch.enrolledCount ?? batch.studentCount ?? 0), 0)
  const totalStudents = studentCount ?? totalStudentsFromBatches
  const seatUtilization = computeSeatUtilization(batches)

  const recentCourses = [...courses]
    .sort((a, b) => safeDateMs(b.updatedAt || b.createdAt) - safeDateMs(a.updatedAt || a.createdAt))
    .slice(0, 5)

  const recentActivity: ActivityItem[] = (() => {
    const items: ActivityItem[] = []

    for (const c of courses) {
      items.push({
        id: `course-created:${c.id}`,
        dotColor: 'blue',
        title: `Course created: ${c.title}`,
        subtitle: timeAgo(c.createdAt),
        timestampMs: safeDateMs(c.createdAt),
      })

      if (c.isPublished) {
        items.push({
          id: `course-published:${c.id}`,
          dotColor: 'purple',
          title: `Course published: ${c.title}`,
          subtitle: timeAgo(c.updatedAt),
          timestampMs: safeDateMs(c.updatedAt),
        })
      }
    }

    for (const b of batches) {
      items.push({
        id: `batch-created:${b.id}`,
        dotColor: 'green',
        title: `Batch created: ${b.name}`,
        subtitle: timeAgo(b.createdAt),
        timestampMs: safeDateMs(b.createdAt),
      })
    }

    return items
      .filter(i => i.timestampMs > 0)
      .sort((a, b) => b.timestampMs - a.timestampMs)
      .slice(0, 5)
  })()

  if (!mounted) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            Welcome back{user?.name || authUser?.name ? `, ${user?.name || authUser?.name}` : ''}.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={handleLogout}>
          <LogOut className="w-4 h-4 mr-2" />
          Logout
        </Button>
      </div>

          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-3">
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
                  <h3 className="text-sm font-medium text-green-100">Batches</h3>
                  <Users className="w-6 h-6 text-green-200" />
                </div>
                <p className="text-3xl font-bold">{loading ? '...' : totalBatches}</p>
                <p className="text-sm text-green-100 mt-1">
                  {isInstructorView
                    ? totalBatches === 0 && !loading
                      ? 'You have no section assignments yet'
                      : 'Sections you\'re assigned to'
                    : loading
                      ? '...'
                      : `${activeBatches.length} active${activeBatches.length !== totalBatches ? `, ${totalBatches - activeBatches.length} inactive` : ''}`}
                </p>
              </CardContent>
            </Card>

            <Card className="bg-gradient-to-br from-indigo-500 to-indigo-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-indigo-100">Classes</h3>
                  <Users className="w-6 h-6 text-indigo-200" />
                </div>
                <p className="text-3xl font-bold">{loading ? '...' : activeClassCount ?? '—'}</p>
                <p className="text-sm text-indigo-100 mt-1">
                  {isInstructorView
                    ? (activeClassCount === 0 || activeClassCount === null) && !loading
                      ? 'You have no class assignments yet'
                      : 'Classes you\'re assigned to'
                    : 'Active in tenant'}
                </p>
              </CardContent>
            </Card>

            <Card className="bg-gradient-to-br from-purple-500 to-purple-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-purple-100">Total Students</h3>
                  <GraduationCap className="w-6 h-6 text-purple-200" />
                </div>
                <p className="text-3xl font-bold">{loading ? '...' : totalStudents.toLocaleString()}</p>
                <p className="text-sm text-purple-100 mt-1">
                  {isInstructorView
                    ? 'Enrolled in your sections'
                    : studentCount !== null
                      ? 'Students in tenant'
                      : 'Enrolled across all batches'}
                </p>
              </CardContent>
            </Card>

            <Card className="bg-gradient-to-br from-orange-500 to-orange-600 text-white border-0">
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-medium text-orange-100">Seat Utilization</h3>
                  <BarChart3 className="w-6 h-6 text-orange-200" />
                </div>
                <p className="text-3xl font-bold">
                  {loading ? '...' : seatUtilization === null ? '—' : `${Math.round(seatUtilization)}%`}
                </p>
                <p className="text-sm text-orange-100 mt-1">
                  {isInstructorView ? 'Across your sections' : 'Across batches with capacity set'}
                </p>
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
                    <p>
                      {isInstructorView
                        ? 'No courses in your assigned classes or sections yet.'
                        : 'No courses yet'}
                    </p>
                    {!isInstructorView && (
                      <Button
                        className="mt-4"
                        onClick={() => router.push('/courses/new')}
                      >
                        Create Your First Course
                      </Button>
                    )}
                  </div>
                ) : (
                  <div className="space-y-4">
                    {recentCourses.map((course) => (
                      <div
                        key={course.id}
                        className="flex items-center justify-between p-4 border rounded-lg hover:bg-gray-50 hover:shadow-sm cursor-pointer transition-all"
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
                        {!isInstructorView && (
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
                        )}
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
                    {!isInstructorView && (
                      <>
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
                      </>
                    )}
                    {(authUser?.role === 'SYSTEM_ADMIN' || authUser?.role === 'TENANT_ADMIN') && (
                      <Button
                        className="w-full justify-start"
                        variant="outline"
                        onClick={() => router.push('/courses/generate')}
                      >
                        <Sparkles className="w-5 h-5 mr-2" />
                        Generate Course with AI
                      </Button>
                    )}
                    <Button
                      className="w-full justify-start"
                      variant="outline"
                      onClick={() => router.push('/course-index')}
                    >
                      <FileText className="w-5 h-5 mr-2" />
                      Manage Course Index
                    </Button>
                    {!isInstructorView && (
                      <Button
                        className="w-full justify-start"
                        variant="outline"
                        onClick={() => router.push('/users')}
                      >
                        <UserCog className="w-5 h-5 mr-2" />
                        Manage Users
                      </Button>
                    )}
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
                  {loading ? (
                    <div className="space-y-3 text-sm">
                      {[...Array(3)].map((_, i) => (
                        <div key={i} className="flex items-start space-x-3 animate-pulse">
                          <div className="w-2 h-2 bg-gray-200 rounded-full mt-2"></div>
                          <div className="flex-1">
                            <div className="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
                            <div className="h-3 bg-gray-200 rounded w-1/3"></div>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : recentActivity.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No recent activity found yet.</p>
                  ) : (
                    <div className="space-y-3 text-sm">
                      {recentActivity.map((item) => (
                        <div key={item.id} className="flex items-start space-x-3">
                          <div
                            className={[
                              'w-2 h-2 rounded-full mt-2',
                              item.dotColor === 'blue' ? 'bg-blue-500' : '',
                              item.dotColor === 'green' ? 'bg-green-500' : '',
                              item.dotColor === 'purple' ? 'bg-purple-500' : '',
                              item.dotColor === 'orange' ? 'bg-orange-500' : '',
                            ].join(' ')}
                          />
                          <div className="flex-1">
                            <p>{item.title}</p>
                            <p className="text-muted-foreground text-xs">{item.subtitle}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
      </div>
  )
}
