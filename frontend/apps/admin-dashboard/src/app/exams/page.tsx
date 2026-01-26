'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Plus, Clock, Calendar, Users, Loader2 } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { apiClient } from '@/lib/api'

interface Exam {
  id: string
  title: string
  description?: string
  status: 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'COMPLETED'
  startTime?: string
  endTime?: string
  reviewMethod: 'INSTRUCTOR' | 'AI' | 'BOTH'
  courseId: string
  createdAt: string
}

export const dynamic = 'force-dynamic'

export default function ExamsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user } = useAuth()
  const [exams, setExams] = useState<Exam[]>([])
  const [loading, setLoading] = useState(true)
  
  // Check if user can create/edit exams
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const canManageExams = !isInstructor && !isSupportStaff

  const loadExams = useCallback(async () => {
    try {
      setLoading(true)
      const response = await apiClient.get<Exam[]>('/api/exams')
      
      // Handle different response formats
      let examsData: Exam[] = []
      if (Array.isArray(response)) {
        examsData = response
      } else if (response && typeof response === 'object') {
        // Handle wrapped response
        if ('data' in response && Array.isArray((response as any).data)) {
          examsData = (response as any).data
        } else if ('body' in response && Array.isArray((response as any).body)) {
          examsData = (response as any).body
        } else if (response !== null && response !== undefined) {
          // If response is an object but not an array, try to extract array from common properties
          const keys = Object.keys(response)
          for (const key of keys) {
            if (Array.isArray((response as any)[key])) {
              examsData = (response as any)[key]
              break
            }
          }
        }
      }
      
      // Ensure all exams have required fields with defaults
      examsData = examsData.map(exam => ({
        ...exam,
        status: exam.status || 'DRAFT',
        reviewMethod: exam.reviewMethod || 'INSTRUCTOR',
        createdAt: exam.createdAt || new Date().toISOString()
      }))
      
      setExams(examsData)
    } catch (error) {
      console.error('Failed to load exams:', error)
      toast({
        title: 'Error',
        description: 'Failed to load exams',
        variant: 'destructive'
      })
      // Set empty array on error to prevent map errors
      setExams([])
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    loadExams()
  }, [loadExams])

  const getStatusBadge = (status: string) => {
    const variants: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
      DRAFT: 'outline',
      SCHEDULED: 'secondary',
      LIVE: 'default',
      COMPLETED: 'outline'
    }
    
    return (
      <Badge variant={variants[status] || 'outline'}>
        {status}
      </Badge>
    )
  }

  const formatDate = (dateString?: string) => {
    if (!dateString) return 'Not scheduled'
    return new Date(dateString).toLocaleString()
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    )
  }

  // Filter out invalid exams (must have id and title)
  const validExams = exams.filter(exam => exam && exam.id && exam.title)

  return (
    <div className="space-y-3">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        {canManageExams && (
          <Button onClick={() => router.push('/exams/new')}>
            <Plus className="h-4 w-4 mr-2" />
            Create Exam
          </Button>
        )}
      </div>

        {validExams.length === 0 ? (
          <Card>
            <CardContent className="py-12 text-center">
              <p className="text-gray-500 mb-4">No exams created yet</p>
              {canManageExams && (
                <Button onClick={() => router.push('/exams/new')}>
                  <Plus className="h-4 w-4 mr-2" />
                  Create Your First Exam
                </Button>
              )}
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4">
            {validExams.map((exam) => {
              try {
                return (
                  <Card key={exam.id} className="cursor-pointer hover:shadow-md transition-shadow"
                        onClick={() => router.push(`/exams/${exam.id}`)}>
                    <CardHeader>
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <CardTitle className="text-xl">{exam.title || 'Untitled Exam'}</CardTitle>
                          {exam.description && (
                            <p className="text-sm text-gray-600 mt-1 line-clamp-2">
                              {exam.description}
                            </p>
                          )}
                        </div>
                        {getStatusBadge(exam.status || 'DRAFT')}
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                        <div className="flex items-center gap-2">
                          <Calendar className="h-4 w-4 text-gray-400" />
                          <div>
                            <div className="text-gray-500">Start</div>
                            <div className="font-medium">{formatDate(exam.startTime)}</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <Clock className="h-4 w-4 text-gray-400" />
                          <div>
                            <div className="text-gray-500">End</div>
                            <div className="font-medium">{formatDate(exam.endTime)}</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <Users className="h-4 w-4 text-gray-400" />
                          <div>
                            <div className="text-gray-500">Review</div>
                            <div className="font-medium">{exam.reviewMethod || 'INSTRUCTOR'}</div>
                          </div>
                        </div>
                        <div>
                          <div className="text-gray-500">Created</div>
                          <div className="font-medium">
                            {exam.createdAt ? new Date(exam.createdAt).toLocaleDateString() : 'N/A'}
                          </div>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                )
              } catch (error) {
                console.error('Error rendering exam:', exam, error)
                return null
              }
            })}
          </div>
        )}
    </div>
  )
}
