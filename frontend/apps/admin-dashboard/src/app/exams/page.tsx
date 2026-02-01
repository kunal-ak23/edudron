'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Plus, Clock, Calendar, Users, Loader2, Search, ChevronLeft, ChevronRight, Filter, X } from 'lucide-react'
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
  timingMode?: 'FIXED_WINDOW' | 'FLEXIBLE_START'
  courseId: string
  classId?: string
  sectionId?: string
  createdAt: string
  updatedAt?: string
  archived?: boolean
}

interface PagedResponse {
  content: Exam[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  statusCounts: Record<string, number>
}

const ITEMS_PER_PAGE = 10

export const dynamic = 'force-dynamic'

export default function ExamsPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const { user } = useAuth()
  
  // State
  const [exams, setExams] = useState<Exam[]>([])
  const [loading, setLoading] = useState(true)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [statusCounts, setStatusCounts] = useState<Record<string, number>>({})
  
  // Filter state - initialize from URL params
  const [searchQuery, setSearchQuery] = useState(searchParams.get('search') || '')
  const [statusFilter, setStatusFilter] = useState(searchParams.get('status') || 'all')
  const [timingModeFilter, setTimingModeFilter] = useState(searchParams.get('timingMode') || 'all')
  const [currentPage, setCurrentPage] = useState(parseInt(searchParams.get('page') || '0'))
  
  // Debounced search
  const [debouncedSearch, setDebouncedSearch] = useState(searchQuery)
  
