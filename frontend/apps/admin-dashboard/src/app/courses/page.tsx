'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute } from '@edudron/ui-components'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Plus, Sparkles, BookOpen, Users, Video, Trash2, Edit, Loader2 } from 'lucide-react'
import { coursesApi } from '@/lib/api'
import type { Course } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

export default function CoursesPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'published' | 'draft'>('all')

  useEffect(() => {
    loadCourses()
  }, [])

  useEffect(() => {
    filterCourses()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courses, searchQuery, statusFilter])

  const loadCourses = async () => {
    try {
      const data = await coursesApi.listCourses()
      setCourses(data)
      setFilteredCourses(data)
    } catch (error) {
      console.error('Failed to load courses:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to load courses',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }

  const filterCourses = () => {
    let filtered = [...courses]

    // Search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase()
      filtered = filtered.filter(
        (course) =>
          course.title.toLowerCase().includes(query) ||
          course.description?.toLowerCase().includes(query)
      )
    }

    // Status filter
    if (statusFilter === 'published') {
      filtered = filtered.filter((course) => course.isPublished)
    } else if (statusFilter === 'draft') {
      filtered = filtered.filter((course) => !course.isPublished)
    }

    setFilteredCourses(filtered)
  }

  const handleDelete = async (courseId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (confirm('Are you sure you want to delete this course?')) {
      try {
        await coursesApi.deleteCourse(courseId)
        toast({
          title: 'Course deleted',
          description: 'The course has been deleted successfully.',
        })
        await loadCourses()
      } catch (error) {
        console.error('Failed to delete course:', error)
        toast({
          variant: 'destructive',
          title: 'Failed to delete course',
          description: extractErrorMessage(error),
        })
      }
    }
  }

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER']}>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="bg-white shadow-sm border-b border-gray-200">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <div className="flex items-center space-x-8">
                <h1
                  className="text-2xl font-bold text-blue-600 cursor-pointer"
                  onClick={() => router.push('/dashboard')}
                >
                  EduDron Admin
                </h1>
                <nav className="hidden md:flex space-x-6">
                  <button
                    onClick={() => router.push('/dashboard')}
                    className="text-gray-700 hover:text-blue-600"
                  >
                    Dashboard
                  </button>
                  <button
                    onClick={() => router.push('/courses')}
                    className="text-gray-700 hover:text-blue-600 font-medium"
                  >
                    Courses
                  </button>
                  <button
                    onClick={() => router.push('/batches')}
                    className="text-gray-700 hover:text-blue-600"
                  >
                    Batches
                  </button>
                </nav>
              </div>
            </div>
          </div>
        </header>

        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          {/* Page Header */}
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h2 className="text-3xl font-bold text-gray-900 mb-2">Course Management</h2>
              <p className="text-gray-600">Create and manage your courses</p>
            </div>
            <div className="flex gap-2">
              <Button 
                variant="outline"
                onClick={() => router.push('/courses/generate')}
              >
                <Sparkles className="w-5 h-5 mr-2" />
                Generate with AI
              </Button>
              <Button onClick={() => router.push('/courses/new')}>
                <Plus className="w-5 h-5 mr-2" />
                Create Course
              </Button>
            </div>
          </div>

          {/* Filters */}
          <div className="mb-6 flex flex-col sm:flex-row gap-4">
            <div className="flex-1">
              <Input
                placeholder="Search courses..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <div className="flex space-x-2">
              <Button
                variant={statusFilter === 'all' ? 'default' : 'outline'}
                size="sm"
                onClick={() => setStatusFilter('all')}
              >
                All
              </Button>
              <Button
                variant={statusFilter === 'published' ? 'default' : 'outline'}
                size="sm"
                onClick={() => setStatusFilter('published')}
              >
                Published
              </Button>
              <Button
                variant={statusFilter === 'draft' ? 'default' : 'outline'}
                size="sm"
                onClick={() => setStatusFilter('draft')}
              >
                Drafts
              </Button>
            </div>
          </div>

          {/* Results Count */}
          <div className="mb-4">
            <p className="text-sm text-gray-600">
              {filteredCourses.length} course{filteredCourses.length !== 1 ? 's' : ''} found
            </p>
          </div>

          {/* Course List */}
          {loading ? (
            <Card>
              <CardContent className="py-12">
                <div className="text-center">
                  <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
                </div>
              </CardContent>
            </Card>
          ) : filteredCourses.length === 0 ? (
            <Card>
              <CardContent className="text-center py-12">
                <BookOpen className="mx-auto h-12 w-12 text-muted-foreground" />
                <h3 className="mt-2 text-sm font-medium">No courses</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Get started by creating a new course.
                </p>
                <div className="mt-6">
                  <Button onClick={() => router.push('/courses/new')}>
                    <Plus className="h-4 w-4 mr-2" />
                    Create Course
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-4">
              {filteredCourses.map((course) => (
                <Card
                  key={course.id}
                  className="hover:shadow-md transition-shadow cursor-pointer"
                  onClick={() => router.push(`/courses/${course.id}`)}
                >
                  <CardContent className="p-6">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-3 mb-2">
                          <h3 className="text-lg font-semibold">{course.title}</h3>
                          <Badge variant={course.isPublished ? 'default' : 'secondary'}>
                            {course.isPublished ? 'Published' : 'Draft'}
                          </Badge>
                        </div>
                        {course.description && (
                          <p className="text-sm text-muted-foreground mb-3 line-clamp-2">
                            {course.description}
                          </p>
                        )}
                        <div className="flex items-center space-x-6 text-sm text-muted-foreground">
                          {course.totalStudentsCount !== undefined && (
                            <span className="flex items-center">
                              <Users className="w-4 h-4 mr-1" />
                              {course.totalStudentsCount} students
                            </span>
                          )}
                          {course.totalLecturesCount !== undefined && (
                            <span className="flex items-center">
                              <Video className="w-4 h-4 mr-1" />
                              {course.totalLecturesCount} lectures
                            </span>
                          )}
                          {course.difficultyLevel && (
                            <Badge variant="outline">
                              {course.difficultyLevel}
                            </Badge>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center space-x-2 ml-4">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation()
                            router.push(`/courses/${course.id}`)
                          }}
                        >
                          <Edit className="h-4 w-4 mr-1" />
                          Edit
                        </Button>
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={(e) => handleDelete(course.id, e)}
                        >
                          <Trash2 className="h-4 w-4 mr-1" />
                          Delete
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </main>
      </div>
    </ProtectedRoute>
  )
}
