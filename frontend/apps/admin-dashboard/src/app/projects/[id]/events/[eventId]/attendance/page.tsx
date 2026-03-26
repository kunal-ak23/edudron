'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
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
import type {
  ProjectDTO,
  ProjectEventDTO,
  ProjectGroupDTO,
  AttendanceEntry,
} from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { downloadCSV, parseCSV } from '@/lib/csv-utils'

interface StudentAttendanceRow {
  studentId: string
  name: string
  email: string
  groupId: string
  groupName: string
  present: boolean
}

export default function AttendancePage() {
  const params = useParams()
  const router = useRouter()
  const { toast } = useToast()
  const projectId = params.id as string
  const eventId = params.eventId as string

  const [project, setProject] = useState<ProjectDTO | null>(null)
  const [event, setEvent] = useState<ProjectEventDTO | null>(null)
  const [rows, setRows] = useState<StudentAttendanceRow[]>([])
  const [loading, setLoading] = useState(true)
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')

  const saveTimerRef = useRef<NodeJS.Timeout | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
    }
  }, [])

  const triggerAutoSave = useCallback(
    (entries: AttendanceEntry[]) => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
      setSaveStatus('saving')
      saveTimerRef.current = setTimeout(async () => {
        try {
          await projectsApi.saveAttendance(projectId, eventId, entries)
          setSaveStatus('saved')
        } catch {
          setSaveStatus('error')
        }
      }, 1000)
    },
    [projectId, eventId]
  )

  // Load data on mount
  useEffect(() => {
    async function load() {
      try {
        const [proj, events, groups, existing] = await Promise.all([
          projectsApi.getProject(projectId),
          projectsApi.getEvents(projectId),
          projectsApi.getGroups(projectId),
          projectsApi.getAttendance(projectId, eventId),
        ])

        setProject(proj)

        const matchedEvent = events.find((e) => e.id === eventId)
        setEvent(matchedEvent || null)

        // Build a lookup of existing attendance by studentId
        const attendanceMap = new Map<string, boolean>()
        for (const entry of existing) {
          attendanceMap.set(entry.studentId, entry.present)
        }

        // Build rows from groups + members
        const studentRows: StudentAttendanceRow[] = []
        for (const group of groups) {
          const groupLabel = group.groupName || `Group ${group.groupNumber}`
          for (const member of group.members) {
            studentRows.push({
              studentId: member.studentId,
              name: member.name || 'Unknown',
              email: member.email || '',
              groupId: group.id,
              groupName: groupLabel,
              present: attendanceMap.get(member.studentId) ?? false,
            })
          }
        }

        setRows(studentRows)
      } catch (err) {
        toast({
          title: 'Error loading attendance',
          description: extractErrorMessage(err),
          variant: 'destructive',
        })
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [projectId, eventId, toast])

  // Toggle a single student
  const toggleStudent = useCallback(
    (studentId: string) => {
      setRows((prev) => {
        const updated = prev.map((r) =>
          r.studentId === studentId ? { ...r, present: !r.present } : r
        )
        triggerAutoSave(
          updated.map((r) => ({ studentId: r.studentId, present: r.present }))
        )
        return updated
      })
    },
    [triggerAutoSave]
  )

  // Select / Deselect all in a group
  const toggleGroup = useCallback(
    (groupId: string, selectAll: boolean) => {
      setRows((prev) => {
        const updated = prev.map((r) =>
          r.groupId === groupId ? { ...r, present: selectAll } : r
        )
        triggerAutoSave(
          updated.map((r) => ({ studentId: r.studentId, present: r.present }))
        )
        return updated
      })
    },
    [triggerAutoSave]
  )

  // CSV Download
  const handleDownload = useCallback(() => {
    const headers = ['Group', 'Student Name', 'Email', 'Present']
    const csvRows = rows.map((r) => [
      r.groupName,
      r.name,
      r.email,
      r.present ? 'Yes' : 'No',
    ])
    const eventName = event?.name || 'attendance'
    const filename = `${eventName.replace(/\s+/g, '_')}_attendance.csv`
    downloadCSV(filename, headers, csvRows)
  }, [rows, event])

  // CSV Upload
  const handleUpload = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return

      const reader = new FileReader()
      reader.onload = (ev) => {
        try {
          const text = ev.target?.result as string
          const parsed = parseCSV(text)
          if (parsed.length < 2) {
            toast({
              title: 'Invalid CSV',
              description: 'The file must have a header row and at least one data row.',
              variant: 'destructive',
            })
            return
          }

          const headerRow = parsed[0].map((h) => h.toLowerCase().trim())
          const emailIdx = headerRow.findIndex(
            (h) => h === 'email' || h === 'e-mail'
          )
          const presentIdx = headerRow.findIndex(
            (h) =>
              h === 'present' || h === 'attendance' || h === 'attended'
          )

          if (emailIdx === -1 || presentIdx === -1) {
            toast({
              title: 'Missing columns',
              description:
                'CSV must have "Email" and "Present" (or "Attendance") columns.',
              variant: 'destructive',
            })
            return
          }

          // Build email -> present map from CSV
          const csvMap = new Map<string, boolean>()
          for (let i = 1; i < parsed.length; i++) {
            const row = parsed[i]
            const email = (row[emailIdx] || '').trim().toLowerCase()
            const val = (row[presentIdx] || '').trim().toLowerCase()
            if (email) {
              csvMap.set(email, val === 'yes' || val === 'true' || val === '1')
            }
          }

          setRows((prev) => {
            let matched = 0
            const updated = prev.map((r) => {
              const csvVal = csvMap.get(r.email.toLowerCase())
              if (csvVal !== undefined) {
                matched++
                return { ...r, present: csvVal }
              }
              return r
            })
            triggerAutoSave(
              updated.map((r) => ({
                studentId: r.studentId,
                present: r.present,
              }))
            )
            toast({
              title: 'CSV imported',
              description: `Updated attendance for ${matched} student(s).`,
            })
            return updated
          })
        } catch {
          toast({
            title: 'Error parsing CSV',
            description: 'Could not parse the uploaded file.',
            variant: 'destructive',
          })
        }
      }
      reader.readAsText(file)

      // Reset file input so the same file can be re-uploaded
      e.target.value = ''
    },
    [toast, triggerAutoSave]
  )

  // Group rows by groupId preserving order
  const groupedRows = rows.reduce<
    { groupId: string; groupName: string; students: StudentAttendanceRow[] }[]
  >((acc, row) => {
    let group = acc.find((g) => g.groupId === row.groupId)
    if (!group) {
      group = { groupId: row.groupId, groupName: row.groupName, students: [] }
      acc.push(group)
    }
    group.students.push(row)
    return acc
  }, [])

  // Summary stats
  const totalStudents = rows.length
  const totalPresent = rows.filter((r) => r.present).length

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="container mx-auto py-6 max-w-5xl space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push(`/projects/${projectId}`)}
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-semibold">
              {event?.name || 'Event'} - Attendance
            </h1>
            {project && (
              <p className="text-sm text-muted-foreground">{project.title}</p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Save status indicator */}
          <SaveStatusBadge status={saveStatus} />

          {/* CSV Upload */}
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            className="hidden"
            onChange={handleUpload}
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => fileInputRef.current?.click()}
          >
            <Upload className="h-4 w-4 mr-1.5" />
            Upload CSV
          </Button>

          {/* CSV Download */}
          <Button variant="outline" size="sm" onClick={handleDownload}>
            <Download className="h-4 w-4 mr-1.5" />
            Download CSV
          </Button>
        </div>
      </div>

      {/* Summary */}
      <div className="flex items-center gap-4 text-sm text-muted-foreground">
        <span>
          {totalPresent} / {totalStudents} present
        </span>
        {totalStudents > 0 && (
          <Badge variant="secondary">
            {Math.round((totalPresent / totalStudents) * 100)}%
          </Badge>
        )}
      </div>

      {/* Grouped tables */}
      {groupedRows.length === 0 && (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            No groups or students found for this project.
          </CardContent>
        </Card>
      )}

      {groupedRows.map((group) => {
        const groupPresent = group.students.filter((s) => s.present).length
        const allPresent = groupPresent === group.students.length
        const nonePresent = groupPresent === 0

        return (
          <Card key={group.groupId}>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base font-medium">
                  {group.groupName}
                  <span className="ml-2 text-sm font-normal text-muted-foreground">
                    ({groupPresent}/{group.students.length} present)
                  </span>
                </CardTitle>
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-xs"
                    disabled={allPresent}
                    onClick={() => toggleGroup(group.groupId, true)}
                  >
                    Select All
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-xs"
                    disabled={nonePresent}
                    onClick={() => toggleGroup(group.groupId, false)}
                  >
                    Deselect All
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="pt-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[250px]">Student Name</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead className="w-[100px] text-center">
                      Present
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {group.students.map((student) => (
                    <TableRow key={student.studentId}>
                      <TableCell className="font-medium">
                        {student.name}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {student.email}
                      </TableCell>
                      <TableCell className="text-center">
                        <Checkbox
                          checked={student.present}
                          onCheckedChange={() =>
                            toggleStudent(student.studentId)
                          }
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}

function SaveStatusBadge({
  status,
}: {
  status: 'idle' | 'saving' | 'saved' | 'error'
}) {
  if (status === 'idle') return null

  if (status === 'saving') {
    return (
      <Badge variant="secondary" className="gap-1.5">
        <Loader2 className="h-3 w-3 animate-spin" />
        Saving...
      </Badge>
    )
  }

  if (status === 'saved') {
    return (
      <Badge
        variant="secondary"
        className="gap-1.5 bg-green-100 text-green-700 border-green-300"
      >
        <Check className="h-3 w-3" />
        Saved
      </Badge>
    )
  }

  return (
    <Badge variant="destructive" className="gap-1.5">
      <AlertCircle className="h-3 w-3" />
      Error saving
    </Badge>
  )
}
