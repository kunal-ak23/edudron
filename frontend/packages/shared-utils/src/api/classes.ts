import { ApiClient } from './ApiClient'
import { Section, CreateSectionRequest } from './sections'

export interface Class {
  id: string
  instituteId: string
  clientId: string
  name: string
  code: string
  academicYear?: string
  grade?: string
  level?: string
  isActive: boolean
  sectionCount?: number
  studentCount?: number
  createdAt: string
  updatedAt: string
}

export interface CreateClassRequest {
  name: string
  code: string
  instituteId: string
  academicYear?: string
  grade?: string
  level?: string
  isActive?: boolean
}

export interface CreateSectionForClassRequest {
  name: string
  description?: string
  startDate?: string
  endDate?: string
  maxStudents?: number
}

export interface CreateClassWithSectionsRequest {
  name: string
  code: string
  instituteId: string
  academicYear?: string
  grade?: string
  level?: string
  isActive?: boolean
  sections: CreateSectionForClassRequest[]
}

export interface ClassWithSections {
  classInfo: Class
  sections: Section[]
}

export interface BatchCreateClassesRequest {
  classes: CreateClassRequest[]
}

export interface BatchCreateClassesResponse {
  classes: Class[]
  totalCreated: number
  message: string
}

export class ClassesApi {
  constructor(private apiClient: ApiClient) {}

  async createClass(instituteId: string, request: CreateClassRequest): Promise<Class> {
    const response = await this.apiClient.post<Class>(`/api/institutes/${instituteId}/classes`, request)
    return response.data
  }

  async createClassWithSections(
    instituteId: string,
    request: CreateClassWithSectionsRequest
  ): Promise<ClassWithSections> {
    const response = await this.apiClient.post<ClassWithSections>(
      `/api/institutes/${instituteId}/classes/with-sections`,
      request
    )
    return response.data
  }

  async batchCreateClasses(
    instituteId: string,
    request: BatchCreateClassesRequest
  ): Promise<BatchCreateClassesResponse> {
    const response = await this.apiClient.post<BatchCreateClassesResponse>(
      `/api/institutes/${instituteId}/classes/batch`,
      request
    )
    return response.data
  }

  async listClassesByInstitute(instituteId: string): Promise<Class[]> {
    const response = await this.apiClient.get<Class[]>(`/api/institutes/${instituteId}/classes`)
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getActiveClassesByInstitute(instituteId: string): Promise<Class[]> {
    const response = await this.apiClient.get<Class[]>(`/api/institutes/${instituteId}/classes/active`)
    if (Array.isArray(response)) {
      return response
    }
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async getClass(id: string): Promise<Class> {
    const response = await this.apiClient.get<Class>(`/api/classes/${id}`)
    return response.data
  }

  async updateClass(id: string, request: CreateClassRequest): Promise<Class> {
    const response = await this.apiClient.put<Class>(`/api/classes/${id}`, request)
    return response.data
  }

  async deleteClass(id: string): Promise<void> {
    await this.apiClient.delete(`/api/classes/${id}`)
  }
}


