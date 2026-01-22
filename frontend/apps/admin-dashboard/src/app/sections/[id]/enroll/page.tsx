'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ArrowLeft, Loader2, Users } from 'lucide-react'
import { enrollmentsApi, sectionsApi, classesApi, coursesApi, institutesApi } from '@/lib/api'
import type { Section, Class, Course, Institute, BulkEnrollmentResult } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'

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

export default function SectionEnrollPage() {
  const router = useRouter()
  const params = useParams()
  const sectionId = params.id as string
  const { toast } = useToast()
  const [section, setSection] = useState<Section | null>(null)
  const [classItem, setClassItem] = useState<Class | null>(null)
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [courses, setCourses] = useState<Course[]>([])
  const [enrolledCourseIds, setEnrolledCourseIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [processingCourseId, setProcessingCourseId] = useState<string | null>(null)
  const [result, setResult] = useState<BulkEnrollmentResult | null>(null)

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      const [sectionData, coursesData] = await Promise.all([
        sectionsApi.getSection(sectionId),
        coursesApi.listCourses(),
      ])
      
      setSection(sectionData)
      
      // Load class and institute
      const classData = await classesApi.getClass(sectionData.classId)
      setClassItem(classData)
      
      const instituteData = await institutesApi.getInstitute(classData.instituteId)
      setInstitute(instituteData)
      
      // Filter courses to only show those assigned to this section or its class
      const assignedCourses = coursesData.filter(c => 
        c.isPublished && 
        c.status !== 'ARCHIVED' &&
        (c.assignedToSectionIds?.includes(sectionId) || 
         c.assignedToClassIds?.includes(classData.id) || false)
      )
      setCourses(assignedCourses)

      // Load existing enrollments to see which courses are already enrolled
      try {
        const enrollments = await enrollmentsApi.listEnrollments()
        const sectionEnrollments = enrollments.filter(e => e.batchId === sectionId)
        const enrolledIds = new Set(sectionEnrollments.map(e => e.courseId))
        setEnrolledCourseIds(enrolledIds)
      } catch (err) {
        console.error('Error loading existing enrollments:', err)
        // Continue without pre-populating enrolled courses
      }
    } catch (err: any) {
      console.error('Error loading data:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load data',
        description: errorMessage,
      })
    } finally {
      setLoading(false)
    }
  }, [sectionId, toast])

  useEffect(() => {
    if (sectionId) {
      loadData()
    }
  }, [sectionId, loadData])

  const handleCourseToggle = async (courseId: string, isCurrentlyEnrolled: boolean) => {
    if (processingCourseId === courseId) return // Prevent double-clicks

    setProcessingCourseId(courseId)
    try {
      if (isCurrentlyEnrolled) {
        // Unenroll from course
        const unenrollResult = await enrollmentsApi.unenrollSectionFromCourse(sectionId, courseId)
        
        // Update enrolled courses set
        setEnrolledCourseIds(prev => {
          const newSet = new Set(prev)
          newSet.delete(courseId)
          return newSet
        })

        toast({
          title: 'Unenrollment completed',
          description: `Successfully unenrolled ${unenrollResult.totalStudents - unenrollResult.failedStudents} students from the course`,
        })

        if (unenrollResult.failedStudents > 0) {
          toast({
            variant: 'destructive',
            title: 'Some unenrollments failed',
            description: `${unenrollResult.failedStudents} students could not be unenrolled`,
          })
        }
      } else {
        // Enroll in course
        const enrollResult = await enrollmentsApi.enrollSectionToCourse(sectionId, courseId)
        
        // Update enrolled courses set
        setEnrolledCourseIds(prev => new Set(prev).add(courseId))

        toast({
          title: 'Enrollment completed',
          description: `Successfully enrolled ${enrollResult.enrolledStudents} students in the course`,
        })

        if (enrollResult.failedStudents > 0) {
          toast({
            variant: 'destructive',
            title: 'Some enrollments failed',
            description: `${enrollResult.failedStudents} students could not be enrolled`,
          })
        }
      }
    } catch (err: any) {
      console.error(`Error ${isCurrentlyEnrolled ? 'unenrolling' : 'enrolling'} section:`, err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: `${isCurrentlyEnrolled ? 'Unenrollment' : 'Enrollment'} failed`,
        description: errorMessage,
      })
    } finally {
      setProcessingCourseId(null)
    }
  }

  if (loading) {
    return (
      <div className="container mx-auto py-8 px-4">
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
        </div>
      </div>
    )
  }

  if (!section || !classItem) {
    return (
      <div className="container mx-auto py-8 px-4">
        <p>Section not found</p>
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 px-4">
      <div className="mb-6">
        <Link href={`/sections/${sectionId}`}>
          <Button variant="ghost" size="sm" className="mb-4">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to Section
            </Button>
          </Link>
          <div className="mb-6">
            <p className="text-gray-600">
            {institute?.name} - {classItem.name} - {section.name}
          </p>
        </div>

        <div className="grid gap-6">
          {/* Section Info */}
          <Card>
            <CardHeader>
              <CardTitle>Section Information</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <Label className="text-sm text-gray-500">Students</Label>
                  <p className="text-lg font-semibold">{section.studentCount || 0}</p>
                </div>
                <div>
                  <Label className="text-sm text-gray-500">Max Students</Label>
                  <p className="text-lg font-semibold">{section.maxStudents || 'N/A'}</p>
                </div>
                <div>
                  <Label className="text-sm text-gray-500">Status</Label>
                  <Badge variant={section.isActive ? 'default' : 'secondary'}>
                    {section.isActive ? 'Active' : 'Inactive'}
                  </Badge>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Course Selection */}
          <Card>
            <CardHeader>
              <CardTitle>Manage Course Enrollments</CardTitle>
              <CardDescription>
                Toggle courses to enroll or unenroll all students in this section. Only courses assigned to this section or its class are shown.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {courses.length === 0 ? (
                <div className="space-y-2">
                  <p className="text-gray-500">No courses assigned to this section or its class.</p>
                  <p className="text-sm text-gray-400">
                    Assign courses to this section or class from the course edit page first.
                  </p>
                </div>
              ) : (
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {courses.map((course) => {
                    const isEnrolled = enrolledCourseIds.has(course.id)
                    const isProcessing = processingCourseId === course.id
                    return (
                      <div
                        key={course.id}
                        className={`flex items-center space-x-2 p-3 border rounded-lg hover:bg-gray-50 ${
                          isProcessing ? 'opacity-50 cursor-wait' : 'cursor-pointer'
                        }`}
                        onClick={() => !isProcessing && handleCourseToggle(course.id, isEnrolled)}
                      >
                        {isProcessing ? (
                          <Loader2 className="h-4 w-4 animate-spin text-primary" />
                        ) : (
                          <input
                            type="checkbox"
                            checked={isEnrolled}
                            onChange={() => handleCourseToggle(course.id, isEnrolled)}
                            className="rounded"
                            disabled={isProcessing}
                          />
                        )}
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <p className="font-medium">{course.title}</p>
                            {isEnrolled && (
                              <Badge variant="default" className="text-xs">Enrolled</Badge>
                            )}
                          </div>
                          {course.description && (
                            <p className="text-sm text-gray-500 line-clamp-1">
                              {toPlainText(course.description)}
                            </p>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </CardContent>
          </Card>

        </div>
      </div>
    </div>
  )
}