  // Check if user can create/edit exams
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const canManageExams = !isInstructor && !isSupportStaff

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery)
      setCurrentPage(0) // Reset to first page on search
    }, 300)
    return () => clearTimeout(timer)
  }, [searchQuery])

  // Update URL when filters change
  useEffect(() => {
    const params = new URLSearchParams()
    if (debouncedSearch) params.set('search', debouncedSearch)
    if (statusFilter !== 'all') params.set('status', statusFilter)
    if (timingModeFilter !== 'all') params.set('timingMode', timingModeFilter)
    if (currentPage > 0) params.set('page', currentPage.toString())
    
    const newUrl = params.toString() ? `?${params.toString()}` : '/exams'
    router.replace(newUrl, { scroll: false })
  }, [debouncedSearch, statusFilter, timingModeFilter, currentPage, router])

  const loadExams = useCallback(async () => {
    try {
      setLoading(true)
      
      // Build query params for backend
      const params = new URLSearchParams()
      params.set('paged', 'true')
      params.set('page', currentPage.toString())
      params.set('size', ITEMS_PER_PAGE.toString())
      if (statusFilter !== 'all') params.set('status', statusFilter)
      if (timingModeFilter !== 'all') params.set('timingMode', timingModeFilter)
      if (debouncedSearch) params.set('search', debouncedSearch)
      
      const response = await apiClient.get<PagedResponse>(`/api/exams?${params.toString()}`)
      
      // ApiClient wraps response in { data: ... }, so access response.data
      const pagedData = (response as any).data || response
      
      if (pagedData && typeof pagedData === 'object' && 'content' in pagedData) {
        setExams(pagedData.content || [])
        setTotalElements(pagedData.totalElements || 0)
        setTotalPages(pagedData.totalPages || 0)
        setStatusCounts(pagedData.statusCounts || {})
      } else {
        // Fallback for legacy response format (array of exams)
        const examsArray = Array.isArray(pagedData) ? pagedData : []
        setExams(examsArray)
        setTotalElements(examsArray.length)
        setTotalPages(1)
      }
    } catch (error) {
      console.error('Failed to load exams:', error)
      toast({
        title: 'Error',
        description: 'Failed to load exams',
        variant: 'destructive'
      })
      setExams([])
    } finally {
      setLoading(false)
    }
  }, [currentPage, statusFilter, timingModeFilter, debouncedSearch, toast])

  useEffect(() => {
    loadExams()
  }, [loadExams])

  // Reset page when filters change (except search which is handled separately)
  useEffect(() => {
    setCurrentPage(0)
  }, [statusFilter, timingModeFilter])

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

  const hasActiveFilters = debouncedSearch || statusFilter !== 'all' || timingModeFilter !== 'all'

  const clearFilters = () => {
    setSearchQuery('')
    setDebouncedSearch('')
    setStatusFilter('all')
    setTimingModeFilter('all')
    setCurrentPage(0)
  }

  if (loading && exams.length === 0) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold">Exams</h1>
          <Badge variant="secondary">{totalElements}</Badge>
        </div>
        {canManageExams && (
          <Button onClick={() => router.push('/exams/new')}>
            <Plus className="h-4 w-4 mr-2" />
            Create Exam
          </Button>
        )}
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="py-4">
          <div className="flex flex-col md:flex-row gap-4">
            {/* Search */}
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <Input
                placeholder="Search exams..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>
            
            {/* Status Filter */}
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-full md:w-[180px]">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Status ({statusCounts.all || 0})</SelectItem>
                <SelectItem value="DRAFT">Draft ({statusCounts.DRAFT || 0})</SelectItem>
                <SelectItem value="SCHEDULED">Scheduled ({statusCounts.SCHEDULED || 0})</SelectItem>
                <SelectItem value="LIVE">Live ({statusCounts.LIVE || 0})</SelectItem>
                <SelectItem value="COMPLETED">Completed ({statusCounts.COMPLETED || 0})</SelectItem>
              </SelectContent>
            </Select>
            
            {/* Timing Mode Filter */}
            <Select value={timingModeFilter} onValueChange={setTimingModeFilter}>
              <SelectTrigger className="w-full md:w-[180px]">
                <SelectValue placeholder="Timing Mode" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Timing</SelectItem>
                <SelectItem value="FIXED_WINDOW">Fixed Window</SelectItem>
                <SelectItem value="FLEXIBLE_START">Flexible</SelectItem>
              </SelectContent>
            </Select>

            {/* Clear Filters */}
            {hasActiveFilters && (
              <Button variant="ghost" onClick={clearFilters} className="gap-2">
                <X className="h-4 w-4" />
                Clear
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Loading overlay */}
      {loading && exams.length > 0 && (
        <div className="flex justify-center py-2">
          <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
        </div>
      )}

      {/* Results */}
      {exams.length === 0 && !loading ? (
        <Card>
          <CardContent className="py-12 text-center">
            {hasActiveFilters ? (
              <>
                <Filter className="h-12 w-12 mx-auto text-gray-300 mb-4" />
                <p className="text-gray-500 mb-4">No exams match your filters</p>
                <Button variant="outline" onClick={clearFilters}>
                  Clear Filters
                </Button>
              </>
            ) : (
              <>
                <p className="text-gray-500 mb-4">No exams created yet</p>
                {canManageExams && (
                  <Button onClick={() => router.push('/exams/new')}>
                    <Plus className="h-4 w-4 mr-2" />
                    Create Your First Exam
                  </Button>
                )}
              </>
            )}
          </CardContent>
        </Card>
      ) : (
        <>
          {/* Exam Cards */}
          <div className="grid gap-4">
            {exams.map((exam) => {
              try {
                return (
                  <Card key={exam.id} className="cursor-pointer hover:shadow-md transition-shadow"
                        onClick={() => router.push(`/exams/${exam.id}`)}>
                    <CardHeader className="pb-2">
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <CardTitle className="text-xl">{exam.title || 'Untitled Exam'}</CardTitle>
                          {exam.description && (
                            <p className="text-sm text-gray-600 mt-1 line-clamp-2">
                              {exam.description}
                            </p>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          {exam.timingMode === 'FLEXIBLE_START' && (
                            <Badge variant="outline" className="text-xs">Flexible</Badge>
                          )}
                          {getStatusBadge(exam.status || 'DRAFT')}
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className={`grid grid-cols-2 gap-4 text-sm ${exam.timingMode === 'FLEXIBLE_START' ? 'md:grid-cols-4' : 'md:grid-cols-5'}`}>
                        {/* For FIXED_WINDOW: show scheduled Start/End */}
                        {exam.timingMode !== 'FLEXIBLE_START' && (
                          <>
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
                          </>
                        )}
                        {/* For FLEXIBLE_START: show when it went live and when ended */}
                        {exam.timingMode === 'FLEXIBLE_START' && (
                          <div className="flex items-center gap-2">
                            <Calendar className="h-4 w-4 text-gray-400" />
                            <div>
                              <div className="text-gray-500">
                                {exam.status === 'LIVE' ? 'Live Since' : exam.status === 'COMPLETED' ? 'Ended' : 'Status'}
                              </div>
                              <div className="font-medium">
                                {exam.status === 'LIVE' && exam.updatedAt 
                                  ? new Date(exam.updatedAt).toLocaleDateString()
                                  : exam.status === 'COMPLETED' && exam.updatedAt
                                  ? new Date(exam.updatedAt).toLocaleDateString()
                                  : exam.status === 'DRAFT' ? 'Not published' : exam.status}
                              </div>
                            </div>
                          </div>
                        )}
                        <div className="flex items-center gap-2">
                          <Users className="h-4 w-4 text-gray-400" />
                          <div>
                            <div className="text-gray-500">Review</div>
                            <div className="font-medium">{exam.reviewMethod || 'INSTRUCTOR'}</div>
                          </div>
                        </div>
                        <div>
                          <div className="text-gray-500">Audience</div>
                          <div className="font-medium">
                            {exam.sectionId ? (
                              <Badge variant="secondary">Specific Section</Badge>
                            ) : exam.classId ? (
                              <Badge variant="default">Class-Wide</Badge>
                            ) : (
                              <Badge variant="outline">All Students</Badge>
                            )}
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

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t pt-4">
              <p className="text-sm text-gray-600">
                Showing {(currentPage * ITEMS_PER_PAGE) + 1} - {Math.min((currentPage + 1) * ITEMS_PER_PAGE, totalElements)} of {totalElements} exams
              </p>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(p => Math.max(0, p - 1))}
                  disabled={currentPage === 0}
                >
                  <ChevronLeft className="h-4 w-4 mr-1" />
                  Previous
                </Button>
                <div className="flex items-center gap-1">
                  {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                    let pageNum: number
                    if (totalPages <= 5) {
                      pageNum = i
                    } else if (currentPage <= 2) {
                      pageNum = i
                    } else if (currentPage >= totalPages - 3) {
                      pageNum = totalPages - 5 + i
                    } else {
                      pageNum = currentPage - 2 + i
                    }
                    return (
                      <Button
                        key={pageNum}
                        variant={currentPage === pageNum ? 'default' : 'outline'}
                        size="sm"
                        className="w-8 h-8 p-0"
                        onClick={() => setCurrentPage(pageNum)}
                      >
                        {pageNum + 1}
                      </Button>
                    )
                  })}
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={currentPage >= totalPages - 1}
                >
                  Next
                  <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
