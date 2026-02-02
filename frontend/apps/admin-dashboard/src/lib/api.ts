import { ApiClient, CoursesApi, CourseGenerationIndexApi, EnrollmentsApi, PaymentsApi, MediaApi, TenantsApi, TenantBrandingApi, TenantFeaturesApi, InstitutesApi, ClassesApi, SectionsApi, LecturesApi, StudentsApi, AnalyticsApi } from '@kunal-ak23/edudron-shared-utils'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

export const apiClient = new ApiClient(GATEWAY_URL)
export const coursesApi = new CoursesApi(apiClient)
export const courseGenerationIndexApi = new CourseGenerationIndexApi(apiClient)
export const enrollmentsApi = new EnrollmentsApi(apiClient)
export const paymentsApi = new PaymentsApi(apiClient)
export const mediaApi = new MediaApi(apiClient)
export const tenantsApi = new TenantsApi(apiClient)
export const tenantBrandingApi = new TenantBrandingApi(apiClient)
export const tenantFeaturesApi = new TenantFeaturesApi(apiClient)
export const institutesApi = new InstitutesApi(apiClient)
export const classesApi = new ClassesApi(apiClient)
export const sectionsApi = new SectionsApi(apiClient)
export const lecturesApi = new LecturesApi(apiClient)
export const studentsApi = new StudentsApi(apiClient)
export const analyticsApi = new AnalyticsApi(apiClient)

// Question management API functions
export interface QuestionOption {
  text: string
  isCorrect: boolean
}

export interface QuestionData {
  questionType: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY' | 'MATCHING'
  questionText: string
  points: number
  options?: QuestionOption[]
  tentativeAnswer?: string
}

export const questionsApi = {
  create: async (examId: string, questionData: QuestionData) => {
    return apiClient.post<any>(`/api/exams/${examId}/questions`, questionData)
  },
  
  update: async (examId: string, questionId: string, questionData: Partial<QuestionData>) => {
    return apiClient.put<any>(`/api/exams/${examId}/questions/${questionId}`, questionData)
  },
  
  delete: async (examId: string, questionId: string) => {
    return apiClient.delete(`/api/exams/${examId}/questions/${questionId}`)
  },
  
  reorder: async (examId: string, questionIds: string[]) => {
    return apiClient.post(`/api/exams/${examId}/questions/reorder`, { questionIds })
  }
}

// Batch exam generation types
export interface BatchExamGenerationRequest {
  courseId: string
  title: string
  description?: string
  instructions?: string
  sectionIds: string[]
  moduleIds: string[]
  generationCriteria?: {
    numberOfQuestions?: number
    difficultyLevel?: 'EASY' | 'MEDIUM' | 'HARD'
    difficultyDistribution?: Record<string, number>
    scorePerDifficulty?: Record<string, number>  // Points to assign per difficulty level (overrides question defaults)
    questionTypes?: string[]
    randomize?: boolean
    uniquePerSection?: boolean
  }
  examSettings?: {
    reviewMethod?: 'INSTRUCTOR' | 'AI' | 'BOTH'
    timeLimitSeconds?: number
    passingScorePercentage?: number
    randomizeQuestions?: boolean
    randomizeMcqOptions?: boolean
    enableProctoring?: boolean
    proctoringMode?: 'DISABLED' | 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING'
    photoIntervalSeconds?: number
    requireIdentityVerification?: boolean
    blockCopyPaste?: boolean
    blockTabSwitch?: boolean
    maxTabSwitchesAllowed?: number
    timingMode?: 'FIXED_WINDOW' | 'FLEXIBLE_START'
    startTime?: string  // ISO 8601 format for FIXED_WINDOW mode
    endTime?: string    // ISO 8601 format for FIXED_WINDOW mode
  }
}

export interface GeneratedExam {
  examId: string
  title: string
  sectionId: string
  sectionName: string
  questionCount: number
  status: string
}

export interface BatchExamGenerationResponse {
  totalRequested: number
  totalCreated: number
  exams: GeneratedExam[]
  errors: string[]
}

export const examsApi = {
  batchGenerate: async (request: BatchExamGenerationRequest): Promise<BatchExamGenerationResponse> => {
    const response = await apiClient.post<BatchExamGenerationResponse>('/api/exams/batch-generate', request)
    return response.data
  },
  
  create: async (examData: any) => {
    return apiClient.post<any>('/api/exams', examData)
  },
  
  getById: async (examId: string) => {
    return apiClient.get<any>(`/api/exams/${examId}`)
  },
  
  list: async () => {
    return apiClient.get<any[]>('/api/exams')
  },
  
  update: async (examId: string, examData: any) => {
    return apiClient.put<any>(`/api/exams/${examId}`, examData)
  },
  
  delete: async (examId: string) => {
    return apiClient.delete(`/api/exams/${examId}`)
  },
  
  schedule: async (examId: string, startTime: string, endTime: string) => {
    return apiClient.put<any>(`/api/exams/${examId}/schedule`, { startTime, endTime })
  },
  
  getCourseSections: async (courseId: string) => {
    return apiClient.get<any[]>(`/api/exams/courses/${courseId}/sections`)
  },
  
  getCourseClasses: async (courseId: string) => {
    return apiClient.get<any[]>(`/api/exams/courses/${courseId}/classes`)
  }
}

