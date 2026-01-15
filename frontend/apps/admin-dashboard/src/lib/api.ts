import { ApiClient, CoursesApi, CourseGenerationIndexApi, EnrollmentsApi, PaymentsApi, MediaApi, TenantsApi, TenantBrandingApi, TenantFeaturesApi, InstitutesApi, ClassesApi, SectionsApi, LecturesApi, StudentsApi } from '@kunal-ak23/edudron-shared-utils'

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

