'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import listPlugin from '@fullcalendar/list'
import interactionPlugin from '@fullcalendar/interaction'
import type { DateSelectArg, EventClickArg, DatesSetArg, EventInput } from '@fullcalendar/core'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  CalendarDays,
  Plus,
  Download,
  Upload,
  Filter,
  X,
  Loader2,
  MapPin,
  Link as LinkIcon,
  Pencil,
  Trash2,
  Clock,
  Users,
  ChevronsUpDown,
} from 'lucide-react'
import { calendarEventsApi, apiClient, sectionsApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { EventImportModal } from '@/components/calendar/EventImportModal'
import type {
  CalendarEvent,
  CreateCalendarEventInput,
} from '@kunal-ak23/edudron-shared-utils'
import { EventType, EventAudience } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

// -- Color map for event types --
const EVENT_TYPE_COLORS: Record<string, { bg: string; border: string; text: string; badge: string }> = {
  HOLIDAY:             { bg: '#dcfce7', border: '#16a34a', text: '#15803d', badge: 'bg-green-100 text-green-800' },
  EXAM:                { bg: '#fee2e2', border: '#dc2626', text: '#b91c1c', badge: 'bg-red-100 text-red-800' },
  SUBMISSION_DEADLINE: { bg: '#ffedd5', border: '#ea580c', text: '#c2410c', badge: 'bg-orange-100 text-orange-800' },
  FACULTY_MEETING:     { bg: '#f3e8ff', border: '#9333ea', text: '#7e22ce', badge: 'bg-purple-100 text-purple-800' },
  REVIEW:              { bg: '#ccfbf1', border: '#0d9488', text: '#0f766e', badge: 'bg-teal-100 text-teal-800' },
  GENERAL:             { bg: '#dbeafe', border: '#2563eb', text: '#1d4ed8', badge: 'bg-blue-100 text-blue-800' },
  CUSTOM:              { bg: '#f3f4f6', border: '#6b7280', text: '#374151', badge: 'bg-gray-100 text-gray-800' },
  PERSONAL:            { bg: '#e0f2fe', border: '#0284c7', text: '#0369a1', badge: 'bg-sky-100 text-sky-800' },
}

const EVENT_TYPE_LABELS: Record<string, string> = {
  HOLIDAY: 'Holiday',
  EXAM: 'Exam',
  SUBMISSION_DEADLINE: 'Submission Deadline',
  FACULTY_MEETING: 'Faculty Meeting',
  REVIEW: 'Review',
  GENERAL: 'General',
  CUSTOM: 'Custom',
  PERSONAL: 'Personal',
}

const AUDIENCE_LABELS: Record<string, string> = {
  TENANT_WIDE: 'Tenant-wide',
  CLASS: 'Class',
  SECTION: 'Section',
  FACULTY_ONLY: 'Faculty Only',
  PERSONAL: 'Personal',
}

interface ClassItem {
  id: string
  name: string
}

interface SectionItem {
  id: string
  name: string
  classId: string
}

interface InstructorItem {
  id: string
  name: string
  email: string
}

// -- Reusable MultiSelect dropdown --
function MultiSelect({ label, options, selected, onChange }: {
  label: string
  options: { value: string; label: string }[]
  selected: string[]
  onChange: (selected: string[]) => void
}) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" className="w-full justify-between text-left font-normal h-10">
          <span className="truncate">
            {selected.length === 0 ? `Select ${label}...` : `${selected.length} selected`}
          </span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-2 max-h-60 overflow-auto" align="start">
        {options.length === 0 && (
          <p className="text-sm text-muted-foreground px-2 py-1.5">No options available</p>
        )}
        {options.map(opt => (
          <label key={opt.value} className="flex items-center gap-2 px-2 py-1.5 hover:bg-accent rounded cursor-pointer text-sm">
            <input
              type="checkbox"
              checked={selected.includes(opt.value)}
              onChange={e => {
                if (e.target.checked) onChange([...selected, opt.value])
                else onChange(selected.filter(v => v !== opt.value))
              }}
              className="rounded border-gray-300"
            />
            {opt.label}
          </label>
        ))}
      </PopoverContent>
    </Popover>
  )
}

// -- Helper: convert CalendarEvent to FullCalendar EventInput --
function toFcEvent(evt: CalendarEvent): EventInput {
  const colors = EVENT_TYPE_COLORS[evt.eventType] || EVENT_TYPE_COLORS.GENERAL
  return {
    id: evt.id,
    title: evt.title,
    start: evt.startDateTime,
    end: evt.endDateTime || undefined,
    allDay: evt.allDay,
    backgroundColor: colors.bg,
    borderColor: colors.border,
    textColor: colors.text,
    extendedProps: { raw: evt },
  }
}

