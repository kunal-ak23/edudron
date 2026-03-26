import { ApiClient } from './ApiClient'

// ============ Types ============

export interface ProjectAttachmentDTO {
  id: string
  projectId: string
  groupId?: string
  context: string
  fileUrl: string
  fileName: string
  fileSizeBytes?: number
  mimeType?: string
  uploadedBy?: string
  createdAt?: string
}

export interface ProjectEventSubmissionDTO {
  id: string
  projectId: string
  eventId: string
  groupId: string
  submissionUrl?: string
  submissionText?: string
  submittedBy: string
  submittedAt: string
  version: number
  status: string
  attachments?: ProjectAttachmentDTO[]
  createdAt?: string
  updatedAt?: string
}

export interface ProjectEventFeedbackDTO {
  id: string
  submissionId: string
  eventId: string
  groupId: string
  comment: string
  feedbackBy: string
  feedbackAt: string
  status: string
}

export interface SubmitEventRequest {
  submissionUrl?: string
  submissionText?: string
  attachments?: AttachmentInfo[]
}

export interface EventFeedbackRequest {
  comment: string
  status: string // REVIEWED or NEEDS_REVISION
}

export interface ProjectDTO {
  id: string
  courseId?: string
  sectionId: string
  title: string
  description?: string
  maxMarks: number
  submissionCutoff?: string
  lateSubmissionAllowed: boolean
  status: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  currentEventId?: string
  statementAttachments?: ProjectAttachmentDTO[]
}

export interface ProjectGroupDTO {
  id: string
  projectId: string
  groupNumber: number
  groupName?: string
  problemStatementId?: string
  submissionUrl?: string
  submittedAt?: string
  submittedBy?: string
  members: Array<{ studentId: string; name?: string; email?: string }>
  submissionAttachments?: ProjectAttachmentDTO[]
}

export interface ProjectEventDTO {
  id: string
  projectId: string
  name: string
  dateTime?: string
  zoomLink?: string
  hasMarks: boolean
  maxMarks?: number
  sequence?: number
  sectionId?: string
  hasSubmission?: boolean
}

export interface ProjectQuestionDTO {
  id: string
  courseId: string
  projectNumber?: string
  title: string
  problemStatement: string
  keyTechnologies?: string[]
  tags?: string[]
  difficulty?: string
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CreateProjectRequest {
  courseId?: string
  sectionId: string
  title: string
  description?: string
  maxMarks?: number
  submissionCutoff?: string
  lateSubmissionAllowed?: boolean
}

export interface BulkProjectSetupRequest {
  courseId: string
  sectionIds: string[]
  groupSize: number
  title: string
  description?: string
  maxMarks?: number
  submissionCutoff?: string
  lateSubmissionAllowed?: boolean
  mixSections?: boolean
  sectionNames?: Record<string, string>
  sectionGroupCounts?: Record<string, number>
  totalGroupCount?: number
  selectedQuestionIds?: string[]
  events?: Array<{
    name: string
    dateTime?: string
    zoomLink?: string
    hasMarks?: boolean
    maxMarks?: number
    sequence?: number
  }>
  eventsBySectionId?: Record<string, Array<{ name: string; dateTime?: string; zoomLink?: string; hasMarks?: boolean; maxMarks?: number; sequence?: number }>>
}

export interface AddSectionsRequest {
  sectionIds: string[]
  groupSize: number
}

export interface GenerateGroupsRequest {
  groupSize: number
}

export interface AttachmentInfo {
  fileUrl: string
  fileName: string
  fileSizeBytes?: number
  mimeType?: string
}

export interface SubmitProjectRequest {
  submissionUrl: string
  attachments?: AttachmentInfo[]
}

export interface ProjectTemplateDTO {
  id: string
  name: string
  description?: string
  maxMarks: number
  groupSize: number
  templateData: {
    events: Array<{ name: string; hasMarks?: boolean; maxMarks?: number; hasSubmission?: boolean; sequence?: number }>
    maxMarks?: number
    lateSubmissionAllowed?: boolean
  }
  createdBy?: string
  createdAt?: string
}

export interface AttendanceEntry {
  studentId: string
  present: boolean
}

export interface GradeEntry {
  studentId: string
  marks: number
}

// ============ ProjectsApi ============

export class ProjectsApi {
  constructor(private apiClient: ApiClient) {}

