import { ApiClient, CoursesApi, EnrollmentsApi, PaymentsApi, LecturesApi, FeedbackApi, NotesApi, IssuesApi } from '@kunal-ak23/edudron-shared-utils'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

// Create a singleton ApiClient instance
let apiClientInstance: ApiClient | null = null

export function getApiClient(): ApiClient {
  if (!apiClientInstance) {
    console.log('[lib/api] Creating new ApiClient instance')
    apiClientInstance = new ApiClient(GATEWAY_URL)
  }
  return apiClientInstance
}

// Export API instances - these will use the singleton ApiClient
// The callbacks will be set up by ApiClientSetup component
export const apiClient = getApiClient()
export const coursesApi = new CoursesApi(apiClient)
export const enrollmentsApi = new EnrollmentsApi(apiClient)
export const paymentsApi = new PaymentsApi(apiClient)
export const lecturesApi = new LecturesApi(apiClient)
export const feedbackApi = new FeedbackApi(apiClient)
export const notesApi = new NotesApi(apiClient)
export const issuesApi = new IssuesApi(apiClient)
