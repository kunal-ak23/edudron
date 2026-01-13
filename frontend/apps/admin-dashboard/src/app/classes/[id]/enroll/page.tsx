'use client'

import { useState, useEffect } from 'react'
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ArrowLeft, Loader2, CheckCircle2, XCircle, Users } from 'lucide-react'
import { enrollmentsApi, classesApi, coursesApi, sectionsApi, institutesApi } from '@/lib/api'
import type { Class, Course, Section, Institute, BulkEnrollmentResult } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'

export default function ClassEnrollPage() {
  const router = useRouter()
  const params = useParams()
  const classId = params.id as string
  const { toast } = useToast()
  const [classItem, setClassItem] = useState<Class | null>(null)
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [sections, setSections] = useState<Section[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [selectedCourseIds, setSelectedCourseIds] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [enrolling, setEnrolling] = useState(false)
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)
  const [result, setResult] = useState<BulkEnrollmentResult | null>(null)
  const [studentCount, setStudentCount] = useState(0)

  useEffect(() => {
    if (classId) {
      loadData()
    }
  }, [classId])

  const loadData = async () => {
    try {
      setLoading(true)
      const [classData, coursesData] = await Promise.all([
        classesApi.getClass(classId),
        coursesApi.listCourses(),
      ])
      
      setClassItem(classData)
      
      // Load institute
      const instituteData = await institutesApi.getInstitute(classData.instituteId)
      setInstitute(instituteData)
      
      // Load sections
      const sectionsData = await sectionsApi.listSectionsByClass(classId)
      setSections(sectionsData)
      
      // Show all published courses - backend will validate assignment
      setCourses(coursesData.filter(c => c.isPublished && c.status !== 'ARCHIVED'))
      
      // Estimate student count (students with enrollments in this class)
      // This is an approximation - actual count would require API call
      setStudentCount(sectionsData.reduce((sum, s) => sum + (s.studentCount || 0), 0))
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
  }

  const handleEnroll = async () => {
    if (selectedCourseIds.length === 0) {
      toast({
        variant: 'destructive',
        title: 'No courses selected',
        description: 'Please select at least one course',
      })
      return
    }

    setEnrolling(true)
    try {
      const results = await enrollmentsApi.enrollClassToCourses(classId, selectedCourseIds)
      
      // Aggregate results
      const aggregated: BulkEnrollmentResult = {
        totalStudents: results.reduce((sum, r) => sum + (r.totalStudents || 0), 0),
        enrolledStudents: results.reduce((sum, r) => sum + (r.enrolledStudents || 0), 0),
        skippedStudents: results.reduce((sum, r) => sum + (r.skippedStudents || 0), 0),
        failedStudents: results.reduce((sum, r) => sum + (r.failedStudents || 0), 0),
        errorMessages: results.flatMap(r => r.errorMessages || []),
      }
      
      setResult(aggregated)
      setShowConfirmDialog(false)
      
      toast({
        title: 'Enrollment completed',
        description: `Successfully enrolled ${aggregated.enrolledStudents} students in ${selectedCourseIds.length} course(s)`,
      })
      
      // Reset selection
      setSelectedCourseIds([])
    } catch (err: any) {
      console.error('Error enrolling class:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Enrollment failed',
        description: errorMessage,
      })
    } finally {
      setEnrolling(false)
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

  if (!classItem) {
    return (
      <div className="container mx-auto py-8 px-4">
        <p>Class not found</p>
      </div>
    )
  }

  return (
    <div className="container mx-auto py-8 px-4">
      <div className="mb-6">
        <Link href={`/classes/${classId}`}>
          <Button variant="ghost" size="sm" className="mb-4">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Class
          </Button>
        </Link>
        <div className="mb-6">
          <p className="text-gray-600">
            {institute?.name} - {classItem.name}
          </p>
        </div>
      </div>

      <div className="grid gap-6">
          {/* Class Info */}
          <Card>
            <CardHeader>
              <CardTitle>Class Information</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <Label className="text-sm text-gray-500">Sections</Label>
                  <p className="text-lg font-semibold">{sections.length}</p>
                </div>
                <div>
                  <Label className="text-sm text-gray-500">Estimated Students</Label>
                  <p className="text-lg font-semibold">{studentCount}</p>
                </div>
                <div>
                  <Label className="text-sm text-gray-500">Status</Label>
                  <Badge variant={classItem.isActive ? 'default' : 'secondary'}>
                    {classItem.isActive ? 'Active' : 'Inactive'}
                  </Badge>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Course Selection */}
          <Card>
            <CardHeader>
              <CardTitle>Select Courses</CardTitle>
              <CardDescription>
                Select courses to enroll all students in this class
              </CardDescription>
            </CardHeader>
            <CardContent>
              {courses.length === 0 ? (
                <p className="text-gray-500">No published courses available</p>
              ) : (
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {courses.map((course) => (
                    <div
                      key={course.id}
                      className="flex items-center space-x-2 p-3 border rounded-lg hover:bg-gray-50 cursor-pointer"
                      onClick={() => {
                        if (selectedCourseIds.includes(course.id)) {
                          setSelectedCourseIds(selectedCourseIds.filter(id => id !== course.id))
                        } else {
                          setSelectedCourseIds([...selectedCourseIds, course.id])
                        }
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={selectedCourseIds.includes(course.id)}
                        onChange={() => {}}
                        className="rounded"
                      />
                      <div className="flex-1">
                        <p className="font-medium">{course.title}</p>
                        {course.description && (
                          <p className="text-sm text-gray-500 line-clamp-1">
                            {course.description}
                          </p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Action */}
          <div className="flex justify-end">
            <Button
              onClick={() => setShowConfirmDialog(true)}
              disabled={selectedCourseIds.length === 0 || enrolling}
            >
              <Users className="mr-2 h-4 w-4" />
              Enroll Class to {selectedCourseIds.length} Course(s)
            </Button>
          </div>

          {/* Results */}
          {result && (
            <Card>
              <CardHeader>
                <CardTitle>Enrollment Results</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-4 gap-4">
                  <div className="text-center p-4 bg-gray-50 rounded-lg">
                    <div className="text-2xl font-bold text-gray-900">
                      {result.totalStudents}
                    </div>
                    <div className="text-sm text-gray-600">Total Students</div>
                  </div>
                  <div className="text-center p-4 bg-green-50 rounded-lg">
                    <div className="text-2xl font-bold text-green-600">
                      {result.enrolledStudents}
                    </div>
                    <div className="text-sm text-gray-600">Enrolled</div>
                  </div>
                  <div className="text-center p-4 bg-yellow-50 rounded-lg">
                    <div className="text-2xl font-bold text-yellow-600">
                      {result.skippedStudents}
                    </div>
                    <div className="text-sm text-gray-600">Skipped</div>
                  </div>
                  <div className="text-center p-4 bg-red-50 rounded-lg">
                    <div className="text-2xl font-bold text-red-600">
                      {result.failedStudents}
                    </div>
                    <div className="text-sm text-gray-600">Failed</div>
                  </div>
                </div>
                {result.errorMessages && result.errorMessages.length > 0 && (
                  <div className="mt-4">
                    <h4 className="font-semibold mb-2">Errors:</h4>
                    <ul className="list-disc list-inside text-sm text-red-600">
                      {result.errorMessages.map((msg, idx) => (
                        <li key={idx}>{msg}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>

        {/* Confirmation Dialog */}
        <Dialog open={showConfirmDialog} onOpenChange={setShowConfirmDialog}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Confirm Enrollment</DialogTitle>
              <DialogDescription>
                Are you sure you want to enroll all students in this class to{' '}
                {selectedCourseIds.length} course(s)? This will enroll approximately{' '}
                {studentCount} students.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setShowConfirmDialog(false)}
                disabled={enrolling}
              >
                Cancel
              </Button>
              <Button onClick={handleEnroll} disabled={enrolling}>
                {enrolling ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Enrolling...
                  </>
                ) : (
                  'Confirm Enrollment'
                )}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
  )
}

