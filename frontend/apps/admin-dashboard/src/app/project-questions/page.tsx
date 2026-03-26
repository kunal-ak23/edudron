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
import { FileText, Plus, Loader2, Upload, Trash2, Pencil, Download } from 'lucide-react'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { projectQuestionsApi, coursesApi } from '@/lib/api'
import type { ProjectQuestionDTO, Course } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

const DIFFICULTY_COLORS: Record<string, string> = {
  EASY: 'bg-green-100 text-green-700 border-green-300',
  MEDIUM: 'bg-yellow-100 text-yellow-700 border-yellow-300',
  HARD: 'bg-red-100 text-red-700 border-red-300',
}

export default function ProjectQuestionsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [questions, setQuestions] = useState<ProjectQuestionDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [courseFilter, setCourseFilter] = useState<string>('ALL')
  const [difficultyFilter, setDifficultyFilter] = useState<string>('ALL')
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

  const loadQuestions = useCallback(async () => {
    setLoading(true)
    try {
      const params: { courseId?: string; difficulty?: string } = {}
      if (courseFilter !== 'ALL') params.courseId = courseFilter
      if (difficultyFilter !== 'ALL') params.difficulty = difficultyFilter
      const result = await projectQuestionsApi.listQuestions(params)
      setQuestions(result.content)
      setTotalElements(result.totalElements)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load questions',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }, [courseFilter, difficultyFilter, toast])

  useEffect(() => {
    loadQuestions()
  }, [loadQuestions])

  const handleDelete = async (id: string) => {
    try {
      await projectQuestionsApi.deleteQuestion(id)
      setQuestions((prev) => prev.filter((q) => q.id !== id))
      setTotalElements((prev) => prev - 1)
      toast({ title: 'Question Deleted' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to delete question',
        description: extractErrorMessage(error),
      })
    }
  }

  return (
    <div className="space-y-3">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <Badge variant="secondary" className="text-xs">
          {totalElements} question{totalElements !== 1 ? 's' : ''}
        </Badge>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => {
              const csv = `projectNumber,title,problemStatement,keyTechnologies,tags,difficulty\n"DA-01","Retail Performance Dashboard","Develop a robust Retail Performance Dashboard to monitor KPIs...","Excel;Power BI;Tableau","Data Analytics","MEDIUM"\n"DA-02","Healthcare Trend Analyzer","Perform a deep-dive analysis of historical Healthcare data...","Excel;Power BI;Tableau","Data Analytics","MEDIUM"\n"AAI-01","Autonomous Retail Researcher Agent","Develop an LLM-powered autonomous researcher agent...","Python;LangChain;CrewAI;LLM API","Agentic AI","HARD"`
              const blob = new Blob([csv], { type: 'text/csv' })
              const url = URL.createObjectURL(blob)
              const a = document.createElement('a')
              a.href = url
              a.download = 'project_questions_sample.csv'
              a.click()
              URL.revokeObjectURL(url)
            }}
          >
            <Download className="h-4 w-4 mr-2" />
            Download Sample
          </Button>
          <Button variant="outline" onClick={() => router.push('/project-questions/bulk-upload')}>
            <Upload className="h-4 w-4 mr-2" />
            Bulk Upload
          </Button>
          <Button onClick={() => router.push('/project-questions/new')}>
            <Plus className="h-4 w-4 mr-2" />
            Add Question
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
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
        <div className="w-48">
          <Select value={difficultyFilter} onValueChange={setDifficultyFilter}>
            <SelectTrigger>
              <SelectValue placeholder="Filter by difficulty" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Difficulties</SelectItem>
              <SelectItem value="EASY">Easy</SelectItem>
              <SelectItem value="MEDIUM">Medium</SelectItem>
              <SelectItem value="HARD">Hard</SelectItem>
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
      ) : questions.length === 0 ? (
        <Card>
          <CardContent className="text-center py-12">
            <FileText className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-2 text-sm font-medium">No questions</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Add project problem statements to the question bank.
            </p>
            <div className="mt-6">
              <Button onClick={() => router.push('/project-questions/new')}>
                <Plus className="h-4 w-4 mr-2" />
                Add Question
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Project No</TableHead>
                <TableHead>Title</TableHead>
                <TableHead>Problem Statement</TableHead>
                <TableHead>Technologies</TableHead>
                <TableHead>Difficulty</TableHead>
                <TableHead>Active</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {questions.map((question) => (
                <TableRow key={question.id}>
                  <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                    {question.projectNumber || '-'}
                  </TableCell>
                  <TableCell className="font-medium max-w-[200px] truncate">
                    {question.title}
                  </TableCell>
                  <TableCell className="max-w-[300px] truncate text-sm text-muted-foreground">
                    {question.problemStatement}
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {question.keyTechnologies?.slice(0, 3).map((tech) => (
                        <Badge key={tech} variant="secondary" className="text-xs">
                          {tech}
                        </Badge>
                      ))}
                      {(question.keyTechnologies?.length || 0) > 3 && (
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Badge variant="secondary" className="text-xs cursor-help">
                                +{(question.keyTechnologies?.length || 0) - 3}
                              </Badge>
                            </TooltipTrigger>
                            <TooltipContent>
                              <div className="flex flex-wrap gap-1 max-w-[250px]">
                                {question.keyTechnologies!.slice(3).map((tech) => (
                                  <Badge key={tech} variant="secondary" className="text-xs">
                                    {tech}
                                  </Badge>
                                ))}
                              </div>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    {question.difficulty && (
                      <Badge
                        variant="outline"
                        className={DIFFICULTY_COLORS[question.difficulty] || ''}
                      >
                        {question.difficulty}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    {question.isActive ? (
                      <Badge variant="outline" className="bg-green-50 text-green-700 border-green-300">
                        Yes
                      </Badge>
                    ) : (
                      <span className="text-muted-foreground text-sm">No</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => router.push(`/project-questions/new?editId=${question.id}`)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDelete(question.id)}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
    </div>
  )
}
