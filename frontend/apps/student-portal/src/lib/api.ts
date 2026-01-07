import { ApiClient, CoursesApi, EnrollmentsApi, PaymentsApi, LecturesApi } from '@edudron/shared-utils'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

export const apiClient = new ApiClient(GATEWAY_URL)
export const coursesApi = new CoursesApi(apiClient)
export const enrollmentsApi = new EnrollmentsApi(apiClient)
export const paymentsApi = new PaymentsApi(apiClient)
export const lecturesApi = new LecturesApi(apiClient)


