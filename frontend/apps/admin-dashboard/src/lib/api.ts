import { ApiClient, CoursesApi, CourseGenerationIndexApi, EnrollmentsApi, PaymentsApi, MediaApi, TenantsApi, TenantBrandingApi, InstitutesApi, ClassesApi, SectionsApi } from '@edudron/shared-utils'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

export const apiClient = new ApiClient(GATEWAY_URL)
export const coursesApi = new CoursesApi(apiClient)
export const courseGenerationIndexApi = new CourseGenerationIndexApi(apiClient)
export const enrollmentsApi = new EnrollmentsApi(apiClient)
export const paymentsApi = new PaymentsApi(apiClient)
export const mediaApi = new MediaApi(apiClient)
export const tenantsApi = new TenantsApi(apiClient)
export const tenantBrandingApi = new TenantBrandingApi(apiClient)
export const institutesApi = new InstitutesApi(apiClient)
export const classesApi = new ClassesApi(apiClient)
export const sectionsApi = new SectionsApi(apiClient)

