import { ApiClient } from './ApiClient'

export interface Course {
  id: string
  title: string
  description?: string
  thumbnailUrl?: string
  previewVideoUrl?: string
  isFree: boolean
  pricePaise?: number
  currency?: string
  categoryId?: string
  tags?: string[]
  difficultyLevel?: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
  language?: string
  totalDurationSeconds?: number
  totalLecturesCount?: number
  totalStudentsCount?: number
  certificateEligible?: boolean
  isPublished: boolean
  status?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  assignedToClassIds?: string[]
  assignedToSectionIds?: string[]
  instructors?: CourseInstructor[]
  learningObjectives?: LearningObjective[]
  sections?: Section[]
  createdAt: string
  updatedAt: string
}

export interface CourseInstructor {
  id: string
  name: string
  title?: string
  bio?: string
  imageUrl?: string
}

export interface LearningObjective {
  id: string
  objective: string
  sequence: number
}

export interface Section {
  id: string
  courseId: string
  title: string
  description?: string
  sequence: number
  lectures?: Lecture[]
  isPublished: boolean
}

export interface Chapter {
  id: string
  courseId: string
  title: string
  description?: string
  order: number
  isPublished: boolean
  createdAt: string
  updatedAt: string
}

export interface LectureContent {
  id: string
  lectureId: string
  contentType: 'VIDEO' | 'PDF' | 'IMAGE' | 'AUDIO' | 'TEXT' | 'LINK' | 'EMBEDDED'
  title?: string
  description?: string
  fileUrl?: string
  videoUrl?: string
  textContent?: string
  externalUrl?: string
  embeddedCode?: string
  sequence: number
  createdAt: string
  updatedAt: string
}

export interface Lecture {
  id: string
  chapterId: string
  title: string
  description?: string
  contentType: 'VIDEO' | 'TEXT' | 'PDF' | 'QUIZ'
  contentUrl?: string
  duration?: number
  order: number
  isPublished: boolean
  contents?: LectureContent[]
  createdAt: string
  updatedAt: string
}

export interface Assessment {
  id: string
  courseId: string
  title: string
  description?: string
  questions: AssessmentQuestion[]
  passingScore: number
  timeLimit?: number
  isPublished: boolean
  createdAt: string
  updatedAt: string
}

export interface AssessmentQuestion {
  id: string
  question: string
  type: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER'
  options?: string[]
  correctAnswer: string | string[]
  points: number
}

export class CoursesApi {
  constructor(private apiClient: ApiClient) {}

  async listCourses(params?: { status?: string; instructorId?: string }): Promise<Course[]> {
    console.log('[CoursesApi.listCourses] Starting request with params:', params)
    const response = await this.apiClient.get<any>('/content/courses', { params })
    console.log('[CoursesApi.listCourses] Raw response:', response)
    console.log('[CoursesApi.listCourses] Response data:', response.data)
    console.log('[CoursesApi.listCourses] Response data type:', typeof response.data)
    console.log('[CoursesApi.listCourses] Response data is array?:', Array.isArray(response.data))
    
    // Handle Spring Data Page response structure: {content: [...], totalElements: ...}
    if (response.data && response.data.content && Array.isArray(response.data.content)) {
      console.log('[CoursesApi.listCourses] Found content array, length:', response.data.content.length)
      console.log('[CoursesApi.listCourses] Content:', response.data.content)
      return response.data.content
    }
    // Fallback: if response is already an array, return it
    if (Array.isArray(response.data)) {
      console.log('[CoursesApi.listCourses] Response.data is already an array, length:', response.data.length)
      return response.data
    }
    console.warn('[CoursesApi.listCourses] No valid course data found in response. Returning empty array.')
    console.warn('[CoursesApi.listCourses] Response structure:', JSON.stringify(response.data, null, 2))
    return []
  }