// -- Helper: format datetime for display --
function formatDateTime(iso: string, allDay: boolean): string {
  const d = new Date(iso)
  if (allDay) return d.toLocaleDateString(undefined, { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' })
  return d.toLocaleString(undefined, { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

// -- Helper: to datetime-local input value --
function toDatetimeLocal(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const offset = d.getTimezoneOffset()
  const local = new Date(d.getTime() - offset * 60000)
  return local.toISOString().slice(0, 16)
}

// -- Default empty form state --
function emptyForm(): CreateCalendarEventInput & { _allDay: boolean; _recurring: boolean; _frequency: string; _days: string[]; _count: string; _until: string; _classIds: string[]; _sectionIds: string[]; _targetUserIds: string[] } {
  return {
    title: '',
    description: '',
    eventType: EventType.GENERAL,
    customTypeLabel: '',
    startDateTime: '',
    endDateTime: '',
    allDay: false,
    audience: EventAudience.TENANT_WIDE,
    classId: '',
    sectionId: '',
    classIds: [],
    sectionIds: [],
    targetUserIds: [],
    isRecurring: false,
    recurrenceRule: '',
    meetingLink: '',
    location: '',
    // internal UI helpers
    _allDay: false,
    _recurring: false,
    _frequency: 'WEEKLY',
    _days: [],
    _count: '10',
    _until: '',
    _classIds: [],
    _sectionIds: [],
    _targetUserIds: [],
  }
}

type FormState = ReturnType<typeof emptyForm>

// Build RRULE string from helpers
function buildRRule(form: FormState): string {
  if (!form._recurring) return ''
  let rule = `FREQ=${form._frequency}`
  if (form._frequency === 'WEEKLY' && form._days.length > 0) {
    rule += `;BYDAY=${form._days.join(',')}`
  }
  if (form._until) {
    rule += `;UNTIL=${form._until.replace(/-/g, '')}T235959Z`
  } else if (form._count) {
    rule += `;COUNT=${form._count}`
  }
  return rule
}

export default function CalendarPage() {
  const { toast } = useToast()
  const calendarRef = useRef<FullCalendar>(null)

  // Data
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [classes, setClasses] = useState<ClassItem[]>([])
  const [sections, setSections] = useState<SectionItem[]>([])
  const [loading, setLoading] = useState(false)

  // Current date range from FullCalendar
  const [dateRange, setDateRange] = useState<{ start: string; end: string } | null>(null)

  // Filters
  const [filterType, setFilterType] = useState<string>('ALL')
  const [filterClassId, setFilterClassId] = useState<string>('')
  const [filterSectionId, setFilterSectionId] = useState<string>('')
  const [filteredSections, setFilteredSections] = useState<SectionItem[]>([])

  // Modals
  const [detailEvent, setDetailEvent] = useState<CalendarEvent | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>(emptyForm())
  const [formSections, setFormSections] = useState<SectionItem[]>([])
  const [instructors, setInstructors] = useState<InstructorItem[]>([])
  const [saving, setSaving] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deletingEvent, setDeletingEvent] = useState<CalendarEvent | null>(null)
  const [deleteMode, setDeleteMode] = useState<'single' | 'series'>('single')
  const [importOpen, setImportOpen] = useState(false)

  // -- Load classes on mount --
  useEffect(() => {
    const loadClasses = async () => {
      try {
        const res = await apiClient.get<ClassItem[]>('/api/classes')
        const data = Array.isArray(res.data) ? res.data : []
        setClasses(data)
      } catch {
        // ignore
      }
    }
    loadClasses()
  }, [])

  // -- Load sections when filter class changes --
  useEffect(() => {
    if (!filterClassId) {
      setFilteredSections([])
      setFilterSectionId('')
      return
    }
    const load = async () => {
      try {
        const data = await sectionsApi.listSectionsByClass(filterClassId)
        const mapped = (data || []).map((s: any) => ({ id: s.id, name: s.name, classId: s.classId }))
        setFilteredSections(mapped)
        setSections(prev => {
          const ids = new Set(prev.map(p => p.id))
          const newOnes = mapped.filter((m: SectionItem) => !ids.has(m.id))
          return [...prev, ...newOnes]
        })
      } catch {
        setFilteredSections([])
      }
    }
    load()
  }, [filterClassId])

  // -- Load sections for form when form classIds change --
  useEffect(() => {
    const ids = form._classIds
    if (!ids || ids.length === 0) {
      setFormSections([])
      return
    }
    const load = async () => {
      try {
        const results = await Promise.all(
          ids.map(cid => sectionsApi.listSectionsByClass(cid))
        )
        const allSections = results.flat().map((s: any) => ({ id: s.id, name: s.name, classId: s.classId }))
        setFormSections(allSections)
      } catch {
        setFormSections([])
      }
    }
    load()
  }, [form._classIds])

  // -- Load instructors on mount --
  useEffect(() => {
    const loadInstructors = async () => {
      try {
        const response = await apiClient.get<{ content: { id: string; name: string; email: string }[] }>('/idp/users/paginated?role=INSTRUCTOR&size=100')
        const users = response.data?.content || []
        setInstructors(users.map(u => ({ id: u.id, name: u.name || u.email, email: u.email })))
      } catch {
        // ignore
      }
    }
    loadInstructors()
  }, [])

  // -- Fetch events --
  const fetchEvents = useCallback(async (start: string, end: string) => {
    setLoading(true)
    try {
      const data = await calendarEventsApi.getEvents({
        startDate: start,
        endDate: end,
        eventType: filterType !== 'ALL' ? filterType : undefined,
        classId: filterClassId || undefined,
        sectionId: filterSectionId || undefined,
      })
      setEvents(data)
    } catch {
      toast({ title: 'Error', description: 'Failed to load calendar events', variant: 'destructive' })
    } finally {
      setLoading(false)
    }
  }, [filterType, filterClassId, filterSectionId, toast])

  // Re-fetch when filters change
  useEffect(() => {
    if (dateRange) {
      fetchEvents(dateRange.start, dateRange.end)
    }
  }, [dateRange, fetchEvents])

  // -- FullCalendar callbacks --
  const handleDatesSet = useCallback((arg: DatesSetArg) => {
    const start = arg.startStr.slice(0, 10)
    const end = arg.endStr.slice(0, 10)
    setDateRange({ start, end })
  }, [])

  const handleEventClick = useCallback((arg: EventClickArg) => {
    const raw = arg.event.extendedProps.raw as CalendarEvent
    setDetailEvent(raw)
  }, [])

  const handleDateSelect = useCallback((arg: DateSelectArg) => {
    const f = emptyForm()
    // Always default to non-all-day with time inputs
    if (arg.allDay) {
      // Clicked a date cell — set date with default times
      f.startDateTime = arg.startStr + 'T09:00'
      f.endDateTime = arg.startStr + 'T10:00'
    } else {
      f.startDateTime = arg.startStr
      f.endDateTime = arg.endStr
    }
    f._allDay = false
    f.allDay = false
    setForm(f)
    setEditingId(null)
    setFormOpen(true)
  }, [])

  // -- Form helpers --
  const updateForm = (patch: Partial<FormState>) => setForm(prev => ({ ...prev, ...patch }))

  const openCreateForm = () => {
    setForm(emptyForm())
    setEditingId(null)
    setFormOpen(true)
  }

  const openEditForm = (evt: CalendarEvent) => {
    setDetailEvent(null)
    setEditingId(evt.id)
    // Populate classIds from array field or fall back to single classId
    const classIds = evt.classIds && evt.classIds.length > 0 ? evt.classIds : (evt.classId ? [evt.classId] : [])
    const sectionIds = evt.sectionIds && evt.sectionIds.length > 0 ? evt.sectionIds : (evt.sectionId ? [evt.sectionId] : [])
    const targetUserIds = evt.targetUserIds || []
    setForm({
      title: evt.title,
      description: evt.description || '',
      eventType: evt.eventType,
      customTypeLabel: evt.customTypeLabel || '',
      startDateTime: toDatetimeLocal(evt.startDateTime),
      endDateTime: toDatetimeLocal(evt.endDateTime),
      allDay: evt.allDay,
      audience: evt.audience,
      classId: evt.classId || '',
      sectionId: evt.sectionId || '',
      classIds: classIds,
      sectionIds: sectionIds,
      targetUserIds: targetUserIds,
      isRecurring: evt.isRecurring,
      recurrenceRule: evt.recurrenceRule || '',
      meetingLink: evt.meetingLink || '',
      location: evt.location || '',
      _allDay: evt.allDay,
      _recurring: evt.isRecurring,
      _frequency: 'WEEKLY',
      _days: [],
      _count: '10',
      _until: '',
      _classIds: classIds,
      _sectionIds: sectionIds,
      _targetUserIds: targetUserIds,
    })
    setFormOpen(true)
  }

  const handleSubmit = async () => {
    if (!form.title.trim()) {
      toast({ title: 'Validation', description: 'Title is required', variant: 'destructive' })
      return
    }
    if (!form.startDateTime) {
      toast({ title: 'Validation', description: 'Start date is required', variant: 'destructive' })
      return
    }

    setSaving(true)
    try {
      const payload: CreateCalendarEventInput = {
        title: form.title.trim(),
        description: form.description || undefined,
        eventType: form.eventType as EventType,
        customTypeLabel: form.eventType === EventType.CUSTOM ? form.customTypeLabel : undefined,
        startDateTime: new Date(form.startDateTime).toISOString(),
        endDateTime: form.endDateTime ? new Date(form.endDateTime).toISOString() : undefined,
        allDay: form._allDay,
        audience: form.audience as EventAudience,
        classIds: (form.audience === EventAudience.CLASS || form.audience === EventAudience.SECTION) && form._classIds.length > 0 ? form._classIds : undefined,
        sectionIds: form.audience === EventAudience.SECTION && form._sectionIds.length > 0 ? form._sectionIds : undefined,
        targetUserIds: form.audience === EventAudience.FACULTY_ONLY && form._targetUserIds.length > 0 ? form._targetUserIds : undefined,
        // Keep legacy single fields for backward compat
        classId: (form.audience === EventAudience.CLASS || form.audience === EventAudience.SECTION) && form._classIds.length === 1 ? form._classIds[0] : undefined,
        sectionId: form.audience === EventAudience.SECTION && form._sectionIds.length === 1 ? form._sectionIds[0] : undefined,
        isRecurring: form._recurring,
        recurrenceRule: form._recurring ? buildRRule(form) : undefined,
        meetingLink: form.meetingLink || undefined,
        location: form.location || undefined,
      }

      if (editingId) {
        await calendarEventsApi.updateEvent(editingId, payload)
        toast({ title: 'Success', description: 'Event updated' })
      } else {
        await calendarEventsApi.createEvent(payload)
        toast({ title: 'Success', description: 'Event created' })
      }

      setFormOpen(false)
      if (dateRange) fetchEvents(dateRange.start, dateRange.end)
    } catch {
      toast({ title: 'Error', description: 'Failed to save event', variant: 'destructive' })
    } finally {
      setSaving(false)
    }
  }

  // -- Delete --
  const confirmDelete = (evt: CalendarEvent) => {
    setDeletingEvent(evt)
    setDeleteMode('single')
    setDeleteDialogOpen(true)
  }

  const handleDelete = async () => {
    if (!deletingEvent) return
    try {
      if (deleteMode === 'series' && deletingEvent.isRecurring) {
        await calendarEventsApi.deleteSeries(deletingEvent.recurrenceParentId || deletingEvent.id)
      } else {
        await calendarEventsApi.deleteEvent(deletingEvent.id)
      }
      toast({ title: 'Success', description: deleteMode === 'series' ? 'Entire series deleted' : 'Event deleted' })
      setDeleteDialogOpen(false)
      setDetailEvent(null)
      if (dateRange) fetchEvents(dateRange.start, dateRange.end)
    } catch {
      toast({ title: 'Error', description: 'Failed to delete event', variant: 'destructive' })
    }
  }

  // -- Export --
  const handleExport = async () => {
    if (!dateRange) return
    try {
      const blob = await calendarEventsApi.exportEvents(
        dateRange.start,
        dateRange.end,
        filterClassId || undefined,
        filterSectionId || undefined,
      )
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `calendar-events-${dateRange.start}-to-${dateRange.end}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast({ title: 'Error', description: 'Failed to export events', variant: 'destructive' })
    }
  }

  // -- Clear filters --
  const clearFilters = () => {
    setFilterType('ALL')
    setFilterClassId('')
    setFilterSectionId('')
    setFilteredSections([])
  }
  const hasFilters = filterType !== 'ALL' || filterClassId !== '' || filterSectionId !== ''

  // Convert events for FullCalendar
  const fcEvents: EventInput[] = events.map(toFcEvent)

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-4 gap-3">
        <div>
          <h1 className="text-2xl font-semibold flex items-center gap-2">
            <CalendarDays className="w-6 h-6" />
            Academic Calendar
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Manage holidays, exams, deadlines, and other events
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <Button variant="outline" size="sm" onClick={handleExport}>
            <Download className="w-4 h-4 mr-1" />
            Export
          </Button>
          <Button variant="outline" size="sm" onClick={() => setImportOpen(true)}>
            <Upload className="w-4 h-4 mr-1" />
            Import
          </Button>
          <Button size="sm" onClick={openCreateForm}>
            <Plus className="w-4 h-4 mr-1" />
            Create Event
          </Button>
        </div>
      </div>

      {/* Filter bar */}
      <Card className="mb-4">
        <CardContent className="py-3 px-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <Filter className="w-4 h-4" />
              Filters
            </div>

            <Select value={filterType} onValueChange={setFilterType}>
              <SelectTrigger className="w-[170px] h-8 text-sm">
                <SelectValue placeholder="Event type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Types</SelectItem>
                {Object.entries(EVENT_TYPE_LABELS).map(([val, label]) => (
                  <SelectItem key={val} value={val}>{label}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select value={filterClassId || '_none'} onValueChange={(v: string) => { setFilterClassId(v === '_none' ? '' : v); setFilterSectionId('') }}>
              <SelectTrigger className="w-[170px] h-8 text-sm">
                <SelectValue placeholder="Class" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="_none">All Classes</SelectItem>
                {classes.map(c => (
                  <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={filterSectionId || '_none'}
              onValueChange={(v: string) => setFilterSectionId(v === '_none' ? '' : v)}
              disabled={!filterClassId}
            >
              <SelectTrigger className="w-[170px] h-8 text-sm">
                <SelectValue placeholder="Section" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="_none">All Sections</SelectItem>
                {filteredSections.map(s => (
                  <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            {hasFilters && (
              <Button variant="ghost" size="sm" onClick={clearFilters} className="h-8 px-2 text-xs">
                <X className="w-3 h-3 mr-1" />
                Clear
              </Button>
            )}

            {loading && <Loader2 className="w-4 h-4 animate-spin text-muted-foreground ml-auto" />}
          </div>
        </CardContent>
      </Card>

      {/* Calendar */}
      <Card>
        <CardContent className="p-2 sm:p-4">
          {/* @ts-ignore FullCalendar React 18 type compatibility */}
          <FullCalendar
            ref={calendarRef}
            plugins={[dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            headerToolbar={{
              left: 'prev,next today',
              center: 'title',
              right: 'dayGridMonth,timeGridWeek,listWeek',
            }}
            events={fcEvents}
            datesSet={handleDatesSet}
            eventClick={handleEventClick}
            selectable
            select={handleDateSelect}
            editable={false}
            dayMaxEvents={3}
            height="auto"
            eventDisplay="block"
            nowIndicator
          />
        </CardContent>
      </Card>

      {/* ---- Event Detail Modal ---- */}
      <Dialog open={!!detailEvent} onOpenChange={(open: boolean) => { if (!open) setDetailEvent(null) }}>
        <DialogContent className="sm:max-w-lg">
          {detailEvent && (() => {
            const colors = EVENT_TYPE_COLORS[detailEvent.eventType] || EVENT_TYPE_COLORS.GENERAL
            return (
              <>
                <DialogHeader>
                  <DialogTitle className="text-lg">{detailEvent.title}</DialogTitle>
                  <DialogDescription className="sr-only">Event details</DialogDescription>
                </DialogHeader>
                <div className="space-y-3 mt-2">
                  {/* Badges */}
                  <div className="flex flex-wrap gap-2">
                    <span className={`inline-flex items-center text-xs px-2 py-0.5 rounded-full font-medium ${colors.badge}`}>
                      {EVENT_TYPE_LABELS[detailEvent.eventType] || detailEvent.eventType}
                      {detailEvent.customTypeLabel ? ` - ${detailEvent.customTypeLabel}` : ''}
                    </span>
                    <span className="inline-flex items-center text-xs px-2 py-0.5 rounded-full font-medium bg-slate-100 text-slate-700">
                      <Users className="w-3 h-3 mr-1" />
                      {AUDIENCE_LABELS[detailEvent.audience] || detailEvent.audience}
                    </span>
                    {detailEvent.isRecurring && (
                      <span className="inline-flex items-center text-xs px-2 py-0.5 rounded-full font-medium bg-indigo-100 text-indigo-700">
                        Recurring
                      </span>
                    )}
                  </div>

                  {/* Date/time */}
                  <div className="flex items-start gap-2 text-sm">
                    <Clock className="w-4 h-4 mt-0.5 text-muted-foreground" />
                    <div>
                      <div>{formatDateTime(detailEvent.startDateTime, detailEvent.allDay)}</div>
                      {detailEvent.endDateTime && (
                        <div className="text-muted-foreground">to {formatDateTime(detailEvent.endDateTime, detailEvent.allDay)}</div>
                      )}
                      {detailEvent.allDay && <span className="text-xs text-muted-foreground">(All day)</span>}
                    </div>
                  </div>

                  {/* Description */}
                  {detailEvent.description && (
                    <p className="text-sm text-muted-foreground whitespace-pre-wrap">{detailEvent.description}</p>
                  )}

                  {/* Classes / Sections / Target Instructors */}
                  {(detailEvent.classNames?.length || detailEvent.className || detailEvent.sectionNames?.length || detailEvent.sectionName) && (
                    <div className="space-y-1.5 text-sm">
                      {(detailEvent.classNames?.length || detailEvent.className) && (
                        <div>
                          <span className="font-medium">Class: </span>
                          <span className="flex flex-wrap gap-1 mt-0.5 inline">
                            {(detailEvent.classNames && detailEvent.classNames.length > 0
                              ? detailEvent.classNames
                              : detailEvent.className ? [detailEvent.className] : []
                            ).map((name, i) => (
                              <Badge key={i} variant="secondary" className="text-xs">{name}</Badge>
                            ))}
                          </span>
                        </div>
                      )}
                      {(detailEvent.sectionNames?.length || detailEvent.sectionName) && (
                        <div>
                          <span className="font-medium">Section: </span>
                          <span className="flex flex-wrap gap-1 mt-0.5 inline">
                            {(detailEvent.sectionNames && detailEvent.sectionNames.length > 0
                              ? detailEvent.sectionNames
                              : detailEvent.sectionName ? [detailEvent.sectionName] : []
                            ).map((name, i) => (
                              <Badge key={i} variant="secondary" className="text-xs">{name}</Badge>
                            ))}
                          </span>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Target instructors */}
                  {detailEvent.targetUserNames && detailEvent.targetUserNames.length > 0 && (
                    <div className="text-sm">
                      <span className="font-medium">Instructors: </span>
                      <span className="flex flex-wrap gap-1 mt-0.5 inline">
                        {detailEvent.targetUserNames.map((name, i) => (
                          <Badge key={i} variant="secondary" className="text-xs">{name}</Badge>
                        ))}
                      </span>
                    </div>
                  )}

                  {/* Location */}
                  {detailEvent.location && (
                    <div className="flex items-center gap-2 text-sm">
                      <MapPin className="w-4 h-4 text-muted-foreground" />
                      {detailEvent.location}
                    </div>
                  )}

                  {/* Meeting link */}
                  {detailEvent.meetingLink && (
                    <div className="flex items-center gap-2 text-sm">
                      <LinkIcon className="w-4 h-4 text-muted-foreground" />
                      <a href={detailEvent.meetingLink} target="_blank" rel="noopener noreferrer" className="text-primary underline truncate">
                        {detailEvent.meetingLink}
                      </a>
                    </div>
                  )}

                  {/* Created by */}
                  {detailEvent.createdByName && (
                    <p className="text-xs text-muted-foreground">Created by {detailEvent.createdByName}</p>
                  )}
                </div>

                <DialogFooter className="mt-4 gap-2">
                  <Button variant="outline" size="sm" onClick={() => openEditForm(detailEvent)}>
                    <Pencil className="w-3 h-3 mr-1" />
                    Edit
                  </Button>
                  <Button variant="destructive" size="sm" onClick={() => { setDetailEvent(null); confirmDelete(detailEvent) }}>
                    <Trash2 className="w-3 h-3 mr-1" />
                    Delete
                  </Button>
                </DialogFooter>
              </>
            )
          })()}
        </DialogContent>
      </Dialog>

      {/* ---- Delete Confirmation ---- */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Event</AlertDialogTitle>
            <AlertDialogDescription>
              {deletingEvent?.isRecurring
                ? 'This is a recurring event. What would you like to delete?'
                : 'Are you sure you want to delete this event? This action cannot be undone.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {deletingEvent?.isRecurring && (
            <div className="flex flex-col gap-2 my-2">
              <Button
                variant={deleteMode === 'single' ? 'default' : 'outline'}
                size="sm"
                onClick={() => setDeleteMode('single')}
              >
                Delete this event only
              </Button>
              <Button
                variant={deleteMode === 'series' ? 'default' : 'outline'}
                size="sm"
                onClick={() => setDeleteMode('series')}
              >
                Delete entire series
              </Button>
            </div>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* ---- Create/Edit Form Dialog (Outlook-style) ---- */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto p-0">
          {/* Header bar with save/cancel */}
          <div className="flex items-center justify-between px-6 py-3 border-b bg-muted/30">
            <h2 className="font-semibold text-lg">{editingId ? 'Edit Event' : 'New Event'}</h2>
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" onClick={() => setFormOpen(false)} disabled={saving}>Discard</Button>
              <Button size="sm" onClick={handleSubmit} disabled={saving}>
                {saving && <Loader2 className="w-3 h-3 mr-1.5 animate-spin" />}
                {editingId ? 'Save' : 'Save'}
              </Button>
            </div>
          </div>

          <div className="px-6 py-4 space-y-0">
            {/* Title — large, prominent like Outlook */}
            <Input
              value={form.title}
              onChange={e => updateForm({ title: e.target.value })}
              placeholder="Add a title"
              className="text-xl font-semibold border-0 border-b rounded-none px-0 shadow-none focus-visible:ring-0 focus-visible:border-primary h-12"
            />

            {/* Date/Time row — Outlook-style horizontal layout */}
            <div className="py-4 space-y-3 border-b">
              <div className="flex items-center gap-3">
                <Clock className="w-4 h-4 text-muted-foreground shrink-0" />
                <div className="flex-1">
                  {form._allDay ? (
                    <div className="flex items-center gap-2 flex-wrap">
                      <Input type="date" className="w-40" value={form.startDateTime?.slice(0, 10) || ''} onChange={e => updateForm({ startDateTime: e.target.value })} />
                      <span className="text-muted-foreground text-sm">to</span>
                      <Input type="date" className="w-40" value={form.endDateTime?.slice(0, 10) || ''} onChange={e => updateForm({ endDateTime: e.target.value })} />
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 flex-wrap">
                      <Input type="date" className="w-36" value={form.startDateTime?.slice(0, 10) || ''} onChange={e => { const time = form.startDateTime?.slice(11, 16) || '09:00'; updateForm({ startDateTime: e.target.value + 'T' + time }) }} />
                      <Input type="time" className="w-28" value={form.startDateTime?.slice(11, 16) || ''} onChange={e => { const date = form.startDateTime?.slice(0, 10) || ''; updateForm({ startDateTime: date + 'T' + e.target.value }) }} />
                      <span className="text-muted-foreground text-sm">—</span>
                      <Input type="date" className="w-36" value={form.endDateTime?.slice(0, 10) || ''} onChange={e => { const time = form.endDateTime?.slice(11, 16) || '10:00'; updateForm({ endDateTime: e.target.value + 'T' + time }) }} />
                      <Input type="time" className="w-28" value={form.endDateTime?.slice(11, 16) || ''} onChange={e => { const date = form.endDateTime?.slice(0, 10) || ''; updateForm({ endDateTime: date + 'T' + e.target.value }) }} />
                    </div>
                  )}
                  <div className="flex items-center gap-4 mt-2">
                    <label className="flex items-center gap-2 text-sm text-muted-foreground cursor-pointer">
                      <input type="checkbox" checked={form._allDay} onChange={e => updateForm({ _allDay: e.target.checked, allDay: e.target.checked })} className="rounded border-gray-300" />
                      All day
                    </label>
                    <label className="flex items-center gap-2 text-sm text-muted-foreground cursor-pointer">
                      <input type="checkbox" checked={form._recurring} onChange={e => updateForm({ _recurring: e.target.checked, isRecurring: e.target.checked })} className="rounded border-gray-300" />
                      Repeat
                    </label>
                  </div>
                </div>
              </div>

              {/* Recurrence options — inline when repeat is checked */}
              {form._recurring && (
                <div className="ml-7 space-y-2 p-3 rounded-lg bg-muted/40 border">
                  <div className="flex items-center gap-2 flex-wrap">
                    <Select value={form._frequency} onValueChange={(v: string) => updateForm({ _frequency: v })}>
                      <SelectTrigger className="w-32 h-8 text-sm"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="DAILY">Daily</SelectItem>
                        <SelectItem value="WEEKLY">Weekly</SelectItem>
                        <SelectItem value="MONTHLY">Monthly</SelectItem>
                      </SelectContent>
                    </Select>
                    {form._frequency === 'WEEKLY' && (
                      <div className="flex gap-1">
                        {['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'].map(day => {
                          const active = form._days.includes(day)
                          return (
                            <button key={day} type="button"
                              onClick={() => updateForm({ _days: active ? form._days.filter(d => d !== day) : [...form._days, day] })}
                              className={`w-8 h-8 rounded-full text-xs font-medium transition-colors ${active ? 'bg-primary text-primary-foreground' : 'bg-background border border-input hover:bg-muted'}`}
                            >{day}</button>
                          )
                        })}
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-2 text-sm">
                    <span className="text-muted-foreground">Ends after</span>
                    <Input type="number" min="1" max="365" className="w-20 h-8 text-sm" value={form._count} onChange={e => updateForm({ _count: e.target.value, _until: '' })} placeholder="times" />
                    <span className="text-muted-foreground">or by</span>
                    <Input type="date" className="w-36 h-8 text-sm" value={form._until} onChange={e => updateForm({ _until: e.target.value, _count: '' })} />
                  </div>
                </div>
              )}
            </div>

            {/* Location row */}
            <div className="flex items-center gap-3 py-3 border-b">
              <MapPin className="w-4 h-4 text-muted-foreground shrink-0" />
              <Input value={form.location || ''} onChange={e => updateForm({ location: e.target.value })} placeholder="Add a location" className="border-0 shadow-none focus-visible:ring-0 px-0 h-9" />
            </div>

            {/* Meeting link row */}
            <div className="flex items-center gap-3 py-3 border-b">
              <LinkIcon className="w-4 h-4 text-muted-foreground shrink-0" />
              <Input type="url" value={form.meetingLink || ''} onChange={e => updateForm({ meetingLink: e.target.value })} placeholder="Add online meeting link" className="border-0 shadow-none focus-visible:ring-0 px-0 h-9" />
            </div>

            {/* Event details row — type + audience side by side */}
            <div className="py-3 border-b space-y-3">
              <div className="flex items-start gap-3">
                <CalendarDays className="w-4 h-4 text-muted-foreground shrink-0 mt-2" />
                <div className="flex-1 grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">Event Type</Label>
                    <Select value={form.eventType} onValueChange={(v: string) => updateForm({ eventType: v as EventType })}>
                      <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {Object.entries(EVENT_TYPE_LABELS).map(([val, label]) => (
                          <SelectItem key={val} value={val}>{label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">Audience</Label>
                    <Select value={form.audience} onValueChange={(v: string) => updateForm({ audience: v as EventAudience, classId: '', sectionId: '', _classIds: [], _sectionIds: [], _targetUserIds: [] })}>
                      <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {Object.entries(AUDIENCE_LABELS).map(([val, label]) => (
                          <SelectItem key={val} value={val}>{label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>

              {/* Custom label */}
              {form.eventType === EventType.CUSTOM && (
                <div className="ml-7">
                  <Input value={form.customTypeLabel || ''} onChange={e => updateForm({ customTypeLabel: e.target.value })} placeholder="Custom type label (e.g. Sports Day)" className="h-9" />
                </div>
              )}

              {/* Classes multi-select */}
              {(form.audience === EventAudience.CLASS || form.audience === EventAudience.SECTION) && (
                <div className="ml-7 space-y-1">
                  <Label className="text-xs text-muted-foreground">Classes</Label>
                  <MultiSelect label="classes" options={classes.map(c => ({ value: c.id, label: c.name }))} selected={form._classIds} onChange={(ids) => updateForm({ _classIds: ids, _sectionIds: [] })} />
                </div>
              )}

              {/* Sections multi-select */}
              {form.audience === EventAudience.SECTION && (
                <div className="ml-7 space-y-1">
                  <Label className="text-xs text-muted-foreground">Sections</Label>
                  <MultiSelect
                    label="sections"
                    options={formSections.map(s => { const cls = classes.find(c => c.id === s.classId); return { value: s.id, label: cls ? `${cls.name} — ${s.name}` : s.name } })}
                    selected={form._sectionIds}
                    onChange={(ids) => updateForm({ _sectionIds: ids })}
                  />
                  {form._classIds.length === 0 && <p className="text-xs text-muted-foreground">Select classes first</p>}
                </div>
              )}

              {/* Instructor targeting */}
              {form.audience === EventAudience.FACULTY_ONLY && (
                <div className="ml-7 space-y-1">
                  <Label className="text-xs text-muted-foreground">Target Instructors <span className="font-normal">(empty = all)</span></Label>
                  <MultiSelect label="instructors" options={instructors.map(i => ({ value: i.id, label: `${i.name} (${i.email})` }))} selected={form._targetUserIds} onChange={(ids) => updateForm({ _targetUserIds: ids })} />
                </div>
              )}
            </div>

            {/* Description — expandable text area at the bottom like Outlook body */}
            <div className="py-3">
              <Textarea
                value={form.description || ''}
                onChange={e => updateForm({ description: e.target.value })}
                rows={4}
                placeholder="Add a description or notes..."
                className="border-0 shadow-none focus-visible:ring-0 px-0 resize-none text-sm"
              />
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* ---- Import Modal ---- */}
      <EventImportModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onImportComplete={() => { if (dateRange) fetchEvents(dateRange.start, dateRange.end) }}
      />
    </div>
  )
}
