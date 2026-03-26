'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { FolderKanban, Plus, Loader2, ChevronLeft, ChevronRight, Layers } from 'lucide-react'
import { projectsApi, coursesApi } from '@/lib/api'
import type { ProjectDTO, Course } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-300',
  ACTIVE: 'bg-green-100 text-green-700 border-green-300',
  COMPLETED: 'bg-blue-100 text-blue-700 border-blue-300',
}

export default function ProjectsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [projects, setProjects] = useState<ProjectDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [courseFilter, setCourseFilter] = useState<string>('ALL')
  const [courses, setCourses] = useState<Course[]>([])
  const [totalElements, setTotalElements] = useState(0)

  useEffect(() => {
    const loadCourses = async () => {
      try {
        const data = await coursesApi.listCourses()
        setCourses(data)
      } catch { /* Non-critical */ }
    }
    loadCourses()
  }, [])

  const loadProjects = useCallback(async () => {
    setLoading(true)
    try {
      const params: { courseId?: string; status?: string } = {}
      if (statusFilter !== 'ALL') params.status = statusFilter
      if (courseFilter !== 'ALL') params.courseId = courseFilter
      const result = await projectsApi.listProjects(params)
      setProjects(result.content)
      setTotalElements(result.totalElements)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load projects',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }, [statusFilter, courseFilter, toast])

  useEffect(() => {
    loadProjects()
  }, [loadProjects])

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  return (
    <div className="space-y-3">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Badge variant="secondary" className="text-xs">
            {totalElements} project{totalElements !== 1 ? 's' : ''}
          </Badge>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => router.push('/projects/bulk-create')}>
            <Layers className="h-4 w-4 mr-2" />
            Bulk Setup
          </Button>
          <Button onClick={() => router.push('/projects/new')}>
            <Plus className="h-4 w-4 mr-2" />
            Create Project
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="w-48">
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue placeholder="Filter by status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              <SelectItem value="DRAFT">Draft</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="COMPLETED">Completed</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="w-48">
          <Select value={courseFilter} onValueChange={setCourseFilter}>
            <SelectTrigger>
              <SelectValue placeholder="Filter by course" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Courses</SelectItem>
              {courses.map((course) => (
                <SelectItem key={course.id} value={course.id}>
                  {course.title}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <Card>
          <CardContent className="py-12">
            <div className="text-center">
              <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
            </div>
          </CardContent>
        </Card>
      ) : projects.length === 0 ? (
        <Card>
          <CardContent className="text-center py-12">
            <FolderKanban className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-2 text-sm font-medium">No projects</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Get started by creating a new project.
            </p>
            <div className="mt-6">
              <Button onClick={() => router.push('/projects/new')}>
                <Plus className="h-4 w-4 mr-2" />
                Create Project
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-center">Max Marks</TableHead>
                <TableHead>Cutoff</TableHead>
                <TableHead>Late Submission</TableHead>
                <TableHead>Created</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {projects.map((project) => (
                <TableRow
                  key={project.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => router.push(`/projects/${project.id}`)}
                >
                  <TableCell className="font-medium max-w-[300px] truncate">
                    {project.title}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className={STATUS_COLORS[project.status] || ''}
                    >
                      {project.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-center">{project.maxMarks}</TableCell>
                  <TableCell>{formatDate(project.submissionCutoff)}</TableCell>
                  <TableCell>
                    {project.lateSubmissionAllowed ? (
                      <Badge variant="outline" className="bg-yellow-50 text-yellow-700 border-yellow-300">Yes</Badge>
                    ) : (
                      <span className="text-muted-foreground text-sm">No</span>
                    )}
                  </TableCell>
                  <TableCell>{formatDate(project.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
    </div>
  )
}
