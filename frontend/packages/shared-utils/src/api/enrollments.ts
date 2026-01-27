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
  courseId?: string // Legacy - batches used to link to courses
  classId?: string // New - sections link to classes
  name: string
  startDate: string
  endDate: string
  capacity?: number // Legacy field name
  maxStudents?: number // New field name (same as capacity)
  enrolledCount?: number // Legacy field name
  studentCount?: number // New field name (same as enrolledCount)
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

  async listAllEnrollmentsPaginated(
    page: number = 0, 
    size: number = 20,
    filters?: {
      courseId?: string
      instituteId?: string
      classId?: string
      sectionId?: string
      email?: string
    }
  ): Promise<{
    content: Enrollment[]
    totalElements: number
    totalPages: number
    size: number
    number: number
  }> {
    // Build query parameters
    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('size', size.toString())
    params.append('sort', 'enrolledAt,desc')
    
    // Add filter parameters if provided
    if (filters) {
      if (filters.courseId) params.append('courseId', filters.courseId)
      if (filters.instituteId) params.append('instituteId', filters.instituteId)
      if (filters.classId) params.append('classId', filters.classId)
      if (filters.sectionId) params.append('sectionId', filters.sectionId)
      if (filters.email) params.append('email', filters.email)
    }
    
    const url = `/api/enrollments/all/paged?${params.toString()}`
    console.log('[EnrollmentsApi] Calling:', url)
    
    const response = await this.apiClient.get<{
      content: Enrollment[]
      totalElements: number
      totalPages: number
      size: number
      number: number
    }>(url)
    
    console.log('[EnrollmentsApi] Raw response:', {
      hasData: !!response.data,
      hasContent: !!(response.data && response.data.content),
      contentLength: response.data?.content?.length,
      totalElements: response.data?.totalElements,
      totalPages: response.data?.totalPages,
      responseKeys: response.data ? Object.keys(response.data) : []
    })
    
    // Handle Spring Data Page response structure
    if (response.data && response.data.content) {
      console.log('[EnrollmentsApi] Returning paginated response with', response.data.content.length, 'items')
      return response.data
    }
    
    // Fallback structure
    console.warn('[EnrollmentsApi] Response structure unexpected, using fallback')
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

  // Section management (replaces legacy Batch management)
  async listSections(classId?: string): Promise<Batch[]> {
    const url = classId 
      ? `/api/classes/${classId}/sections`
      : '/api/sections'
    const response = await this.apiClient.get<Batch[]>(url)
    return Array.isArray(response.data) ? response.data : []
  }

  // Legacy Batch management - deprecated, use Sections instead
  async listBatches(courseId?: string): Promise<Batch[]> {
    // Redirect to sections API since batches table no longer exists
    return this.listSections()
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

  async enrollStudentInCourse(studentId: string, courseId: string, options?: {
    classId?: string
    sectionId?: string
    instituteId?: string
  }): Promise<Enrollment> {
    const response = await this.apiClient.post<Enrollment>(
      `/api/students/${studentId}/enroll/${courseId}`,
      options || {}
    )
    return response.data
  }

  // Get students by section
  async getStudentsBySection(sectionId: string): Promise<SectionStudentDTO[]> {
    const response = await this.apiClient.get<SectionStudentDTO[]>(`/api/sections/${sectionId}/students`)
    return Array.isArray(response.data) ? response.data : []
  }

  // Get students by section (paginated)
  async getStudentsBySectionPaginated(
    sectionId: string,
    page: number = 0,
    size: number = 20
  ): Promise<{
    content: SectionStudentDTO[]
    totalElements: number
    totalPages: number
    size: number
    number: number
  }> {
    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('size', size.toString())
    params.append('sort', 'name,asc')
    
    const url = `/api/sections/${sectionId}/students/paged?${params.toString()}`
    const response = await this.apiClient.get<{
      content: SectionStudentDTO[]
      totalElements: number
      totalPages: number
      size: number
      number: number
    }>(url)
    
    if (response.data && response.data.content) {
      return response.data
    }
    
    return {
      content: Array.isArray(response.data) ? response.data : [],
      totalElements: 0,
      totalPages: 0,
      size: size,
      number: page
    }
  }

  // Get students by class
  async getStudentsByClass(classId: string): Promise<ClassStudentDTO[]> {
    const response = await this.apiClient.get<ClassStudentDTO[]>(`/api/classes/${classId}/students`)
    return Array.isArray(response.data) ? response.data : []
  }

  // Get students by class (paginated)
  async getStudentsByClassPaginated(
    classId: string,
    page: number = 0,
    size: number = 20
  ): Promise<{
    content: ClassStudentDTO[]
    totalElements: number
    totalPages: number
    size: number
    number: number
  }> {
    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('size', size.toString())
    params.append('sort', 'name,asc')
    
    const url = `/api/classes/${classId}/students/paged?${params.toString()}`
    const response = await this.apiClient.get<{
      content: ClassStudentDTO[]
      totalElements: number
      totalPages: number
      size: number
      number: number
    }>(url)
    
    if (response.data && response.data.content) {
      return response.data
    }
    
    return {
      content: Array.isArray(response.data) ? response.data : [],
      totalElements: 0,
      totalPages: 0,
      size: size,
      number: page
    }
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

export interface SectionStudentDTO {
  id: string
  name: string
  email: string
  phone?: string
}export interface ClassStudentDTO {
  id: string
  name: string
  email: string
  phone?: string
}