'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
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
  Loader2,
  ArrowLeft,
  Save,
  Play,
  CheckCircle2,
  Users,
  Shuffle,
  Plus,
  Trash2,
  Pencil,
  ClipboardCheck,
  Award,
  ExternalLink,
} from 'lucide-react'
import { projectsApi, sectionsApi, coursesApi, projectQuestionsApi, enrollmentsApi } from '@/lib/api'
import type {
  ProjectDTO,
  ProjectGroupDTO,
  ProjectEventDTO,
  AttendanceEntry,
  GradeEntry,
} from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export const dynamic = 'force-dynamic'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-300',
  ACTIVE: 'bg-green-100 text-green-700 border-green-300',
  COMPLETED: 'bg-blue-100 text-blue-700 border-blue-300',
}

export default function ProjectDetailPage() {
  const params = useParams()
  const router = useRouter()
  const { toast } = useToast()
  const projectId = params.id as string

  const [project, setProject] = useState<ProjectDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // Edit form state
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [maxMarks, setMaxMarks] = useState(100)
  const [submissionCutoff, setSubmissionCutoff] = useState('')
  const [lateSubmissionAllowed, setLateSubmissionAllowed] = useState(false)

  // Groups state
  const [groups, setGroups] = useState<ProjectGroupDTO[]>([])
  const [loadingGroups, setLoadingGroups] = useState(false)
  const [generateDialogOpen, setGenerateDialogOpen] = useState(false)
  const [groupSize, setGroupSize] = useState(4)
  const [generatingGroups, setGeneratingGroups] = useState(false)
  const [assigningStatements, setAssigningStatements] = useState(false)

  // Events state
  const [events, setEvents] = useState<ProjectEventDTO[]>([])
  const [eventDialogOpen, setEventDialogOpen] = useState(false)
  const [editingEvent, setEditingEvent] = useState<ProjectEventDTO | null>(null)
  const [eventName, setEventName] = useState('')
  const [eventDateTime, setEventDateTime] = useState('')
  const [eventZoomLink, setEventZoomLink] = useState('')
  const [eventHasMarks, setEventHasMarks] = useState(false)
  const [eventMaxMarks, setEventMaxMarks] = useState(10)
  const [savingEvent, setSavingEvent] = useState(false)

  // Attendance dialog state
  const [attendanceDialogOpen, setAttendanceDialogOpen] = useState(false)
  const [attendanceEventId, setAttendanceEventId] = useState('')
  const [attendanceEntries, setAttendanceEntries] = useState<AttendanceEntry[]>([])
  const [savingAttendance, setSavingAttendance] = useState(false)

  // Grades dialog state
  const [gradesDialogOpen, setGradesDialogOpen] = useState(false)
  const [gradesEventId, setGradesEventId] = useState('')
  const [gradeEntries, setGradeEntries] = useState<GradeEntry[]>([])
  const [savingGrades, setSavingGrades] = useState(false)

  // Course & section info
  const [courseName, setCourseName] = useState('')
  const [sectionNames, setSectionNames] = useState<string[]>([])

  // Name lookup maps
  const [studentNames, setStudentNames] = useState<Record<string, string>>({})
  const [statementTitles, setStatementTitles] = useState<Record<string, string>>({})

  // Add Sections dialog state
  const [addSectionsDialogOpen, setAddSectionsDialogOpen] = useState(false)
  const [availableSections, setAvailableSections] = useState<{ id: string; name: string; studentCount: number }[]>([])
  const [loadingSections, setLoadingSections] = useState(false)
  const [selectedSectionIds, setSelectedSectionIds] = useState<string[]>([])
  const [addSectionsGroupSize, setAddSectionsGroupSize] = useState(3)
  const [addingSections, setAddingSections] = useState(false)

  const loadProject = useCallback(async () => {
    setLoading(true)
    try {
      const data = await projectsApi.getProject(projectId)
      setProject(data)
      setTitle(data.title)
      setDescription(data.description || '')
      setMaxMarks(data.maxMarks)
      // Convert ISO datetime to datetime-local format (YYYY-MM-DDTHH:mm)
      if (data.submissionCutoff) {
        const d = new Date(data.submissionCutoff)
        const pad = (n: number) => n.toString().padStart(2, '0')
        setSubmissionCutoff(`${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`)
      } else {
        setSubmissionCutoff('')
      }
      setLateSubmissionAllowed(data.lateSubmissionAllowed)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load project',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }, [projectId, toast])

  // Load course and section names
  useEffect(() => {
    if (!project) return
    const loadInfo = async () => {
      try {
        if (project.courseId) {
          const courses = await coursesApi.listCourses()
          const course = courses.find((c: any) => c.id === project.courseId)
          if (course) setCourseName(course.title)
        }
        if (project.sectionId) {
          const ids = project.sectionId.split(',').map((s: string) => s.trim()).filter(Boolean)
          const names: string[] = []
          for (const id of ids) {
            try {
              const section = await sectionsApi.getSection(id)
              names.push(section.name || id)
            } catch {
              names.push(id)
            }
          }
          setSectionNames(names)
        }
      } catch { /* Non-critical */ }
    }
    loadInfo()
  }, [project])

  const loadGroups = useCallback(async () => {
    setLoadingGroups(true)
    try {
      const data = await projectsApi.getGroups(projectId)
      setGroups(data)
    } catch (error) {
      // May return empty if no groups yet
      setGroups([])
    } finally {
      setLoadingGroups(false)
    }
  }, [projectId])

  useEffect(() => {
    loadProject()
    loadGroups()
  }, [loadProject, loadGroups])

  // Resolve student names and problem statement titles when groups change
  useEffect(() => {
    if (!groups.length || !project) return
    const resolve = async () => {
      // Resolve student names from sections
      try {
        const sectionIds = project.sectionId ? project.sectionId.split(',').map((s: string) => s.trim()).filter(Boolean) : []
        const nameMap: Record<string, string> = {}
        for (const sid of sectionIds) {
          try {
            const res = await enrollmentsApi.apiClient.get<any[]>(`/api/sections/${sid}/students`)
            const students = Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : [])
            students.forEach((s: any) => {
              if (s.id && (s.name || s.email)) {
                nameMap[s.id] = s.name || s.email
              }
            })
          } catch { /* skip */ }
        }
        if (Object.keys(nameMap).length > 0) setStudentNames(nameMap)
      } catch { /* skip */ }

      // Resolve problem statement titles
      try {
        if (project.courseId) {
          const result = await projectQuestionsApi.listQuestions({ courseId: project.courseId })
          const titleMap: Record<string, string> = {}
          result.content.forEach((q: any) => {
            titleMap[q.id] = q.projectNumber ? `${q.projectNumber}: ${q.title}` : q.title
          })
          if (Object.keys(titleMap).length > 0) setStatementTitles(titleMap)
        }
      } catch { /* skip */ }
    }
    resolve()
  }, [groups, project])

  const handleSave = async () => {
    setSaving(true)
    try {
      const updated = await projectsApi.updateProject(projectId, {
        title: title.trim(),
        description: description.trim() || undefined,
        maxMarks,
        submissionCutoff: submissionCutoff ? new Date(submissionCutoff).toISOString() : undefined,
        lateSubmissionAllowed,
      })
      setProject(updated)
      toast({ title: 'Project Updated', description: 'Changes saved successfully.' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to save',
        description: extractErrorMessage(error),
      })
    } finally {
      setSaving(false)
    }
  }

  const handleActivate = async () => {
    try {
      const updated = await projectsApi.activateProject(projectId)
      setProject(updated)
      toast({ title: 'Project Activated' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to activate',
        description: extractErrorMessage(error),
      })
    }
  }

  const handleComplete = async () => {
    try {
      const updated = await projectsApi.completeProject(projectId)
      setProject(updated)
      toast({ title: 'Project Completed' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to complete',
        description: extractErrorMessage(error),
      })
    }
  }

  const handleGenerateGroups = async () => {
    setGeneratingGroups(true)
    try {
      const newGroups = await projectsApi.generateGroups(projectId, { groupSize })
      setGroups(newGroups)
      setGenerateDialogOpen(false)
      toast({ title: 'Groups Generated', description: `${newGroups.length} groups created.` })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to generate groups',
        description: extractErrorMessage(error),
      })
    } finally {
      setGeneratingGroups(false)
    }
  }

  const handleAssignStatements = async () => {
    setAssigningStatements(true)
    try {
      await projectsApi.assignStatements(projectId)
      await loadGroups()
      toast({ title: 'Statements Assigned' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to assign statements',
        description: extractErrorMessage(error),
      })
    } finally {
      setAssigningStatements(false)
    }
  }

  // Event handlers
  const openEventDialog = (event?: ProjectEventDTO) => {
    if (event) {
      setEditingEvent(event)
      setEventName(event.name)
      setEventDateTime(event.dateTime || '')
      setEventZoomLink(event.zoomLink || '')
      setEventHasMarks(event.hasMarks)
      setEventMaxMarks(event.maxMarks || 10)
    } else {
      setEditingEvent(null)
      setEventName('')
      setEventDateTime('')
      setEventZoomLink('')
      setEventHasMarks(false)
      setEventMaxMarks(10)
    }
    setEventDialogOpen(true)
  }

  const handleSaveEvent = async () => {
    if (!eventName.trim()) return
    setSavingEvent(true)
    try {
      const eventData = {
        name: eventName.trim(),
        dateTime: eventDateTime || undefined,
        zoomLink: eventZoomLink.trim() || undefined,
        hasMarks: eventHasMarks,
        maxMarks: eventHasMarks ? eventMaxMarks : undefined,
      }

      if (editingEvent) {
        const updated = await projectsApi.updateEvent(projectId, editingEvent.id, eventData)
        setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)))
      } else {
        const created = await projectsApi.addEvent(projectId, eventData)
        setEvents((prev) => [...prev, created])
      }
      setEventDialogOpen(false)
      toast({ title: editingEvent ? 'Event Updated' : 'Event Added' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to save event',
        description: extractErrorMessage(error),
      })
    } finally {
      setSavingEvent(false)
    }
  }

  const handleDeleteEvent = async (eventId: string) => {
    try {
      await projectsApi.deleteEvent(projectId, eventId)
      setEvents((prev) => prev.filter((e) => e.id !== eventId))
      toast({ title: 'Event Deleted' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to delete event',
        description: extractErrorMessage(error),
      })
    }
  }

  // Attendance
  const openAttendanceDialog = (eventId: string) => {
    setAttendanceEventId(eventId)
    // Build entries from all group members
    const entries: AttendanceEntry[] = []
    groups.forEach((group) => {
      group.members.forEach((member) => {
        entries.push({ studentId: member.studentId, present: false })
      })
    })
    setAttendanceEntries(entries)
    setAttendanceDialogOpen(true)
  }

  const toggleAttendance = (studentId: string) => {
    setAttendanceEntries((prev) =>
      prev.map((e) => (e.studentId === studentId ? { ...e, present: !e.present } : e))
    )
  }

  const handleSaveAttendance = async () => {
    setSavingAttendance(true)
    try {
      await projectsApi.saveAttendance(projectId, attendanceEventId, attendanceEntries)
      setAttendanceDialogOpen(false)
      toast({ title: 'Attendance Saved' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to save attendance',
        description: extractErrorMessage(error),
      })
    } finally {
      setSavingAttendance(false)
    }
  }

  // Grades
  const openGradesDialog = (eventId: string) => {
    setGradesEventId(eventId)
    const entries: GradeEntry[] = []
    groups.forEach((group) => {
      group.members.forEach((member) => {
        entries.push({ studentId: member.studentId, marks: 0 })
      })
    })
    setGradeEntries(entries)
    setGradesDialogOpen(true)
  }

  const updateGrade = (studentId: string, marks: number) => {
    setGradeEntries((prev) =>
      prev.map((e) => (e.studentId === studentId ? { ...e, marks } : e))
    )
  }

  const handleSaveGrades = async () => {
    setSavingGrades(true)
    try {
      await projectsApi.saveGrades(projectId, gradesEventId, gradeEntries)
      setGradesDialogOpen(false)
      toast({ title: 'Grades Saved' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to save grades',
        description: extractErrorMessage(error),
      })
    } finally {
      setSavingGrades(false)
    }
  }

  // Add Sections
  const openAddSectionsDialog = async () => {
    if (!project?.courseId) return
    setAddSectionsDialogOpen(true)
    setLoadingSections(true)
    setSelectedSectionIds([])
    setAddSectionsGroupSize(3)
    try {
      const allSectionIds: string[] = await projectsApi.getSectionsByCourse(project.courseId)
      const existingSectionIds = project.sectionId
        ? project.sectionId.split(',').map((s: string) => s.trim()).filter(Boolean)
        : []
      const remainingIds = allSectionIds.filter((id: string) => !existingSectionIds.includes(id))

      const sectionDetails = await Promise.all(
        remainingIds.map(async (id: string) => {
          try {
            const section = await sectionsApi.getSection(id)
            return { id, name: section.name || id, studentCount: section.studentCount ?? 0 }
          } catch {
            return { id, name: id, studentCount: 0 }
          }
        })
      )
      setAvailableSections(sectionDetails)
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load sections',
        description: extractErrorMessage(error),
      })
      setAvailableSections([])
    } finally {
      setLoadingSections(false)
    }
  }

  const toggleSectionSelection = (sectionId: string) => {
    setSelectedSectionIds((prev) =>
      prev.includes(sectionId)
        ? prev.filter((id) => id !== sectionId)
        : [...prev, sectionId]
    )
  }

  const handleAddSections = async () => {
    if (selectedSectionIds.length === 0) return
    setAddingSections(true)
    try {
      await projectsApi.addSections(projectId, {
        sectionIds: selectedSectionIds,
        groupSize: addSectionsGroupSize,
      })
      setAddSectionsDialogOpen(false)
      toast({ title: 'Sections Added', description: `${selectedSectionIds.length} section(s) added to the project.` })
      await loadProject()
      await loadGroups()
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to add sections',
        description: extractErrorMessage(error),
      })
    } finally {
      setAddingSections(false)
    }
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  // Helper to find member info across groups
  const findMember = (studentId: string) => {
    for (const group of groups) {
      const member = group.members.find((m) => m.studentId === studentId)
      if (member) return { ...member, groupNumber: group.groupNumber }
    }
    return null
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!project) {
    return (
      <div className="text-center py-12">
        <p className="text-muted-foreground">Project not found.</p>
        <Button variant="outline" className="mt-4" onClick={() => router.push('/projects')}>
          <ArrowLeft className="h-4 w-4 mr-2" /> Back to Projects
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={() => router.push('/projects')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <h1 className="text-xl font-semibold">{project.title}</h1>
          <Badge variant="outline" className={STATUS_COLORS[project.status] || ''}>
            {project.status}
          </Badge>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={openAddSectionsDialog}>
            <Plus className="h-4 w-4 mr-2" /> Add Sections
          </Button>
          {project.status === 'DRAFT' && (
            <Button variant="outline" onClick={handleActivate}>
              <Play className="h-4 w-4 mr-2" /> Activate
            </Button>
          )}
          {project.status === 'ACTIVE' && (
            <Button variant="outline" onClick={handleComplete}>
              <CheckCircle2 className="h-4 w-4 mr-2" /> Complete
            </Button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="groups">Groups ({groups.length})</TabsTrigger>
          <TabsTrigger value="events">Events ({events.length})</TabsTrigger>
        </TabsList>

        {/* Overview Tab */}
        <TabsContent value="overview">
          <Card>
            <CardHeader>
              <CardTitle>Project Details</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {/* Course & Sections (read-only info) */}
                {(courseName || sectionNames.length > 0) && (
                  <div className="grid grid-cols-2 gap-4 p-3 bg-muted/50 rounded-lg">
                    {courseName && (
                      <div>
                        <Label className="text-xs text-muted-foreground">Course</Label>
                        <p className="text-sm font-medium">{courseName}</p>
                      </div>
                    )}
                    {sectionNames.length > 0 && (
                      <div>
                        <Label className="text-xs text-muted-foreground">Sections</Label>
                        <div className="flex flex-wrap gap-1 mt-0.5">
                          {sectionNames.map((name, i) => (
                            <Badge key={i} variant="secondary" className="text-xs">{name}</Badge>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
                <div className="space-y-2">
                  <Label>Title</Label>
                  <Input value={title} onChange={(e) => setTitle(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>Description</Label>
                  <Textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={3}
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Max Marks</Label>
                    <Input
                      type="number"
                      min={1}
                      value={maxMarks}
                      onChange={(e) => setMaxMarks(parseInt(e.target.value) || 100)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Submission Cutoff</Label>
                    <Input
                      type="datetime-local"
                      value={submissionCutoff}
                      onChange={(e) => setSubmissionCutoff(e.target.value)}
                    />
                  </div>
                </div>
                <div className="flex items-center space-x-3">
                  <Switch
                    checked={lateSubmissionAllowed}
                    onCheckedChange={setLateSubmissionAllowed}
                  />
                  <Label>Allow Late Submission</Label>
                </div>
                <div className="flex justify-end">
                  <Button onClick={handleSave} disabled={saving}>
                    {saving ? (
                      <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Saving...</>
                    ) : (
                      <><Save className="h-4 w-4 mr-2" /> Save Changes</>
                    )}
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Groups Tab */}
        <TabsContent value="groups">
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Button onClick={() => setGenerateDialogOpen(true)}>
                <Users className="h-4 w-4 mr-2" /> Generate Groups
              </Button>
              {groups.length > 0 && (
                <Button variant="outline" onClick={handleAssignStatements} disabled={assigningStatements}>
                  {assigningStatements ? (
                    <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Assigning...</>
                  ) : (
                    <><Shuffle className="h-4 w-4 mr-2" /> Assign Statements</>
                  )}
                </Button>
              )}
            </div>

            {loadingGroups ? (
              <Card>
                <CardContent className="py-8 text-center">
                  <Loader2 className="h-6 w-6 animate-spin text-primary mx-auto" />
                </CardContent>
              </Card>
            ) : groups.length === 0 ? (
              <Card>
                <CardContent className="py-8 text-center">
                  <Users className="mx-auto h-10 w-10 text-muted-foreground" />
                  <p className="mt-2 text-sm text-muted-foreground">
                    No groups yet. Generate groups to get started.
                  </p>
                </CardContent>
              </Card>
            ) : (
              <div className="grid gap-3">
                {groups.map((group) => (
                  <Card key={group.id}>
                    <CardContent className="pt-4">
                      <div className="flex items-start justify-between">
                        <div>
                          <h3 className="font-medium">Group {group.groupNumber}</h3>
                          {group.problemStatementId && (
                            <p className="text-xs text-muted-foreground mt-1">
                              Statement: {statementTitles[group.problemStatementId] || group.problemStatementId}
                            </p>
                          )}
                        </div>
                        {group.submittedAt ? (
                          <div className="text-right">
                            <Badge variant="outline" className="bg-green-50 text-green-700 border-green-300">
                              Submitted
                            </Badge>
                            <p className="text-xs text-muted-foreground mt-1">
                              {formatDate(group.submittedAt)}
                            </p>
                            {group.submissionUrl && (
                              <a
                                href={group.submissionUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center text-xs text-blue-600 hover:underline mt-1"
                              >
                                <ExternalLink className="h-3 w-3 mr-1" /> View Submission
                              </a>
                            )}
                          </div>
                        ) : (
                          <Badge variant="outline" className="bg-gray-50 text-gray-500 border-gray-200">
                            Not Submitted
                          </Badge>
                        )}
                      </div>
                      <div className="mt-3">
                        <p className="text-xs font-medium text-muted-foreground mb-1">Members:</p>
                        <div className="flex flex-wrap gap-2">
                          {group.members.map((member) => (
                            <span
                              key={member.studentId}
                              className="inline-flex items-center px-2 py-1 rounded-md bg-muted text-xs"
                            >
                              {member.name || member.email || studentNames[member.studentId] || member.studentId}
                            </span>
                          ))}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </div>
        </TabsContent>

        {/* Events Tab */}
        <TabsContent value="events">
          <div className="space-y-3">
            <div>
              <Button onClick={() => openEventDialog()}>
                <Plus className="h-4 w-4 mr-2" /> Add Event
              </Button>
            </div>

            {events.length === 0 ? (
              <Card>
                <CardContent className="py-8 text-center">
                  <p className="text-sm text-muted-foreground">
                    No events yet. Add events like reviews, presentations, or demos.
                  </p>
                </CardContent>
              </Card>
            ) : (
              <Card>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Date & Time</TableHead>
                      <TableHead>Zoom Link</TableHead>
                      <TableHead>Marks</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {events.map((event) => (
                      <TableRow key={event.id}>
                        <TableCell className="font-medium">{event.name}</TableCell>
                        <TableCell>{formatDate(event.dateTime)}</TableCell>
                        <TableCell>
                          {event.zoomLink ? (
                            <a
                              href={event.zoomLink}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-blue-600 hover:underline text-sm"
                            >
                              Join
                            </a>
                          ) : (
                            '-'
                          )}
                        </TableCell>
                        <TableCell>
                          {event.hasMarks ? (
                            <Badge variant="secondary">{event.maxMarks} marks</Badge>
                          ) : (
                            '-'
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-1">
                            {groups.length > 0 && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => openAttendanceDialog(event.id)}
                                title="Attendance"
                              >
                                <ClipboardCheck className="h-4 w-4" />
                              </Button>
                            )}
                            {event.hasMarks && groups.length > 0 && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => openGradesDialog(event.id)}
                                title="Grades"
                              >
                                <Award className="h-4 w-4" />
                              </Button>
                            )}
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => openEventDialog(event)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleDeleteEvent(event.id)}
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
        </TabsContent>
      </Tabs>

      {/* Generate Groups Dialog */}
      <Dialog open={generateDialogOpen} onOpenChange={setGenerateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Generate Groups</DialogTitle>
            <DialogDescription>
              Automatically create groups from enrolled students.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Group Size</Label>
              <Input
                type="number"
                min={2}
                max={10}
                value={groupSize}
                onChange={(e) => setGroupSize(parseInt(e.target.value) || 4)}
              />
              <p className="text-xs text-muted-foreground">
                Students will be randomly assigned to groups of this size.
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setGenerateDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleGenerateGroups} disabled={generatingGroups}>
              {generatingGroups ? (
                <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Generating...</>
              ) : (
                'Generate'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Event Dialog */}
      <Dialog open={eventDialogOpen} onOpenChange={setEventDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingEvent ? 'Edit Event' : 'Add Event'}</DialogTitle>
            <DialogDescription>
              {editingEvent ? 'Update event details.' : 'Create a new project event.'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Name <span className="text-destructive">*</span></Label>
              <Input
                value={eventName}
                onChange={(e) => setEventName(e.target.value)}
                placeholder="e.g. Mid-review, Final Presentation"
              />
            </div>
            <div className="space-y-2">
              <Label>Date & Time</Label>
              <Input
                type="datetime-local"
                value={eventDateTime}
                onChange={(e) => setEventDateTime(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>Zoom Link</Label>
              <Input
                value={eventZoomLink}
                onChange={(e) => setEventZoomLink(e.target.value)}
                placeholder="https://zoom.us/j/..."
              />
            </div>
            <div className="flex items-center space-x-3">
              <Switch checked={eventHasMarks} onCheckedChange={setEventHasMarks} />
              <Label>Has Marks</Label>
            </div>
            {eventHasMarks && (
              <div className="space-y-2">
                <Label>Max Marks</Label>
                <Input
                  type="number"
                  min={1}
                  value={eventMaxMarks}
                  onChange={(e) => setEventMaxMarks(parseInt(e.target.value) || 10)}
                />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEventDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSaveEvent} disabled={savingEvent || !eventName.trim()}>
              {savingEvent ? (
                <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Saving...</>
              ) : (
                'Save'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Attendance Dialog */}
      <Dialog open={attendanceDialogOpen} onOpenChange={setAttendanceDialogOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Mark Attendance</DialogTitle>
            <DialogDescription>
              Check the box for students who are present.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            {groups.map((group) => (
              <div key={group.id}>
                <h4 className="text-sm font-medium mb-2">Group {group.groupNumber}</h4>
                <div className="space-y-2 ml-2">
                  {group.members.map((member) => {
                    const entry = attendanceEntries.find((e) => e.studentId === member.studentId)
                    return (
                      <label
                        key={member.studentId}
                        className="flex items-center gap-2 text-sm cursor-pointer"
                      >
                        <Checkbox
                          checked={entry?.present || false}
                          onCheckedChange={() => toggleAttendance(member.studentId)}
                        />
                        <span>{member.name || member.email || member.studentId}</span>
                      </label>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAttendanceDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSaveAttendance} disabled={savingAttendance}>
              {savingAttendance ? (
                <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Saving...</>
              ) : (
                'Save Attendance'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Grades Dialog */}
      <Dialog open={gradesDialogOpen} onOpenChange={setGradesDialogOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Enter Grades</DialogTitle>
            <DialogDescription>
              Enter marks for each student.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            {groups.map((group) => (
              <div key={group.id}>
                <h4 className="text-sm font-medium mb-2">Group {group.groupNumber}</h4>
                <div className="space-y-2 ml-2">
                  {group.members.map((member) => {
                    const entry = gradeEntries.find((e) => e.studentId === member.studentId)
                    return (
                      <div key={member.studentId} className="flex items-center gap-3">
                        <span className="text-sm flex-1">
                          {member.name || member.email || member.studentId}
                        </span>
                        <Input
                          type="number"
                          min={0}
                          className="w-24"
                          value={entry?.marks || 0}
                          onChange={(e) =>
                            updateGrade(member.studentId, parseInt(e.target.value) || 0)
                          }
                        />
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setGradesDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSaveGrades} disabled={savingGrades}>
              {savingGrades ? (
                <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Saving...</>
              ) : (
                'Save Grades'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add Sections Dialog */}
      <Dialog open={addSectionsDialogOpen} onOpenChange={setAddSectionsDialogOpen}>
        <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Add Sections</DialogTitle>
            <DialogDescription>
              Select sections to add to this project. Students from these sections will be grouped automatically.
            </DialogDescription>
          </DialogHeader>
          {loadingSections ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin text-primary" />
            </div>
          ) : availableSections.length === 0 ? (
            <p className="text-sm text-muted-foreground py-4 text-center">
              No additional sections available for this course.
            </p>
          ) : (
            <div className="space-y-4">
              <div className="space-y-2">
                {availableSections.map((section) => (
                  <label
                    key={section.id}
                    className="flex items-center gap-3 p-2 rounded-md hover:bg-muted cursor-pointer"
                  >
                    <Checkbox
                      checked={selectedSectionIds.includes(section.id)}
                      onCheckedChange={() => toggleSectionSelection(section.id)}
                    />
                    <div className="flex-1">
                      <span className="text-sm font-medium">{section.name}</span>
                      <span className="text-xs text-muted-foreground ml-2">
                        ({section.studentCount} students)
                      </span>
                    </div>
                  </label>
                ))}
              </div>
              <div className="space-y-2">
                <Label>Group Size</Label>
                <Input
                  type="number"
                  min={1}
                  value={addSectionsGroupSize}
                  onChange={(e) => setAddSectionsGroupSize(parseInt(e.target.value) || 3)}
                />
                <p className="text-xs text-muted-foreground">
                  Students in each new section will be grouped into teams of this size.
                </p>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddSectionsDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleAddSections}
              disabled={addingSections || selectedSectionIds.length === 0}
            >
              {addingSections ? (
                <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Adding...</>
              ) : (
                `Add ${selectedSectionIds.length > 0 ? selectedSectionIds.length + ' ' : ''}Section${selectedSectionIds.length !== 1 ? 's' : ''}`
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
