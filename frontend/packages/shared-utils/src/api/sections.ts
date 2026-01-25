import { ApiClient } from './ApiClient'

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
}


