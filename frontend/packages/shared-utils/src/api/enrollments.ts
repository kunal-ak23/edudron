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
  studentId?: string
  enrollmentId?: string
  overallProgress?: number
  chaptersCompleted?: number
  totalChapters?: number
  totalLectures?: number
  completedLectures?: number
  completionPercentage?: number
  totalTimeSpentSeconds?: number
  lastAccessedAt?: string
  lectureProgress?: LectureProgress[]
  sectionProgress?: SectionProgress[]
}

export interface LectureProgress {
  id: string
  lectureId: string
  sectionId?: string
  isCompleted: boolean
  progressPercentage: number
  timeSpentSeconds: number
  lastAccessedAt?: string
  completedAt?: string
}

export interface SectionProgress {
  id: string
  sectionId: string
  isCompleted: boolean
  progressPercentage: number
  timeSpentSeconds: number
  lastAccessedAt?: string
  completedAt?: string
}

export class EnrollmentsApi {
  constructor(private apiClient: ApiClient) {}

  async listEnrollments(): Promise<Enrollment[]> {
    console.log('[EnrollmentsApi.listEnrollments] Starting request')
    const response = await this.apiClient.get<any>('/api/enrollments')
    console.log('[EnrollmentsApi.listEnrollments] Raw response:', response)
    console.log('[EnrollmentsApi.listEnrollments] Response data:', response.data)
    console.log('[EnrollmentsApi.listEnrollments] Response data type:', typeof response.data)
    console.log('[EnrollmentsApi.listEnrollments] Response data is array?:', Array.isArray(response.data))
    
    // Handle Spring Data Page response structure: {content: [...], totalElements: ...}
    if (response.data && response.data.content && Array.isArray(response.data.content)) {
      console.log('[EnrollmentsApi.listEnrollments] Found content array, length:', response.data.content.length)
      console.log('[EnrollmentsApi.listEnrollments] Content:', response.data.content)
      return response.data.content
    }
    // Fallback: if response is already an array, return it
    if (Array.isArray(response.data)) {
      console.log('[EnrollmentsApi.listEnrollments] Response.data is already an array, length:', response.data.length)
      return response.data
    }
    console.warn('[EnrollmentsApi.listEnrollments] No valid enrollment data found in response. Returning empty array.')
    console.warn('[EnrollmentsApi.listEnrollments] Response structure:', JSON.stringify(response.data, null, 2))
    return []
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

  async getLectureProgress(courseId: string): Promise<LectureProgress[]> {
    const response = await this.apiClient.get<LectureProgress[]>(`/api/courses/${courseId}/lectures/progress`)
    return Array.isArray(response.data) ? response.data : []
  }

  async updateProgress(courseId: string, request: {
    lectureId?: string
    sectionId?: string
    isCompleted?: boolean
    progressPercentage?: number
    timeSpentSeconds?: number
  }): Promise<LectureProgress | SectionProgress> {
    const response = await this.apiClient.put<LectureProgress | SectionProgress>(
      `/api/courses/${courseId}/progress`,
      request
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

  // Bulk enrollment methods
  async enrollClassToCourse(classId: string, courseId: string): Promise<BulkEnrollmentResult> {
    const response = await this.apiClient.post<BulkEnrollmentResult>(
      `/api/classes/${classId}/enroll/${courseId}`
    )
    return response.data
  }

  async enrollSectionToCourse(sectionId: string, courseId: string): Promise<BulkEnrollmentResult> {
    const response = await this.apiClient.post<BulkEnrollmentResult>(
      `/api/sections/${sectionId}/enroll/${courseId}`
    )
    return response.data
  }

  async enrollClassToCourses(classId: string, courseIds: string[]): Promise<BulkEnrollmentResult[]> {
    const response = await this.apiClient.post<BulkEnrollmentResult[]>(
      `/api/classes/${classId}/enroll-batch`,
      { courseIds }
    )
    return Array.isArray(response.data) ? response.data : []
  }

  async enrollSectionToCourses(sectionId: string, courseIds: string[]): Promise<BulkEnrollmentResult[]> {
    const response = await this.apiClient.post<BulkEnrollmentResult[]>(
      `/api/sections/${sectionId}/enroll-batch`,
      { courseIds }
    )
    return Array.isArray(response.data) ? response.data : []
  }
}

export interface BulkEnrollmentResult {
  totalStudents: number
  enrolledStudents: number
  skippedStudents: number
  failedStudents: number
  enrolledStudentIds?: string[]
  errorMessages?: string[]
}

