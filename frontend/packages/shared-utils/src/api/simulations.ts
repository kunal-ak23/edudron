import { ApiClient } from './ApiClient'

// ============ Types ============

export interface GenerateSimulationRequest {
  concept: string
  subject: string
  audience: 'UNDERGRADUATE' | 'MBA' | 'GRADUATE'
  courseId?: string
  lectureId?: string
  description?: string
  targetDepth?: number  // 10-30, default 15
  choicesPerNode?: number  // 2-4, default 3
}

export interface SimulationDTO {
  id: string
  title: string
  concept: string
  subject: string
  audience: string
  description?: string
  courseId?: string
  lectureId?: string
  treeData?: any  // full tree (admin only)
  targetDepth: number
  choicesPerNode: number
  maxDepth?: number
  status: 'DRAFT' | 'GENERATING' | 'REVIEW' | 'PUBLISHED' | 'ARCHIVED'
  visibility: 'ALL' | 'ASSIGNED_ONLY'
  assignedToSectionIds?: string[]
  createdBy?: string
  publishedAt?: string
  createdAt?: string
  totalPlays: number
}

export interface SimulationNodeDTO {
  nodeId: string
  type: 'SCENARIO' | 'TERMINAL'
  narrative: string
  decisionType?: string
  decisionConfig?: any
  choices?: ChoiceDTO[]
  debrief?: DebriefDTO
  score?: number
  terminal: boolean
}

export interface ChoiceDTO {
  id: string
  text: string
}

export interface DebriefDTO {
  yourPath: string
  conceptAtWork: string
  theGap: string
  playAgain: string
}

export interface DecisionInput {
  nodeId: string
  choiceId?: string  // for NARRATIVE_CHOICE
  input?: Record<string, any>  // for interactive types
}

export interface SimulationPlayDTO {
  id: string
  simulationId: string
  simulationTitle: string
  attemptNumber: number
  isPrimary: boolean
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED'
  decisionsMade: number
  score?: number
  startedAt: string
  completedAt?: string
}

export interface SimulationExportDTO {
  version: string
  exportedAt: string
  simulation: {
    title: string
    concept: string
    subject: string
    audience: string
    description?: string
    treeData: any
    targetDepth: number
    choicesPerNode: number
    metadataJson?: any
  }
}

export interface SimulationAIGenerationJobDTO {
  jobId: string
  jobType: string
  status: 'PENDING' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  message?: string
  progress?: number
  result?: any
  error?: string
}

// ============ API Class ============

export class SimulationsApi {
  constructor(private apiClient: ApiClient) {}

  // ---- Admin ----

  async generateSimulation(request: GenerateSimulationRequest): Promise<{ jobId: string; simulationId: string }> {
    const response = await this.apiClient.post<{ jobId: string; simulationId: string }>(
      '/content/api/simulations/generate', request)
    return response.data
  }

  async getGenerationJobStatus(jobId: string): Promise<SimulationAIGenerationJobDTO> {
    const response = await this.apiClient.get<SimulationAIGenerationJobDTO>(
      `/content/api/simulations/generate/jobs/${jobId}`)
    return response.data
  }

  async listSimulations(page = 0, size = 20, status?: string): Promise<{ content: SimulationDTO[]; totalElements: number; totalPages: number }> {
    let url = `/content/api/simulations?page=${page}&size=${size}`
    if (status) url += `&status=${status}`
    const response = await this.apiClient.get<any>(url)
    // Handle Spring Data Page format
    const data = response.data
    if (data?.content) return data
    if (Array.isArray(data)) return { content: data, totalElements: data.length, totalPages: 1 }
    return { content: [], totalElements: 0, totalPages: 0 }
  }

  async getSimulation(id: string): Promise<SimulationDTO> {
    const response = await this.apiClient.get<SimulationDTO>(`/content/api/simulations/${id}`)
    return response.data
  }

  async updateSimulation(id: string, data: Partial<SimulationDTO>): Promise<SimulationDTO> {
    const response = await this.apiClient.put<SimulationDTO>(`/content/api/simulations/${id}`, data)
    return response.data
  }

  async updateTree(id: string, treeData: any): Promise<SimulationDTO> {
    const response = await this.apiClient.put<SimulationDTO>(`/content/api/simulations/${id}/tree`, treeData)
    return response.data
  }

  async publishSimulation(id: string): Promise<SimulationDTO> {
    const response = await this.apiClient.post<SimulationDTO>(`/content/api/simulations/${id}/publish`, {})
    return response.data
  }

  async archiveSimulation(id: string): Promise<SimulationDTO> {
    const response = await this.apiClient.post<SimulationDTO>(`/content/api/simulations/${id}/archive`, {})
    return response.data
  }

  async exportSimulation(id: string): Promise<SimulationExportDTO> {
    const response = await this.apiClient.post<SimulationExportDTO>(`/content/api/simulations/${id}/export`, {})
    return response.data
  }

  async importSimulation(data: SimulationExportDTO): Promise<SimulationDTO> {
    const response = await this.apiClient.post<SimulationDTO>('/content/api/simulations/import', data)
    return response.data
  }

  // ---- Student ----

  async getAvailableSimulations(): Promise<SimulationDTO[]> {
    const response = await this.apiClient.get<SimulationDTO[]>('/content/api/simulations/available')
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }

  async startPlay(simulationId: string): Promise<SimulationPlayDTO> {
    const response = await this.apiClient.post<SimulationPlayDTO>(
      `/content/api/simulations/${simulationId}/play`, {})
    return response.data
  }

  async getCurrentNode(simulationId: string, playId: string): Promise<SimulationNodeDTO> {
    const response = await this.apiClient.get<SimulationNodeDTO>(
      `/content/api/simulations/${simulationId}/play/${playId}`)
    return response.data
  }

  async submitDecision(simulationId: string, playId: string, input: DecisionInput): Promise<SimulationNodeDTO> {
    const response = await this.apiClient.post<SimulationNodeDTO>(
      `/content/api/simulations/${simulationId}/play/${playId}/decide`, input)
    return response.data
  }

  async getDebrief(simulationId: string, playId: string): Promise<SimulationNodeDTO> {
    const response = await this.apiClient.get<SimulationNodeDTO>(
      `/content/api/simulations/${simulationId}/play/${playId}/debrief`)
    return response.data
  }

  async getPlayHistory(simulationId: string): Promise<SimulationPlayDTO[]> {
    const response = await this.apiClient.get<SimulationPlayDTO[]>(
      `/content/api/simulations/${simulationId}/history`)
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }

  async getAllPlayHistory(): Promise<SimulationPlayDTO[]> {
    const response = await this.apiClient.get<SimulationPlayDTO[]>('/content/api/simulations/my-history')
    const data = response.data
    return Array.isArray(data) ? data : ((data as any)?.data || [])
  }
}