  // ---- Admin ----

  async createProject(data: CreateProjectRequest): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>('/api/projects', data)
    return response.data
  }

  async bulkSetup(data: BulkProjectSetupRequest): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>('/api/projects/bulk-setup', data)
    return response.data
  }

  async getSectionsByCourse(courseId: string): Promise<string[]> {
    const response = await this.apiClient.get<string[]>(`/api/projects/sections-by-course/${courseId}`)
    return Array.isArray(response.data) ? response.data : (Array.isArray(response) ? response : [])
  }

  async addSections(projectId: string, data: AddSectionsRequest): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>(`/api/projects/${projectId}/add-sections`, data)
    return response.data
  }

  async listProjects(params?: { courseId?: string; sectionId?: string; status?: string }): Promise<{ content: ProjectDTO[]; totalElements: number; totalPages: number }> {
    let url = '/api/projects'
    const queryParts: string[] = []
    if (params?.courseId) queryParts.push(`courseId=${params.courseId}`)
    if (params?.sectionId) queryParts.push(`sectionId=${params.sectionId}`)
    if (params?.status) queryParts.push(`status=${params.status}`)
    if (queryParts.length > 0) url += '?' + queryParts.join('&')

    const response = await this.apiClient.get<any>(url)
    const data = response.data
    if (data?.content) return data
    if (Array.isArray(data)) return { content: data, totalElements: data.length, totalPages: 1 }
    return { content: [], totalElements: 0, totalPages: 0 }
  }

  async getProject(id: string): Promise<ProjectDTO> {
    const response = await this.apiClient.get<ProjectDTO>(`/api/projects/${id}`)
    return response.data
  }

  async updateProject(id: string, data: Partial<CreateProjectRequest>): Promise<ProjectDTO> {
    const response = await this.apiClient.put<ProjectDTO>(`/api/projects/${id}`, data)
    return response.data
  }

  async activateProject(id: string): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>(`/api/projects/${id}/activate`, {})
    return response.data
  }

  async completeProject(id: string): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>(`/api/projects/${id}/complete`, {})
    return response.data
  }

  async generateGroups(id: string, data: GenerateGroupsRequest): Promise<ProjectGroupDTO[]> {
    const response = await this.apiClient.post<ProjectGroupDTO[]>(`/api/projects/${id}/generate-groups`, data)
    const result = response.data
    return Array.isArray(result) ? result : ((result as any)?.data || [])
  }

  async getGroups(id: string): Promise<ProjectGroupDTO[]> {
    const response = await this.apiClient.get<ProjectGroupDTO[]>(`/api/projects/${id}/groups`)
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }

  async assignStatements(id: string): Promise<ProjectGroupDTO[]> {
    const response = await this.apiClient.post<ProjectGroupDTO[]>(`/api/projects/${id}/assign-statements`, {})
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }

  async getEvents(id: string): Promise<ProjectEventDTO[]> {
    const response = await this.apiClient.get<ProjectEventDTO[]>(`/api/projects/${id}/events`)
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }

  async addEvent(id: string, data: Partial<ProjectEventDTO>): Promise<ProjectEventDTO> {
    const response = await this.apiClient.post<ProjectEventDTO>(`/api/projects/${id}/events`, data)
    return response.data
  }

  async updateEvent(id: string, eventId: string, data: Partial<ProjectEventDTO>): Promise<ProjectEventDTO> {
    const response = await this.apiClient.put<ProjectEventDTO>(`/api/projects/${id}/events/${eventId}`, data)
    return response.data
  }

  async deleteEvent(id: string, eventId: string): Promise<void> {
    await this.apiClient.delete(`/api/projects/${id}/events/${eventId}`)
  }

  async getAttendance(id: string, eventId: string): Promise<Array<{ studentId: string; groupId: string; present: boolean }>> {
    const response = await this.apiClient.get<any[]>(`/api/projects/${id}/events/${eventId}/attendance`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getGrades(id: string, eventId: string): Promise<Array<{ studentId: string; groupId: string; marks: number }>> {
    const response = await this.apiClient.get<any[]>(`/api/projects/${id}/events/${eventId}/grades`)
    return Array.isArray(response.data) ? response.data : []
  }

  async saveAttendance(id: string, eventId: string, entries: AttendanceEntry[]): Promise<void> {
    await this.apiClient.post(`/api/projects/${id}/events/${eventId}/attendance`, { entries })
  }

  async saveGrades(id: string, eventId: string, entries: GradeEntry[]): Promise<void> {
    await this.apiClient.post(`/api/projects/${id}/events/${eventId}/grades`, { entries })
  }

  async submitProject(id: string, groupId: string, data: SubmitProjectRequest): Promise<ProjectGroupDTO> {
    const response = await this.apiClient.post<ProjectGroupDTO>(`/api/projects/${id}/groups/${groupId}/submit`, data)
    return response.data
  }

  // ---- Event Submissions (Admin) ----

  async getEventSubmissions(id: string, eventId: string): Promise<any[]> {
    const response = await this.apiClient.get<any[]>(`/api/projects/${id}/events/${eventId}/submissions`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getGroupEventSubmission(id: string, eventId: string, groupId: string): Promise<any> {
    const response = await this.apiClient.get<any>(`/api/projects/${id}/events/${eventId}/submissions/${groupId}`)
    return response.data
  }

  async giveEventFeedback(id: string, eventId: string, groupId: string, data: EventFeedbackRequest): Promise<ProjectEventFeedbackDTO> {
    const response = await this.apiClient.post<ProjectEventFeedbackDTO>(
      `/api/projects/${id}/events/${eventId}/submissions/${groupId}/feedback`, data)
    return response.data
  }

  async advancePhase(id: string, nextEventId: string | null): Promise<ProjectDTO> {
    const response = await this.apiClient.post<ProjectDTO>(`/api/projects/${id}/advance-phase`, { nextEventId })
    return response.data
  }

  // ---- Dashboard ----

  async getProjectDashboard(id: string): Promise<any> {
    const response = await this.apiClient.get<any>(`/api/projects/${id}/dashboard`)
    return response.data
  }

  // ---- Templates ----

  async listTemplates(): Promise<ProjectTemplateDTO[]> {
    const response = await this.apiClient.get<ProjectTemplateDTO[]>('/api/projects/templates')
    return Array.isArray(response.data) ? response.data : []
  }

  async getTemplate(templateId: string): Promise<ProjectTemplateDTO> {
    const response = await this.apiClient.get<ProjectTemplateDTO>(`/api/projects/templates/${templateId}`)
    return response.data
  }

  async saveAsTemplate(id: string, data: { name: string; description?: string }): Promise<ProjectTemplateDTO> {
    const response = await this.apiClient.post<ProjectTemplateDTO>(`/api/projects/${id}/save-as-template`, data)
    return response.data
  }

  async deleteTemplate(templateId: string): Promise<void> {
    await this.apiClient.delete(`/api/projects/templates/${templateId}`)
  }

  // ---- Attachments ----

  async getAttachments(id: string, context?: 'STATEMENT' | 'SUBMISSION'): Promise<ProjectAttachmentDTO[]> {
    const url = context ? `/api/projects/${id}/attachments?context=${context}` : `/api/projects/${id}/attachments`
    const response = await this.apiClient.get<ProjectAttachmentDTO[]>(url)
    return Array.isArray(response.data) ? response.data : []
  }

  async addAttachment(id: string, data: { groupId?: string; context: string; fileUrl: string; fileName: string; fileSizeBytes?: number; mimeType?: string }): Promise<ProjectAttachmentDTO> {
    const response = await this.apiClient.post<ProjectAttachmentDTO>(`/api/projects/${id}/attachments`, data)
    return response.data
  }

  async deleteAttachment(id: string, attachmentId: string): Promise<void> {
    await this.apiClient.delete(`/api/projects/${id}/attachments/${attachmentId}`)
  }

  // ---- Student ----

  async getMyProjects(): Promise<ProjectDTO[]> {
    const response = await this.apiClient.get<ProjectDTO[]>('/api/projects/my-projects')
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }

  async getMyGroup(id: string): Promise<ProjectGroupDTO> {
    const response = await this.apiClient.get<ProjectGroupDTO>(`/api/projects/${id}/my-group`)
    return response.data
  }

  async submitMyProject(id: string, data: SubmitProjectRequest): Promise<ProjectGroupDTO> {
    const response = await this.apiClient.post<ProjectGroupDTO>(`/api/projects/${id}/my-group/submit`, data)
    return response.data
  }

  async getMyAttendance(id: string): Promise<any> {
    const response = await this.apiClient.get<any>(`/api/projects/${id}/my-attendance`)
    return response.data
  }

  async getSubmissionHistory(id: string): Promise<any[]> {
    const response = await this.apiClient.get<any[]>(`/api/projects/${id}/my-group/history`)
    return Array.isArray(response.data) ? response.data : (Array.isArray(response) ? response : [])
  }

  async submitToEvent(id: string, eventId: string, data: SubmitEventRequest): Promise<ProjectEventSubmissionDTO> {
    const response = await this.apiClient.post<ProjectEventSubmissionDTO>(
      `/api/projects/${id}/events/${eventId}/my-submission`, data)
    return response.data
  }

  async getMyEventSubmission(id: string, eventId: string): Promise<ProjectEventSubmissionDTO | null> {
    try {
      const response = await this.apiClient.get<ProjectEventSubmissionDTO>(
        `/api/projects/${id}/events/${eventId}/my-submission`)
      return response.data
    } catch { return null }
  }

  async getMyEventSubmissionHistory(id: string, eventId: string): Promise<ProjectEventSubmissionDTO[]> {
    const response = await this.apiClient.get<ProjectEventSubmissionDTO[]>(
      `/api/projects/${id}/events/${eventId}/my-submission/history`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getMyEventFeedback(id: string, eventId: string): Promise<ProjectEventFeedbackDTO[]> {
    const response = await this.apiClient.get<ProjectEventFeedbackDTO[]>(
      `/api/projects/${id}/events/${eventId}/my-submission/feedback`)
    return Array.isArray(response.data) ? response.data : []
  }
}

// ============ ProjectQuestionsApi ============

export class ProjectQuestionsApi {
  constructor(private apiClient: ApiClient) {}

  async createQuestion(data: Partial<ProjectQuestionDTO>): Promise<ProjectQuestionDTO> {
    const response = await this.apiClient.post<ProjectQuestionDTO>('/api/project-questions', data)
    return response.data
  }

  async listQuestions(params?: { courseId?: string; difficulty?: string }): Promise<{ content: ProjectQuestionDTO[]; totalElements: number; totalPages: number }> {
    let url = '/api/project-questions'
    const queryParts: string[] = []
    if (params?.courseId) queryParts.push(`courseId=${params.courseId}`)
    if (params?.difficulty) queryParts.push(`difficulty=${params.difficulty}`)
    if (queryParts.length > 0) url += '?' + queryParts.join('&')

    const response = await this.apiClient.get<any>(url)
    const data = response.data
    if (data?.content) return data
    if (Array.isArray(data)) return { content: data, totalElements: data.length, totalPages: 1 }
    return { content: [], totalElements: 0, totalPages: 0 }
  }

  async getQuestion(id: string): Promise<ProjectQuestionDTO> {
    const response = await this.apiClient.get<ProjectQuestionDTO>(`/api/project-questions/${id}`)
    return response.data
  }

  async updateQuestion(id: string, data: Partial<ProjectQuestionDTO>): Promise<ProjectQuestionDTO> {
    const response = await this.apiClient.put<ProjectQuestionDTO>(`/api/project-questions/${id}`, data)
    return response.data
  }

  async deleteQuestion(id: string): Promise<void> {
    await this.apiClient.delete(`/api/project-questions/${id}`)
  }

  async bulkUpload(questions: Partial<ProjectQuestionDTO>[]): Promise<ProjectQuestionDTO[]> {
    const response = await this.apiClient.post<ProjectQuestionDTO[]>('/api/project-questions/bulk-upload', { questions })
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }
}
