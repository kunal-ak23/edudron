import { ApiClient } from './ApiClient'

export interface Enrollment {
  id: string
  studentId: string
  courseId: string
  batchId?: string // Now represents Section ID (kept for backward compatibility)
  instituteId?: string
  classId?: string
  status: 'ACTIVE' | 'COMPLETED' | 'DROPPED'
  enrolledAt: string
  completedAt?: string
  progress?: number
}

export interface Batch {
  id: string
  courseId: string
  name: string
  startDate: string
  endDate: string
  capacity?: number
  enrolledCount?: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface Progress {
  courseId: string
  studentId: string
  overallProgress: number
  chaptersCompleted: number
  totalChapters: number
  lastAccessedAt?: string
}

export class EnrollmentsApi {
  constructor(private apiClient: ApiClient) {}

  async listEnrollments(): Promise<Enrollment[]> {
    const response = await this.apiClient.get<Enrollment[]>('/api/enrollments')
    return Array.isArray(response.data) ? response.data : []
  }

  async getEnrollment(id: string): Promise<Enrollment> {
    const response = await this.apiClient.get<Enrollment>(`/api/enrollments/${id}`)
    return response.data
  }

  async enrollInCourse(courseId: string, batchId?: string): Promise<Enrollment> {
    const response = await this.apiClient.post<Enrollment>(
      `/api/courses/${courseId}/enroll`,
      batchId ? { batchId } : {}
    )
    return response.data
  }

  async unenrollFromCourse(courseId: string): Promise<void> {
    await this.apiClient.delete(`/api/courses/${courseId}/enroll`)
  }

  async checkEnrollment(courseId: string): Promise<boolean> {
    try {
      const response = await this.apiClient.get<{ enrolled: boolean }>(
        `/api/courses/${courseId}/enrolled`
      )
      return response.data?.enrolled || false
    } catch {
      return false
    }
  }

  async getProgress(courseId: string): Promise<Progress> {
    const response = await this.apiClient.get<Progress>(`/api/courses/${courseId}/progress`)
    return response.data
  }

  async updateProgress(courseId: string, progress: Partial<Progress>): Promise<Progress> {
    const response = await this.apiClient.put<Progress>(
      `/api/courses/${courseId}/progress`,
      progress
    )
    return response.data
  }

  // Batch management
  async listBatches(courseId?: string): Promise<Batch[]> {
    const url = courseId 
      ? `/api/batches/courses/${courseId}`
      : '/api/batches'
    const response = await this.apiClient.get<Batch[]>(url)
    return Array.isArray(response.data) ? response.data : []
  }

  async getBatch(id: string): Promise<Batch> {
    const response = await this.apiClient.get<Batch>(`/api/batches/${id}`)
    return response.data
  }

  async createBatch(batch: Partial<Batch>): Promise<Batch> {
    const response = await this.apiClient.post<Batch>('/api/batches', batch)
    return response.data
  }

  async updateBatch(id: string, batch: Partial<Batch>): Promise<Batch> {
    const response = await this.apiClient.put<Batch>(`/api/batches/${id}`, batch)
    return response.data
  }

  async deleteBatch(id: string): Promise<void> {
    await this.apiClient.delete(`/api/batches/${id}`)
  }

  async getBatchProgress(id: string): Promise<any> {
    const response = await this.apiClient.get(`/api/batches/${id}/progress`)
    return response.data
  }
}

