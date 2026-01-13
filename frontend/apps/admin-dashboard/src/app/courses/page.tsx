'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@edudron/shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Plus, Sparkles, BookOpen, Users, Video, Trash2, Edit, Loader2 } from 'lucide-react'
import { coursesApi } from '@/lib/api'
import type { Course } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

// Utility function to strip HTML tags and convert to plain text
function stripHtmlTags(html: string): string {
  if (!html) return ''
  
  // Remove script and style tags
  let text = html.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
  text = text.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
  
  // Replace common HTML entities
  text = text.replace(/&nbsp;/g, ' ')
  text = text.replace(/&amp;/g, '&')
  text = text.replace(/&lt;/g, '<')
  text = text.replace(/&gt;/g, '>')
  text = text.replace(/&quot;/g, '"')
  text = text.replace(/&#39;/g, "'")
  text = text.replace(/&apos;/g, "'")
  
  // Remove HTML tags
  text = text.replace(/<[^>]+>/g, '')
  
  // Clean up whitespace
  text = text.replace(/\s+/g, ' ')
  text = text.trim()
  
  return text
}

export default function CoursesPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'published' | 'draft'>('all')
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [courseToDelete, setCourseToDelete] = useState<string | null>(null)

  useEffect(() => {
    loadCourses()
  }, [])

  useEffect(() => {
    console.log('[CoursesPage] Filtering courses - courses:', courses.length, 'searchQuery:', searchQuery, 'statusFilter:', statusFilter)
    filterCourses()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courses, searchQuery, statusFilter])

  const loadCourses = async () => {
    try {
      console.log('[CoursesPage] Starting to load courses...')
      console.log('[CoursesPage] coursesApi:', coursesApi)
      console.log('[CoursesPage] coursesApi.listCourses:', coursesApi.listCourses)
      
      // Make the API call and log the raw response
      console.log('[CoursesPage] Calling coursesApi.listCourses()...')
      const data = await coursesApi.listCourses()
      
      console.log('[CoursesPage] ✅ API call completed')
      console.log('[CoursesPage] Received data from API:', data)
      console.log('[CoursesPage] Data type:', typeof data)
      console.log('[CoursesPage] Is array?:', Array.isArray(data))
      console.log('[CoursesPage] Data length:', Array.isArray(data) ? data.length : 'N/A')
      console.log('[CoursesPage] Full data:', JSON.stringify(data, null, 2))
      
      if (!Array.isArray(data)) {
        console.error('[CoursesPage] ❌ Data is not an array! Type:', typeof data, 'Value:', data)
      }
      
      setCourses(data)
      setFilteredCourses(data)
      console.log('[CoursesPage] ✅ State updated - courses:', data.length, 'filteredCourses:', data.length)
    } catch (error) {
      console.error('[CoursesPage] ❌ Failed to load courses:', error)
      console.error('[CoursesPage] Error type:', error?.constructor?.name)
      console.error('[CoursesPage] Error details:', {
        message: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
        response: (error as any)?.response?.data,
        fullError: error
      })
      toast({
        variant: 'destructive',
        title: 'Failed to load courses',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
      console.log('[CoursesPage] Loading finished')
    }
  }

  const filterCourses = () => {
    console.log('[CoursesPage.filterCourses] Starting filter - courses:', courses.length)
    let filtered = [...courses]
    console.log('[CoursesPage.filterCourses] Initial filtered count:', filtered.length)

    // Search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase()
      console.log('[CoursesPage.filterCourses] Applying search filter:', query)
      filtered = filtered.filter(
        (course) =>
          course.title.toLowerCase().includes(query) ||
          course.description?.toLowerCase().includes(query)
      )
      console.log('[CoursesPage.filterCourses] After search filter:', filtered.length)
    }

    // Status filter
    if (statusFilter === 'published') {
      console.log('[CoursesPage.filterCourses] Applying published filter')
      filtered = filtered.filter((course) => course.isPublished)
      console.log('[CoursesPage.filterCourses] After published filter:', filtered.length)
    } else if (statusFilter === 'draft') {
      console.log('[CoursesPage.filterCourses] Applying draft filter')
      filtered = filtered.filter((course) => !course.isPublished)
      console.log('[CoursesPage.filterCourses] After draft filter:', filtered.length)
    }

    console.log('[CoursesPage.filterCourses] Final filtered count:', filtered.length)
    console.log('[CoursesPage.filterCourses] Filtered courses:', filtered.map(c => ({ id: c.id, title: c.title, isPublished: c.isPublished })))
    setFilteredCourses(filtered)
  }

  const handleDeleteClick = (courseId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    setCourseToDelete(courseId)
    setShowDeleteDialog(true)
  }

  const handleDeleteConfirm = async () => {
    if (!courseToDelete) return
    
    try {
      await coursesApi.deleteCourse(courseToDelete)
      toast({
        title: 'Course deleted',
        description: 'The course has been deleted successfully.',
      })
      await loadCourses()
      setShowDeleteDialog(false)
      setCourseToDelete(null)
    } catch (error) {
      console.error('Failed to delete course:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to delete course',
        description: extractErrorMessage(error),
      })
    }
  }

  return (
    
      <div className="space-y-3">
          {/* Page Header */}
          <div className="flex items-center justify-between">
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
          <div className="mb-3 flex flex-col sm:flex-row gap-4">
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
                  <CardContent className="p-4">
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
                            {stripHtmlTags(course.description)}
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
                          onClick={(e) => handleDeleteClick(course.id, e)}
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

      {/* Delete Confirmation Dialog */}
      <Dialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Course</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete this course? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowDeleteDialog(false)
                setCourseToDelete(null)
              }}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteConfirm}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      </div>
  )
}
