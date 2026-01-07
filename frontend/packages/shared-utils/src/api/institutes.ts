import { ApiClient } from './ApiClient'

export enum InstituteType {
  SCHOOL = 'SCHOOL',
  COLLEGE = 'COLLEGE',
  UNIVERSITY = 'UNIVERSITY'
}

export interface Institute {
  id: string
  clientId: string
  name: string
  code: string
  type: InstituteType
  address?: string
  isActive: boolean
  classCount?: number
  createdAt: string
  updatedAt: string
}

export interface CreateInstituteRequest {
  name: string
  code: string
  type: InstituteType
  address?: string
  isActive?: boolean
}

export class InstitutesApi {
  constructor(private apiClient: ApiClient) {}

  async createInstitute(request: CreateInstituteRequest): Promise<Institute> {
    const response = await this.apiClient.post<Institute>('/api/institutes', request)
    return response.data
  }

  async listInstitutes(): Promise<Institute[]> {
    const response = await this.apiClient.get<Institute[]>('/api/institutes')
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getActiveInstitutes(): Promise<Institute[]> {
    const response = await this.apiClient.get<Institute[]>('/api/institutes/active')
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getInstitute(id: string): Promise<Institute> {
    const response = await this.apiClient.get<Institute>(`/api/institutes/${id}`)
    return response.data
  }

  async updateInstitute(id: string, request: CreateInstituteRequest): Promise<Institute> {
    const response = await this.apiClient.put<Institute>(`/api/institutes/${id}`, request)
    return response.data
  }

  async deleteInstitute(id: string): Promise<void> {
    await this.apiClient.delete(`/api/institutes/${id}`)
  }
}


