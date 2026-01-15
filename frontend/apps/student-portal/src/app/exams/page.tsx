'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button, Card, CardHeader, CardTitle, CardContent } from '@kunal-ak23/edudron-ui-components'
import { Badge } from '@/components/ui/badge'
import { Clock, Calendar, CheckCircle, Loader2 } from 'lucide-react'
import { StudentLayout } from '@/components/StudentLayout'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { apiClient } from '@/lib/api'

interface Exam {
  id: string
  title: string
  description?: string
  status: 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'COMPLETED'
  startTime?: string
  endTime?: string
  courseId: string
  timeLimitSeconds?: number
}

export const dynamic = 'force-dynamic'

export default function ExamsPage() {
  const router = useRouter()
  const [exams, setExams] = useState<Exam[]>([])
  const [loading, setLoading] = useState(true)
  const [isInitialLoad, setIsInitialLoad] = useState(true)

  const loadExams = async (showLoader = false) => {
    try {
      if (showLoader || isInitialLoad) {
        setLoading(true)
      }
      
      const data = await apiClient.get<Exam[]>('/api/student/exams')
      console.log('Exams API response:', data)
      
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
          console.warn('Unexpected API response format, attempting to parse:', data)
          examsData = []
        } else {
          examsData = []
        }
      } else {
        console.warn('Unexpected API response format:', data)
        examsData = []
      }
      
      console.log('Processed exams data:', examsData, 'Count:', examsData.length)
      
      // Deduplicate exams by ID to prevent duplicates
      const uniqueExams = examsData.reduce((acc, exam) => {
        if (exam && exam.id && !acc.find(e => e.id === exam.id)) {
          acc.push(exam)
        }
        return acc
      }, [] as Exam[])
      
      console.log('Unique exams:', uniqueExams, 'Count:', uniqueExams.length)
      setExams(uniqueExams)
      setIsInitialLoad(false)
    } catch (error: any) {
      console.error('Failed to load exams:', error)
      console.error('Error details:', error?.message, error?.response)
      setExams([])
      setIsInitialLoad(false)
    } finally {
      // Always set loading to false, even if there was an error
      setLoading(false)
      console.log('Loading state set to false')
    }
  }

  useEffect(() => {
    let mounted = true
    let loadingTimeout: NodeJS.Timeout | null = null
    
    // Safety timeout to prevent infinite loading
    loadingTimeout = setTimeout(() => {
      if (mounted) {
        console.warn('Loading timeout - forcing loading state to false')
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
        console.error('Error in fetchExams:', error)
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
  
  const liveExams = uniqueExams.filter(e => e.status === 'LIVE')
  const scheduledExams = uniqueExams.filter(e => e.status === 'SCHEDULED')
  const completedExams = uniqueExams.filter(e => e.status === 'COMPLETED')

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

          {liveExams.length > 0 && (
            <div>
              <h2 className="text-xl font-semibold mb-4">Live Exams</h2>
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
                        {getStatusBadge(exam.status)}
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-4 text-sm text-gray-600">
                          <div className="flex items-center gap-2">
                            <Clock className="h-4 w-4" />
                            <span>Ends: {formatDate(exam.endTime)}</span>
                          </div>
                          {exam.timeLimitSeconds && (
                            <div>
                              Duration: {Math.floor(exam.timeLimitSeconds / 60)} minutes
                            </div>
                          )}
                        </div>
                        <Button onClick={() => router.push(`/exams/${exam.id}/take`)}>
                          Take Exam
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}

          {scheduledExams.length > 0 && (
            <div>
              <h2 className="text-xl font-semibold mb-4">Upcoming Exams</h2>
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
                          {isAvailable ? (
                            <Button onClick={() => {
                              router.push(`/exams/${exam.id}/take`)
                            }}>
                              Take Exam
                            </Button>
                          ) : (
                            <Button variant="outline" disabled>
                              Not Available Yet
                            </Button>
                          )}
                        </div>
                      </CardContent>
                    </Card>
                  )
                })}
              </div>
            </div>
          )}

          {completedExams.length > 0 && (
            <div>
              <h2 className="text-xl font-semibold mb-4">Completed Exams</h2>
              <div className="grid gap-4">
                {completedExams.map((exam) => (
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
            </div>
          )}

          {uniqueExams.length === 0 && !loading && (
            <Card>
              <CardContent className="py-12 text-center">
                <p className="text-gray-500">No exams available</p>
              </CardContent>
            </Card>
          )}
          </div>
        </main>
      </StudentLayout>
    </ProtectedRoute>
  )
}