  async getCourse(id: string): Promise<Course> {
    try {
      console.log('[CoursesApi.getCourse] Fetching course with id:', id)
      const response = await this.apiClient.get<Course>(`/content/courses/${id}`)
      console.log('[CoursesApi.getCourse] Full response object:', response)
      console.log('[CoursesApi.getCourse] Response.data:', response.data)
      console.log('[CoursesApi.getCourse] Response.data type:', typeof response.data)
      
      // ApiClient.get wraps responses in { data: ... } format
      // So response.data should be the Course object
      let course = response.data
      
      // Handle potential double-wrapping (if backend also wraps)
      if (course && typeof course === 'object' && 'data' in course && !('id' in course) && !('title' in course)) {
        console.log('[CoursesApi.getCourse] Detected double-wrapping, unwrapping...')
        course = (course as any).data
      }
      
      console.log('[CoursesApi.getCourse] Final course object:', course)
      console.log('[CoursesApi.getCourse] Course keys:', course && typeof course === 'object' ? Object.keys(course) : 'N/A')
      
      if (!course) {
        console.error('[CoursesApi.getCourse] Course data is null or undefined after parsing')
        throw new Error('Course not found')
      }
      
      // Validate it's a Course object
      if (typeof course !== 'object') {
        console.error('[CoursesApi.getCourse] Course data is not an object:', typeof course)
        throw new Error('Invalid course data received: not an object')
      }
      
      if (!('id' in course) || !('title' in course)) {
        console.error('[CoursesApi.getCourse] Invalid course data structure - missing id or title:', course)
        throw new Error('Invalid course data received: missing required fields')
      }
      
      console.log('[CoursesApi.getCourse] Successfully parsed course:', { id: course.id, title: course.title })
      return course as Course
    } catch (error: any) {
      console.error('[CoursesApi.getCourse] Error fetching course:', error)
      console.error('[CoursesApi.getCourse] Error response:', error.response)
      console.error('[CoursesApi.getCourse] Error status:', error.response?.status)
      console.error('[CoursesApi.getCourse] Error data:', error.response?.data)
      console.error('[CoursesApi.getCourse] Error message:', error.message)
      // Re-throw to let the caller handle it
      throw error
    }
  }

  async createCourse(course: Partial<Course>): Promise<Course> {
    const response = await this.apiClient.post<Course>('/content/courses', course)
    return response.data
  }

  async updateCourse(id: string, course: Partial<Course>): Promise<Course> {
    const response = await this.apiClient.put<Course>(`/content/courses/${id}`, course)
    return response.data
  }

  async deleteCourse(id: string): Promise<void> {
    await this.apiClient.delete(`/content/courses/${id}`)
  }

  async publishCourse(id: string): Promise<Course> {
    const response = await this.apiClient.post<Course>(`/content/courses/${id}/publish`, {})
    return response.data
  }

  async unpublishCourse(id: string): Promise<Course> {
    // Unpublish by updating isPublished to false
    const course = await this.getCourse(id)
    return await this.updateCourse(id, { ...course, isPublished: false })
  }

  async getChapters(courseId: string): Promise<Chapter[]> {
    // Use lectures endpoint (sections are now called lectures)
    const response = await this.apiClient.get<Section[]>(`/content/courses/${courseId}/lectures`)
    // Map Section[] to Chapter[] for backward compatibility
    const sections = Array.isArray(response.data) ? response.data : []
    return sections.map(s => ({
      id: s.id,
      courseId: s.courseId,
      title: s.title,
      description: s.description,
      order: s.sequence,
      isPublished: s.isPublished,
      createdAt: '',
      updatedAt: ''
    }))
  }

