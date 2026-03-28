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
  CalendarDays,
  Plus,
  Filter,
  X,
  Loader2,
  MapPin,
  Link as LinkIcon,
  Pencil,
  Trash2,
  Clock,
  Users,
} from 'lucide-react'
import { calendarEventsApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import type {
  CalendarEvent,
  CreateCalendarEventInput,
} from '@kunal-ak23/edudron-shared-utils'
import { EventType, EventAudience } from '@kunal-ak23/edudron-shared-utils'
import { StudentLayout } from '@/components/StudentLayout'

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

// -- Personal event form state --
interface PersonalFormState {
  title: string
  description: string
  startDateTime: string
  endDateTime: string
  _allDay: boolean
  _recurring: boolean
  _frequency: string
  _days: string[]
  _count: string
  _until: string
}

function emptyPersonalForm(): PersonalFormState {
  return {
    title: '',
    description: '',
    startDateTime: '',
    endDateTime: '',
    _allDay: false,
    _recurring: false,
    _frequency: 'WEEKLY',
    _days: [],
    _count: '10',
    _until: '',
  }
}

// Build RRULE string from helpers
function buildRRule(form: PersonalFormState): string {
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
  const [loading, setLoading] = useState(false)

  // Current date range from FullCalendar
  const [dateRange, setDateRange] = useState<{ start: string; end: string } | null>(null)

  // Filter: event type only
  const [filterType, setFilterType] = useState<string>('ALL')

  // Modals
  const [detailEvent, setDetailEvent] = useState<CalendarEvent | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<PersonalFormState>(emptyPersonalForm())
  const [saving, setSaving] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deletingEvent, setDeletingEvent] = useState<CalendarEvent | null>(null)
  const [deleteMode, setDeleteMode] = useState<'single' | 'series'>('single')

  // -- Fetch events --
  const fetchEvents = useCallback(async (start: string, end: string) => {
    setLoading(true)
    try {
      const data = await calendarEventsApi.getEvents({
        startDate: start,
        endDate: end,
        eventType: filterType !== 'ALL' ? filterType : undefined,
      })
      setEvents(data)
    } catch {
      toast({ title: 'Error', description: 'Failed to load calendar events', variant: 'destructive' })
    } finally {
      setLoading(false)
    }
  }, [filterType, toast])

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
    const f = emptyPersonalForm()
    f.startDateTime = arg.startStr
    if (arg.allDay) {
      f._allDay = true
    } else {
      f.startDateTime = arg.startStr
      f.endDateTime = arg.endStr
    }
    setForm(f)
    setEditingId(null)
    setFormOpen(true)
  }, [])

  // -- Form helpers --
  const updateForm = (patch: Partial<PersonalFormState>) => setForm(prev => ({ ...prev, ...patch }))

  const openCreateForm = () => {
    setForm(emptyPersonalForm())
    setEditingId(null)
    setFormOpen(true)
  }

  const openEditForm = (evt: CalendarEvent) => {
    setDetailEvent(null)
    setEditingId(evt.id)
    setForm({
      title: evt.title,
      description: evt.description || '',
      startDateTime: toDatetimeLocal(evt.startDateTime),
      endDateTime: toDatetimeLocal(evt.endDateTime),
      _allDay: evt.allDay,
      _recurring: evt.isRecurring,
      _frequency: 'WEEKLY',
      _days: [],
      _count: '10',
      _until: '',
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
        eventType: EventType.PERSONAL,
        startDateTime: new Date(form.startDateTime).toISOString(),
        endDateTime: form.endDateTime ? new Date(form.endDateTime).toISOString() : undefined,
        allDay: form._allDay,
        audience: EventAudience.PERSONAL,
        isRecurring: form._recurring,
        recurrenceRule: form._recurring ? buildRRule(form) : undefined,
      }

      if (editingId) {
        await calendarEventsApi.updateEvent(editingId, payload)
        toast({ title: 'Success', description: 'Event updated' })
      } else {
        await calendarEventsApi.createPersonalEvent(payload)
        toast({ title: 'Success', description: 'Personal event created' })
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

  // Check if an event is personal (editable by student)
  const isPersonalEvent = (evt: CalendarEvent) => evt.eventType === 'PERSONAL' || evt.audience === 'PERSONAL'

  // -- Clear filters --
  const clearFilters = () => setFilterType('ALL')
  const hasFilters = filterType !== 'ALL'

  // Convert events for FullCalendar
  const fcEvents: EventInput[] = events.map(toFcEvent)

  return (
    <StudentLayout>
      <div className="max-w-[1400px] mx-auto px-6 sm:px-8 lg:px-12 py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-4 gap-3">
          <div>
            <h1 className="text-2xl font-semibold flex items-center gap-2">
              <CalendarDays className="w-6 h-6" />
              My Calendar
            </h1>
            <p className="text-sm text-gray-500 mt-1">
              View your schedule, deadlines, and personal events
            </p>
          </div>
          <Button size="sm" onClick={openCreateForm}>
            <Plus className="w-4 h-4 mr-1" />
            Personal Event
          </Button>
        </div>

        {/* Filter bar */}
        <Card className="mb-4">
          <CardContent className="py-3 px-4">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1.5 text-sm text-gray-500">
                <Filter className="w-4 h-4" />
                Filter
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

              {hasFilters && (
                <Button variant="ghost" size="sm" onClick={clearFilters} className="h-8 px-2 text-xs">
                  <X className="w-3 h-3 mr-1" />
                  Clear
                </Button>
              )}

              {loading && <Loader2 className="w-4 h-4 animate-spin text-gray-400 ml-auto" />}
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
              const personal = isPersonalEvent(detailEvent)
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
                      <Clock className="w-4 h-4 mt-0.5 text-gray-400" />
                      <div>
                        <div>{formatDateTime(detailEvent.startDateTime, detailEvent.allDay)}</div>
                        {detailEvent.endDateTime && (
                          <div className="text-gray-500">to {formatDateTime(detailEvent.endDateTime, detailEvent.allDay)}</div>
                        )}
                        {detailEvent.allDay && <span className="text-xs text-gray-500">(All day)</span>}
                      </div>
                    </div>

                    {/* Description */}
                    {detailEvent.description && (
                      <p className="text-sm text-gray-500 whitespace-pre-wrap">{detailEvent.description}</p>
                    )}

                    {/* Class / Section */}
                    {(detailEvent.className || detailEvent.sectionName) && (
                      <div className="text-sm">
                        {detailEvent.className && <span className="font-medium">Class: </span>}
                        {detailEvent.className && <span>{detailEvent.className}</span>}
                        {detailEvent.sectionName && (
                          <>
                            {detailEvent.className && <span className="mx-1">/</span>}
                            <span className="font-medium">Section: </span>
                            <span>{detailEvent.sectionName}</span>
                          </>
                        )}
                      </div>
                    )}

                    {/* Location */}
                    {detailEvent.location && (
                      <div className="flex items-center gap-2 text-sm">
                        <MapPin className="w-4 h-4 text-gray-400" />
                        {detailEvent.location}
                      </div>
                    )}

                    {/* Meeting link */}
                    {detailEvent.meetingLink && (
                      <div className="flex items-center gap-2 text-sm">
                        <LinkIcon className="w-4 h-4 text-gray-400" />
                        <a href={detailEvent.meetingLink} target="_blank" rel="noopener noreferrer" className="text-primary-600 underline truncate">
                          Join Meeting
                        </a>
                      </div>
                    )}

                    {/* Created by */}
                    {detailEvent.createdByName && !personal && (
                      <p className="text-xs text-gray-400">Created by {detailEvent.createdByName}</p>
                    )}
                  </div>

                  {/* Only show edit/delete for personal events */}
                  {personal && (
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
                  )}
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
              <AlertDialogAction onClick={handleDelete} className="bg-red-600 text-white hover:bg-red-700">
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        {/* ---- Personal Event Create/Edit Form ---- */}
        <Dialog open={formOpen} onOpenChange={setFormOpen}>
          <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>{editingId ? 'Edit Personal Event' : 'Create Personal Event'}</DialogTitle>
              <DialogDescription>
                {editingId ? 'Update your personal event details.' : 'Add a personal event to your calendar.'}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 mt-2">
              {/* Title */}
              <div className="space-y-1.5">
                <Label htmlFor="evt-title">Title *</Label>
                <Input id="evt-title" value={form.title} onChange={e => updateForm({ title: e.target.value })} placeholder="Event title" />
              </div>

              {/* Description */}
              <div className="space-y-1.5">
                <Label htmlFor="evt-desc">Description</Label>
                <Textarea id="evt-desc" value={form.description} onChange={e => updateForm({ description: e.target.value })} rows={3} placeholder="Optional description" />
              </div>

              {/* All Day */}
              <div className="flex items-center justify-between">
                <Label htmlFor="evt-allday">All Day</Label>
                <Switch id="evt-allday" checked={form._allDay} onCheckedChange={(v: boolean) => updateForm({ _allDay: v })} />
              </div>

              {/* Start */}
              <div className="space-y-1.5">
                <Label htmlFor="evt-start">Start {form._allDay ? 'Date' : 'Date & Time'} *</Label>
                <Input
                  id="evt-start"
                  type={form._allDay ? 'date' : 'datetime-local'}
                  value={form._allDay ? (form.startDateTime?.slice(0, 10) || '') : (form.startDateTime || '')}
                  onChange={e => updateForm({ startDateTime: e.target.value })}
                />
              </div>

              {/* End */}
              <div className="space-y-1.5">
                <Label htmlFor="evt-end">End {form._allDay ? 'Date' : 'Date & Time'}</Label>
                <Input
                  id="evt-end"
                  type={form._allDay ? 'date' : 'datetime-local'}
                  value={form._allDay ? (form.endDateTime?.slice(0, 10) || '') : (form.endDateTime || '')}
                  onChange={e => updateForm({ endDateTime: e.target.value })}
                />
              </div>

              {/* Recurring */}
              <div className="flex items-center justify-between">
                <Label htmlFor="evt-recurring">Recurring</Label>
                <Switch id="evt-recurring" checked={form._recurring} onCheckedChange={(v: boolean) => updateForm({ _recurring: v })} />
              </div>

              {form._recurring && (
                <div className="space-y-3 border rounded-lg p-3 bg-gray-50">
                  <div className="space-y-1.5">
                    <Label>Frequency</Label>
                    <Select value={form._frequency} onValueChange={(v: string) => updateForm({ _frequency: v })}>
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="DAILY">Daily</SelectItem>
                        <SelectItem value="WEEKLY">Weekly</SelectItem>
                        <SelectItem value="MONTHLY">Monthly</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {form._frequency === 'WEEKLY' && (
                    <div className="space-y-1.5">
                      <Label>Repeat on</Label>
                      <div className="flex gap-1 flex-wrap">
                        {['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'].map(day => {
                          const active = form._days.includes(day)
                          return (
                            <button
                              key={day}
                              type="button"
                              onClick={() => updateForm({ _days: active ? form._days.filter(d => d !== day) : [...form._days, day] })}
                              className={`w-9 h-8 rounded text-xs font-medium border transition-colors ${active ? 'bg-primary-600 text-white border-primary-600' : 'bg-white border-gray-300 hover:bg-gray-100'}`}
                            >
                              {day}
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                      <Label htmlFor="evt-count">Count</Label>
                      <Input id="evt-count" type="number" min="1" max="365" value={form._count} onChange={e => updateForm({ _count: e.target.value, _until: '' })} placeholder="e.g. 10" />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="evt-until">Until</Label>
                      <Input id="evt-until" type="date" value={form._until} onChange={e => updateForm({ _until: e.target.value, _count: '' })} />
                    </div>
                  </div>
                </div>
              )}
            </div>

            <DialogFooter className="mt-4">
              <Button variant="outline" onClick={() => setFormOpen(false)} disabled={saving}>Cancel</Button>
              <Button onClick={handleSubmit} disabled={saving}>
                {saving && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                {editingId ? 'Update Event' : 'Create Event'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </StudentLayout>
  )
}
