import { ApiClient } from './ApiClient'

// ============ Types ============

export interface GenerateSimulationRequest {
  concept: string
  subject: string
  audience: 'UNDERGRADUATE' | 'MBA' | 'GRADUATE'
  courseId?: string
  lectureId?: string
  description?: string
  targetYears?: number   // 3-7, default 5
  decisionsPerYear?: number  // 4-8, default 6
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
  simulationData?: any  // full structure (admin only)
  targetYears: number
  decisionsPerYear: number
  status: string
  visibility: string
  assignedToSectionIds?: string[]
  createdBy?: string
  publishedAt?: string
  createdAt?: string
  totalPlays: number
}

export interface AdvisorDialog {
  mood: 'neutral' | 'concerned' | 'excited' | 'disappointed' | 'proud'
  text: string
  advisorName?: string
  characterId?: string
  retirementYear?: number
  farewellMessage?: string
}

export interface FinancialReport {
  departments: Record<string, {
    invested: number
    return: number | null
    roi: string | null
    note: string | null
  }>
  totalInvested: number
  totalReturns: number
  endingBudget: number
}

export interface SimulationStateDTO {
  phase: 'DECISION' | 'YEAR_END_REVIEW' | 'DEBRIEF' | 'FIRED'
  currentYear: number
  currentDecision: number
  totalDecisions: number
  totalYears: number
  currentRole: string
  cumulativeScore: number
  yearScore: number
  performanceBand: string
  currentBudget?: number
  financialReport?: FinancialReport
  advisorDialog?: AdvisorDialog
  advisorReaction?: AdvisorDialog
  decision?: SimulationDecisionDTO
  yearEndReview?: YearEndReviewDTO
  debrief?: DebriefDTO
  openingNarrative?: string
  decisionHistory?: Array<{
    year: number
    decision: number
    label: string
    quality: 'GOOD' | 'MEDIUM' | 'BAD'
    points: number
  }>
  goodDecisionCount?: number
  badDecisionCount?: number
  neutralDecisionCount?: number
  keyInsights?: string[]
}

export interface MentorGuidance {
  courseConnection?: string
  realWorldExample?: string
  mentorNote?: string
  mentorTip?: string
  choiceHints?: Record<string, { hint: string; risk: 'low' | 'medium' | 'high' }>
  stakeholderHints?: Record<string, { hint: string; priority: 'high' | 'medium' | 'low' }>
  candidateHints?: Record<string, { hint: string; fit: 'strong' | 'moderate' | 'weak' }>
  guidanceLevel?: 'FULL' | 'LIGHT'
}

export interface SimulationDecisionDTO {
  decisionId: string
  narrative: string
  decisionType?: string
  displayLabel?: string
  decisionConfig?: any
  choices?: ChoiceDTO[]
  conceptKeywords?: Array<{ term: string; explanation: string }>
  mentorGuidance?: MentorGuidance
}

export interface ChoiceDTO {
  id: string
  text: string
}

export interface YearEndReviewDTO {
  year: number
  band: string
  metrics: Record<string, any>
  feedback: Record<string, string>
  promotionTitle?: string
  fired: boolean
}

export interface DebriefDTO {
  yourPath: string
  conceptAtWork: string
  theGap: string
  playAgain: string
}

export interface DecisionInput {
  decisionId: string
  choiceId?: string  // for NARRATIVE_CHOICE
  input?: Record<string, any>  // for interactive types
}

export interface SimulationPlayDTO {
  id: string
  simulationId: string
  simulationTitle: string
  attemptNumber: number
  isPrimary: boolean
  status: string
  currentYear: number
  currentDecision: number
  currentRole?: string
  cumulativeScore: number
  currentBudget?: number
  finalScore?: number
  performanceBand?: string
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
    simulationData: any
    targetYears: number
    decisionsPerYear: number
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

export interface SimulationSuggestionRequest {
  courseId: string
  lectureIds?: string[]
}

export interface SimulationSuggestionResponse {
  concept: string
  subject: string
  audience: string
  description: string
  existingSimulations: Array<{ id: string; title: string; concept: string; status: string }>
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

  async updateSimulationData(id: string, simulationData: any): Promise<SimulationDTO> {
    const response = await this.apiClient.put<SimulationDTO>(`/content/api/simulations/${id}/data`, simulationData)
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

  async deleteSimulation(id: string): Promise<void> {
    await this.apiClient.delete(`/content/api/simulations/${id}`)
  }

  async moveToDraft(id: string): Promise<SimulationDTO> {
    const response = await this.apiClient.post<SimulationDTO>(`/content/api/simulations/${id}/move-to-draft`, {})
    return response.data
  }

  async moveToPublished(id: string): Promise<SimulationDTO> {
    const response = await this.apiClient.post<SimulationDTO>(`/content/api/simulations/${id}/move-to-published`, {})
    return response.data
  }

  async abandonPlay(playId: string): Promise<void> {
    await this.apiClient.post(`/content/api/simulations/play/${playId}/abandon`, {})
  }

  async regenerateMentorGuidance(id: string): Promise<{ status: string; message: string }> {
    const response = await this.apiClient.post<{ status: string; message: string }>(
      `/content/api/simulations/${id}/regenerate-mentor-guidance`, {}, { timeout: 120000 }
    )
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

  async suggestFromCourse(request: SimulationSuggestionRequest): Promise<SimulationSuggestionResponse> {
    const response = await this.apiClient.post<SimulationSuggestionResponse>(
      '/content/api/simulations/suggest-from-course', request)
    return response.data
  }

  // ---- Student ----

  async getSimulationDetails(id: string): Promise<SimulationDTO> {
    const response = await this.apiClient.get<SimulationDTO>(`/content/api/simulations/${id}/details`)
    return response.data
  }

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

  async getCurrentState(playId: string): Promise<SimulationStateDTO> {
    const response = await this.apiClient.get<SimulationStateDTO>(
      `/content/api/simulations/play/${playId}/state`)
    return response.data
  }

  async submitDecision(playId: string, input: DecisionInput): Promise<SimulationStateDTO> {
    const response = await this.apiClient.post<SimulationStateDTO>(
      `/content/api/simulations/play/${playId}/decide`, input)
    return response.data
  }

  async advanceYear(playId: string): Promise<SimulationStateDTO> {
    const response = await this.apiClient.post<SimulationStateDTO>(
      `/content/api/simulations/play/${playId}/advance-year`, {})
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