  async getSections(courseId: string): Promise<Section[]> {
    const response = await this.apiClient.get<Section[]>(`/content/courses/${courseId}/lectures`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getAssessments(courseId: string): Promise<Assessment[]> {
    const response = await this.apiClient.get<Assessment[]>(`/content/courses/${courseId}/assessments`)
    return Array.isArray(response.data) ? response.data : []
  }

  async generateCourse(request: GenerateCourseRequest, onProgress?: (progress: number, message: string) => void): Promise<Course> {
    // Check if request includes a PDF file
    let job: AIGenerationJobDTO
    
    if (request.pdfFile) {
      // Use multipart form data for PDF upload
      const formData = new FormData()
      // Always include prompt, even if empty (backend expects it)
      formData.append('prompt', request.prompt || '')
      if (request.categoryId) formData.append('categoryId', request.categoryId)
      if (request.difficultyLevel) formData.append('difficultyLevel', request.difficultyLevel)
      if (request.language) formData.append('language', request.language)
      if (request.tags && request.tags.length > 0) formData.append('tags', request.tags.join(','))
      if (request.certificateEligible !== undefined) formData.append('certificateEligible', String(request.certificateEligible))
      if (request.maxCompletionDays) formData.append('maxCompletionDays', String(request.maxCompletionDays))
      if (request.referenceIndexIds && request.referenceIndexIds.length > 0) formData.append('referenceIndexIds', request.referenceIndexIds.join(','))
      if (request.writingFormatId) formData.append('writingFormatId', request.writingFormatId)
      if (request.writingFormat) formData.append('writingFormat', request.writingFormat)
      // Append PDF file - this must be the actual File object
      formData.append('pdfFile', request.pdfFile, request.pdfFile.name)
      
      const response = await this.apiClient.postForm<AIGenerationJobDTO>('/content/courses/generate', formData)
      job = response.data
    } else {
      // Use JSON for regular requests (backward compatibility)
      const response = await this.apiClient.post<AIGenerationJobDTO>('/content/courses/generate', request)
      job = response.data
    }
    
    // Poll for job completion
    return this.waitForJobCompletion(job.jobId, onProgress)
  }
  
  async getCourseGenerationJobStatus(jobId: string): Promise<AIGenerationJobDTO> {
    const response = await this.apiClient.get<AIGenerationJobDTO>(`/content/courses/generate/jobs/${jobId}`)
    return response.data
  }
  
  private async waitForJobCompletion(jobId: string, onProgress?: (progress: number, message: string) => void, maxWaitTime: number = 300000): Promise<Course> {
    const startTime = Date.now()
    const pollInterval = 2000 // Poll every 2 seconds
    
    while (Date.now() - startTime < maxWaitTime) {
      const job = await this.getCourseGenerationJobStatus(jobId)
      
      if (onProgress && job.progress !== undefined && job.message) {
        onProgress(job.progress, job.message)
      }
      
      if (job.status === 'COMPLETED') {
        if (job.result && typeof job.result === 'object' && 'id' in job.result) {
          return job.result as Course
        }
        throw new Error('Job completed but result is invalid')
      }
      
      if (job.status === 'FAILED') {
        throw new Error(job.error || job.message || 'Course generation failed')
      }
      
      // Wait before next poll
      await new Promise(resolve => setTimeout(resolve, pollInterval))
    }
    
    throw new Error('Course generation timed out')
  }
}

export interface GenerateCourseRequest {
  prompt: string
  categoryId?: string
  difficultyLevel?: string
  language?: string
  tags?: string[]
  certificateEligible?: boolean
  maxCompletionDays?: number
  referenceIndexIds?: string[]
  writingFormatId?: string
  writingFormat?: string
  pdfFile?: File
}

export interface AIGenerationJobDTO {
  jobId: string
  jobType: 'COURSE_GENERATION' | 'LECTURE_GENERATION' | 'SUB_LECTURE_GENERATION'
  status: 'PENDING' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  message?: string
  clientId?: string
  userId?: string
  result?: Course | any
  error?: string
  createdAt: string
  updatedAt: string
  progress?: number
}

export interface CourseGenerationIndex {
  id: string
  title: string
  description?: string
  indexType: 'REFERENCE_CONTENT' | 'WRITING_FORMAT'
  fileUrl?: string
  fileSizeBytes?: number
  mimeType?: string
  extractedText?: string
  writingFormat?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export class CourseGenerationIndexApi {
  constructor(private apiClient: ApiClient) {}

  async uploadReferenceContent(
    title: string,
    description: string | undefined,
    file: File
  ): Promise<CourseGenerationIndex> {
    const formData = new FormData()
    formData.append('title', title)
    if (description) formData.append('description', description)
    formData.append('file', file)
    
    const response = await this.apiClient.postForm<CourseGenerationIndex>(
      '/content/course-generation-index/reference-content',
      formData
    )
    return response.data
  }

  async createWritingFormat(
    title: string,
    description: string | undefined,
    writingFormat?: string,
    file?: File
  ): Promise<CourseGenerationIndex> {
    const formData = new FormData()
    formData.append('title', title)
    if (description) formData.append('description', description)
    if (writingFormat) formData.append('writingFormat', writingFormat)
    if (file) formData.append('file', file)
    
    const response = await this.apiClient.postForm<CourseGenerationIndex>(
      '/content/course-generation-index/writing-format',
      formData
    )
    return response.data
  }

  async listIndexes(): Promise<CourseGenerationIndex[]> {
    const response = await this.apiClient.get<CourseGenerationIndex[]>('/content/course-generation-index')
    return Array.isArray(response.data) ? response.data : []
  }

  async getIndexesByType(type: 'REFERENCE_CONTENT' | 'WRITING_FORMAT'): Promise<CourseGenerationIndex[]> {
    const response = await this.apiClient.get<CourseGenerationIndex[]>(`/content/course-generation-index/type/${type}`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getIndex(id: string): Promise<CourseGenerationIndex> {
    const response = await this.apiClient.get<CourseGenerationIndex>(`/content/course-generation-index/${id}`)
    return response.data
  }

  async deleteIndex(id: string): Promise<void> {
    await this.apiClient.delete(`/content/course-generation-index/${id}`)
  }
}

