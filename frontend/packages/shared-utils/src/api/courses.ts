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
    const response = await this.apiClient.get<Course>(`/content/courses/${id}`)
    return response.data
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

  async getChapters(courseId: string): Promise<Chapter[]> {
    const response = await this.apiClient.get<Chapter[]>(`/content/courses/${courseId}/chapters`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getAssessments(courseId: string): Promise<Assessment[]> {
    const response = await this.apiClient.get<Assessment[]>(`/content/courses/${courseId}/assessments`)
    return Array.isArray(response.data) ? response.data : []
  }

  async generateCourse(request: GenerateCourseRequest): Promise<Course> {
    const response = await this.apiClient.post<Course>('/content/courses/generate', request)
    return response.data
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

