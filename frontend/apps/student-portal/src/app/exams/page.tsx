'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button, Card, CardHeader, CardTitle, CardContent } from '@kunal-ak23/edudron-ui-components'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Clock, Calendar, CheckCircle, Loader2, PlayCircle, ClipboardList, ChevronLeft, ChevronRight } from 'lucide-react'
import { StudentLayout } from '@/components/StudentLayout'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { apiClient } from '@/lib/api'
import { logJourneyEventWithoutSubmission } from '@/lib/journey-api'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'

interface Exam {
  id: string
  title: string
  description?: string
  status: 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'COMPLETED'
  startTime?: string
  endTime?: string
  courseId: string
  timeLimitSeconds?: number
  maxAttempts?: number
  attemptsTaken?: number
  timingMode?: 'FIXED_WINDOW' | 'FLEXIBLE_START'
}

export const dynamic = 'force-dynamic'

export default function ExamsPage() {
  const router = useRouter()
  const { needsTenantSelection } = useAuth()
  const [exams, setExams] = useState<Exam[]>([])
  const [loading, setLoading] = useState(true)
  const [isInitialLoad, setIsInitialLoad] = useState(true)
  const [completedPage, setCompletedPage] = useState(1)
  const ITEMS_PER_PAGE = 10

  const loadExams = async (showLoader = false) => {
    try {
      if (showLoader || isInitialLoad) {
        setLoading(true)
      }
      
      const data = await apiClient.get<Exam[]>('/api/student/exams')
      
      // Ensure data is an array - handle cases where API might return wrapped response or null
      let examsData: Exam[] = []
      if (Array.isArray(data)) {
        examsData = data
      } else if (data && typeof data === 'object') {
        // Check for various possible response formats
        if ('data' in data && Array.isArray(data.data)) {
          examsData = data.data
        } else if (data && Object.keys(data).length > 0) {
          // If it's an object but not an array, try to extract array from it
          examsData = []
        } else {
          examsData = []
        }
      } else {
        examsData = []
      }
      
      // Deduplicate exams by ID to prevent duplicates
      const uniqueExams = examsData.reduce((acc, exam) => {
        if (exam && exam.id && !acc.find(e => e.id === exam.id)) {
          acc.push(exam)
        }
        return acc
      }, [] as Exam[])
      setExams(uniqueExams)
      setIsInitialLoad(false)
    } catch (error: any) {
      setExams([])
      setIsInitialLoad(false)
    } finally {
      // Always set loading to false, even if there was an error
      setLoading(false)
    }
  }

  useEffect(() => {
    if (needsTenantSelection) {
      router.replace('/select-tenant')
      setLoading(false)
      setIsInitialLoad(false)
      return
    }

    let mounted = true
    let loadingTimeout: NodeJS.Timeout | null = null
    
    // Safety timeout to prevent infinite loading
    loadingTimeout = setTimeout(() => {
      if (mounted) {
        setLoading(false)
        setIsInitialLoad(false)
      }
    }, 10000) // 10 second timeout
    
    const fetchExams = async () => {
      try {
        await loadExams(true) // Show loader on initial load
        // Clear timeout if load completed successfully
        if (loadingTimeout) {
          clearTimeout(loadingTimeout)
        }
      } catch (error) {
        if (mounted) {
          setLoading(false)
          setIsInitialLoad(false)
        }
        if (loadingTimeout) {
          clearTimeout(loadingTimeout)
        }
      }
    }
    
    if (mounted) {
      fetchExams()
    }
    
    // Set up polling to check for exam status updates
    // Poll every 30 seconds to catch when scheduled exams become live
    // Don't show loader during polling to prevent flashing
    const pollInterval = setInterval(() => {
      if (mounted) {
        loadExams(false) // Don't show loader during polling
      }
    }, 30000) // Poll every 30 seconds
    
    return () => {
      mounted = false
      if (loadingTimeout) {
        clearTimeout(loadingTimeout)
      }
      clearInterval(pollInterval)
    }
  }, [])

  const getStatusBadge = (status: string) => {
    if (status === 'LIVE') {
      return <Badge className="bg-green-500">Live</Badge>
    } else if (status === 'SCHEDULED') {
      return <Badge variant="secondary">Scheduled</Badge>
    } else if (status === 'COMPLETED') {
      return <Badge variant="outline">Completed</Badge>
    }
    return null
  }

  const formatDate = (dateString?: string) => {
    if (!dateString) return 'Not scheduled'
    return new Date(dateString).toLocaleString()
  }

  const getTimeUntilStart = (startTime?: string) => {
    if (!startTime) return null
    const now = new Date().getTime()
    const start = new Date(startTime).getTime()
    const diff = start - now
    
    if (diff <= 0) {
      return null // Exam should be live now
    }
    
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    const seconds = Math.floor((diff % (1000 * 60)) / 1000)
    
    if (days > 0) return `${days}d ${hours}h`
    if (hours > 0) return `${hours}h ${minutes}m`
    if (minutes > 0) return `${minutes}m`
    return `${seconds}s`
  }

  // Ensure exams is always an array before filtering
  // Deduplicate by ID first to prevent same exam appearing multiple times
  const examsArray = Array.isArray(exams) ? exams : []
  const uniqueExams = examsArray.reduce((acc, exam) => {
    if (!acc.find(e => e.id === exam.id)) {
      acc.push(exam)
    }
    return acc
  }, [] as Exam[])
  
  // Filter with real-time checks based on actual time AND student's completion status
  const now = new Date()
  
  // Helper: Check if student has completed this exam (used all attempts or has a submission)
  const hasCompletedExam = (exam: Exam) => {
    // If max attempts is set and student has reached it
    if (exam.maxAttempts && exam.maxAttempts > 0 && exam.attemptsTaken && exam.attemptsTaken >= exam.maxAttempts) {
      return true
    }
    // If student has taken at least one attempt (submitted the exam)
    if (exam.attemptsTaken && exam.attemptsTaken > 0) {
      return true
    }
    return false
  }
  
  // Live exams: current time is between start and end time AND student hasn't completed it
  const liveExams = uniqueExams.filter(e => {
    // If student has already completed this exam, don't show in Live
    if (hasCompletedExam(e)) {
      return false
    }
    
    // If no start/end time, fall back to status check
    if (!e.startTime || !e.endTime) {
      return e.status === 'LIVE'
    }
    
    const startTime = new Date(e.startTime)
    const endTime = new Date(e.endTime)
    
    // Real-time check: exam is live if current time is within the window
    const isWithinTimeWindow = now >= startTime && now <= endTime
    return isWithinTimeWindow
  })
  
  // Scheduled exams: start time is in the future AND student hasn't completed it
  const scheduledExams = uniqueExams.filter(e => {
    // If student has already completed this exam, don't show in Scheduled
    if (hasCompletedExam(e)) {
      return false
    }
    
    // If no start/end time, fall back to status check
    if (!e.startTime || !e.endTime) {
      return e.status === 'SCHEDULED'
    }
    
    const startTime = new Date(e.startTime)
    const endTime = new Date(e.endTime)
    
    // Real-time check: exam is scheduled if start time is in future and hasn't ended
    const isInFuture = now < startTime && now <= endTime
    return isInFuture
  })
  
  // Completed exams: student has completed it OR end time has passed OR status is COMPLETED
  const completedExams = uniqueExams.filter(e => {
    // Student has completed this exam (taken their attempts)
    if (hasCompletedExam(e)) {
      return true
    }
    
    if (e.status === 'COMPLETED') return true
    
    // Real-time check: exam is completed if end time has passed
    if (e.endTime) {
      const endTime = new Date(e.endTime)
      return now > endTime
    }
    
    return false
  })

  if (loading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <main className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
            <div className="flex items-center justify-center h-64">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          </main>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <main className="max-w-[1600px] mx-auto px-6 sm:px-8 lg:px-12 py-3">
          <div className="space-y-6">
            <div>
              <h1 className="text-3xl font-bold">Exams</h1>
              <p className="text-gray-600 mt-1">View and take your exams</p>
            </div>

          <Tabs defaultValue={liveExams.length > 0 ? "live" : scheduledExams.length > 0 ? "upcoming" : "completed"} className="w-full">
            <TabsList className="bg-transparent p-0 gap-2">
              <TabsTrigger value="live" className="flex items-center gap-2 px-4 py-2 rounded-full border border-gray-200 data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:bg-primary/5">
                <PlayCircle className="h-4 w-4 flex-shrink-0" />
                <span>Live</span>
                {liveExams.length > 0 && (
                  <Badge variant="secondary" className="bg-green-100 text-green-700">
                    {liveExams.length}
                  </Badge>
                )}
              </TabsTrigger>
              <TabsTrigger value="upcoming" className="flex items-center gap-2 px-4 py-2 rounded-full border border-gray-200 data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:bg-primary/5">
                <Clock className="h-4 w-4 flex-shrink-0" />
                <span>Upcoming</span>
                {scheduledExams.length > 0 && (
                  <Badge variant="secondary">
                    {scheduledExams.length}
                  </Badge>
                )}
              </TabsTrigger>
              <TabsTrigger value="completed" className="flex items-center gap-2 px-4 py-2 rounded-full border border-gray-200 data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:bg-primary/5">
                <CheckCircle className="h-4 w-4 flex-shrink-0" />
                <span>Completed</span>
                {completedExams.length > 0 && (
                  <Badge variant="secondary">
                    {completedExams.length}
                  </Badge>
                )}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="live" className="mt-6">
              {liveExams.length > 0 ? (
                <div className="grid gap-4">
                  {liveExams.map((exam) => (
                    <Card key={exam.id} className="border-green-200">
                      <CardHeader>
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <CardTitle className="text-xl">{exam.title}</CardTitle>
                            {exam.description && (
                              <p className="text-sm text-gray-600 mt-1">
                                {exam.description}
                              </p>
                            )}
                          </div>
                          {getStatusBadge('LIVE')}
                        </div>
                      </CardHeader>
                      <CardContent>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-4 text-sm text-gray-600">
                            {/* Only show "Ends" for FIXED_WINDOW exams */}
                            {exam.timingMode !== 'FLEXIBLE_START' && exam.endTime && (
                              <div className="flex items-center gap-2">
                                <Clock className="h-4 w-4" />
                                <span>Ends: {formatDate(exam.endTime)}</span>
                              </div>
                            )}
                            {exam.timeLimitSeconds && (
                              <div className="flex items-center gap-2">
                                <Clock className="h-4 w-4" />
                                <span>Duration: {Math.floor(exam.timeLimitSeconds / 60)} minutes</span>
                              </div>
                            )}
                            {exam.maxAttempts && exam.maxAttempts > 0 && (
                              <div>
                                Attempts: {exam.attemptsTaken || 0}/{exam.maxAttempts}
                              </div>
                            )}
                          </div>
                          <Button 
                            onClick={() => {
                              logJourneyEventWithoutSubmission(exam.id, { eventType: 'EXAM_TAKE_CLICKED', severity: 'INFO' })
                              router.push(`/exams/${exam.id}/take`)
                            }}
                            disabled={
                              !!(exam.timingMode !== 'FLEXIBLE_START' && exam.endTime && new Date() > new Date(exam.endTime)) ||
                              !!(exam.maxAttempts && exam.attemptsTaken && exam.attemptsTaken >= exam.maxAttempts)
                            }
                          >
                            {exam.timingMode !== 'FLEXIBLE_START' && exam.endTime && new Date() > new Date(exam.endTime) 
                              ? 'Exam Ended' 
                              : exam.maxAttempts && exam.attemptsTaken && exam.attemptsTaken >= exam.maxAttempts
                              ? 'Max Attempts Reached'
                              : 'Take Exam'}
                          </Button>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              ) : (
                <Card>
                  <CardContent className="py-12 text-center">
                    <PlayCircle className="h-12 w-12 mx-auto text-gray-300 mb-4" />
                    <p className="text-gray-500">No live exams at the moment</p>
                    <p className="text-sm text-gray-400 mt-1">Check the Upcoming tab for scheduled exams</p>
                  </CardContent>
                </Card>
              )}
            </TabsContent>

            <TabsContent value="upcoming" className="mt-6">
              {scheduledExams.length > 0 ? (
                <div className="grid gap-4">
                  {scheduledExams.map((exam) => {
                    const timeUntil = getTimeUntilStart(exam.startTime)
                    const now = new Date().getTime()
                    const startTime = exam.startTime ? new Date(exam.startTime).getTime() : null
                    const isAvailable = startTime !== null && now >= startTime
                    
                    return (
                      <Card key={exam.id}>
                        <CardHeader>
                          <div className="flex items-start justify-between">
                            <div className="flex-1">
                              <CardTitle className="text-xl">{exam.title}</CardTitle>
                              {exam.description && (
                                <p className="text-sm text-gray-600 mt-1">
                                  {exam.description}
                                </p>
                              )}
                            </div>
                            {getStatusBadge(exam.status)}
                          </div>
                        </CardHeader>
                        <CardContent>
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-4 text-sm text-gray-600">
                              <div className="flex items-center gap-2">
                                <Calendar className="h-4 w-4" />
                                <span>Starts: {formatDate(exam.startTime)}</span>
                              </div>
                              {timeUntil && (
                                <Badge variant="outline">
                                  Starts in {timeUntil}
                                </Badge>
                              )}
                              {!timeUntil && !isAvailable && (
                                <Badge variant="outline" className="bg-yellow-50 text-yellow-700">
                                  Starting soon...
                                </Badge>
                              )}
                            </div>
                            {(() => {
                              // Real-time check: has exam ended?
                              const hasEnded = exam.endTime && new Date() > new Date(exam.endTime)
                              if (hasEnded) {
                                return (
                                  <Button variant="outline" disabled>
                                    Exam Ended
                                  </Button>
                                )
                              }
                              if (isAvailable) {
                                return (
                                  <Button onClick={() => {
                                    logJourneyEventWithoutSubmission(exam.id, { eventType: 'EXAM_TAKE_CLICKED', severity: 'INFO' })
                                    router.push(`/exams/${exam.id}/take`)
                                  }}>
                                    Take Exam
                                  </Button>
                                )
                              }
                              return (
                                <Button variant="outline" disabled>
                                  Not Available Yet
                                </Button>
                              )
                            })()}
                          </div>
                        </CardContent>
                      </Card>
                    )
                  })}
                </div>
              ) : (
                <Card>
                  <CardContent className="py-12 text-center">
                    <Clock className="h-12 w-12 mx-auto text-gray-300 mb-4" />
                    <p className="text-gray-500">No upcoming exams scheduled</p>
                  </CardContent>
                </Card>
              )}
            </TabsContent>

            <TabsContent value="completed" className="mt-6">
              {completedExams.length > 0 ? (
                <div className="space-y-4">
                  <div className="grid gap-4">
                    {completedExams
                      .slice((completedPage - 1) * ITEMS_PER_PAGE, completedPage * ITEMS_PER_PAGE)
                      .map((exam) => (
                      <Card key={exam.id}>
                        <CardHeader>
                          <div className="flex items-start justify-between">
                            <div className="flex-1">
                              <CardTitle className="text-xl">{exam.title}</CardTitle>
                              {exam.description && (
                                <p className="text-sm text-gray-600 mt-1">
                                  {exam.description}
                                </p>
                              )}
                            </div>
                            {getStatusBadge('COMPLETED')}
                          </div>
                        </CardHeader>
                        <CardContent>
                          <div className="flex items-center justify-between">
                            <div className="text-sm text-gray-600">
                              Completed: {formatDate(exam.endTime)}
                            </div>
                            <Button variant="outline" onClick={() => router.push(`/exams/${exam.id}/results`)}>
                              <CheckCircle className="h-4 w-4 mr-2" />
                              View Results
                            </Button>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                  
                  {/* Pagination */}
                  {completedExams.length > ITEMS_PER_PAGE && (
                    <div className="flex items-center justify-between border-t pt-4">
                      <p className="text-sm text-gray-600">
                        Showing {((completedPage - 1) * ITEMS_PER_PAGE) + 1} - {Math.min(completedPage * ITEMS_PER_PAGE, completedExams.length)} of {completedExams.length} exams
                      </p>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCompletedPage(p => Math.max(1, p - 1))}
                          disabled={completedPage === 1}
                        >
                          <ChevronLeft className="h-4 w-4 mr-1" />
                          Previous
                        </Button>
                        <span className="text-sm text-gray-600 px-2">
                          Page {completedPage} of {Math.ceil(completedExams.length / ITEMS_PER_PAGE)}
                        </span>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCompletedPage(p => Math.min(Math.ceil(completedExams.length / ITEMS_PER_PAGE), p + 1))}
                          disabled={completedPage >= Math.ceil(completedExams.length / ITEMS_PER_PAGE)}
                        >
                          Next
                          <ChevronRight className="h-4 w-4 ml-1" />
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <Card>
                  <CardContent className="py-12 text-center">
                    <ClipboardList className="h-12 w-12 mx-auto text-gray-300 mb-4" />
                    <p className="text-gray-500">No completed exams yet</p>
                  </CardContent>
                </Card>
              )}
            </TabsContent>
          </Tabs>
          </div>
        </main>
      </StudentLayout>
    </ProtectedRoute>
  )
}
