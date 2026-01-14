import { ApiClient } from './ApiClient'

/**
 * Enum defining all tenant-level feature flags.
 */
export enum TenantFeatureType {
  STUDENT_SELF_ENROLLMENT = 'STUDENT_SELF_ENROLLMENT'
}

/**
 * DTO representing a single tenant feature with its effective value and metadata.
 */
export interface TenantFeatureDto {
  feature: TenantFeatureType
  enabled: boolean // Effective value (default or override)
  isOverridden: boolean // Whether tenant has custom value
  defaultValue: boolean // The default value for this feature
}

/**
 * API client for managing tenant feature settings.
 */
export class TenantFeaturesApi {
  constructor(private apiClient: ApiClient) {}

  /**
   * Get all features with their effective values for the current tenant.
   */
  async getAllFeatures(): Promise<TenantFeatureDto[]> {
    const response = await this.apiClient.get<TenantFeatureDto[]>('/api/tenant/features')
    return Array.isArray(response.data) ? response.data : []
  }

  /**
   * Get a specific feature with its effective value.
   */
  async getFeature(feature: TenantFeatureType): Promise<TenantFeatureDto> {
    const response = await this.apiClient.get<TenantFeatureDto>(`/api/tenant/features/${feature}`)
    return response.data
  }

  /**
   * Update a feature override for the current tenant.
   */
  async updateFeature(feature: TenantFeatureType, enabled: boolean): Promise<TenantFeatureDto> {
    const response = await this.apiClient.put<TenantFeatureDto>(
      `/api/tenant/features/${feature}`,
      { enabled }
    )
    return response.data
  }

  /**
   * Reset a feature to its default value (delete override).
   */
  async resetFeature(feature: TenantFeatureType): Promise<void> {
    await this.apiClient.delete(`/api/tenant/features/${feature}`)
  }

  /**
   * Get the effective value for a specific feature.
   */
  async isFeatureEnabled(feature: TenantFeatureType): Promise<boolean> {
    const dto = await this.getFeature(feature)
    return dto.enabled
  }

  /**
   * Convenience method for checking student self-enrollment feature.
   */
  async isStudentSelfEnrollmentEnabled(): Promise<boolean> {
    try {
      const response = await this.apiClient.get<boolean>('/api/tenant/features/student-self-enrollment')
      return response.data === true
    } catch (error) {
      // Default to false if call fails
      console.warn('Failed to fetch student self-enrollment feature, defaulting to false:', error)
      return false
    }
  }
}
