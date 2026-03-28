import { ApiClient } from './ApiClient'

export enum EventType {
  HOLIDAY = 'HOLIDAY',
  EXAM = 'EXAM',
  SUBMISSION_DEADLINE = 'SUBMISSION_DEADLINE',
  FACULTY_MEETING = 'FACULTY_MEETING',
  REVIEW = 'REVIEW',
  GENERAL = 'GENERAL',
  CUSTOM = 'CUSTOM',
  PERSONAL = 'PERSONAL',
}

export enum EventAudience {
  TENANT_WIDE = 'TENANT_WIDE',
  CLASS = 'CLASS',
  SECTION = 'SECTION',
  FACULTY_ONLY = 'FACULTY_ONLY',
  PERSONAL = 'PERSONAL',
}

export interface CalendarEvent {
  id: string
  title: string
  description?: string
  eventType: EventType
  customTypeLabel?: string
  startDateTime: string
  endDateTime?: string
  allDay: boolean
  audience: EventAudience
  classId?: string
  className?: string
  sectionId?: string
  sectionName?: string
  createdByUserId: string
  createdByName?: string
  isRecurring: boolean
  recurrenceRule?: string
  recurrenceParentId?: string
  meetingLink?: string
  location?: string
  color?: string
  metadata?: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface CreateCalendarEventInput {
  title: string
  description?: string
  eventType: EventType
  customTypeLabel?: string
  startDateTime: string
  endDateTime?: string
  allDay?: boolean
  audience: EventAudience
  classId?: string
  sectionId?: string
  isRecurring?: boolean
  recurrenceRule?: string
  meetingLink?: string
  location?: string
  color?: string
  metadata?: Record<string, unknown>
}

export interface CalendarEventImportResult {
  created: number
  errors: number
  errorDetails: { row: number; message: string }[]
}

export class CalendarEventsApi {
  constructor(private apiClient: ApiClient) {}

  async getEvents(params: {
    startDate: string
    endDate: string
    classId?: string
    sectionId?: string
    eventType?: string
    audience?: string
  }): Promise<CalendarEvent[]> {
    const query = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v) query.set(k, v)
    })
    const response = await this.apiClient.get<CalendarEvent[]>(`/api/calendar/events?${query.toString()}`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getEvent(id: string): Promise<CalendarEvent> {
    const response = await this.apiClient.get<CalendarEvent>(`/api/calendar/events/${id}`)
    return response.data
  }

  async createEvent(data: CreateCalendarEventInput): Promise<CalendarEvent> {
    const response = await this.apiClient.post<CalendarEvent>('/api/calendar/events', data)
    return response.data
  }

  async updateEvent(id: string, data: Partial<CreateCalendarEventInput>): Promise<CalendarEvent> {
    const response = await this.apiClient.put<CalendarEvent>(`/api/calendar/events/${id}`, data)
    return response.data
  }

  async deleteEvent(id: string): Promise<void> {
    await this.apiClient.delete(`/api/calendar/events/${id}`)
  }

  async updateSeries(id: string, data: Partial<CreateCalendarEventInput>): Promise<void> {
    await this.apiClient.put(`/api/calendar/events/${id}/series`, data)
  }

  async deleteSeries(id: string): Promise<void> {
    await this.apiClient.delete(`/api/calendar/events/${id}/series`)
  }

  async createPersonalEvent(data: CreateCalendarEventInput): Promise<CalendarEvent> {
    const response = await this.apiClient.post<CalendarEvent>('/api/calendar/events/personal', data)
    return response.data
  }

  async importEvents(file: File): Promise<CalendarEventImportResult> {
    const formData = new FormData()
    formData.append('file', file)
    const response = await this.apiClient.postForm<CalendarEventImportResult>('/api/calendar/events/import', formData)
    return response.data
  }

  async exportEvents(startDate: string, endDate: string, classId?: string, sectionId?: string): Promise<Blob> {
    const query = new URLSearchParams({ startDate, endDate })
    if (classId) query.set('classId', classId)
    if (sectionId) query.set('sectionId', sectionId)
    return this.apiClient.downloadFile(`/api/calendar/events/export?${query.toString()}`)
  }

  async getImportTemplate(): Promise<Blob> {
    return this.apiClient.downloadFile('/api/calendar/events/import/template')
  }
}
