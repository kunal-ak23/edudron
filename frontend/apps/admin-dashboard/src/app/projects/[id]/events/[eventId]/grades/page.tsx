'use client'

import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  ArrowLeft,
  Download,
  Upload,
  Loader2,
  Check,
  AlertCircle,
} from 'lucide-react'
import { projectsApi } from '@/lib/api'
import { downloadCSV, parseCSV } from '@/lib/csv-utils'
import type {
  ProjectEventDTO,
  ProjectGroupDTO,
  GradeEntry,
} from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'

export const dynamic = 'force-dynamic'

interface StudentGradeRow {
  studentId: string
  studentName: string
  email: string
  groupName: string
  groupId: string
  marks: string // string for controlled input
}

export default function EventGradesPage() {
  const params = useParams()
  const router = useRouter()
  const { toast } = useToast()
  const projectId = params.id as string
  const eventId = params.eventId as string

  const [event, setEvent] = useState<ProjectEventDTO | null>(null)
  const [rows, setRows] = useState<StudentGradeRow[]>([])
  const [loading, setLoading] = useState(true)
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const saveTimerRef = useRef<NodeJS.Timeout | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  // Load data on mount
  useEffect(() => {
    async function load() {
      try {
        const [events, groups, existingGrades] = await Promise.all([
          projectsApi.getEvents(projectId),
          projectsApi.getGroups(projectId),
          projectsApi.getGrades(projectId, eventId),
        ])

        const currentEvent = events.find((e) => e.id === eventId) || null
        setEvent(currentEvent)

        // Build a map of existing grades by studentId
        const gradeMap = new Map<string, number>()
        for (const g of existingGrades) {
          gradeMap.set(g.studentId, g.marks)
        }

        // Flatten groups into student rows
        const studentRows: StudentGradeRow[] = []
        const sortedGroups = [...groups].sort(
          (a, b) => (a.groupNumber ?? 0) - (b.groupNumber ?? 0)
        )

        for (const group of sortedGroups) {
          const groupLabel = group.groupName || `Group ${group.groupNumber}`
          for (const member of group.members || []) {
            const existingMarks = gradeMap.get(member.studentId)
            studentRows.push({
              studentId: member.studentId,
              studentName: member.name || 'Unknown',
              email: member.email || '',
              groupName: groupLabel,
              groupId: group.id,
              marks: existingMarks !== undefined ? String(existingMarks) : '',
            })
          }
        }

        setRows(studentRows)
      } catch (err) {
        toast({
          title: 'Error',
          description: 'Failed to load grades data. Please try again.',
          variant: 'destructive',
        })
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [projectId, eventId, toast])

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
    }
  }, [])

  const triggerAutoSave = useCallback(
    (entries: GradeEntry[]) => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
      setSaveStatus('saving')
      saveTimerRef.current = setTimeout(async () => {
        try {
          await projectsApi.saveGrades(projectId, eventId, entries)
          setSaveStatus('saved')
        } catch {
          setSaveStatus('error')
        }
      }, 1000)
    },
    [projectId, eventId]
  )

  const buildGradeEntries = useCallback(
    (currentRows: StudentGradeRow[]): GradeEntry[] => {
      return currentRows
        .filter((r) => r.marks !== '' && !isNaN(Number(r.marks)))
        .map((r) => ({
          studentId: r.studentId,
          marks: Number(r.marks),
        }))
    },
    []
  )

  const handleMarksChange = useCallback(
    (studentId: string, value: string) => {
      const maxMarks = event?.maxMarks ?? Infinity

      // Allow empty string (clearing the field)
      if (value !== '') {
        const num = Number(value)
        if (isNaN(num)) return
        if (num < 0) return
        if (num > maxMarks) return
      }

      setRows((prev) => {
        const updated = prev.map((r) =>
          r.studentId === studentId ? { ...r, marks: value } : r
        )
        triggerAutoSave(buildGradeEntries(updated))
        return updated
      })
    },
    [event?.maxMarks, triggerAutoSave, buildGradeEntries]
  )

  const handleCSVDownload = useCallback(() => {
    const headers = ['Group', 'Student Name', 'Email', 'Marks']
    const csvRows = rows.map((r) => [r.groupName, r.studentName, r.email, r.marks])
    const eventName = event?.name?.replace(/[^a-zA-Z0-9]/g, '_') || 'grades'
    downloadCSV(`${eventName}_grades.csv`, headers, csvRows)
  }, [rows, event])

  const handleCSVUpload = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return

      const reader = new FileReader()
      reader.onload = (ev) => {
        const text = ev.target?.result as string
        if (!text) return

        const parsed = parseCSV(text)
        if (parsed.length < 2) {
          toast({
            title: 'Invalid CSV',
            description: 'The CSV file must have a header row and at least one data row.',
            variant: 'destructive',
          })
          return
        }

        // Find email and marks column indices from header
        const header = parsed[0].map((h) => h.toLowerCase().trim())
        const emailIdx = header.findIndex(
          (h) => h === 'email' || h === 'e-mail' || h === 'email address'
        )
        const marksIdx = header.findIndex(
          (h) => h === 'marks' || h === 'grade' || h === 'score'
        )

        if (emailIdx === -1 || marksIdx === -1) {
          toast({
            title: 'Invalid CSV format',
            description: 'CSV must have "Email" and "Marks" columns.',
            variant: 'destructive',
          })
          return
        }

        const maxMarks = event?.maxMarks ?? Infinity
        const emailToMarks = new Map<string, string>()
        let skippedCount = 0

        for (let i = 1; i < parsed.length; i++) {
          const row = parsed[i]
          const email = row[emailIdx]?.trim().toLowerCase()
          const marksStr = row[marksIdx]?.trim()
          if (!email || marksStr === undefined) continue

          const num = Number(marksStr)
          if (marksStr === '' || isNaN(num) || num < 0 || num > maxMarks) {
            skippedCount++
            continue
          }
          emailToMarks.set(email, marksStr)
        }

        setRows((prev) => {
          let matchedCount = 0
          const updated = prev.map((r) => {
            const csvMarks = emailToMarks.get(r.email.toLowerCase())
            if (csvMarks !== undefined) {
              matchedCount++
              return { ...r, marks: csvMarks }
            }
            return r
          })
          triggerAutoSave(buildGradeEntries(updated))
          toast({
            title: 'CSV imported',
            description: `Updated ${matchedCount} student(s).${skippedCount > 0 ? ` Skipped ${skippedCount} invalid row(s).` : ''}`,
          })
          return updated
        })
      }
      reader.readAsText(file)

      // Reset so the same file can be re-uploaded
      if (fileInputRef.current) fileInputRef.current.value = ''
    },
    [event?.maxMarks, toast, triggerAutoSave, buildGradeEntries]
  )

  // Compute summary stats
  const gradedRows = rows.filter((r) => r.marks !== '' && !isNaN(Number(r.marks)))
  const totalStudents = rows.length
  const gradedCount = gradedRows.length
  const averageMarks =
    gradedCount > 0
      ? gradedRows.reduce((sum, r) => sum + Number(r.marks), 0) / gradedCount
      : 0

  // Group rows for display
  const groupedRows: { groupName: string; students: StudentGradeRow[] }[] = []
  const groupMap = new Map<string, StudentGradeRow[]>()
  for (const row of rows) {
    const existing = groupMap.get(row.groupName)
    if (existing) {
      existing.push(row)
    } else {
      groupMap.set(row.groupName, [row])
    }
  }
  for (const [groupName, students] of groupMap) {
    groupedRows.push({ groupName, students })
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="container mx-auto py-6 max-w-5xl space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push(`/projects/${projectId}`)}
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-semibold">
              {event?.name || 'Event'} - Grades
            </h1>
            {event?.maxMarks !== undefined && event.maxMarks !== null && (
              <p className="text-sm text-muted-foreground">
                Max marks: {event.maxMarks}
              </p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* Save status indicator */}
          <div className="flex items-center gap-1.5 text-sm">
            {saveStatus === 'saving' && (
              <>
                <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                <span className="text-muted-foreground">Saving...</span>
              </>
            )}
            {saveStatus === 'saved' && (
              <>
                <Check className="h-4 w-4 text-green-600" />
                <span className="text-green-600">Saved</span>
              </>
            )}
            {saveStatus === 'error' && (
              <>
                <AlertCircle className="h-4 w-4 text-red-600" />
                <span className="text-red-600">Error</span>
              </>
            )}
          </div>

          {/* CSV Upload */}
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            className="hidden"
            onChange={handleCSVUpload}
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => fileInputRef.current?.click()}
          >
            <Upload className="h-4 w-4 mr-1.5" />
            Import CSV
          </Button>

          {/* CSV Download */}
          <Button variant="outline" size="sm" onClick={handleCSVDownload}>
            <Download className="h-4 w-4 mr-1.5" />
            Export CSV
          </Button>
        </div>
      </div>

      {/* Grades Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[200px]">Group</TableHead>
                <TableHead>Student Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead className="w-[120px] text-right">
                  Marks{event?.maxMarks ? ` / ${event.maxMarks}` : ''}
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {groupedRows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className="text-center py-8 text-muted-foreground">
                    No students found. Make sure groups have been generated for this project.
                  </TableCell>
                </TableRow>
              )}
              {groupedRows.map((group) =>
                group.students.map((student, idx) => (
                  <TableRow key={student.studentId}>
                    {idx === 0 ? (
                      <TableCell
                        className="font-medium align-top"
                        rowSpan={group.students.length}
                      >
                        <Badge variant="outline">{group.groupName}</Badge>
                      </TableCell>
                    ) : null}
                    <TableCell>{student.studentName}</TableCell>
                    <TableCell className="text-muted-foreground text-sm">
                      {student.email}
                    </TableCell>
                    <TableCell className="text-right">
                      <Input
                        type="number"
                        min={0}
                        max={event?.maxMarks ?? undefined}
                        step="any"
                        placeholder="-"
                        className="w-[100px] ml-auto text-right"
                        value={student.marks}
                        onChange={(e) =>
                          handleMarksChange(student.studentId, e.target.value)
                        }
                      />
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Summary */}
      {totalStudents > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Summary</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-8 text-sm">
              <div>
                <span className="text-muted-foreground">Total students: </span>
                <span className="font-medium">{totalStudents}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Graded: </span>
                <span className="font-medium">
                  {gradedCount} / {totalStudents}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">Average marks: </span>
                <span className="font-medium">
                  {gradedCount > 0 ? averageMarks.toFixed(1) : '-'}
                  {event?.maxMarks ? ` / ${event.maxMarks}` : ''}
                </span>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
