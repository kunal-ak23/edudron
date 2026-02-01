'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
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
import { coursesApi, apiClient } from '@/lib/api'
import type { Course } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

interface InstructorAccess {
  allowedClassIds: string[]
  allowedSectionIds: string[]
  allowedCourseIds: string[]
}

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

// Utility function to convert HTML or markdown to plain text for list display
function toPlainText(content: string): string {
  if (!content) return ''
  
  let text = content
  
  // Remove script and style tags (HTML)
  text = text.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
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
  
  // Remove markdown syntax
  // Headers (# ## ###)
  text = text.replace(/^#{1,6}\s+/gm, '')
  // Bold (**text** or __text__)
  text = text.replace(/\*\*([^*]+)\*\*/g, '$1')
  text = text.replace(/__([^_]+)__/g, '$1')
  // Italic (*text* or _text_)
  text = text.replace(/\*([^*]+)\*/g, '$1')
  text = text.replace(/_([^_]+)_/g, '$1')
  // Code (`code`)
  text = text.replace(/`([^`]+)`/g, '$1')
  // Links [text](url)
  text = text.replace(/\[([^\]]+)\]\([^\)]+\)/g, '$1')
  // Images ![alt](url)
  text = text.replace(/!\[([^\]]*)\]\([^\)]+\)/g, '$1')
  // Lists (- or * or 1.)
  text = text.replace(/^[\s]*[-*+]\s+/gm, '')
  text = text.replace(/^[\s]*\d+\.\s+/gm, '')
  // Blockquotes (>)
  text = text.replace(/^>\s+/gm, '')
  // Code blocks (```)
  text = text.replace(/```[\s\S]*?```/g, '')
  // Horizontal rules (--- or ***)
  text = text.replace(/^[-*]{3,}$/gm, '')
  
  // Clean up whitespace
  text = text.replace(/\s+/g, ' ')
  text = text.trim()
  
  return text
}

export default function CoursesPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user } = useAuth()
  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'published' | 'draft'>('all')
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [courseToDelete, setCourseToDelete] = useState<string | null>(null)
  
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const canManageContent = !isInstructor && !isSupportStaff
  const canUseAI = user?.role === 'SYSTEM_ADMIN' || user?.role === 'TENANT_ADMIN'
  
  // Ref to track if initial load has been triggered (prevents duplicate calls)
  const loadTriggeredRef = useRef(false)
  
  const loadCourses = useCallback(async (forceReload = false) => {
    // Skip if already loaded, unless force reload
    if (loadTriggeredRef.current && !forceReload) {
      return
    }
    loadTriggeredRef.current = true
    setLoading(true)
    
    try {
      // For instructors, fetch their allowed course IDs first
      let allowedCourseIds: Set<string> | null = null
      
      if ((isInstructor || isSupportStaff) && user?.id) {
        try {
          const accessResponse = await apiClient.get<InstructorAccess>(`/api/instructor-assignments/instructor/${user.id}/access`)
          allowedCourseIds = new Set(accessResponse.data.allowedCourseIds || [])
        } catch (err) {
          console.error('Failed to load instructor access:', err)
          allowedCourseIds = new Set()
        }
      }
      
      let data = await coursesApi.listCourses()
      
      // Filter courses for instructors
      if (allowedCourseIds !== null) {
        data = data.filter(course => allowedCourseIds!.has(course.id))
      }
      
      setCourses(data)
      setFilteredCourses(data)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load courses',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }, [toast, isInstructor, isSupportStaff, user?.id])

  const filterCourses = useCallback(() => {
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
  }, [courses, searchQuery, statusFilter])

  // Load courses once when user is ready
  useEffect(() => {
    // For instructors/support staff, wait for user.id
    // For others, just need user to be defined
    const userReady = user !== undefined && ((!isInstructor && !isSupportStaff) || user?.id)
    if (userReady) {
      loadCourses()
    }
  }, [user?.id, isInstructor, isSupportStaff]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    filterCourses()
  }, [courses, searchQuery, statusFilter, filterCourses])

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
      setShowDeleteDialog(false)
      setCourseToDelete(null)
      // Reload courses after delete
      loadCourses(true)
    } catch (error) {
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
              {canManageContent && (
                <>
                  {canUseAI && (
                    <Button 
                      variant="outline"
                      onClick={() => router.push('/courses/generate')}
                    >
                      <Sparkles className="w-5 h-5 mr-2" />
                      Generate with AI
                    </Button>
                  )}
                  <Button onClick={() => router.push('/courses/new')}>
                    <Plus className="w-5 h-5 mr-2" />
                    Create Course
                  </Button>
                </>
              )}
              {(isInstructor || isSupportStaff) && (
                <p className="text-sm text-muted-foreground">
                  View-only access: You can view courses but cannot create or edit them
                </p>
              )}
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
                  {canManageContent ? 'Get started by creating a new course.' : 'No courses available to view.'}
                </p>
                {canManageContent && (
                  <div className="mt-6">
                    <Button onClick={() => router.push('/courses/new')}>
                      <Plus className="h-4 w-4 mr-2" />
                      Create Course
                    </Button>
                  </div>
                )}
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
                            {toPlainText(course.description)}
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
                      {canManageContent && (
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
                      )}
                      {(isInstructor || isSupportStaff) && (
                        <div className="ml-4">
                          <span className="text-sm text-muted-foreground">View only</span>
                        </div>
                      )}
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
