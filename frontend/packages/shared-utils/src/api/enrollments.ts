import { ApiClient } from './ApiClient'

export interface Enrollment {
  id: string
  studentId: string
  studentEmail?: string // Student email for display purposes
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
    const response = await this.apiClient.get<any>('/api/enrollments')
    
    // Handle Spring Data Page response structure: {content: [...], totalElements: ...}
    if (response.data && response.data.content && Array.isArray(response.data.content)) {
      return response.data.content
    }
    // Fallback: if response is already an array, return it
    if (Array.isArray(response.data)) {
      return response.data
    }
    return []
  }

  async listAllEnrollments(): Promise<Enrollment[]> {
    const response = await this.apiClient.get<Enrollment[]>('/api/enrollments/all')
    return Array.isArray(response.data) ? response.data : []
  }

  async listAllEnrollmentsPaginated(page: number = 0, size: number = 20): Promise<{
    content: Enrollment[]
    totalElements: number
    totalPages: number
    size: number
    number: number
  }> {
    const response = await this.apiClient.get<{
      content: Enrollment[]
      totalElements: number
      totalPages: number
      size: number
      number: number
    }>(`/api/enrollments/all/paged?page=${page}&size=${size}&sort=enrolledAt,desc`)
    
    // Handle Spring Data Page response structure
    if (response.data && response.data.content) {
      return response.data
    }
    
    // Fallback structure
    return {
      content: Array.isArray(response.data) ? response.data : [],
      totalElements: 0,
      totalPages: 0,
      size: size,
      number: page
    }
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

  // Bulk unenrollment methods
  async unenrollClassFromCourse(classId: string, courseId: string): Promise<BulkEnrollmentResult> {
    const response = await this.apiClient.delete<BulkEnrollmentResult>(
      `/api/classes/${classId}/enroll/${courseId}`
    )
    return response.data
  }

  async unenrollSectionFromCourse(sectionId: string, courseId: string): Promise<BulkEnrollmentResult> {
    const response = await this.apiClient.delete<BulkEnrollmentResult>(
      `/api/sections/${sectionId}/enroll/${courseId}`
    )
    return response.data
  }

  // Admin enrollment management
  async deleteEnrollment(enrollmentId: string): Promise<void> {
    await this.apiClient.delete(`/api/enrollments/${enrollmentId}`)
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

