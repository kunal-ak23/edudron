import { ApiClient } from './ApiClient'

// ============ Types ============

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
}

export interface ProjectGroupDTO {
  id: string
  projectId: string
  groupNumber: number
  problemStatementId?: string
  submissionUrl?: string
  submittedAt?: string
  submittedBy?: string
  members: Array<{ studentId: string; name?: string; email?: string }>
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
}

export interface ProjectQuestionDTO {
  id: string
  courseId: string
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

export interface GenerateGroupsRequest {
  groupSize: number
}

export interface SubmitProjectRequest {
  submissionUrl: string
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

  async saveAttendance(id: string, eventId: string, entries: AttendanceEntry[]): Promise<void> {
    await this.apiClient.post(`/api/projects/${id}/events/${eventId}/attendance`, entries)
  }

  async saveGrades(id: string, eventId: string, entries: GradeEntry[]): Promise<void> {
    await this.apiClient.post(`/api/projects/${id}/events/${eventId}/grades`, entries)
  }

  async submitProject(id: string, groupId: string, data: SubmitProjectRequest): Promise<ProjectGroupDTO> {
    const response = await this.apiClient.post<ProjectGroupDTO>(`/api/projects/${id}/groups/${groupId}/submit`, data)
    return response.data
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
    const response = await this.apiClient.post<ProjectQuestionDTO[]>('/api/project-questions/bulk-upload', questions)
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }
}
