'use client'

import { useEffect, useState } from 'react'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { Copy, Search, CheckCircle2, XCircle, Loader2, ArrowRight } from 'lucide-react'
import { coursesApi, tenantsApi } from '@/lib/api'
import type { Course, CourseCopyRequest, AIGenerationJobDTO, CourseCopyResult } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { Checkbox } from '@/components/ui/checkbox'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

interface Tenant {
  id: string
  name: string
  displayName?: string
}

export default function CourseCopyPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  
  const [sourceTenants, setSourceTenants] = useState<Tenant[]>([])
  const [targetTenants, setTargetTenants] = useState<Tenant[]>([])
  const [selectedSourceTenant, setSelectedSourceTenant] = useState<string>('')
  const [selectedTargetTenant, setSelectedTargetTenant] = useState<string>('')
  
  const [courses, setCourses] = useState<Course[]>([])
  const [filteredCourses, setFilteredCourses] = useState<Course[]>([])
  const [searchQuery, setSearchQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingCourses, setLoadingCourses] = useState(false)
  
  // Copy dialog state
  const [copyDialogOpen, setCopyDialogOpen] = useState(false)
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null)
  const [newCourseTitle, setNewCourseTitle] = useState('')
  const [copyPublishedState, setCopyPublishedState] = useState(false)
  const [copying, setCopying] = useState(false)
  
  // Job status state
  const [jobId, setJobId] = useState<string | null>(null)
  const [jobProgress, setJobProgress] = useState(0)
  const [jobMessage, setJobMessage] = useState('')
  const [jobStatus, setJobStatus] = useState<'PENDING' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'>('PENDING')
  const [copyResult, setCopyResult] = useState<CourseCopyResult | null>(null)

  // Check if user is SYSTEM_ADMIN
  useEffect(() => {
    if (user && user.role !== 'SYSTEM_ADMIN') {
      toast({
        title: 'Access Denied',
        description: 'Only SYSTEM_ADMIN users can access this page',
        variant: 'destructive',
      })
    }
  }, [user, toast])

  // Load tenants
  useEffect(() => {
    const loadTenants = async () => {
      try {
        const tenantList = await tenantsApi.listTenants()
        setSourceTenants(tenantList)
        setTargetTenants(tenantList)
      } catch (error) {
        toast({
          title: 'Error',
          description: extractErrorMessage(error),
          variant: 'destructive',
        })
      }
    }
    loadTenants()
  }, [toast])

  // Load courses when source tenant changes
  useEffect(() => {
    if (!selectedSourceTenant) {
      setCourses([])
      setFilteredCourses([])
      return
    }

    const loadCourses = async () => {
      setLoadingCourses(true)
      try {
        const courseList = await coursesApi.listCourses()
        setCourses(courseList)
        setFilteredCourses(courseList)
      } catch (error) {
        toast({
          title: 'Error loading courses',
          description: extractErrorMessage(error),
          variant: 'destructive',
        })
      } finally {
        setLoadingCourses(false)
      }
    }
    loadCourses()
  }, [selectedSourceTenant, toast])

  // Filter courses based on search
  useEffect(() => {
    if (!searchQuery) {
      setFilteredCourses(courses)
      return
    }
    const query = searchQuery.toLowerCase()
    const filtered = courses.filter(
      (course) =>
        course.title.toLowerCase().includes(query) ||
        (course.description && course.description.toLowerCase().includes(query))
    )
    setFilteredCourses(filtered)
  }, [searchQuery, courses])

  const handleCopyClick = (course: Course) => {
    setSelectedCourse(course)
    setNewCourseTitle('')
    setCopyPublishedState(false)
    setCopyDialogOpen(true)
    setJobId(null)
    setCopyResult(null)
    setJobProgress(0)
    setJobMessage('')
    setJobStatus('PENDING')
  }

  const handleCopySubmit = async () => {
    if (!selectedCourse || !selectedTargetTenant) return

    setCopying(true)
    try {
      const request: CourseCopyRequest = {
        targetClientId: selectedTargetTenant,
        newCourseTitle: newCourseTitle || undefined,
        copyPublishedState,
      }

      // Submit job
      const job = await coursesApi.copyCourseToTenant(selectedCourse.id, request)
      setJobId(job.jobId)
      setJobStatus(job.status)
      setJobProgress(job.progress || 0)
      setJobMessage(job.message || 'Job submitted')

      // Start polling
      pollJobStatus(job.jobId)
    } catch (error) {
      toast({
        title: 'Failed to submit copy job',
        description: extractErrorMessage(error),
        variant: 'destructive',
      })
      setCopying(false)
    }
  }

  const pollJobStatus = async (jobId: string) => {
    const interval = setInterval(async () => {
      try {
        const job = await coursesApi.getCourseCopyJobStatus(jobId)
        
        setJobStatus(job.status)
        setJobProgress(job.progress || 0)
        setJobMessage(job.message || '')

        if (job.status === 'COMPLETED') {
          clearInterval(interval)
          setCopying(false)
          setCopyResult(job.result as CourseCopyResult)
          toast({
            title: 'Course Copied Successfully!',
            description: `New course ID: ${(job.result as CourseCopyResult)?.newCourseId}`,
          })
        } else if (job.status === 'FAILED') {
          clearInterval(interval)
          setCopying(false)
          toast({
            title: 'Course Copy Failed',
            description: job.error || 'Unknown error occurred',
            variant: 'destructive',
          })
        }
      } catch (error) {
        console.error('Error polling job status:', error)
      }
    }, 2000) // Poll every 2 seconds
  }

  const handleCloseDialog = () => {
    setCopyDialogOpen(false)
    setSelectedCourse(null)
    setNewCourseTitle('')
    setCopyPublishedState(false)
    setJobId(null)
    setCopyResult(null)
    setJobProgress(0)
    setJobMessage('')
    setJobStatus('PENDING')
    setCopying(false)
  }

  if (user && user.role !== 'SYSTEM_ADMIN') {
    return (
      <div className="container mx-auto p-6">
        <Card>
          <CardHeader>
            <CardTitle>Access Denied</CardTitle>
            <CardDescription>
              Only SYSTEM_ADMIN users can access this page.
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">Cross-Tenant Course Copy</h1>
        <p className="text-gray-600">
          Copy courses from one tenant to another, including all content, assessments, and media.
        </p>
      </div>

      {/* Tenant Selection */}
      <Card>
        <CardHeader>
          <CardTitle>Select Tenants</CardTitle>
          <CardDescription>
            Choose the source tenant to view courses, then select courses to copy to a target tenant.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Source Tenant</Label>
              <Select value={selectedSourceTenant} onValueChange={setSelectedSourceTenant}>
                <SelectTrigger>
                  <SelectValue placeholder="Select source tenant" />
                </SelectTrigger>
                <SelectContent>
                  {sourceTenants.map((tenant) => (
                    <SelectItem key={tenant.id} value={tenant.id}>
                      {tenant.displayName || tenant.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            
            <div className="space-y-2">
              <Label>Target Tenant</Label>
              <Select value={selectedTargetTenant} onValueChange={setSelectedTargetTenant}>
                <SelectTrigger>
                  <SelectValue placeholder="Select target tenant" />
                </SelectTrigger>
                <SelectContent>
                  {targetTenants
                    .filter((t) => t.id !== selectedSourceTenant)
                    .map((tenant) => (
                      <SelectItem key={tenant.id} value={tenant.id}>
                        {tenant.displayName || tenant.name}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Course Browser */}
      {selectedSourceTenant && (
        <Card>
          <CardHeader>
            <CardTitle>Available Courses</CardTitle>
            <CardDescription>
              Select a course to copy to the target tenant.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center space-x-2">
                <Search className="w-5 h-5 text-gray-400" />
                <Input
                  placeholder="Search courses..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="flex-1"
                />
              </div>

              {loadingCourses ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="w-8 h-8 animate-spin text-blue-600" />
                </div>
              ) : filteredCourses.length === 0 ? (
                <div className="text-center py-12 text-gray-500">
                  {searchQuery ? 'No courses found matching your search.' : 'No courses available.'}
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {filteredCourses.map((course) => (
                    <Card key={course.id} className="hover:shadow-lg transition-shadow">
                      <CardHeader>
                        <div className="flex items-start justify-between">
                          <CardTitle className="text-lg line-clamp-2">{course.title}</CardTitle>
                          {course.isPublished && (
                            <Badge variant="outline" className="ml-2">Published</Badge>
                          )}
                        </div>
                        {course.description && (
                          <CardDescription className="line-clamp-3">
                            {course.description}
                          </CardDescription>
                        )}
                      </CardHeader>
                      <CardContent>
                        <div className="space-y-2 text-sm text-gray-600">
                          <div>Lectures: {course.totalLecturesCount || 0}</div>
                          <div>Duration: {Math.round((course.totalDurationSeconds || 0) / 60)} minutes</div>
                          <Button
                            onClick={() => handleCopyClick(course)}
                            disabled={!selectedTargetTenant}
                            className="w-full mt-4"
                            variant="outline"
                          >
                            <Copy className="w-4 h-4 mr-2" />
                            Copy to Target
                          </Button>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Copy Dialog */}
      <Dialog open={copyDialogOpen} onOpenChange={setCopyDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Copy Course to Another Tenant</DialogTitle>
            <DialogDescription>
              This will create a complete copy of the course in the target tenant, including all content and media files.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            {!jobId ? (
              <>
                <div className="space-y-2">
                  <Label>Source Course</Label>
                  <div className="text-sm bg-gray-50 p-3 rounded">
                    <div className="font-medium">{selectedCourse?.title}</div>
                    <div className="text-gray-600 text-xs mt-1">
                      {selectedCourse?.totalLecturesCount || 0} lectures • {Math.round((selectedCourse?.totalDurationSeconds || 0) / 60)} minutes
                    </div>
                  </div>
                </div>

                <div className="space-y-2">
                  <Label>Target Tenant</Label>
                  <div className="text-sm bg-gray-50 p-3 rounded">
                    {targetTenants.find((t) => t.id === selectedTargetTenant)?.displayName ||
                      targetTenants.find((t) => t.id === selectedTargetTenant)?.name}
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="newTitle">
                    New Course Title <span className="text-gray-500 text-sm">(optional)</span>
                  </Label>
                  <Input
                    id="newTitle"
                    placeholder={`Copy of ${selectedCourse?.title}`}
                    value={newCourseTitle}
                    onChange={(e) => setNewCourseTitle(e.target.value)}
                  />
                </div>

                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="publish"
                    checked={copyPublishedState}
                    onCheckedChange={(checked) => setCopyPublishedState(checked as boolean)}
                  />
                  <Label htmlFor="publish" className="cursor-pointer">
                    Copy published state (if source is published, copy will be published too)
                  </Label>
                </div>
              </>
            ) : (
              <div className="space-y-4">
                {/* Progress Display */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium">{jobMessage || 'Processing...'}</span>
                    <span>{jobProgress}%</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-3">
                    <div
                      className="bg-blue-600 h-3 rounded-full transition-all duration-500"
                      style={{ width: `${jobProgress}%` }}
                    />
                  </div>
                </div>

                {/* Status */}
                <div className="flex items-center space-x-2">
                  {jobStatus === 'COMPLETED' && (
                    <>
                      <CheckCircle2 className="w-5 h-5 text-green-600" />
                      <span className="text-green-600 font-medium">Copy Completed!</span>
                    </>
                  )}
                  {jobStatus === 'FAILED' && (
                    <>
                      <XCircle className="w-5 h-5 text-red-600" />
                      <span className="text-red-600 font-medium">Copy Failed</span>
                    </>
                  )}
                  {(jobStatus === 'PENDING' || jobStatus === 'QUEUED' || jobStatus === 'PROCESSING') && (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin text-blue-600" />
                      <span className="text-blue-600 font-medium">
                        {jobStatus === 'PENDING' && 'Waiting to start...'}
                        {jobStatus === 'QUEUED' && 'Queued for processing...'}
                        {jobStatus === 'PROCESSING' && 'Copying course...'}
                      </span>
                    </>
                  )}
                </div>

                {/* Result Details */}
                {copyResult && (
                  <div className="bg-green-50 border border-green-200 rounded-lg p-4 space-y-2">
                    <div className="font-medium text-green-900">Course Copy Summary</div>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div>New Course ID:</div>
                      <div className="font-mono text-xs">{copyResult.newCourseId}</div>
                      <div>Duration:</div>
                      <div>{copyResult.duration}</div>
                      <div>Sections:</div>
                      <div>{copyResult.copiedEntities.sections || 0}</div>
                      <div>Lectures:</div>
                      <div>{copyResult.copiedEntities.lectures || 0}</div>
                      <div>Assessments:</div>
                      <div>{copyResult.copiedEntities.assessments || 0}</div>
                      <div>Media Files:</div>
                      <div>{copyResult.copiedEntities.mediaAssets || 0}</div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <DialogFooter>
            {!jobId ? (
              <>
                <Button variant="outline" onClick={handleCloseDialog}>
                  Cancel
                </Button>
                <Button onClick={handleCopySubmit} disabled={copying || !selectedTargetTenant}>
                  {copying ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      Starting Copy...
                    </>
                  ) : (
                    <>
                      <Copy className="w-4 h-4 mr-2" />
                      Start Copy
                    </>
                  )}
                </Button>
              </>
            ) : (
              <Button onClick={handleCloseDialog} disabled={jobStatus === 'PROCESSING'}>
                {jobStatus === 'COMPLETED' ? 'Done' : jobStatus === 'FAILED' ? 'Close' : 'Close'}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
