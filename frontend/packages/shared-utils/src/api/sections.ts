import { ApiClient } from './ApiClient'
import type { CoordinatorResponse } from './classes'

export interface Section {
  id: string
  clientId: string
  name: string
  description?: string
  classId: string
  startDate?: string
  endDate?: string
  maxStudents?: number
  isActive: boolean
  isBacklog?: boolean
  studentCount?: number
  createdAt: string
  updatedAt: string
}

export interface CreateSectionRequest {
  name: string
  description?: string
  classId: string
  startDate?: string
  endDate?: string
  maxStudents?: number
  isBacklog?: boolean
}

export interface BatchCreateSectionsRequest {
  sections: CreateSectionRequest[]
}

export interface BatchCreateSectionsResponse {
  sections: Section[]
  totalCreated: number
  message: string
}

export class SectionsApi {
  constructor(private apiClient: ApiClient) {}

  async createSection(classId: string, request: CreateSectionRequest): Promise<Section> {
    const response = await this.apiClient.post<Section>(`/api/classes/${classId}/sections`, request)
    return response.data
  }

  async batchCreateSections(
    classId: string,
    request: BatchCreateSectionsRequest
  ): Promise<BatchCreateSectionsResponse> {
    const response = await this.apiClient.post<BatchCreateSectionsResponse>(
      `/api/classes/${classId}/sections/batch`,
      request
    )
    return response.data
  }

  async listSectionsByClass(classId: string): Promise<Section[]> {
    const response = await this.apiClient.get<Section[]>(`/api/classes/${classId}/sections`)
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getActiveSectionsByClass(classId: string): Promise<Section[]> {
    const response = await this.apiClient.get<Section[]>(`/api/classes/${classId}/sections/active`)
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getSection(id: string): Promise<Section> {
    const response = await this.apiClient.get<Section>(`/api/sections/${id}`)
    return response.data
  }

  async updateSection(id: string, request: CreateSectionRequest): Promise<Section> {
    const response = await this.apiClient.put<Section>(`/api/sections/${id}`, request)
    return response.data
  }

  async deleteSection(id: string): Promise<void> {
    await this.apiClient.delete(`/api/sections/${id}`)
  }

  async activateSection(id: string): Promise<void> {
    await this.apiClient.put(`/api/sections/${id}/activate`, {})
  }

  async assignSectionCoordinator(sectionId: string, coordinatorUserId: string): Promise<CoordinatorResponse> {
    const response = await this.apiClient.put<CoordinatorResponse>(`/api/sections/${sectionId}/coordinator`, { coordinatorUserId })
    return response.data
  }

  async removeSectionCoordinator(sectionId: string): Promise<void> {
    await this.apiClient.delete(`/api/sections/${sectionId}/coordinator`)
  }

  async getSectionCoordinator(sectionId: string): Promise<CoordinatorResponse | null> {
    try {
      const response = await this.apiClient.get<CoordinatorResponse>(`/api/sections/${sectionId}/coordinator`)
      return response.data || null
    } catch {
      return null
    }
  }
}


